package galkon.bot

import galkon.common.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

class ApiClient(private val baseUrl: String) {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(GameJson)
        }
        install(ContentEncoding) {
            gzip()
        }
    }

    suspend fun createGame(numPlanets: Int = 26, numTurns: Int = 40): CreateGameResponse =
        http.post("$baseUrl/games") {
            contentType(ContentType.Application.Json)
            setBody(CreateGameRequest(numPlanets = numPlanets, numTurns = numTurns))
        }.body()

    suspend fun startGame(gameCode: String, playerId: String) {
        http.post("$baseUrl/games/$gameCode/start") {
            contentType(ContentType.Application.Json)
            setBody(StartGameRequest(playerId))
        }.assertSuccess()
    }

    suspend fun join(gameCode: String, playerName: String): JoinGameResponse =
        http.post("$baseUrl/games/$gameCode/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinGameRequest(playerName))
        }.body()

    suspend fun status(gameCode: String): GameStatusResponse =
        http.get("$baseUrl/games/$gameCode/status").body()

    suspend fun galaxy(gameCode: String): GalaxyResponse =
        http.get("$baseUrl/games/$gameCode/galaxy").body()

    suspend fun state(gameCode: String, playerId: String): PlayerViewResponse =
        http.get("$baseUrl/games/$gameCode/state/$playerId").body()

    suspend fun submitOrders(gameCode: String, playerId: String, orders: List<FleetOrderDto>) {
        http.post("$baseUrl/games/$gameCode/orders/$playerId") {
            contentType(ContentType.Application.Json)
            setBody(SubmitOrdersRequest(orders))
        }.assertSuccess()
    }

    suspend fun setupVote(gameCode: String, playerId: String, agree: Boolean) {
        http.post("$baseUrl/games/$gameCode/setup/$playerId") {
            contentType(ContentType.Application.Json)
            setBody(SetupVoteRequest(agree))
        }.assertSuccess()
    }

    private suspend fun HttpResponse.assertSuccess() {
        if (!status.isSuccess()) {
            throw RuntimeException("API error ${status.value}: ${bodyAsText()}")
        }
    }

    suspend fun scores(gameCode: String): ScoresResponse =
        http.get("$baseUrl/games/$gameCode/scores").body()

    fun close() = http.close()
}
