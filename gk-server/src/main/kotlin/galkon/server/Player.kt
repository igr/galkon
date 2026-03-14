package galkon.server

import galkon.common.*
import galkon.game.*

/** Convert a PlayerView to the serializable response DTO. */
fun PlayerView.toResponse(): PlayerViewResponse {
    val planetDtos = planets.map { vp ->
        when (vp) {
            is ViewPlanet.Owned -> ViewPlanetDto(
                label = vp.label.label,
                x = vp.position.x,
                y = vp.position.y,
                owner = vp.owner.toJson(),
                ships = vp.ships.value,
                production = vp.production.value,
                killRatio = vp.killRatio.value,
            )
            is ViewPlanet.Visible -> ViewPlanetDto(
                label = vp.label.label,
                x = vp.position.x,
                y = vp.position.y,
                owner = vp.owner.toJson(),
                ships = vp.ships?.value,
                production = vp.production?.value,
                killRatio = vp.killRatio?.value,
            )
        }
    }

    return PlayerViewResponse(
        phase = phase.toJson(),
        turn = turn,
        planets = planetDtos,
        fleets = fleets.map { it.toDto() },
        turnEvents = turnEvents.map { it.toDto() },
        players = players.map { it.toDto() },
        gridWidth = grid.width,
        gridHeight = grid.height,
        spaceWidth = space.width,
        spaceHeight = space.height,
        seed = seed,
        numTurns = numTurns,
        setupRound = (phase as? GamePhase.SetUp)?.round,
        setupMaxRounds = if (phase is GamePhase.SetUp) 3 else null,
    )
}
