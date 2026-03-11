package galkon.game

import galkon.common.*
import galkon.common.Owner.Player

internal fun PlayerId.ownsPlanet(planet: Planet) = planet.owner == Player(this)

internal fun PlayerId.canLaunchShipsFrom(planet: Planet, ships: ShipCount) =
    ownsPlanet(planet) && planet.ships >= ships && ships.some

internal fun GameState.hasOneOrNoPlayersLeft(): Boolean {
    val activePlayers = this.players.keys
    val eliminated = this.eliminated
    val remaining = activePlayers.filter { it !in eliminated }
    return remaining.size <= 1
}

internal fun GameState.hasReachedTurnLimit()=
    currentTurn >= config.numTurns
