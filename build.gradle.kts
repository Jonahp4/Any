plugins {
    java
}

group = "io.github.jonahp4"
version = "1.0.0"
description = "Re-enables vanilla piston (TNT/carpet/rail) and tripwire (string) duping on Paper"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
    }

    processResources {
        val props = mapOf("version" to project.version, "description" to project.description)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        manifest {
            // No NMS here, so tell Paper to skip its plugin remapper entirely (faster first boot).
            attributes("paperweight-mappings-namespace" to "mojang")
        }
    }

    test {
        useJUnitPlatform()
    }
}
