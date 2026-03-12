package galkon.client

import galkon.common.TurnEventDto
import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.create
import org.w3c.dom.HTMLElement

fun renderEvents(state: AppState): HTMLElement = document.create.div("events") {
    h3 { +"EVENT LOG" }

    if (state.turnEvents.isEmpty()) {
        div { style = "color: ${Colors.TEXT_DIM}"; +"No events yet" }
        return@div
    }

    val visibleCount = if (state.eventPlaybackIndex < 0) state.turnEvents.size
    else minOf(state.eventPlaybackIndex + 1, state.turnEvents.size)

    for (i in 0 until visibleCount) {
        renderEvent(state.turnEvents[i])
    }
}

private fun FlowContent.renderEvent(event: TurnEventDto) {
    val (cssClass, text) = when (event.type) {
        "fleet_launched" -> "neutral-event" to "> Fleet launched: ${event.from} > ${event.to} (${event.ships} ships)"
        "fleet_arrived" -> "good" to "> Fleet arrived at ${event.to} (${event.ships} ships)"
        "battle" -> {
            val won = event.conquered == true
            val cls = if (won) "good" else "bad"
            val result = if (won) "Won!" else "Lost!"
            val surviving = if (won) event.attackerSurviving else event.defenderSurviving
            cls to "> Battle at ${event.planet}: $result ${event.attackerShips} vs ${event.defenderShips}, $surviving survive"
        }
        "revolt" -> "warn" to "> Planet ${event.planet} revolted!"
        "production" -> "good" to "> Production at ${event.planet}: +${event.ships} ships"
        "turn_started" -> "turn-marker" to "--- Turn ${event.turn} ---"
        "event_production_changed" -> "warn" to "> Planet ${event.planet}: production changed to ${event.newProduction}"
        "event_kill_ratio_changed" -> "warn" to "> Planet ${event.planet}: kill ratio changed to ${event.newKillRatio}%"
        "event_revolt_thwarted" -> "neutral-event" to "> A revolt on planet ${event.planet} has been thwarted"
        else -> "neutral-event" to "> ${event.type}"
    }

    div("event $cssClass") { +text }
}
