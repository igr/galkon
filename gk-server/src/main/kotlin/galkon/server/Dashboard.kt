package galkon.server

import galkon.common.Colors
import galkon.common.GamePhase
import galkon.common.GameStatus
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun Route.dashboardRoutes(lobby: Lobby) {

    get("/dashboard") {
        val sessions = lobby.allSessions().map { it.snapshotStatus() to it.lastActivity }
        val totalGames = sessions.size
        val inLobby = sessions.count { it.first.phase == GamePhase.Lobby }
        val inProgress = sessions.count { it.first.phase is GamePhase.InProgress }
        val finished = sessions.count { it.first.phase == GamePhase.Finished }
        val totalPlayers = sessions.sumOf { it.first.players.size }

        call.respondHtml {
            dashboard(
                totalGames, inLobby, inProgress, finished, totalPlayers, sessions)
        }
    }
}

private fun HTML.dashboard(
    totalGames: Int,
    inLobby: Int,
    inProgress: Int,
    finished: Int,
    totalPlayers: Int,
    sessions: List<Pair<GameStatus, Long>>,
) {
    val now = System.currentTimeMillis()
    head {
        title { +"Galkon Dashboard" }
        style {
            unsafe {
                raw(CSS)
            }
        }
    }
    body {
        h1 { +"Galkon Dashboard" }
        p("version") {
            +"Server: $gameVersion"
            +" | "
            a(href = "/docs") { +"Swagger UI" }
            +" | "
            a(href = "/openapi") { +"OpenAPI Spec" }
        }

        div("stats") {
            stat("Total Games", "$totalGames")
            stat("Lobby", "$inLobby")
            stat("In Progress", "$inProgress")
            stat("Finished", "$finished")
            stat("Total Players", "$totalPlayers")
        }

        if (sessions.isNotEmpty()) {
            table {
                thead {
                    tr {
                        for (h in listOf("Code", "Phase", "Turn", "Players", "Idle")) {
                            th { +h }
                        }
                    }
                }
                tbody {
                    for ((status, lastActivity) in sessions.sortedByDescending { it.second }) {
                        val phase = when (val p = status.phase) {
                            is GamePhase.Lobby -> "Lobby"

                            is GamePhase.SetUp -> "Setup ${p.round}/3"
                            is GamePhase.InProgress -> "Turn ${p.turn}"
                            is GamePhase.Finished -> "Finished"
                        }
                        val idle = formatDuration(now - lastActivity)
                        tr {
                            td("code") { +status.gameCode }
                            td { +phase }
                            td { +if (status.phase is GamePhase.InProgress) "${(status.phase as GamePhase.InProgress).turn}" else "-" }
                            td {
                                +"${status.players.size}"
                                if (status.players.isNotEmpty()) {
                                    ul("player-list") {
                                        for (p in status.players) {
                                            li {
                                                +p.name.value
                                                span("player-id") { +" ${p.id.value}" }
                                            }
                                        }
                                    }
                                }
                            }
                            td { +idle }
                        }
                    }
                }
            }
        } else {
            p("empty") { +"No active games" }
        }
    }
}

private fun FlowContent.stat(label: String, value: String) {
    div("stat") {
        div("stat-value") { +value }
        div("stat-label") { +label }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

private val CSS = """
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
        background: ${Colors.BG_DARK};
        color: ${Colors.TEXT};
        font-family: 'Courier New', monospace;
        padding: 30px;
        max-width: 900px;
        margin: 0 auto;
    }
    h1 { color: ${Colors.ACCENT}; margin-bottom: 5px; }
    .version { color: ${Colors.BORDER_LIGHT}; font-size: 0.85em; margin-bottom: 20px; }
    .stats {
        display: flex;
        gap: 15px;
        margin-bottom: 25px;
    }
    .stat {
        background: ${Colors.BG_PANEL};
        border: 1px solid ${Colors.BORDER};
        padding: 12px 20px;
        text-align: center;
        flex: 1;
    }
    .stat-value { color: ${Colors.ACCENT}; font-size: 1.8em; font-weight: bold; }
    .stat-label { color: ${Colors.TEXT_MUTED}; font-size: 0.8em; margin-top: 4px; }
    table { width: 100%; border-collapse: collapse; }
    th {
        color: ${Colors.ACCENT};
        border-bottom: 2px solid ${Colors.ACCENT};
        padding: 8px;
        text-align: left;
        font-size: 0.9em;
    }
    td { padding: 8px; border-bottom: 1px solid ${Colors.BORDER}; }
    .version a { color: ${Colors.ACCENT}; text-decoration: none; }
    .version a:hover { text-decoration: underline; }
    .code { font-weight: bold; letter-spacing: 2px; }
    .player-list { list-style: none; margin-top: 4px; }
    .player-list li { font-size: 0.85em; }
    .player-id { color: ${Colors.TEXT_DIM}; font-size: 0.85em; }
    .empty { color: ${Colors.TEXT_DIM}; font-style: italic; margin-top: 20px; }
""".trimIndent()
