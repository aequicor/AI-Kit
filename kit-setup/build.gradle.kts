plugins {
    kotlin("multiplatform") version "2.1.20"
}

group = "com.aikit"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    mingwX64("mingwX64") {
        binaries {
            executable {
                entryPoint = "com.aikit.setup.main"
                baseName = "kit-setup"
            }
        }
    }
    macosX64("macosX64") {
        binaries {
            executable {
                entryPoint = "com.aikit.setup.main"
                baseName = "kit-setup"
            }
        }
    }
    macosArm64("macosArm64") {
        binaries {
            executable {
                entryPoint = "com.aikit.setup.main"
                baseName = "kit-setup"
            }
        }
    }
    linuxX64("linuxX64") {
        binaries {
            executable {
                entryPoint = "com.aikit.setup.main"
                baseName = "kit-setup"
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
    }
}
