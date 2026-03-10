plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "galkon.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":common"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core.js)
                implementation(libs.kotlinx.html.js)
                implementation(devNpm("sass", "1.87.0"))
                implementation(devNpm("sass-loader", "16.0.5"))
                implementation(devNpm("style-loader", "4.0.0"))
                implementation(devNpm("css-loader", "7.1.2"))
            }
        }
    }
}
