package galkon.client

import galkon.common.*

/** Which screen is currently shown. */
enum class Screen { LOBBY, GAME, SCORES }

/** Client-side state. */
data class AppState(
    val screen: Screen = Screen.LOBBY,
    val serverUrl: String = "",
    val gameCode: String = "",
    val playerName: String = "",
    val playerId: String = "",
    val gamePhase: String = "lobby",
    val currentTurn: Int = 0,
    val planets: List<ViewPlanetDto> = emptyList(),
    val fleets: List<FleetDto> = emptyList(),
    val turnEvents: List<TurnEventDto> = emptyList(),
    val players: List<PlayerInfoDto> = emptyList(),
    val gridWidth: Int = 16,
    val gridHeight: Int = 16,
    val spaceWidth: Int = 16,
    val spaceHeight: Int = 16,
    val orderFrom: String = "",
    val orderTo: String = "",
    val orderShips: String = "",
    val pendingOrders: List<FleetOrderDto> = emptyList(),
    val ordersSubmitted: Boolean = false,
    val scores: List<PlayerScoreDto> = emptyList(),
    val inputSeed: String = "",
    val seed: String = "",
    val setupRound: Int = 0,
    val setupMaxRounds: Int = 3,
    val setupVoteSubmitted: Boolean = false,
    val error: String = "",
    val eventPlaybackIndex: Int = -1,
    val menuOpen: Boolean = false,
    val battleEvent: TurnEventDto? = null,
    val aboutOpen: Boolean = false,
    val sidebarVisible: Boolean = true,
    val rulesOpen: Boolean = false,
    val rulesHtml: String = "",
    val filterMyPlanets: Boolean = false,
)
