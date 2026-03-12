package galkon.bot

import galkon.common.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

fun buildPrompt(
    playerId: String,
    view: PlayerViewResponse,
): String = buildString {
    append("""
        |You are playing a space conquest game called Galkon.
        |
        |## Rules
        |- You own planets that produce ships each turn.
        |- You send fleets from your planets to conquer enemy or neutral planets.
        |- Ships travel at speed 2 per turn (distance is Euclidean between grid positions).
        |- When your fleet arrives at a planet you don't own, combat happens.
        |- Kill ratio (%) determines combat effectiveness. Higher is better.
        |- Defenders get a home-court bonus (5-31%).
        |- Production: each owned planet produces 'production' ships per turn.
        |- You can only see planets you own, enemy player planets, and neutrals you've visited.
        |- Game lasts ${view.turn}/${inferMaxTurns(view)} turns. Most planets + ships wins.
        |
        |""".trimMargin())

    // Player info
    val me = view.players.find { it.id == playerId }
    val myName = me?.name ?: "Unknown"
    appendLine("## You")
    appendLine("Player: $myName (id: $playerId)")
    appendLine()

    // My planets
    val myPlanets = view.planets.filter {
        it.owner is JsonPrimitive && it.owner.jsonPrimitive.content == playerId
    }
    val enemyPlanets = view.planets.filter {
        it.owner is JsonPrimitive && it.owner.jsonPrimitive.content != "neutral" && it.owner.jsonPrimitive.content != playerId
    }
    val neutralPlanets = view.planets.filter {
        it.owner is JsonPrimitive && it.owner.jsonPrimitive.content == "neutral"
    }
    val unknownPlanets = view.planets.filter { it.ships == null }

    appendLine("## Your Planets")
    if (myPlanets.isEmpty()) {
        appendLine("(none - you may be eliminated!)")
    } else {
        appendLine("| Planet | Pos | Ships | Production | Kill% |")
        appendLine("|--------|-----|-------|------------|-------|")
        for (p in myPlanets.sortedByDescending { it.ships ?: 0 }) {
            appendLine("| ${p.label} | (${p.x},${p.y}) | ${p.ships} | ${p.production} | ${p.killRatio}% |")
        }
    }
    appendLine()

    // My fleets
    if (view.fleets.isNotEmpty()) {
        appendLine("## Your Fleets In Transit")
        appendLine("| From | To | Ships | ETA (turns) |")
        appendLine("|------|----|-------|-------------|")
        for (f in view.fleets) {
            val eta = (f.distanceRemaining / 2.0).let { kotlin.math.ceil(it).toInt() }
            appendLine("| ${f.from} | ${f.to} | ${f.ships} | $eta |")
        }
        appendLine()
    }

    // Enemy planets
    if (enemyPlanets.isNotEmpty()) {
        appendLine("## Visible Enemy Planets")
        appendLine("| Planet | Pos | Owner | Ships | Production | Kill% |")
        appendLine("|--------|-----|-------|-------|------------|-------|")
        for (p in enemyPlanets) {
            val ownerName = view.players.find { it.id == p.owner.jsonPrimitive.content }?.name ?: "?"
            appendLine("| ${p.label} | (${p.x},${p.y}) | $ownerName | ${p.ships ?: "?"} | ${p.production ?: "?"} | ${p.killRatio?.let { "$it%" } ?: "?"} |")
        }
        appendLine()
    }

    // Neutral planets with info
    val knownNeutrals = neutralPlanets.filter { it.ships != null }
    val unknownNeutrals = neutralPlanets.filter { it.ships == null } + unknownPlanets
    if (knownNeutrals.isNotEmpty()) {
        appendLine("## Known Neutral Planets")
        appendLine("| Planet | Pos | Ships | Production | Kill% |")
        appendLine("|--------|-----|-------|------------|-------|")
        for (p in knownNeutrals.sortedBy { it.ships ?: 999 }) {
            appendLine("| ${p.label} | (${p.x},${p.y}) | ${p.ships} | ${p.production} | ${p.killRatio}% |")
        }
        appendLine()
    }

    if (unknownNeutrals.isNotEmpty()) {
        appendLine("## Unexplored Planets (no intel)")
        appendLine(unknownNeutrals.joinToString(", ") { "${it.label}(${it.x},${it.y})" })
        appendLine()
    }

    // Recent events
    val events = view.turnEvents.filter { it.type != "turn_started" }
    if (events.isNotEmpty()) {
        appendLine("## Recent Events")
        for (e in events) {
            appendLine("- ${formatEvent(e, view.players, playerId)}")
        }
        appendLine()
    }

    // Distance helper — include all non-owned planets (enemy, neutral, unexplored)
    val allTargets = view.planets.filter {
        !(it.owner is JsonPrimitive && it.owner.jsonPrimitive.content == playerId)
    }
    if (myPlanets.isNotEmpty() && allTargets.isNotEmpty()) {
        appendLine("## Key Distances From Your Planets (ETA in turns)")
        for (mp in myPlanets) {
            val closest = allTargets.sortedBy { distance(mp.x, mp.y, it.x, it.y) }.take(7)
            val dists = closest.joinToString(", ") { t ->
                val d = distance(mp.x, mp.y, t.x, t.y)
                val eta = kotlin.math.ceil(d / 2.0).toInt()
                "${t.label}:${eta}t"
            }
            appendLine("  ${mp.label} -> $dists")
        }
        appendLine()
    }

    append("""
        |## Your Task
        |Decide which fleets to send this turn. You want to expand, defend key planets, and attack weak enemies.
        |Consider: distance (closer = faster), kill ratio (attack low-kill% targets), ship count (don't attack with too few), and keeping reserves for defense.
        |
        |Respond with ONLY your orders in this exact format (one per line), or NONE if you want to skip:
        |```
        |FROM TO SHIPS
        |```
        |Example: `A B 5` means send 5 ships from planet A to planet B.
        |You can send multiple orders. Do not send more ships than you have on a planet.
        |""".trimMargin())
}

private fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Double {
    val dx = (x2 - x1).toDouble()
    val dy = (y2 - y1).toDouble()
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

@Suppress("UNUSED_PARAMETER")
private fun inferMaxTurns(view: PlayerViewResponse): Int {
    // Default game config is 40 turns
    return 40
}

private fun formatEvent(e: TurnEventDto, players: List<PlayerInfoDto>, @Suppress("UNUSED_PARAMETER") myId: String): String {
    fun name(id: String?) = players.find { it.id == id }?.name ?: id ?: "?"
    return when (e.type) {
        "fleet_launched" -> "Fleet launched: ${e.ships} ships from ${e.from} to ${e.to}"
        "fleet_arrived" -> "Fleet arrived: ${e.ships} ships at ${e.to}"
        "battle" -> {
            val outcome = if (e.conquered == true) "CONQUERED" else "REPELLED"
            "Battle at ${e.planet}: ${name(e.attacker)} attacked (${e.attackerShips}->${e.attackerSurviving}) vs defender (${e.defenderShips}->${e.defenderSurviving}) - $outcome"
        }
        "revolt" -> "Planet ${e.planet} revolted against ${name(e.formerOwner)}!"
        "production" -> "Production at ${e.planet}: +${e.ships} ships"
        "event_production_changed" -> "Event: production at ${e.planet} changed to ${e.newProduction}"
        "event_kill_ratio_changed" -> "Event: kill ratio at ${e.planet} changed to ${e.newKillRatio}%"
        "event_revolt_thwarted" -> "Revolt thwarted at ${e.planet}"
        else -> "${e.type}: $e"
    }
}
