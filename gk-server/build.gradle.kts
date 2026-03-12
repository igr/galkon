plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

application {
    mainClass.set("galkon.server.MainKt")
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
    }
}

val generateVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated-resources/version")
    val versionFile = outputDir.map { it.file("version.txt") }
    outputs.dir(outputDir)
    doLast {
        val sha = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }
            .standardOutput.asText.get().trim()
        versionFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(sha)
        }
    }
}

sourceSets.main {
    resources.srcDir(generateVersion)
}

// Include client dist in the application distribution
val copyClientDist by tasks.registering(Copy::class) {
    dependsOn(":gk-client:jsBrowserDistribution")
    from("${rootProject.projectDir}/gk-client/build/dist/js/productionExecutable")
    into(layout.buildDirectory.dir("client-dist"))
}

distributions {
    main {
        contents {
            from(copyClientDist) {
                into("client-dist")
            }
        }
    }
}

dependencies {
    implementation(project(":gk-game"))
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
}
