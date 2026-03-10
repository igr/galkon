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

    get("/health") {
        call.respond(OkResponse(version = gameVersion))
    }

    post("/games") {
        val req = call.receive<CreateGameRequest>()
        val seed = if (req.seed.isNotEmpty()) stringToSeed(req.seed) else null
        call.respondResult(
            lobby.createGame(seed).map { CreateGameResponse(it) },
            HttpStatusCode.Created,
        )
    }

    post("/games/{code}/join") {
        val session = lobby.requireSession(call.requireParam("code"))
        val req = call.receive<JoinGameRequest>()
        call.respondResult(
            session.join(req.playerName).map { JoinGameResponse(it.value) },
            HttpStatusCode.Created,
        )
    }

    post("/games/{code}/start") {
        val session = lobby.requireSession(call.requireParam("code"))
        val req = call.receive<StartGameRequest>()
        call.respondResult(session.start(PlayerId(req.playerId)).map { OkResponse() })
    }

    post("/games/{code}/setup/{playerId}") {
        val session = lobby.requireSession(call.requireParam("code"))
        val playerId = PlayerId(call.requireParam("playerId"))
        val req = call.receive<SetupVoteRequest>()
        call.respondResult(session.submitSetupVote(playerId, req.agree).map { OkResponse() })
    }

    post("/games/{code}/orders/{playerId}") {
        val session = lobby.requireSession(call.requireParam("code"))
        val playerId = PlayerId(call.requireParam("playerId"))
        val req = call.receive<SubmitOrdersRequest>()
        call.respondResult(session.submitOrders(playerId, req.orders.map { it.toDomain() }).map { OkResponse() })
    }

    get("/games/{code}/state/{playerId}") {
        val session = lobby.requireSession(call.requireParam("code"))
        val playerId = PlayerId(call.requireParam("playerId"))
        call.respondResult(session.getState(playerId).map { it.toResponse() })
    }

    get("/games/{code}/scores") {
        val session = lobby.requireSession(call.requireParam("code"))
        call.respondResult(session.getScores().map { ScoresResponse(it.map { s -> s.toDto() }) })
    }

    get("/games/{code}/status") {
        val session = lobby.requireSession(call.requireParam("code"))
        call.respond(session.getStatus().toDto())
    }
}
