package galkon.game

import galkon.common.*

internal fun PlayerId.canLaunchShipsFrom(planet: Planet, ships: ShipCount) =
    planet.owner == Owner.Player(this) && planet.ships >= ships && ships.some

internal fun GameState.hasOneOrNoPlayersLeft(): Boolean {
    val activePlayers = this.players.keys
    val eliminated = this.eliminated
    val remaining = activePlayers.filter { it !in eliminated }
    return remaining.size <= 1
}

internal fun GameState.hasReachedTurnLimit()=
    currentTurn >= config.numTurns
