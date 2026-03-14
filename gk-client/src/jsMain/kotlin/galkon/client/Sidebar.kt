package galkon.client

import galkon.common.PlanetId
import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.onClickFunction
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement

fun renderSidebar(state: AppState): HTMLElement = document.create.div("sidebar") {
    div("sidebar-section-header") {
        h3 { +"PLANETS" }
        span("planet-filter-toggle") {
            if (state.filterMyPlanets) {
                +"[MINE]"
            } else {
                +"[ALL]"
            }
            onClickFunction = { updateState { copy(filterMyPlanets = !filterMyPlanets) } }
        }
    }

    val allocated = allocatedShips(state)

    val planets = if (state.filterMyPlanets) {
        state.planets.filter { ownerIsYou(it.owner) }
    } else {
        state.planets
    }

    for (p in planets.sortedBy { PlanetId(it.label) }) {
        val isYou = ownerIsYou(p.owner)
        val isNeutral = p.owner.jsonPrimitive.content == "neutral"
        val cssClass = when {
            isYou -> "planet-row mine"
            isNeutral -> "planet-row neutral"
            else -> "planet-row enemy"
        }
        val ownerPrefix = if (!isYou && !isNeutral) "${shortName(ownerName(p.owner))} " else ""
        val detail = run {
            val ships: Int = p.ships ?: return@run ownerName(p.owner)
            val pending = allocated[p.label] ?: 0
            val displayShips = if (isYou && pending > 0) "${ships - pending}/$ships" else "$ships"
            "${ownerPrefix}S:$displayShips P:${p.production ?: "?"}  K:${p.killRatio ?: "?"}"
        }
        div(cssClass) {
            span("label") { +p.label }
            span { +detail }
        }
    }

    h3 { +"YOUR FLEETS" }
    if (state.fleets.isEmpty()) {
        div { style = "color: ${Colors.TEXT_DIM}"; +"None in transit" }
    }
    for (f in state.fleets.sortedBy { it.distanceRemaining }) {
        val turnsLeft = kotlin.math.ceil(f.distanceRemaining / 2.0).toInt()
        div("fleet-row") { +"${f.from}>${f.to} ${f.ships} ships (${turnsLeft}t)" }
    }
}

/** Ships allocated in pending orders, keyed by planet label. */
fun allocatedShips(state: AppState): Map<String, Int> =
    state.pendingOrders.groupBy { it.from }.mapValues { (_, orders) -> orders.sumOf { it.ships } }

private fun shortName(name: String): String =
    if (name.length > 10) name.take(10) + ".." else name

private fun ownerIsYou(owner: kotlinx.serialization.json.JsonElement): Boolean {
    val content = owner.jsonPrimitive.content
    return content != "neutral" && content == currentState.playerId
}
