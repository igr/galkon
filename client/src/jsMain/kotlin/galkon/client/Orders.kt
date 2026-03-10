package galkon.client

import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

fun renderOrders(state: AppState): HTMLElement = document.create.div("orders") {
    if (state.ordersSubmitted) {
        div("waiting") { +"Orders submitted. Waiting for other players..." }
        return@div
    }

    // Input row
    div("inputs") {
        span { +"From:" }
        textInput {
            id = "input-order-from"
            value = state.orderFrom
            placeholder = "Planet"
            style = "width: 50px"
            onInputFunction = { e -> updateState { copy(orderFrom = (e.target as HTMLInputElement).value.uppercase()) } }
        }

        span { +"To:" }
        textInput {
            id = "input-order-to"
            value = state.orderTo
            placeholder = "Planet"
            style = "width: 50px"
            onInputFunction = { e -> updateState { copy(orderTo = (e.target as HTMLInputElement).value.uppercase()) } }
        }

        span { +"Ships:" }
        textInput {
            id = "input-order-ships"
            value = state.orderShips
            placeholder = "#"
            style = "width: 60px"
            onInputFunction = { e -> updateState { copy(orderShips = (e.target as HTMLInputElement).value) } }
        }

        button { +"Add"; onClickFunction = { doAddOrder() } }
        button { +"🚀 Submit Turn"; onClickFunction = { doSubmitOrders() } }
    }

    // Pending orders queue
    if (state.pendingOrders.isNotEmpty()) {
        div("queue") {
            state.pendingOrders.forEachIndexed { idx, order ->
                span("queue-item") {
                    span { +"${order.from}>${order.to}(${order.ships})" }
                    span("remove") {
                        +"x"
                        onClickFunction = {
                            updateState { copy(pendingOrders = pendingOrders.filterIndexed { i, _ -> i != idx }) }
                        }
                    }
                }
            }
        }
    }
}
