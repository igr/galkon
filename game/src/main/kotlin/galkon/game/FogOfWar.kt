package galkon.game

import galkon.common.*

/** A planet as seen by a specific player. */
sealed interface ViewPlanet {
    val label: PlanetId
    val position: GridPos
    val owner: Owner

    data class Owned(
        override val label: PlanetId,
        override val position: GridPos,
        override val owner: Owner,
        val ships: ShipCount,
        val production: ShipCount,
        val killRatio: KillRatio,
    ) : ViewPlanet

    data class Visible(
        override val label: PlanetId,
        override val position: GridPos,
        override val owner: Owner,
        val ships: ShipCount?,
        val production: ShipCount?,
        val killRatio: KillRatio?,
    ) : ViewPlanet
}

/** The player's fog-of-war filtered view of the game. */
data class PlayerView(
    val phase: GamePhase,
    val turn: Int,
    val planets: List<ViewPlanet>,
    val fleets: List<Fleet>,
    val turnEvents: List<TurnEvent>,
    val players: List<PlayerInfo>,
    val grid: Dimension,
    val space: Dimension,
    val seed: String,
)

/** Generate a fog-of-war filtered view for a specific player. */
fun makePlayerView(state: GameState, playerId: PlayerId): PlayerView {
    val currentTurn = (state.phase as? GamePhase.InProgress)?.turn ?: 0
    val playerVisited = state.visited[playerId] ?: emptySet()

    val fullVisibility = state.phase is GamePhase.SetUp

    val viewPlanets = state.planets.map { p ->
        when {
            fullVisibility || p.owner == Owner.Player(playerId) -> {
                ViewPlanet.Owned(p.label, p.position, p.owner, p.ships, p.production, p.killRatio)
            }
            else -> {
                val isEnemy = p.owner is Owner.Player
                val visited = p.label in playerVisited
                val reveal = isEnemy || visited
                ViewPlanet.Visible(
                    p.label, p.position, p.owner,
                    ships = if (reveal) p.ships else null,
                    production = if (reveal) p.production else null,
                    killRatio = if (reveal) p.killRatio else null,
                )
            }
        }
    }

    val viewFleets = state.fleets.filter { it.owner == playerId }

    val viewResults = state.turnEvents.filter { result ->
        when (result) {
            is TurnEvent.FleetLaunched -> result.player == playerId
            is TurnEvent.FleetArrived -> result.player == playerId
            is TurnEvent.BattleResolved -> result.attacker == playerId
            is TurnEvent.PlanetRevolted -> result.formerOwner == playerId
            is TurnEvent.ProductionCompleted -> result.player == playerId
            is TurnEvent.TurnStarted -> true
            is TurnEvent.EventProductionChanged -> result.owner == playerId
            is TurnEvent.EventKillRatioChanged -> result.owner == playerId
            is TurnEvent.EventRevoltThwarted -> result.owner == playerId
        }
    }

    val playerList = state.players.values.toList()

    return PlayerView(
        phase = state.phase,
        turn = currentTurn,
        planets = viewPlanets,
        fleets = viewFleets,
        turnEvents = viewResults,
        players = playerList,
        grid = state.config.grid,
        space = state.config.space,
        seed = seedToString(state.config.seed),
    )
}
