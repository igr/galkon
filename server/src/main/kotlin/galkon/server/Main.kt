package galkon.server

import galkon.common.GameConfig
import galkon.common.GameJson
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import java.io.File

private val log = logger()

/** Game version from git commit SHA, embedded at build time. */
val gameVersion: String =
    object {}.javaClass.getResourceAsStream("/version.txt")?.bufferedReader()?.readText()?.trim() ?: "dev"

fun main() {
    log.info("Galkon server: [$gameVersion]")
    val lobby = Lobby()

    // Start cleanup coroutine
    val cleanupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    cleanupScope.launch {
        while (isActive) {
            delay(GameConfig.CLEANUP_INTERVAL)
            lobby.removeInactive()
        }
    }

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(GameJson)
        }
        install(CORS) {
            allowSameOrigin = true
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.ContentType)
        }
        install(StatusPages) {
            exception<HttpException> { call, cause ->
                call.respond(cause.status, galkon.common.ErrorResponse(cause.message))
            }
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    galkon.common.ErrorResponse(cause.message ?: "Internal server error")
                )
            }
        }
        routing {
            gameRoutes(lobby)
            dashboardRoutes(lobby)

            // Serve client static files — try multiple locations
            // In distribution: JAR is in lib/, go up to distribution root
            val appBase = try {
                File(Lobby::class.java.protectionDomain.codeSource.location.toURI()).parentFile.parentFile
            } catch (_: Exception) { null }

            val clientDir = listOfNotNull(
                "client/build/dist/js/productionExecutable",
                "../client/build/dist/js/productionExecutable",
                "client-dist",
                appBase?.resolve("client-dist")?.path,
            ).map { File(it) }.firstOrNull { it.exists() && it.isDirectory }

            if (clientDir != null) {
                staticFiles("/", clientDir) {
                    default("index.html")
                }
            }
        }
    }.start(wait = true)
}
