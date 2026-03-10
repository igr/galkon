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
    updateGamePhase(state)

    state.orders.clear()
}

private fun initTurn(state: GameState) {
    state.turnEvents.clear()
    state.turnEvents.add(TurnEvent.TurnStarted(state.currentTurn))
}

private fun updateGamePhase(state: GameState) {
    val activePlayers = state.players.keys
    val eliminated = state.eliminated
    val remaining = activePlayers.filter { it !in eliminated }
    val currentTurn = state.currentTurn
    when {
        remaining.size <= 1 -> {
            state.phase = GamePhase.Finished
        }

        currentTurn >= state.config.numTurns -> {
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
    for ((playerId, playerOrders) in state.orders) {
        for (order in playerOrders) {
            val origin = state.planets.find { it.label == order.from } ?: continue
            val target = state.planets.find { it.label == order.to } ?: continue

            if (origin.owner != Owner.Player(playerId) || origin.ships < order.ships || order.ships.none) continue

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
        val planet = state.planets.find { it.label == fleet.to } ?: continue
        if (planet.owner != Owner.Player(fleet.owner)) {
            state.visited.getOrPut(fleet.owner) { mutableSetOf() }.add(fleet.to)
        }
    }

    // Process each fleet individually, interleaved by player
    val byOwner = arrived.groupBy { it.owner }.mapValues { it.value.toMutableList() }

    while (byOwner.any { it.value.isNotEmpty() }) {
        // Player with more remaining fleets goes first each cycle
        val sortedOwners = byOwner.entries
            .filter { it.value.isNotEmpty() }
            .sortedWith(compareByDescending<Map.Entry<PlayerId, MutableList<Fleet>>> { it.value.size }
                .thenBy { it.key.value })
            .map { it.key }

        for (fleetOwner in sortedOwners) {
            val fleetQueue = byOwner[fleetOwner] ?: continue
            if (fleetQueue.isEmpty()) continue
            val fleet = fleetQueue.removeFirst()

            // Re-fetch planet state (may have changed from prior fleet resolution)
            val planet = state.planets.find { it.label == fleet.to } ?: continue

            if (planet.owner == Owner.Player(fleetOwner)) {
                // Reinforcement
                state.planets.update(fleet.to) { it.copy(ships = it.ships + fleet.ships) }
                state.turnEvents.add(TurnEvent.FleetArrived(fleetOwner, fleet.to, fleet.ships))
            } else {
                // Attack — individual fleet combat
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
                    )
                )

                if (outcome == BattleOutcome.CONQUERED) {
                    state.planets.update(fleet.to) { it.copy(owner = Owner.Player(fleetOwner), ships = surviving.attacker) }
                } else {
                    state.planets.update(fleet.to) {
                        if (surviving.defender.none) it.copy(owner = Owner.Neutral, ships = ShipCount.ZERO)
                        else it.copy(ships = surviving.defender)
                    }
                }

                // Event trigger after each combat (~33% chance)
                maybeFireEvent(state)?.let { state.turnEvents.add(it) }
            }
        }
    }

    // One more event trigger at the end of fleet processing (~33% chance)
    maybeFireEvent(state)?.let { state.turnEvents.add(it) }
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
