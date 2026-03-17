package galkon.bot

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

private val BOT_NAME = "Claude-$botCode"

fun main(args: Array<String>) {
    val create = args.firstOrNull() == "--create"
    val positionalArgs = if (create) args.drop(1) else args.toList()

    if (!create && positionalArgs.isEmpty()) {
        System.err.println("Usage: gk-bot <gameCode> [serverUrl]")
        System.err.println("       gk-bot --create [serverUrl]")
        System.err.println("  serverUrl defaults to http://localhost:8080")
        kotlin.system.exitProcess(1)
    }

    val serverUrl = if (create) {
        positionalArgs.firstOrNull() ?: "http://localhost:8080"
    } else {
        positionalArgs.getOrElse(1) { "http://localhost:8080" }
    }

    val api = ApiClient(serverUrl)

    runBlocking {
        try {
            println("=== Galkon Bot ($BOT_NAME) ===")

            val gameCode: String
            val playerId: String

            if (create) {
                println("Creating new game...")
                val resp = api.createGame()
                gameCode = resp.gameCode
                println("Game created: $gameCode")

                val joinResp = api.join(gameCode, BOT_NAME)
                playerId = joinResp.playerId
                println("Joined as host! Player ID: $playerId")
                println("Waiting for other players to join...")

                while (true) {
                    val status = api.status(gameCode)
                    if (status.players.size >= 2) {
                        api.startGame(gameCode, playerId)
                        println("Game started with ${status.players.size} players!")
                        break
                    }
                    delay(2000)
                }
            } else {
                gameCode = positionalArgs[0]
                println("Joining game $gameCode as $BOT_NAME...")
                val joinResp = api.join(gameCode, BOT_NAME)
                playerId = joinResp.playerId
                println("Joined! Player ID: $playerId")
            }

            println("Server: $serverUrl")
            println()

            gameLoop(api, gameCode, playerId)
        } finally {
            api.close()
        }
    }
}

private suspend fun gameLoop(api: ApiClient, gameCode: String, playerId: String) {
    var lastTurn = -1

    while (true) {
        val status = api.status(gameCode)

        when (val phase = status.phase) {
            is JsonPrimitive if phase.content == "lobby" -> {
                println("[lobby] Waiting for game to start...")
                delay(2000)
            }

            is JsonObject if phase["status"]?.jsonPrimitive?.content == "setup" -> {
                val round = phase["round"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                println("[setup] Round $round - voting to accept galaxy")
                api.setupVote(gameCode, playerId, agree = true)
                delay(2000)
            }

            is JsonObject if phase["status"]?.jsonPrimitive?.content == "in_progress" -> {
                val turn = phase["turn"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                if (turn == lastTurn) {
                    // Waiting for other players to submit
                    delay(2000)
                    continue
                }

                lastTurn = turn
                playTurn(api, gameCode, playerId, turn)
            }

            is JsonPrimitive if phase.content == "finished" -> {
                println("[finished] Game over!")
                printScores(api, gameCode)
                break
            }

            else -> {
                println("[?] Unknown phase: $phase - waiting...")
                delay(2000)
            }
        }
    }
}

private suspend fun playTurn(api: ApiClient, gameCode: String, playerId: String, turn: Int) {
    println("╔══════════════════════════════════════╗")
    println("            TURN $turn")
    println("╚══════════════════════════════════════╝")

    val view = api.state(gameCode, playerId)

    // Show summary
    val myPlanets = view.planets.filter {
        it.owner is JsonPrimitive && it.owner.jsonPrimitive.content == playerId
    }
    val totalShips = myPlanets.sumOf { it.ships ?: 0 }
    println("Planets: ${myPlanets.size} | Ships: $totalShips | Fleets: ${view.fleets.size}")

    // Build prompt and ask Claude
    val prompt = buildPrompt(playerId, view)
    println("Asking Claude for orders...")

    val response = askClaude(gameCode, prompt)
    println("Claude says:\n$response\n")

    // Parse and submit orders
    val myPlanetLabels = myPlanets.map { it.label }.toSet()
    val orders = parseOrders(response, myPlanetLabels)

    if (orders.isEmpty()) {
        println("No orders this turn.")
    } else {
        println("Orders:")
        for (o in orders) {
            println("  ${o.from} -> ${o.to}: ${o.ships} ships")
        }
    }

    api.submitOrders(gameCode, playerId, orders)
    println("Orders submitted. Waiting for next turn...\n")
}

private suspend fun printScores(api: ApiClient, gameCode: String) {
    try {
        val scores = api.scores(gameCode)
        println("\nFinal standings:")
        for ((i, s) in scores.scores.withIndex()) {
            println("  ${i + 1}. ${s.playerName} - ${s.planetsOwned} planets, ${s.totalShips} ships, score: ${"%.1f".format(s.score)}")
        }
    } catch (e: Exception) {
        println("Could not fetch scores: ${e.message}")
    }
}
