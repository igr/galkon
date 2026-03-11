package galkon.game

import galkon.common.*
import java.util.UUID

private const val MAX_SETUP_ROUNDS = 3

/** Wraps game state with synchronization for a single game. */
class GameSession(val gameCode: String, gamConfig: GameConfig) {
    private var state = GameState.phaseLobby(gamConfig)

    /** The first player to join is the host. */
    private var hostId: PlayerId? = null

    @Volatile
    var lastActivity: Long = System.currentTimeMillis()
        private set

    private fun touch() { lastActivity = System.currentTimeMillis() }

    @Synchronized
    fun join(playerName: String): Result<PlayerId> {
        touch()
        return when {
            state.phase != GamePhase.Lobby -> {
                Result.failure(Exception("Game already started"))
            }

            state.players.size >= GameConfig.MAX_PLAYERS -> {
                Result.failure(Exception("Game is full (max ${GameConfig.MAX_PLAYERS} players)"))
            }

            else -> {
                val playerId = PlayerId(UUID.randomUUID().toString())
                state.players[playerId] = PlayerInfo(playerId, PlayerName(playerName))
                if (hostId == null) hostId = playerId
                Result.success(playerId)
            }
        }
    }

    /** Returns true if the game should be removed (host left). */
    @Synchronized
    fun leave(playerId: PlayerId): Result<Boolean> {
        touch()
        return when {
            state.phase != GamePhase.Lobby -> Result.failure(Exception("Game already started"))
            playerId !in state.players -> Result.failure(Exception("Invalid player"))
            playerId == hostId -> Result.success(true)
            else -> {
                state.players.remove(playerId)
                Result.success(false)
            }
        }
    }

    @Synchronized
    fun start(playerId: PlayerId): Result<Unit> {
        touch()
        return when {
            state.phase != GamePhase.Lobby -> {
                Result.failure(Exception("Game already started"))
            }

            playerId != hostId -> {
                Result.failure(Exception("Only the host can start the game"))
            }

            state.players.size < 2 -> {
                Result.failure(Exception("Need at least 2 players"))
            }

            else -> {
                state = phaseGameToSetUp(state.config, state.players)
                Result.success(Unit)
            }
        }
    }

    @Synchronized
    fun submitSetupVote(playerId: PlayerId, agree: Boolean): Result<Unit> {
        touch()
        val phase = state.phase
        return when {
            phase !is GamePhase.SetUp -> Result.failure(Exception("Game not in setup phase"))
            playerId !in state.players -> Result.failure(Exception("Invalid player"))
            else -> {
                state.setupVotes[playerId] = agree

                if (state.setupVotes.size == state.players.size) {
                    val allAgree = state.setupVotes.values.all { it }
                    if (allAgree || phase.round >= MAX_SETUP_ROUNDS) {
                        phaseSetUpToInProgress(state)
                    } else {
                        val newConfig = state.config.copy(seed = state.config.seed + 1)
                        state = phaseGameToSetUp(newConfig, state.players, phase.round + 1)
                    }
                }
                Result.success(Unit)
            }
        }
    }

    @Synchronized
    fun submitOrders(playerId: PlayerId, orders: List<FleetOrder>): Result<Unit> {
        touch()
        when {
            state.phase !is GamePhase.InProgress -> {
                return Result.failure(Exception("Game not in progress"))
            }

            playerId !in state.players -> {
                return Result.failure(Exception("Invalid player"))
            }

            // Auto-resolve when all non-eliminated players have submitted
            else -> {
                state.orders[playerId] = orders

                // Auto-resolve when all non-eliminated players have submitted
                val activePlayers = state.players.keys.filter { it !in state.eliminated }
                if (activePlayers.all { it in state.orders }) {
                    resolveTurn(state)
                }

                return Result.success(Unit)
            }
        }
    }

    @Synchronized
    fun getState(playerId: PlayerId): Result<PlayerView> {
        touch()
        return when (playerId) {
            !in state.players -> {
                Result.failure(Exception("Invalid player"))
            }

            else -> Result.success(makePlayerView(state, playerId))
        }
    }

    @Synchronized
    fun getStatus(): GameStatus {
        touch()
        return GameStatus(
            gameCode = gameCode,
            phase = state.phase,
            players = state.players.values.toList(),
        )
    }

    /** Read-only snapshot for dashboard; does NOT update lastActivity. */
    @Synchronized
    fun snapshotStatus(): GameStatus = GameStatus(
        gameCode = gameCode,
        phase = state.phase,
        players = state.players.values.toList(),
    )

    @Synchronized
    fun getScores(): Result<List<PlayerScore>> {
        touch()
        return when {
            state.phase != GamePhase.Finished -> {
                Result.failure(Exception("Game not finished"))
            }

            else -> Result.success(calculateScores(state))
        }
    }
}
