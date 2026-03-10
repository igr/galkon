plugins {
    kotlin("multiplatform") version "2.1.10" apply false
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.1.10" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
