package galkon.client

import galkon.common.*
import kotlinx.coroutines.await
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.fetch.RequestInit
import kotlin.js.json

private fun headers(): dynamic = json("Content-Type" to "application/json")

private suspend fun fetchJson(url: String, method: String = "GET", body: String? = null): String {
    val init = RequestInit(
        method = method,
        headers = headers(),
    )
    if (body != null) {
        init.body = body
    }
    val response = kotlinx.browser.window.fetch(url, init).await()
    val text = response.text().await()
    if (!response.ok) {
        val msg = try {
            GameJson.decodeFromString<ErrorResponse>(text).error
        } catch (_: Exception) {
            text
        }
        throw Exception(msg)
    }
    return text
}

suspend fun apiCreateGame(serverUrl: String, seed: String = "", numPlanets: Int = 26, numTurns: Int = 40): CreateGameResponse {
    val body = GameJson.encodeToString(CreateGameRequest.serializer(), CreateGameRequest(seed, numPlanets, numTurns))
    val text = fetchJson("$serverUrl/games", "POST", body)
    return GameJson.decodeFromString(text)
}

suspend fun apiJoinGame(serverUrl: String, code: String, playerName: String): JoinGameResponse {
    val body = GameJson.encodeToString(JoinGameRequest.serializer(), JoinGameRequest(playerName))
    val text = fetchJson("$serverUrl/games/$code/join", "POST", body)
    return GameJson.decodeFromString(text)
}

suspend fun apiLeaveGame(serverUrl: String, code: String, playerId: String) {
    fetchJson("$serverUrl/games/$code/leave/$playerId", "POST")
}

suspend fun apiStartGame(serverUrl: String, code: String, playerId: String) {
    val body = GameJson.encodeToString(StartGameRequest.serializer(), StartGameRequest(playerId))
    fetchJson("$serverUrl/games/$code/start", "POST", body)
}

suspend fun apiSubmitSetupVote(serverUrl: String, code: String, playerId: String, agree: Boolean) {
    val body = GameJson.encodeToString(SetupVoteRequest.serializer(), SetupVoteRequest(agree))
    fetchJson("$serverUrl/games/$code/setup/$playerId", "POST", body)
}

suspend fun apiSubmitOrders(serverUrl: String, code: String, playerId: String, orders: List<FleetOrderDto>) {
    val body = GameJson.encodeToString(SubmitOrdersRequest.serializer(), SubmitOrdersRequest(orders))
    fetchJson("$serverUrl/games/$code/orders/$playerId", "POST", body)
}

suspend fun apiGetState(serverUrl: String, code: String, playerId: String): PlayerViewResponse {
    val text = fetchJson("$serverUrl/games/$code/state/$playerId")
    return GameJson.decodeFromString(text)
}

suspend fun apiGetStatus(serverUrl: String, code: String): GameStatusResponse {
    val text = fetchJson("$serverUrl/games/$code/status")
    return GameJson.decodeFromString(text)
}

suspend fun apiGetScores(serverUrl: String, code: String): ScoresResponse {
    val text = fetchJson("$serverUrl/games/$code/scores")
    return GameJson.decodeFromString(text)
}

/** Parse the phase from the JSON element. */
fun parsePhase(phase: kotlinx.serialization.json.JsonElement): Pair<String, Int> {
    return when {
        phase is JsonPrimitive && phase.isString -> {
            phase.content to 0
        }
        else -> {
            val obj = phase.jsonObject
            val status = obj["status"]?.jsonPrimitive?.content ?: "lobby"
            val turn = obj["turn"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: obj["round"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: 0
            status to turn
        }
    }
}
