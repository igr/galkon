package galkon.common

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Configuration constants for a game. */
data class GameConfig(
    val numPlanets: Int = 26,
    val numTurns: Int = 40,
    val grid: Dimension = Dimension(16, 16),
    val space: Dimension = Dimension(16, 16),
    val seed: Long = Random.nextLong(),
) {
    companion object {
        /** Home planet starting values. */
        val HOME_SHIPS = ShipCount(10)
        val HOME_PRODUCTION = ShipCount(10)
        val HOME_KILL_RATIO = KillRatio(40)

        /** Neutral planet ranges. */
        val NEUTRAL_SHIPS_MIN = ShipCount(1)
        val NEUTRAL_SHIPS_MAX = ShipCount(10)
        val NEUTRAL_PRODUCTION_MIN = ShipCount(3)
        val NEUTRAL_PRODUCTION_MAX = ShipCount(10)
        val NEUTRAL_KILL_RATIO_MIN = KillRatio(15)
        val NEUTRAL_KILL_RATIO_MAX = KillRatio(50)

        /** Ship speed in light-years per turn. */
        const val SHIP_SPEED = 2.0

        /** Home court bonus range for defenders. */
        val HOME_COURT_BONUS_MIN = KillRatio(5)
        val HOME_COURT_BONUS_MAX = KillRatio(31)

        /** Defender kill ratio cap after home court bonus. */
        val DEFENDER_KILL_CAP = KillRatio(60)

        /** Max players per game. */
        const val MAX_PLAYERS = 8

        /** Max concurrent games. */
        const val MAX_GAMES = 100

        /** Inactive game timeout. */
        val INACTIVE_TIMEOUT: Duration = 30.minutes

        /** Cleanup interval. */
        val CLEANUP_INTERVAL: Duration = 60.seconds
    }
}
