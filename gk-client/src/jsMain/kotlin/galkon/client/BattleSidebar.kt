package galkon.client

import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.onClickFunction
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement

fun renderBattleSidebar(state: AppState): HTMLElement {
    val event = state.battleEvent ?: return renderSidebar(state)

    val attackerName = state.players.find { it.id == event.attacker }?.name ?: "Attacker"
    val defenderOwner = event.defender
    val defenderName = when {
        defenderOwner == null -> "Neutral"
        defenderOwner.jsonPrimitive.content == "neutral" -> "Neutral"
        else -> state.players.find { it.id == defenderOwner.jsonPrimitive.content }?.name ?: "Defender"
    }

    val won = event.conquered == true

    return document.create.div("battle-sidebar") {
        div("battle-header") {
            +"BATTLE"
        }
        div("battle-planet") {
            +"Planet "
            span("battle-planet-label") { +"${event.planet}" }
        }

        div("battle-versus") {
            div("battle-side") {
                div("side-role attacker-role") { +"ATK" }
                div("side-name") { +attackerName }
                div("side-ships") {
                    id = "battle-attacker-ships"
                    +"${event.attackerShips}"
                }
                div("side-label") { +"ships" }
                if (event.attackerKillRatio != null) {
                    div("side-kill") { +"K:${event.attackerKillRatio}%" }
                }
            }

            div("battle-vs") { +"VS" }

            div("battle-side") {
                div("side-role defender-role") { +"DEF" }
                div("side-name") { +defenderName }
                div("side-ships") {
                    id = "battle-defender-ships"
                    +"${event.defenderShips}"
                }
                div("side-label") { +"ships" }
                if (event.defenderKillRatio != null) {
                    div("side-kill") { +"K:${event.defenderKillRatio}%" }
                }
            }
        }

        div("battle-result-container") {
            id = "battle-result"
            style = "opacity: 0; transition: opacity 0.3s ease-in"

            val resultText = if (won) "CONQUERED" else "REPELLED"
            val resultClass = if (won) "conquered" else "repelled"
            val surviving = if (won) event.attackerSurviving else event.defenderSurviving
            div("battle-result-text $resultClass") { +resultText }
            div("battle-result-surviving") { +"$surviving ships remain" }
        }

        div("battle-skip") {
            button {
                +"Skip"
                onClickFunction = { skipBattle() }
            }
        }
    }
}
