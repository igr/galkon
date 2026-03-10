package galkon.game

import galkon.common.*

/** Full mutable state of a game. */
data class GameState(
    val config: GameConfig,
    var phase: GamePhase,
    val planets: MutableList<Planet>,
    val fleets: MutableList<Fleet>,
    val players: MutableMap<PlayerId, PlayerInfo>,
    val orders: MutableMap<PlayerId, List<FleetOrder>>,
    val turnEvents: MutableList<TurnEvent>,
    val eliminated: MutableList<PlayerId>,
    val visited: MutableMap<PlayerId, MutableSet<PlanetId>>,
    val setupVotes: MutableMap<PlayerId, Boolean> = mutableMapOf(),
) {
    /** Get the current turn number. */
    val currentTurn: Int
        get() = when (phase) {
            is GamePhase.InProgress -> (phase as GamePhase.InProgress).turn
            is GamePhase.Finished -> config.numTurns
            else -> 0
        }

    companion object {
        fun lobby(config: GameConfig) = GameState(
            config = config,
            phase = GamePhase.Lobby,
            planets = mutableListOf(),
            fleets = mutableListOf(),
            players = mutableMapOf(),
            orders = mutableMapOf(),
            turnEvents = mutableListOf(),
            eliminated = mutableListOf(),
            visited = mutableMapOf(),
        )
    }
}

/** Generate universe and enter the SetUp voting phase. */
fun transitionGameToSetUp(config: GameConfig, players: Map<PlayerId, PlayerInfo>, round: Int = 1): GameState {
    val planets = generateUniverse(config, players.keys.toList())
    return GameState(
        config = config,
        phase = GamePhase.SetUp(round),
        planets = planets.toMutableList(),
        fleets = mutableListOf(),
        players = players.toMutableMap(),
        orders = mutableMapOf(),
        turnEvents = mutableListOf(),
        eliminated = mutableListOf(),
        visited = mutableMapOf(),
    )
}

/** Transition from SetUp to InProgress, keeping the current galaxy. */
fun transitionSetUpToInProgress(state: GameState) {
    state.phase = GamePhase.InProgress(1)
    state.setupVotes.clear()
}

/** Create the initial game state from config and players. */
fun transitionGameToInProgress(config: GameConfig, players: Map<PlayerId, PlayerInfo>): GameState {
    val planets = generateUniverse(config, players.keys.toList())
    return GameState(
        config = config,
        phase = GamePhase.InProgress(1),
        planets = planets.toMutableList(),
        fleets = mutableListOf(),
        players = players.toMutableMap(),
        orders = mutableMapOf(),
        turnEvents = mutableListOf(),
        eliminated = mutableListOf(),
        visited = mutableMapOf(),
    )
}
