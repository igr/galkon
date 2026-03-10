plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("galkon.server.MainKt")
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

dependencies {
    implementation(project(":game"))
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
}
