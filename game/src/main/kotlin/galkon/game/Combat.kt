package galkon.game

import galkon.common.Combatants
import galkon.common.GameConfig
import galkon.common.GameConfig.Companion.DEFENDER_KILL_CAP
import galkon.common.GameConfig.Companion.HOME_COURT_BONUS_MAX
import galkon.common.GameConfig.Companion.HOME_COURT_BONUS_MIN
import galkon.common.KillRatio
import galkon.common.ShipCount
import kotlin.random.Random

/**
 * Resolve combat between attackers and defenders using the BASIC per-round algorithm.
 * Home court bonus is computed ONCE before battle.
 * Each round: one random roll (chanceOfDeath 20..80), at most one ship per side dies.
 * Attacker is checked first — if attacker reaches 0, round ends without checking defender.
 */
internal fun resolveCombat(
    attackers: ShipCount,
    attackerKillRatio: KillRatio,
    defenders: ShipCount,
    defenderKillRatio: KillRatio
): Combatants {
    var atk = attackers
    var def = defenders

    // Home court bonus computed ONCE before battle
    val homeBonus = KillRatio.random(HOME_COURT_BONUS_MIN, HOME_COURT_BONUS_MAX)
    val defenderKill = (defenderKillRatio + homeBonus).coerceAtMost(DEFENDER_KILL_CAP)
    val attackerKill = attackerKillRatio

    while (def.some) {
        val chanceOfDeath = KillRatio.random(20, 81) // 20..80

        if (attackerKill < chanceOfDeath) {
            atk = atk.decrease()
        }

        if (atk.none) break // attacker eliminated first

        if (defenderKill < chanceOfDeath) {
            def = def.decrease()
        }
    }

    return Combatants(atk, def)
}
