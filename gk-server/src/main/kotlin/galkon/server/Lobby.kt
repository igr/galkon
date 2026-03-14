package galkon.server

import galkon.common.GameConfig
import galkon.game.GameSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Thread-safe game session registry. */
class Lobby {
    private val sessions = ConcurrentHashMap<String, GameSession>()

    fun createGame(seed: Long? = null, numPlanets: Int = 26): Result<String> {
        if (sessions.size >= GameConfig.MAX_GAMES) {
            return Result.failure(Exception("Max games reached"))
        }
        val code = generateCode()
        val config = GameConfig(
            seed = seed ?: Random.nextLong(),
            numPlanets = numPlanets.coerceIn(4, 36),
        )
        val session = GameSession(code, config)
        sessions[code] = session
        return Result.success(code)
    }

    fun getSession(code: String): GameSession? = sessions[code]

    fun allSessions(): List<GameSession> = sessions.values.toList()

    fun removeGame(code: String) {
        sessions.remove(code)
    }

    fun removeInactive() {
        val now = System.currentTimeMillis()
        val timeoutMs = GameConfig.INACTIVE_TIMEOUT.inWholeMilliseconds
        sessions.entries.removeIf { (_, session) ->
            now - session.lastActivity > timeoutMs
        }
    }

    private fun generateCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        while (true) {
            val code = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
            if (!sessions.containsKey(code)) return code
        }
    }
}
