package galkon.game

import galkon.common.*
import galkon.common.KillRatio.Companion.ZERO
import kotlin.random.Random

/**
 * Maybe fire a random event (~33% chance).
 * Selects a random planet; only fires if owned by a player.
 * Returns the event result or null if no event fired.
 */
internal fun maybeFireEvent(state: GameState): TurnEvent? {
    if (Random.nextInt(3) != 1) return null // ~33% chance

    if (state.planets.isEmpty()) return null
    val planet = state.planets.random()
    val owner = planet.owner
    if (owner !is Owner.Player) return null

    val roll = Random.nextInt(90) // 0..89

    return when {
        roll > 66 -> {
            // Change Production: +/- 1-9, ~50/50 increase/decrease, floor at 0
            val change = Random.nextInt(1, 10)
            val increase = Random.nextBoolean()
            val newProd = if (increase) ShipCount(planet.production.value + change)
            else ShipCount(maxOf(0, planet.production.value - change))
            state.planets.update(planet.label) { it.copy(production = newProd) }
            TurnEvent.EventProductionChanged(planet.label, owner.id, newProd)
        }

        roll > 33 -> {
            // Change Kill Ratio: +/- 1-19, ~60% increase / ~40% decrease, floor at 0
            val change = KillRatio.random(1, 20)
            val increase = Random.nextInt(100) < 60
            val newKill = if (increase) planet.killRatio + change
            else (planet.killRatio - change).coerceAtLeast(ZERO)
            state.planets.update(planet.label) { it.copy(killRatio = newKill) }
            TurnEvent.EventKillRatioChanged(planet.label, owner.id, newKill)
        }

        roll > 1 -> {
            // Revolt (flavor text only — no game effect)
            TurnEvent.EventRevoltThwarted(planet.label, owner.id)
        }

        else -> null
    }
}
