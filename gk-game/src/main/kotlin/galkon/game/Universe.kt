package galkon.game

import galkon.common.*
import kotlin.math.sqrt
import kotlin.random.Random

/** Calculate distance between two grid positions. */
private fun distance(a: GridPos, b: GridPos): Double {
    val dx = (b.x - a.x).toDouble()
    val dy = (b.y - a.y).toDouble()
    return sqrt(dx * dx + dy * dy)
}

/** Calculate distance between two planets. */
internal fun distance(p1: Planet, p2: Planet): Double = distance(p1.position, p2.position)

/** Generate the universe with planets for the given players. */
internal fun generateUniverse(config: GameConfig, playerIds: List<PlayerId>): List<Planet> {
    val labels = PlanetId.ALL_LABELS.take(config.numPlanets)
    val posRng = Random(config.seed)

    // Generate home planet positions (no minimum spacing, only prevent exact overlap)
    val homePositions = generateHomePositions(playerIds.size, config, posRng)

    // Generate remaining positions
    val occupied = homePositions.toMutableSet()
    val neutralCount = config.numPlanets - playerIds.size
    val neutralPositions = generatePositions(neutralCount, config, occupied, posRng)

    // Build home planets
    val homePlanets = labels.take(playerIds.size).zip(homePositions).zip(playerIds).map { (labelPos, playerId) ->
        val (label, pos) = labelPos
        Planet(
            label = label,
            position = pos,
            owner = Owner.Player(playerId),
            ships = GameConfig.HOME_SHIPS,
            production = GameConfig.HOME_PRODUCTION,
            killRatio = GameConfig.HOME_KILL_RATIO,
        )
    }

    // Build neutral planets
    val neutralPlanets = labels.drop(playerIds.size).zip(neutralPositions).map { (label, pos) ->
        Planet(
            label = label,
            position = pos,
            owner = Owner.Neutral,
            ships = ShipCount.random(GameConfig.NEUTRAL_SHIPS_MIN, GameConfig.NEUTRAL_SHIPS_MAX),
            production = ShipCount.random(GameConfig.NEUTRAL_PRODUCTION_MIN, GameConfig.NEUTRAL_PRODUCTION_MAX),
            killRatio = KillRatio.random(GameConfig.NEUTRAL_KILL_RATIO_MIN, GameConfig.NEUTRAL_KILL_RATIO_MAX),
        )
    }

    return homePlanets + neutralPlanets
}

private fun generateHomePositions(count: Int, config: GameConfig, rng: Random): List<GridPos> {
    val positions = mutableListOf<GridPos>()
    while (positions.size < count) {
        val pos = randomPosition(config, rng)
        if (pos !in positions) {
            positions.add(pos)
        }
    }
    return positions
}

private fun generatePositions(count: Int, config: GameConfig, occupied: MutableSet<GridPos>, rng: Random): List<GridPos> {
    val positions = mutableListOf<GridPos>()
    while (positions.size < count) {
        val pos = randomPosition(config, rng)
        if (pos !in occupied) {
            occupied.add(pos)
            positions.add(pos)
        }
    }
    return positions
}

private fun randomPosition(config: GameConfig, rng: Random): GridPos =
    GridPos(rng.nextInt(config.grid.width), rng.nextInt(config.grid.height))
