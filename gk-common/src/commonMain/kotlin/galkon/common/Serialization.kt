package galkon.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Shared JSON config. */
val GameJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// ---- Request DTOs ----

@Serializable
data class CreateGameRequest(
    val seed: String = "",
    val numPlanets: Int = 26,
)

@Serializable
data class JoinGameRequest(
    val playerName: String,
)

@Serializable
data class StartGameRequest(
    val playerId: String,
)

@Serializable
data class SubmitOrdersRequest(
    val orders: List<FleetOrderDto>,
)

@Serializable
data class SetupVoteRequest(
    val agree: Boolean,
)

@Serializable
data class FleetOrderDto(
    val from: String,
    val to: String,
    val ships: Int,
)

// ---- Response DTOs ----

@Serializable
data class CreateGameResponse(
    val gameCode: String,
)

@Serializable
data class JoinGameResponse(
    val playerId: String,
)

@Serializable
data class OkResponse(
    val status: String = "ok",
    val version: String? = null,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

@Serializable
data class PlayerInfoDto(
    val id: String,
    val name: String,
)

@Serializable
data class GameStatusResponse(
    val gameCode: String,
    val phase: JsonElement,
    val players: List<PlayerInfoDto>,
)

@Serializable
data class PlayerViewResponse(
    val phase: JsonElement,
    val turn: Int,
    val planets: List<ViewPlanetDto>,
    val fleets: List<FleetDto>,
    val turnEvents: List<TurnEventDto>,
    val players: List<PlayerInfoDto>,
    val gridWidth: Int,
    val gridHeight: Int,
    val spaceWidth: Int,
    val spaceHeight: Int,
    val seed: String = "",
    val setupRound: Int? = null,
    val setupMaxRounds: Int? = null,
)

@Serializable
data class ViewPlanetDto(
    val label: String,
    val x: Int,
    val y: Int,
    val owner: JsonElement,
    val ships: Int? = null,
    val production: Int? = null,
    val killRatio: Int? = null,
)

@Serializable
data class FleetDto(
    val owner: String,
    val ships: Int,
    val from: String,
    val to: String,
    val distanceRemaining: Double,
)

@Serializable
data class TurnEventDto(
    val type: String,
    val player: String? = null,
    val from: String? = null,
    val to: String? = null,
    val ships: Int? = null,
    val planet: String? = null,
    val attacker: String? = null,
    val defender: JsonElement? = null,
    val attackerShips: Int? = null,
    val defenderShips: Int? = null,
    val attackerSurviving: Int? = null,
    val defenderSurviving: Int? = null,
    val conquered: Boolean? = null,
    val formerOwner: String? = null,
    val turn: Int? = null,
    val newProduction: Int? = null,
    val newKillRatio: Int? = null,
    val owner: String? = null,
    val attackerKillRatio: Int? = null,
    val defenderKillRatio: Int? = null,
)

@Serializable
data class ScoresResponse(
    val scores: List<PlayerScoreDto>,
)

@Serializable
data class PlayerScoreDto(
    val playerId: String,
    val playerName: String,
    val planetsOwned: Int,
    val totalShips: Int,
    val score: Double,
)

@Serializable
data class GalaxyPlanetDto(
    val name: String,
    val x: Int,
    val y: Int,
)

@Serializable
data class GalaxyResponse(
    val width: Int,
    val height: Int,
    val planets: List<GalaxyPlanetDto>,
)

// ---- Conversion helpers ----

fun Owner.toJson(): JsonElement = when (this) {
    is Owner.Neutral -> JsonPrimitive("neutral")
    is Owner.Player -> JsonPrimitive(id.value)
}

fun JsonElement.toOwner(): Owner {
    val prim = jsonPrimitive
    return if (prim.content == "neutral") Owner.Neutral
    else Owner.Player(PlayerId(prim.content))
}

fun GamePhase.toJson(): JsonElement = when (this) {
    is GamePhase.Lobby -> JsonPrimitive("lobby")
    is GamePhase.SetUp -> Json.parseToJsonElement(
        """{"status":"setup","round":$round}"""
    )
    is GamePhase.InProgress -> Json.parseToJsonElement(
        """{"status":"in_progress","turn":$turn}"""
    )
    is GamePhase.Finished -> JsonPrimitive("finished")
}

fun FleetOrderDto.toDomain(): FleetOrder = FleetOrder(
    from = PlanetId(from),
    to = PlanetId(to),
    ships = ShipCount(ships),
)

fun Fleet.toDto(): FleetDto = FleetDto(
    owner = owner.value,
    ships = ships.value,
    from = from.label,
    to = to.label,
    distanceRemaining = distanceRemaining,
)

fun TurnEvent.toDto(): TurnEventDto = when (this) {
    is TurnEvent.FleetLaunched -> TurnEventDto(
        type = "fleet_launched", player = player.value,
        from = from.label, to = to.label, ships = ships.value,
    )
    is TurnEvent.FleetArrived -> TurnEventDto(
        type = "fleet_arrived", player = player.value,
        to = to.label, ships = ships.value,
    )
    is TurnEvent.BattleResolved -> TurnEventDto(
        type = "battle", planet = planet.label,
        attacker = attacker.value, defender = defender.toJson(),
        attackerShips = forces.attacker.value, defenderShips = forces.defender.value,
        attackerSurviving = surviving.attacker.value, defenderSurviving = surviving.defender.value,
        conquered = outcome == BattleOutcome.CONQUERED,
        attackerKillRatio = attackerKillRatio.value, defenderKillRatio = defenderKillRatio.value,
    )
    is TurnEvent.PlanetRevolted -> TurnEventDto(
        type = "revolt", planet = planet.label, formerOwner = formerOwner.value,
    )
    is TurnEvent.ProductionCompleted -> TurnEventDto(
        type = "production", player = player.value,
        planet = planet.label, ships = ships.value,
    )
    is TurnEvent.TurnStarted -> TurnEventDto(
        type = "turn_started", turn = turn,
    )
    is TurnEvent.EventProductionChanged -> TurnEventDto(
        type = "event_production_changed", planet = planet.label,
        owner = owner.value, newProduction = newProduction.value,
    )
    is TurnEvent.EventKillRatioChanged -> TurnEventDto(
        type = "event_kill_ratio_changed", planet = planet.label,
        owner = owner.value, newKillRatio = newKillRatio.value,
    )
    is TurnEvent.EventRevoltThwarted -> TurnEventDto(
        type = "event_revolt_thwarted", planet = planet.label,
        owner = owner.value,
    )
}

fun PlayerScore.toDto(): PlayerScoreDto = PlayerScoreDto(
    playerId = player.id.value,
    playerName = player.name.value,
    planetsOwned = planetsOwned,
    totalShips = totalShips.value,
    score = score,
)

fun PlayerInfo.toDto(): PlayerInfoDto = PlayerInfoDto(id = id.value, name = name.value)

fun GameStatus.toDto(): GameStatusResponse = GameStatusResponse(
    gameCode = gameCode,
    phase = phase.toJson(),
    players = players.map { it.toDto() },
)
