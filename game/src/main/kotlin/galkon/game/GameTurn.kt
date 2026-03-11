package galkon.game

import galkon.common.*

/** Resolve a full turn: launch -> move -> combat (with events) -> production -> victory check. */
fun resolveTurn(state: GameState) {
    initTurn(state)
    launchFleets(state)
    moveFleets(state)
    resolveArrivals(state)
    resolveProduction(state)
    updateEliminations(state)
    updateGameInProgressPhase(state)

    state.orders.clear()
}

private fun initTurn(state: GameState) {
    state.turnEvents.clear()
    state.turnEvents.add(TurnEvent.TurnStarted(state.currentTurn))
}

private fun updateGameInProgressPhase(state: GameState) {
    val currentTurn = state.currentTurn
    when {
        state.hasOneOrNoPlayersLeft() -> {
            state.phase = GamePhase.Finished
        }
        state.hasReachedTurnLimit() -> {
            // End-game fleet resolution before finishing
            resolveEndGame(state)
            state.phase = GamePhase.Finished
        }
        else -> {
            state.phase = GamePhase.InProgress(currentTurn + 1)
        }
    }
}


private fun updateEliminations(state: GameState) {
    val activePlayers = state.players.keys
    val eliminated = activePlayers.filter { pid ->
        val hasPlanet = state.planets.any { it.owner == Owner.Player(pid) }
        val hasFleet = state.fleets.any { it.owner == pid }
        !hasPlanet && !hasFleet
    }
    state.eliminated.clear()
    state.eliminated.addAll(eliminated)
}

private fun launchFleets(state: GameState) {
    state.forEachOrder { playerId, order ->
        val origin = state.findPlanetById(order.from) ?: return@forEachOrder
        val target = state.findPlanetById(order.to) ?: return@forEachOrder

        if (!playerId.canLaunchShipsFrom(origin, order.ships)) return@forEachOrder

        val dist = distance(origin, target)
        val fleet = Fleet(
            owner = playerId,
            ships = order.ships,
            killRatio = origin.killRatio,
            from = order.from,
            to = order.to,
            distanceRemaining = dist,
        )

        state.fleets.add(fleet)
        state.planets.update(order.from) { it.copy(ships = it.ships - order.ships) }
        state.turnEvents.add(TurnEvent.FleetLaunched(playerId, order.from, order.to, order.ships))
    }
}

private fun moveFleets(state: GameState) {
    state.fleets.replaceAll { it.copy(distanceRemaining = it.distanceRemaining - GameConfig.SHIP_SPEED) }
}

/**
 * Resolve fleet arrivals. Each fleet attacks separately in sequence.
 * Fleets are interleaved by player — player with more fleets goes first each cycle.
 * Events trigger after each combat (~33%) and once at end of fleet processing (~33%).
 */
private fun resolveArrivals(state: GameState) {
    val arrived = state.fleets.filter { it.distanceRemaining <= 0.0 }
    state.fleets.removeAll { it.distanceRemaining <= 0.0 }

    // Mark attacked planets as visited for fog of war
    for (fleet in arrived) {
        val planet = state.findPlanetById(fleet.to) ?: continue
        if (planet.owner != Owner.Player(fleet.owner)) {
            state.visited.getOrPut(fleet.owner) { mutableSetOf() }.add(fleet.to)
        }
    }

    // Process each fleet individually, interleaved by player
    val byOwner = arrived.groupBy { it.owner }.mapValues { it.value.toMutableList() }

    // Player with more total fleets goes first — fixed at start
    val sortedOwners = byOwner.entries
        .sortedWith(compareByDescending { it.value.size })
        .map { it.key }

    while (byOwner.any { it.value.isNotEmpty() }) {
        for (fleetOwner in sortedOwners) {
            val fleetQueue = byOwner[fleetOwner] ?: continue
            if (fleetQueue.isEmpty()) continue
            val fleet = fleetQueue.removeFirst()

            // Re-fetch planet state (may have changed from prior fleet resolution)
            val planet = state.findPlanetById(fleet.to) ?: continue

            when (planet.owner) {
                Owner.Player(fleetOwner) -> {
                    // Reinforcement
                    state.planets.update(fleet.to) { it.copy(ships = it.ships + fleet.ships) }
                    state.turnEvents.add(TurnEvent.FleetArrived(fleetOwner, fleet.to, fleet.ships))
                }
                else -> {
                    // Attack — individual fleet combat
                    attack(state, fleetOwner, fleet, planet)
                }
            }

            // Event trigger after each fleet arrival (~33% chance)
            maybeFireEvent(state)?.let { state.turnEvents.add(it) }
        }
    }

    // One more event trigger at the end of fleet processing (~33% chance)
    maybeFireEvent(state)?.let { state.turnEvents.add(it) }
}

private fun attack(
    state: GameState,
    fleetOwner: PlayerId,
    fleet: Fleet,
    planet: Planet
) {
    val surviving = resolveCombat(
        fleet.ships, fleet.killRatio,
        planet.ships, planet.killRatio
    )
    val outcome = when {
        surviving.attacker.some && surviving.defender.none -> BattleOutcome.CONQUERED
        surviving.defender.some -> BattleOutcome.REPELLED
        else -> BattleOutcome.DRAW
    }

    state.turnEvents.add(
        TurnEvent.BattleResolved(
            planet = fleet.to,
            attacker = fleetOwner,
            defender = planet.owner,
            forces = Combatants(fleet.ships, planet.ships),
            surviving = surviving,
            outcome = outcome,
            attackerKillRatio = fleet.killRatio,
            defenderKillRatio = planet.killRatio,
        )
    )

    when (outcome) {
        BattleOutcome.CONQUERED -> {
            state.planets.update(fleet.to) { it.copy(owner = Owner.Player(fleetOwner), ships = surviving.attacker) }
        }

        else -> {
            // DRAW: keep existing owner with 0 ships (dead code — can't happen due to sequential checking)
            // REPELLED: defender retains planet with surviving ships
            state.planets.update(fleet.to) { it.copy(ships = surviving.defender) }
        }
    }
}

private fun resolveProduction(state: GameState) {
    state.planets.replaceAll { planet ->
        when (val owner = planet.owner) {
            is Owner.Player -> {
                state.turnEvents.add(TurnEvent.ProductionCompleted(owner.id, planet.label, planet.production))
                planet.copy(ships = planet.ships + planet.production)
            }

            else -> planet
        }
    }
}

/**
 * End-game fleet resolution: run up to 25 additional movement cycles.
 * Each cycle: move fleets, resolve arrivals/combat, trigger events, run production.
 * Stop early if no fleets remain in transit.
 */
private fun resolveEndGame(state: GameState) {
    repeat(25) {
        if (state.fleets.isEmpty()) return
        moveFleets(state)
        resolveArrivals(state)
        resolveProduction(state)
    }
}
