
plugins {
    kotlin("jvm") version "1.6.0" apply false
    id("com.github.gmazzo.buildconfig") version "3.0.3" apply false
}

allprojects {
    group = "io.github.staakk"
    version = "1.0-SNAPSHOT"
}

subprojects {
    repositories {
        mavenCentral()
    }
}
