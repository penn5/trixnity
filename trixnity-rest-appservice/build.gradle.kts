plugins {
    kotlin("plugin.serialization") version Versions.kotlin
    kotlin("multiplatform") version Versions.kotlin
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                api(project(":trixnity-rest-client"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}-native-mt") {
                    version { strictly("${Versions.kotlinxCoroutines}-native-mt") }
                }

                implementation("io.ktor:ktor-server-core:${Versions.ktor}")

                implementation("io.ktor:ktor-auth:${Versions.ktor}")
                implementation("io.ktor:ktor-serialization:${Versions.ktor}")

                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("io.mockk:mockk:${Versions.mockk}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("io.ktor:ktor-server-test-host:${Versions.ktor}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}