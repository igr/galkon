package galkon.common

import kotlin.jvm.JvmInline
import kotlin.random.Random

/** Validated planet identifier — single character A-Z or 0-9.
 *  Natural ordering: A-Z first, then 0-9. */
data class PlanetId(val label: String) : Comparable<PlanetId> {
    init {
        require(label.length == 1 && label[0] in VALID_CHARS) {
            "Invalid planet label: $label"
        }
    }

    /**
     * Natural ordering: A-Z first, then 0-9.
     * This ensures that planets are sorted in a consistent and intuitive way,
     */
    override fun compareTo(other: PlanetId): Int {
        val a = label[0]
        val b = other.label[0]
        val aIsDigit = a.isDigit()
        val bIsDigit = b.isDigit()
        if (aIsDigit != bIsDigit) return if (aIsDigit) 1 else -1
        return a.compareTo(b)
    }

    override fun toString() = label

    companion object {
        // to do not correct if number of planets > 36, but good enough for now
        private val VALID_CHARS = ('A'..'Z') + ('0'..'9')

        val ALL_LABELS: List<PlanetId> = VALID_CHARS.map { PlanetId(it.toString()) }.sorted()
    }
}

/** Typed player identifier (UUID string). */
@JvmInline
value class PlayerId(val value: String) {
    override fun toString() = value
}

/** Typed player name. */
@JvmInline
value class PlayerName(val value: String) {
    override fun toString() = value
}

/** Typed kill ratio (percentage 0–100). */
@JvmInline
value class KillRatio(val value: Int) : Comparable<KillRatio> {
    operator fun plus(increment: KillRatio) = KillRatio(value + increment.value)
    operator fun minus(decrement: KillRatio) = KillRatio(value - decrement.value)
    fun coerceAtMost(maximum: KillRatio) = if (value > maximum.value) maximum else this
    fun coerceAtLeast(minimum: KillRatio) = if (value < minimum.value) minimum else this
    override operator fun compareTo(other: KillRatio) = value.compareTo(other.value)
    override fun toString() = value.toString()

    companion object {
        val ZERO = KillRatio(0)
        fun random(from: Int, until: Int) = KillRatio(Random.nextInt(from, until))
        fun random(min: KillRatio, max: KillRatio) = KillRatio(Random.nextInt(min.value, max.value + 1))
    }
}

/** Typed ship count with arithmetic operators. */
@JvmInline
value class ShipCount(val value: Int) : Comparable<ShipCount> {
    val some: Boolean get() = value > 0
    val none: Boolean get() = value <= 0
    operator fun plus(other: ShipCount) = ShipCount(value + other.value)
    operator fun minus(other: ShipCount) = ShipCount(value - other.value)
    fun decrease() = ShipCount(value - 1)
    fun increase() = ShipCount(value + 1)
    override operator fun compareTo(other: ShipCount) = value.compareTo(other.value)
    override fun toString() = value.toString()

    companion object {
        val ZERO = ShipCount(0)
        fun random(min: ShipCount, max: ShipCount) = ShipCount(Random.nextInt(min.value, max.value + 1))
    }
}

/** Sum ship counts from elements of a collection. */
inline fun <T> Iterable<T>.sumShips(selector: (T) -> ShipCount): ShipCount =
    ShipCount(sumOf { selector(it).value })

/** Position on the galaxy grid. */
data class GridPos(val x: Int, val y: Int)

/** Width–height pair. */
data class Dimension(val width: Int, val height: Int)

/** Owner of a planet: either neutral or a player. */
sealed interface Owner {
    data object Neutral : Owner
    data class Player(val id: PlayerId) : Owner
}

/** A planet in the galaxy. */
data class Planet(
    val label: PlanetId,
    val position: GridPos,
    val owner: Owner,
    val ships: ShipCount,
    val production: ShipCount,
    val killRatio: KillRatio,
)

/** Update a single planet by label in place. */
fun MutableList<Planet>.update(label: PlanetId, transform: (Planet) -> Planet) {
    forEachIndexed { i, p -> if (p.label == label) this[i] = transform(p) }
}

/** An order to send ships from one planet to another. */
data class FleetOrder(
    val from: PlanetId,
    val to: PlanetId,
    val ships: ShipCount,
)

/** A fleet in transit between planets. */
data class Fleet(
    val owner: PlayerId,
    val ships: ShipCount,
    val killRatio: KillRatio,
    val from: PlanetId,
    val to: PlanetId,
    val distanceRemaining: Double,
)

/** The phase of a game. */
sealed interface GamePhase {
    data object Lobby : GamePhase
    data class SetUp(val round: Int) : GamePhase
    data class InProgress(val turn: Int) : GamePhase
    data object Finished : GamePhase
}

/** Attacker–defender ship count pair for combat. */
data class Combatants(val attacker: ShipCount, val defender: ShipCount)

/** Outcome of a battle. */
enum class BattleOutcome { CONQUERED, REPELLED, DRAW }

/** Events that occur during turn resolution. */
sealed interface TurnEvent {
    data class FleetLaunched(val player: PlayerId, val from: PlanetId, val to: PlanetId, val ships: ShipCount) : TurnEvent
    data class FleetArrived(val player: PlayerId, val to: PlanetId, val ships: ShipCount) : TurnEvent
    data class BattleResolved(
        val planet: PlanetId,
        val attacker: PlayerId,
        val defender: Owner,
        val forces: Combatants,
        val surviving: Combatants,
        val outcome: BattleOutcome,
        val attackerKillRatio: KillRatio,
        val defenderKillRatio: KillRatio,
    ) : TurnEvent
    data class PlanetRevolted(val planet: PlanetId, val formerOwner: PlayerId) : TurnEvent
    data class ProductionCompleted(val player: PlayerId, val planet: PlanetId, val ships: ShipCount) : TurnEvent
    data class TurnStarted(val turn: Int) : TurnEvent
    data class EventProductionChanged(val planet: PlanetId, val owner: PlayerId, val newProduction: ShipCount) : TurnEvent
    data class EventKillRatioChanged(val planet: PlanetId, val owner: PlayerId, val newKillRatio: KillRatio) : TurnEvent
    data class EventRevoltThwarted(val planet: PlanetId, val owner: PlayerId) : TurnEvent
}

/** A player's score at the end of the game. */
data class PlayerScore(
    val player: PlayerInfo,
    val planetsOwned: Int,
    val totalShips: ShipCount,
    val score: Double,
)

/** Player info for status/lobby display. */
data class PlayerInfo(
    val id: PlayerId,
    val name: PlayerName,
)

/** Game status snapshot. */
data class GameStatus(
    val gameCode: String,
    val phase: GamePhase,
    val players: List<PlayerInfo>,
)
