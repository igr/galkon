plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("galkon.bot.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    standardOutput = System.out
}

dependencies {
    implementation(project(":gk-common"))
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
}
