package galkon.game

import galkon.common.Planet
import galkon.common.PlayerInfo
import galkon.common.PlayerScore
import galkon.common.ShipCount
import galkon.common.sumShips

/**
 * Calculate scores for all players.
 * score = INT(averageKill * planetShips / 8) + (planetsOwned * 50)
 */
fun calculateScores(state: GameState): List<PlayerScore> =
    state.players.map { (playerId, playerInfo) ->
        val ownedPlanets = state.planets.filter { playerId.ownsPlanet(it) }
        when (ownedPlanets.size) {
            0 -> {
                PlayerScore(playerInfo, 0, ShipCount.ZERO, 0.0)
            }
            else -> {
                score(ownedPlanets, playerInfo)
            }
        }
    }.sortedWith(compareByDescending<PlayerScore> { it.planetsOwned }.thenByDescending { it.score })

private fun score(
    ownedPlanets: List<Planet>,
    playerInfo: PlayerInfo,
): PlayerScore {
    val planetsOwned = ownedPlanets.size
    val planetShips = ownedPlanets.sumShips { it.ships }
    val totalKillRatio = ownedPlanets.sumOf { it.killRatio.value }
    val averageKill = totalKillRatio.toDouble() / planetsOwned
    val score = ((averageKill * planetShips.value / 8.0).toInt() + planetsOwned * 50).toDouble()

    return PlayerScore(playerInfo, planetsOwned, planetShips, score)
}
