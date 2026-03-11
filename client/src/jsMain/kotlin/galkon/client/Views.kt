package galkon.client

import galkon.common.*
import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/** Render the entire app based on current state. */
fun renderApp(state: AppState) {
    val app = document.getElementById("app") ?: return

    // Save focus state before re-render
    val activeId = (document.activeElement as? HTMLInputElement)?.id?.takeIf { it.isNotEmpty() }
    val selectionStart = (document.activeElement as? HTMLInputElement)?.selectionStart
    val selectionEnd = (document.activeElement as? HTMLInputElement)?.selectionEnd

    app.innerHTML = ""
    when (state.screen) {
        Screen.LOBBY -> app.appendChild(renderLobby(state))
        Screen.GAME -> app.appendChild(renderGame(state))
        Screen.SCORES -> app.appendChild(renderScores(state))
    }

    // Restore focus after re-render
    if (activeId != null) {
        val el = document.getElementById(activeId) as? HTMLInputElement
        el?.focus()
        el?.selectionStart = selectionStart
        el?.selectionEnd = selectionEnd
    }
}

private fun renderLobby(state: AppState): HTMLElement = document.create.div("lobby") {
    h1 { +"GALKON" }

    // Server URL (collapsible)
    details("section") {
        summary { +"Server URL" }
        textInput {
            id = "input-server-url"
            value = state.serverUrl
            placeholder = "http://localhost:8080"
            autoComplete = "off"
            onInputFunction = { e -> updateState { copy(serverUrl = (e.target as HTMLInputElement).value) } }
        }
    }

    if (state.gameCode.isEmpty() || state.playerId.isEmpty()) {
        // Name input
        div("section") {
            h2 { +"Your Name" }
            textInput {
                id = "input-player-name"
                value = state.playerName
                placeholder = "Enter name"
                autoComplete = "off"
                onInputFunction = { e -> updateState { copy(playerName = (e.target as HTMLInputElement).value) } }
            }
        }

        // Create game
        div("section") {
            h2 { +"Create New Game" }
            textInput {
                id = "input-seed"
                value = state.inputSeed
                placeholder = "Seed (optional)"
                autoComplete = "off"
                onInputFunction = { e -> updateState { copy(inputSeed = (e.target as HTMLInputElement).value.uppercase()) } }
            }
            button { +"Create Game"; onClickFunction = { doCreateGame() } }
        }

        // Join game
        div("section") {
            h2 { +"Join Game" }
            textInput {
                id = "input-game-code"
                value = state.gameCode
                placeholder = "Game code"
                autoComplete = "off"
                onInputFunction = { e -> updateState { copy(gameCode = (e.target as HTMLInputElement).value.uppercase()) } }
            }
            button { +"Join Game"; onClickFunction = { doJoinGame() } }
        }
    } else {
        // Waiting room
        div("section") {
            h2 { +"Game Code" }
            div("game-code") { +state.gameCode }

            h2 { +"Players" }
            div("player-list") {
                state.players.forEachIndexed { idx, p ->
                    div { +"${idx + 1}. ${p.name}" }
                }
            }

            if (state.players.firstOrNull()?.id == state.playerId) {
                button { +"Start Game"; onClickFunction = { doStartGame() } }
            } else {
                div {
                    +"Waiting for host to start..."
                    style = "color: ${Colors.TEXT_DIM}; margin-top: 10px"
                }
            }
            button(classes = "btn-cancel") { +"Cancel"; onClickFunction = { doCancelGame() } }
        }
    }

    if (state.error.isNotEmpty()) {
        div("error") { +state.error }
    }
}

private fun renderGame(state: AppState): HTMLElement = document.create.div("game") {
    // Header
    div("header") {
        h1 { +"GALKON - Game: ${state.gameCode}" }
        div("header-right") {
            if (state.gamePhase == "setup") {
                span("turn-info") { +"Setup ${state.setupRound}/${state.setupMaxRounds}" }
            } else {
                span("turn-info") { +"Turn: ${state.currentTurn}/40" }
            }
            div("menu-wrapper") {
                button(classes = "menu-btn") {
                    +"\u2261"
                    onClickFunction = { updateState { copy(menuOpen = !menuOpen) } }
                }
                if (state.menuOpen) {
                    div("menu-dropdown") {
                        div("menu-item") {
                            +"Exit Game"
                            onClickFunction = { updateState { AppState(serverUrl = serverUrl) } }
                        }
                        div("menu-item") {
                            +"About"
                            onClickFunction = { updateState { copy(menuOpen = false, aboutOpen = true) } }
                        }
                    }
                }
            }
        }
    }
}. also { div ->
    div.appendChild(renderGalaxy(state))
    if (state.battleEvent != null) {
        div.appendChild(renderBattleSidebar(state))
    } else {
        div.appendChild(renderSidebar(state))
    }

    if (state.gamePhase == "setup") {
        div.appendChild(renderSetupVoteBar(state))
    } else {
        div.appendChild(renderOrders(state))
        div.appendChild(renderEvents(state))
    }

    if (state.error.isNotEmpty()) {
        div.appendChild(document.create.div("error") { +state.error })
    }

    if (state.aboutOpen) {
        div.appendChild(document.create.div("about-overlay") {
            div("about-popup") {
                p { +"Just wanted to play this game again :)" }
                p {
                    a(href = "https://igo.rs") { +"igo.rs" }
                    +" / "
                    a(href = "https://github.com/igr/galkon") { +"github.com/igr/galkon" }
                }
                button { +"Close"; onClickFunction = { updateState { copy(aboutOpen = false) } } }
            }
        })
    }
}

private fun renderSetupVoteBar(state: AppState): HTMLElement = document.create.div("setup-vote") {
    if (state.setupVoteSubmitted) {
        div("waiting") { +"Vote submitted. Waiting for other players..." }
    } else {
        div("vote-prompt") {
            span { +"Do you agree with this galaxy layout?" }
            if (state.setupRound >= state.setupMaxRounds) {
                span("warn") { +" (Final round)" }
            }
        }
        div("vote-buttons") {
            button(classes = "btn-agree") {
                +"Agree"
                onClickFunction = { doSetupVote(true) }
            }
            button(classes = "btn-disagree") {
                +"Disagree"
                onClickFunction = { doSetupVote(false) }
            }
        }
    }
}

private fun renderScores(state: AppState): HTMLElement = document.create.div("scores") {
    h1 { +"GAME OVER" }

    table {
        thead {
            tr {
                for (h in listOf("#", "Player", "Planets", "Ships", "Score")) {
                    th { +h }
                }
            }
        }
        tbody {
            state.scores.forEachIndexed { idx, s ->
                tr {
                    td { +"${idx + 1}" }
                    td { +s.playerName }
                    td { +"${s.planetsOwned}" }
                    td { +"${s.totalShips}" }
                    td { +s.score.toFixed(0) }
                }
            }
        }
    }

    button { +"Back to Lobby"; onClickFunction = { updateState { AppState(serverUrl = serverUrl) } } }
}

private fun Double.toFixed(digits: Int): String = asDynamic().toFixed(digits) as String

// ---- Utility ----

fun ownerColor(owner: kotlinx.serialization.json.JsonElement): String {
    val prim = owner.jsonPrimitive
    val content = prim.content
    return if (content == "neutral") Colors.NEUTRAL
    else if (content == currentState.playerId) Colors.ACCENT
    else Colors.DANGER
}

fun ownerName(owner: kotlinx.serialization.json.JsonElement): String {
    val prim = owner.jsonPrimitive
    val content = prim.content
    if (content == "neutral") return "Neutral"
    if (content == currentState.playerId) return "You"
    return currentState.players.find { it.id == content }?.name ?: "Player"
}
