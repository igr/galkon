package galkon.server

import galkon.common.*
import galkon.common.stringToSeed
import galkon.game.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Exception that maps to an HTTP error response. */
class HttpException(val status: HttpStatusCode, override val message: String) : Exception(message)

/** Extract a required path parameter or throw. */
private fun RoutingCall.requireParam(name: String): String =
    parameters[name] ?: throw HttpException(HttpStatusCode.BadRequest, "Missing $name")

/** Respond with a Result, using the given status on success. */
private suspend inline fun <reified T : Any> RoutingCall.respondResult(
    result: Result<T>,
    successStatus: HttpStatusCode = HttpStatusCode.OK,
) {
    result.fold(
        onSuccess = { respond(successStatus, it) },
        onFailure = { throw HttpException(HttpStatusCode.BadRequest, it.message ?: "Error") },
    )
}

private fun Lobby.requireSession(code: String): GameSession =
    getSession(code) ?: throw HttpException(HttpStatusCode.NotFound, "Game not found")

fun Routing.gameRoutes(lobby: Lobby) {

    /**
     * Check server health and version.
     *
     * Returns the current server version derived from the git commit SHA.
     * Use this endpoint for liveness probes and deployment verification.
     *
     * Responses:
     * - 200 [OkResponse] Server is healthy.
     */
    get("/health") {
        call.respond(OkResponse(version = gameVersion))
    }

    /**
     * Create a new game lobby.
     *
     * Creates a new game session and returns a join code that other players
     * can use to join. An optional seed can be provided for reproducible
     * galaxy generation.
     *
     * Responses:
     * - 201 [CreateGameResponse] Game created successfully with a join code.
     */
    post("/games") {
        val req = call.receive<CreateGameRequest>()
        val seed = if (req.seed.isNotEmpty()) stringToSeed(req.seed) else null
        call.respondResult(
            lobby.createGame(seed, req.numPlanets, req.numTurns).map { CreateGameResponse(it) },
            HttpStatusCode.Created,
        )
    }

    /**
     * Join an existing game.
     *
     * Adds a player to the game lobby identified by the join code.
     * The player receives a unique player ID used for subsequent requests.
     *
     * Path: code [String] The game join code.
     *
     * Responses:
     * - 201 [JoinGameResponse] Player joined successfully, returns the player ID.
     * - 400 Player name is invalid or game cannot be joined.
     * - 404 Game not found.
     */
    post("/games/{code}/join") {
        val session = lobby.requireSession(call.requireParam("code"))
        val req = call.receive<JoinGameRequest>()
        call.respondResult(
            session.join(req.playerName).map { JoinGameResponse(it.value) },
            HttpStatusCode.Created,
        )
    }

    /**
     * Leave a game.
     *
     * Removes a player from the game lobby. If the last player leaves,
     * the game session is removed entirely.
     *
     * Path: code [String] The game join code.
     * Path: playerId [String] The player's unique ID.
     *
     * Responses:
     * - 200 [OkResponse] Player left successfully.
     * - 400 Player cannot leave at this point.
     * - 404 Game not found.
     */
    post("/games/{code}/leave/{playerId}") {
        val code = call.requireParam("code")
        val session = lobby.requireSession(code)
        val playerId = PlayerId(call.requireParam("playerId"))
        call.respondResult(session.leave(playerId).map { removeGame ->
            if (removeGame) lobby.removeGame(code)
            OkResponse()
        })
    }

    /**
     * Start the game.
     *
     * Transitions the game from the lobby phase to galaxy setup.
     * Only the player who created the game can start it, and at least
     * the minimum number of players must have joined.
     *
     * Path: code [String] The game join code.
     *
     * Responses:
     * - 200 [OkResponse] Game started successfully.
     * - 400 Not enough players or caller is not the host.
     * - 404 Game not found.
     */
    post("/games/{code}/start") {
        val session = lobby.requireSession(call.requireParam("code"))
        val req = call.receive<StartGameRequest>()
        call.respondResult(session.start(PlayerId(req.playerId)).map { OkResponse() })
    }

    /**
     * Submit a setup vote.
     *
     * During the setup phase, each player votes to accept or reject the
     * generated galaxy. The game proceeds when all players agree.
     *
     * Path: code [String] The game join code.
     * Path: playerId [String] The player's unique ID.
     *
     * Responses:
     * - 200 [OkResponse] Vote recorded.
     * - 400 Voting is not allowed in the current phase.
     * - 404 Game not found.
     */
    post("/games/{code}/setup/{playerId}") {
        val session = lobby.requireSession(call.requireParam("code"))
        val playerId = PlayerId(call.requireParam("playerId"))
        val req = call.receive<SetupVoteRequest>()
        call.respondResult(session.submitSetupVote(playerId, req.agree).map { OkResponse() })
    }

    /**
     * Submit fleet orders for a turn.
     *
     * Sends the player's fleet movement orders for the current turn.
     * Once all players have submitted orders, the turn is resolved.
     *
     * Path: code [String] The game join code.
     * Path: playerId [String] The player's unique ID.
     *
     * Responses:
     * - 200 [OkResponse] Orders submitted.
     * - 400 Orders are invalid or not accepted in the current phase.
     * - 404 Game not found.
     */
    post("/games/{code}/orders/{playerId}") {
        val session = lobby.requireSession(call.requireParam("code"))
        val playerId = PlayerId(call.requireParam("playerId"))
        val req = call.receive<SubmitOrdersRequest>()
        call.respondResult(session.submitOrders(playerId, req.orders.map { it.toDomain() }).map { OkResponse() })
    }

    /**
     * Get the game state for a player.
     *
     * Returns the player's view of the current game state, including
     * their planets, fleets, visible enemies, and turn events.
     * Each player only sees information relevant to their position.
     *
     * Path: code [String] The game join code.
     * Path: playerId [String] The player's unique ID.
     *
     * Responses:
     * - 200 The player's game state view.
     * - 400 Invalid player.
     * - 404 Game not found.
     */
    get("/games/{code}/state/{playerId}") {
        val session = lobby.requireSession(call.requireParam("code"))
        val playerId = PlayerId(call.requireParam("playerId"))
        call.respondResult(session.getState(playerId).map { it.toResponse() })
    }

    /**
     * Get the galaxy structure.
     *
     * Returns the galaxy grid dimensions and the list of all planets
     * with their names and positions. Available once the game has
     * started (after the lobby phase).
     *
     * Path: code [String] The game join code.
     *
     * Responses:
     * - 200 [GalaxyResponse] Galaxy structure.
     * - 400 Game has not started yet.
     * - 404 Game not found.
     */
    get("/games/{code}/galaxy") {
        val session = lobby.requireSession(call.requireParam("code"))
        call.respondResult(session.getGalaxy())
    }

    /**
     * Get the game scores.
     *
     * Returns the current scoreboard with each player's stats including
     * planets owned, total ships, and kill ratio.
     *
     * Path: code [String] The game join code.
     *
     * Responses:
     * - 200 [ScoresResponse] Current game scores.
     * - 404 Game not found.
     */
    get("/games/{code}/scores") {
        val session = lobby.requireSession(call.requireParam("code"))
        call.respondResult(session.getScores().map { ScoresResponse(it.map { s -> s.toDto() }) })
    }

    /**
     * Get the game status.
     *
     * Returns the current game phase, turn number, and list of players
     * with their ready state. Use this for polling the lobby or
     * checking if all players have submitted their turns.
     *
     * Path: code [String] The game join code.
     *
     * Responses:
     * - 200 Current game status.
     * - 404 Game not found.
     */
    get("/games/{code}/status") {
        val session = lobby.requireSession(call.requireParam("code"))
        call.respond(session.getStatus().toDto())
    }
}
