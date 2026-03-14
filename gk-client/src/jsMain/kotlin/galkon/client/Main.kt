@file:Suppress("unused")

package galkon.client

import galkon.common.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.await

private val styles = js("require('./style.scss')")

var currentState = AppState()
    private set

private val scope = MainScope()
private var pollTimerId: Int? = null

fun main() {
    // Auto-detect server URL
    val loc = window.location
    currentState = currentState.copy(serverUrl = "${loc.protocol}//${loc.host}")
    renderApp(currentState)
}

fun updateState(transform: AppState.() -> AppState) {
    currentState = currentState.transform()
    renderApp(currentState)
}

fun showRules() {
    if (currentState.rulesHtml.isNotEmpty()) {
        updateState { copy(rulesOpen = true) }
        return
    }
    scope.launch {
        try {
            val html = window.fetch("rules.html").await().text().await()
            updateState { copy(rulesHtml = html, rulesOpen = true) }
        } catch (e: Exception) {
            updateState { copy(error = "Failed to load rules") }
        }
    }
}

// ---- Game lifecycle actions ----

fun doCreateGame() {
    scope.launch {
        try {
            val resp = apiCreateGame(currentState.serverUrl, currentState.inputSeed, currentState.inputNumPlanets)
            val joinResp = apiJoinGame(currentState.serverUrl, resp.gameCode, currentState.playerName.ifEmpty { "Player" })
            updateState {
                copy(
                    gameCode = resp.gameCode,
                    playerId = joinResp.playerId,
                    error = "",
                )
            }
            startPollingStatus()
        } catch (e: Exception) {
            updateState { copy(error = e.message ?: "Failed to create game") }
        }
    }
}

fun doJoinGame() {
    scope.launch {
        try {
            val resp = apiJoinGame(currentState.serverUrl, currentState.gameCode, currentState.playerName.ifEmpty { "Player" })
            updateState {
                copy(
                    playerId = resp.playerId,
                    error = "",
                )
            }
            startPollingStatus()
        } catch (e: Exception) {
            updateState { copy(error = e.message ?: "Failed to join game") }
        }
    }
}

fun doCancelGame() {
    scope.launch {
        try {
            stopPolling()
            apiLeaveGame(currentState.serverUrl, currentState.gameCode, currentState.playerId)
            updateState { AppState(serverUrl = serverUrl) }
        } catch (e: Exception) {
            updateState { copy(error = e.message ?: "Failed to cancel") }
        }
    }
}

fun doStartGame() {
    scope.launch {
        try {
            apiStartGame(currentState.serverUrl, currentState.gameCode, currentState.playerId)
            updateState { copy(error = "") }
            // Poll will detect the transition
        } catch (e: Exception) {
            updateState { copy(error = e.message ?: "Failed to start game") }
        }
    }
}

fun doAddOrder() {
    val state = currentState
    val ships = state.orderShips.toIntOrNull()
    if (state.orderFrom.isEmpty() || state.orderTo.isEmpty() || ships == null || ships <= 0) return

    val planet = state.planets.find { it.label == state.orderFrom }
    val available = (planet?.ships ?: 0) - (allocatedShips(state)[state.orderFrom] ?: 0)
    if (ships > available) {
        updateState { copy(error = "Not enough ships on ${state.orderFrom} ($available available)") }
        return
    }

    val order = FleetOrderDto(state.orderFrom, state.orderTo, ships)
    updateState {
        copy(
            pendingOrders = pendingOrders + order,
            orderFrom = "",
            orderTo = "",
            orderShips = "",
            error = "",
        )
    }
}

fun doSetupVote(agree: Boolean) {
    scope.launch {
        try {
            apiSubmitSetupVote(currentState.serverUrl, currentState.gameCode, currentState.playerId, agree)
            updateState { copy(setupVoteSubmitted = true, error = "") }
            startPollingState()
        } catch (e: Exception) {
            updateState { copy(error = e.message ?: "Failed to submit vote") }
        }
    }
}

fun doSubmitOrders() {
    val state = currentState
    scope.launch {
        try {
            apiSubmitOrders(state.serverUrl, state.gameCode, state.playerId, state.pendingOrders)
            updateState {
                copy(
                    pendingOrders = emptyList(),
                    ordersSubmitted = true,
                    error = "",
                )
            }
            // Start polling for turn resolution
            startPollingState()
        } catch (e: Exception) {
            updateState { copy(error = e.message ?: "Failed to submit orders") }
        }
    }
}

// ---- Polling ----

private fun startPollingStatus() {
    stopPolling()
    pollTimerId = window.setInterval({
        scope.launch { pollStatus() }
    }, 2000)
}

private fun startPollingState() {
    stopPolling()
    pollTimerId = window.setInterval({
        scope.launch { pollState() }
    }, 2000)
}

private fun stopPolling() {
    pollTimerId?.let { window.clearInterval(it) }
    pollTimerId = null
}

private suspend fun pollStatus() {
    try {
        val status = apiGetStatus(currentState.serverUrl, currentState.gameCode)
        val (phase, turn) = parsePhase(status.phase)

        updateState {
            copy(
                players = status.players,
                gamePhase = phase,
                currentTurn = turn,
            )
        }

        if (phase == "setup" || phase == "in_progress") {
            // Game started! Switch to game screen and fetch full state
            stopPolling()
            val state = apiGetState(currentState.serverUrl, currentState.gameCode, currentState.playerId)
            val (p2, t2) = parsePhase(state.phase)
            updateState {
                copy(
                    screen = Screen.GAME,
                    gamePhase = p2,
                    currentTurn = t2,
                    planets = state.planets,
                    fleets = state.fleets,
                    turnEvents = state.turnEvents,
                    players = state.players,
                    gridWidth = state.gridWidth,
                    gridHeight = state.gridHeight,
                    spaceWidth = state.spaceWidth,
                    spaceHeight = state.spaceHeight,
                    seed = state.seed,
                    setupRound = state.setupRound ?: 0,
                    setupMaxRounds = state.setupMaxRounds ?: 3,
                    setupVoteSubmitted = false,
                )
            }
        }
    } catch (e: Exception) {
        // Silently ignore poll errors
    }
}

private suspend fun pollState() {
    try {
        val state = apiGetState(currentState.serverUrl, currentState.gameCode, currentState.playerId)
        val (phase, turn) = parsePhase(state.phase)

        if (phase == "finished") {
            stopPolling()
            val scores = apiGetScores(currentState.serverUrl, currentState.gameCode)
            updateState {
                copy(
                    screen = Screen.SCORES,
                    scores = scores.scores,
                )
            }
            return
        }

        // Setup phase: detect round change or transition to in_progress
        if (phase == "setup") {
            val newRound = state.setupRound ?: 0
            if (newRound != currentState.setupRound) {
                updateState {
                    copy(
                        gamePhase = phase,
                        planets = state.planets,
                        players = state.players,
                        gridWidth = state.gridWidth,
                        gridHeight = state.gridHeight,
                        spaceWidth = state.spaceWidth,
                        spaceHeight = state.spaceHeight,
                        seed = state.seed,
                        setupRound = newRound,
                        setupMaxRounds = state.setupMaxRounds ?: 3,
                        setupVoteSubmitted = false,
                    )
                }
            }
            return
        }

        if (phase == "in_progress" && currentState.gamePhase == "setup") {
            // Transitioned from setup to in_progress
            stopPolling()
            updateState {
                copy(
                    gamePhase = phase,
                    currentTurn = turn,
                    planets = state.planets,
                    fleets = state.fleets,
                    turnEvents = state.turnEvents,
                    players = state.players,
                    seed = state.seed,
                    setupRound = 0,
                    setupVoteSubmitted = false,
                    ordersSubmitted = false,
                )
            }
            return
        }

        // Check if turn advanced (turn resolution happened)
        if (turn > currentState.currentTurn && currentState.ordersSubmitted) {
            stopPolling()
            updateState {
                copy(
                    gamePhase = phase,
                    currentTurn = turn,
                    planets = state.planets,
                    fleets = state.fleets,
                    turnEvents = state.turnEvents,
                    players = state.players,
                    ordersSubmitted = false,
                    eventPlaybackIndex = -2,
                    seed = state.seed,
                )
            }
            // Play back events with delay
            playEvents()
        }
    } catch (e: Exception) {
        // Silently ignore poll errors
    }
}

private var battleAnimFrameId: Int? = null
private var battleEndTimerId: Int? = null
private var battleContinuation: (() -> Unit)? = null

private fun playEvents() {
    val events = currentState.turnEvents
    if (events.isEmpty()) return
    playEventAt(0, events)
}

private fun playEventAt(index: Int, events: List<TurnEventDto>) {
    if (index >= events.size) {
        updateState { copy(eventPlaybackIndex = events.size, battleEvent = null) }
        return
    }

    val event = events[index]

    if (event.type == "battle") {
        // Show battle sidebar before the event appears in the log
        updateState { copy(battleEvent = event) }
        battleContinuation = {
            updateState { copy(eventPlaybackIndex = index) }
            window.setTimeout({
                playEventAt(index + 1, events)
            }, 300)
        }
        startBattleAnimation(event)
    } else {
        updateState { copy(eventPlaybackIndex = index) }
        window.setTimeout({
            playEventAt(index + 1, events)
        }, 300)
    }
}

private fun startBattleAnimation(event: TurnEventDto) {
    val startTime = window.performance.now()
    val duration = 3000.0
    val winnerDelay = 1500

    val initialAttacker = event.attackerShips ?: 0
    val initialDefender = event.defenderShips ?: 0
    val finalAttacker = event.attackerSurviving ?: 0
    val finalDefender = event.defenderSurviving ?: 0

    fun animate(timestamp: Double) {
        val elapsed = timestamp - startTime
        val progress = minOf(elapsed / duration, 1.0)

        // Ease-out curve for more dramatic feel
        val eased = 1.0 - (1.0 - progress) * (1.0 - progress)

        val currentAttacker = initialAttacker - ((initialAttacker - finalAttacker) * eased).toInt()
        val currentDefender = initialDefender - ((initialDefender - finalDefender) * eased).toInt()

        // Update DOM directly (avoid re-render)
        kotlinx.browser.document.getElementById("battle-attacker-ships")?.textContent = "$currentAttacker"
        kotlinx.browser.document.getElementById("battle-defender-ships")?.textContent = "$currentDefender"

        if (progress < 1.0) {
            battleAnimFrameId = window.requestAnimationFrame { animate(it) }
        } else {
            // Show result
            (kotlinx.browser.document.getElementById("battle-result") as? org.w3c.dom.HTMLElement)
                ?.style?.opacity = "1"
            // Wait then continue
            battleEndTimerId = window.setTimeout({
                val cont = battleContinuation
                battleContinuation = null
                updateState { copy(battleEvent = null) }
                cont?.invoke()
            }, winnerDelay)
        }
    }

    battleAnimFrameId = window.requestAnimationFrame { animate(it) }
}

fun skipBattle() {
    battleAnimFrameId?.let { window.cancelAnimationFrame(it) }
    battleAnimFrameId = null
    battleEndTimerId?.let { window.clearTimeout(it) }
    battleEndTimerId = null
    val cont = battleContinuation
    battleContinuation = null
    updateState { copy(battleEvent = null) }
    cont?.invoke()
}
