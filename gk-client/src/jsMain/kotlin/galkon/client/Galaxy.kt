package galkon.client

import kotlinx.browser.document
import kotlinx.html.div
import kotlinx.html.dom.create
import org.w3c.dom.HTMLElement
import org.w3c.dom.svg.SVGElement

private const val SVG_NS = "http://www.w3.org/2000/svg"

fun renderGalaxy(state: AppState): HTMLElement {
    val container = document.create.div("galaxy") {}
    val svgWidth = state.gridWidth * state.spaceWidth
    val svgHeight = state.gridHeight * state.spaceHeight

    val svg = document.createElementNS(SVG_NS, "svg") as SVGElement
    svg.setAttribute("viewBox", "0 0 $svgWidth $svgHeight")
    svg.setAttribute("preserveAspectRatio", "xMidYMid meet")

    // Background grid lines (subtle)
    for (x in 0..state.gridWidth) {
        val line = document.createElementNS(SVG_NS, "line")
        line.setAttribute("x1", "${x * state.spaceWidth}")
        line.setAttribute("y1", "0")
        line.setAttribute("x2", "${x * state.spaceWidth}")
        line.setAttribute("y2", "$svgHeight")
        line.setAttribute("stroke", Colors.GRID_LINE)
        line.setAttribute("stroke-width", "0.5")
        svg.appendChild(line)
    }
    for (y in 0..state.gridHeight) {
        val line = document.createElementNS(SVG_NS, "line")
        line.setAttribute("x1", "0")
        line.setAttribute("y1", "${y * state.spaceHeight}")
        line.setAttribute("x2", "$svgWidth")
        line.setAttribute("y2", "${y * state.spaceHeight}")
        line.setAttribute("stroke", Colors.GRID_LINE)
        line.setAttribute("stroke-width", "0.5")
        svg.appendChild(line)
    }

    // Render planets
    val battlePlanetLabel = state.battleEvent?.planet

    // During event playback, collect defender colors for not-yet-shown battle events
    // so planet colors don't spoil outcomes before the battle animation plays.
    val pendingBattleDefenders = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    if (state.eventPlaybackIndex < state.turnEvents.size) {
        val firstVisible = when {
            state.eventPlaybackIndex < 0 -> 0
            else -> state.eventPlaybackIndex + 1
        }
        for (i in firstVisible until state.turnEvents.size) {
            val ev = state.turnEvents[i]
            val evPlanet = ev.planet
            val evDefender = ev.defender
            if (ev.type == "battle" && evPlanet != null && evDefender != null) {
                if (evPlanet !in pendingBattleDefenders) {
                    pendingBattleDefenders[evPlanet] = evDefender
                }
            }
        }
    }

    for (planet in state.planets) {
        val cx = planet.x * state.spaceWidth + state.spaceWidth / 2
        val cy = planet.y * state.spaceHeight + state.spaceHeight / 2
        // Use defender color for planets with pending or active battle events
        val pendingDefender = pendingBattleDefenders[planet.label]
        val color = if (pendingDefender != null) {
            ownerColor(pendingDefender)
        } else {
            ownerColor(planet.owner)
        }
        val isSelected = planet.label == state.orderFrom || planet.label == state.orderTo

        // Battle halo ring
        if (planet.label == battlePlanetLabel) {
            val halo = document.createElementNS(SVG_NS, "circle")
            halo.setAttribute("cx", "$cx")
            halo.setAttribute("cy", "$cy")
            halo.setAttribute("r", "13")
            halo.setAttribute("fill", "none")
            halo.setAttribute("stroke", Colors.WARN)
            halo.setAttribute("stroke-width", "4")
            svg.appendChild(halo)
        }

        // Planet circle
        val circle = document.createElementNS(SVG_NS, "circle")
        circle.setAttribute("cx", "$cx")
        circle.setAttribute("cy", "$cy")
        circle.setAttribute("r", "7")
        circle.setAttribute("fill", color)
        circle.setAttribute("stroke", if (isSelected) Colors.WHITE else color)
        circle.setAttribute("stroke-width", if (isSelected) "2" else "1")
        circle.setAttribute("cursor", "pointer")
        circle.addEventListener("click", {
            onPlanetClick(planet.label)
        })
        svg.appendChild(circle)

        // Planet label (centered)
        val text = document.createElementNS(SVG_NS, "text")
        text.setAttribute("x", "$cx")
        text.setAttribute("y", "${cy + 1}")
        text.setAttribute("text-anchor", "middle")
        text.setAttribute("dominant-baseline", "middle")
        text.setAttribute("fill", Colors.BLACK)
        text.setAttribute("font-size", "8")
        text.setAttribute("font-family", "Courier New, monospace")
        text.setAttribute("font-weight", "bold")
        text.setAttribute("pointer-events", "none")
        text.textContent = planet.label
        svg.appendChild(text)

        // Ship count (right under label, color background with black text)
        val ships = planet.ships
        if (ships != null) {
            val shipText = document.createElementNS(SVG_NS, "text")
            shipText.setAttribute("x", "$cx")
            shipText.setAttribute("y", "${cy + 6}")
            shipText.setAttribute("text-anchor", "middle")
            shipText.setAttribute("dominant-baseline", "middle")
            shipText.setAttribute("fill", Colors.BLACK)
            shipText.setAttribute("font-size", "5")
            shipText.setAttribute("font-family", "sans-serif")
            shipText.setAttribute("pointer-events", "none")
            shipText.textContent = "$ships"
            svg.appendChild(shipText)
        }
    }

    // Render fleet arrows
    for (fleet in state.fleets) {
        val fromPlanet = state.planets.find { it.label == fleet.from }
        val toPlanet = state.planets.find { it.label == fleet.to }
        if (fromPlanet != null && toPlanet != null) {
            val x1 = fromPlanet.x * state.spaceWidth + state.spaceWidth / 2
            val y1 = fromPlanet.y * state.spaceHeight + state.spaceHeight / 2
            val x2 = toPlanet.x * state.spaceWidth + state.spaceWidth / 2
            val y2 = toPlanet.y * state.spaceHeight + state.spaceHeight / 2
            val fleetIndex = state.players.indexOfFirst { it.id == fleet.owner }
            val fleetColor = if (fleetIndex >= 0) Colors.PLAYER[fleetIndex % Colors.PLAYER.size] else Colors.NEUTRAL

            val fx = (x1 + x2) / 2
            val fy = (y1 + y2) / 2

            val line = document.createElementNS(SVG_NS, "line")
            line.setAttribute("x1", "$x1")
            line.setAttribute("y1", "$y1")
            line.setAttribute("x2", "$x2")
            line.setAttribute("y2", "$y2")
            line.setAttribute("stroke", fleetColor + "44")
            line.setAttribute("stroke-width", "1")
            line.setAttribute("stroke-dasharray", "4,4")
            svg.appendChild(line)

            val fleetText = document.createElementNS(SVG_NS, "text")
            fleetText.setAttribute("x", "$fx")
            fleetText.setAttribute("y", "$fy")
            fleetText.setAttribute("text-anchor", "middle")
            fleetText.setAttribute("fill", fleetColor)
            fleetText.setAttribute("font-size", "5")
            fleetText.setAttribute("font-family", "Courier New, monospace")
            fleetText.textContent = "${fleet.ships}"
            svg.appendChild(fleetText)
        }
    }

    container.appendChild(svg)

    if (state.seed.isNotEmpty()) {
        val seedDiv = document.createElement("div") as HTMLElement
        seedDiv.className = "seed-display"
        seedDiv.textContent = "Seed: ${state.seed}"
        container.appendChild(seedDiv)
    }

    return container
}

private fun onPlanetClick(label: String) {
    val state = currentState
    if (state.gamePhase == "setup") return
    if (state.orderFrom.isEmpty()) {
        updateState { copy(orderFrom = label) }
    } else if (state.orderTo.isEmpty() && label != state.orderFrom) {
        updateState { copy(orderTo = label) }
    } else {
        updateState { copy(orderFrom = label, orderTo = "") }
    }
}
