plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.1"
    id("com.gradleup.shadow") version "8.3.0"
}

group = "com.ohtoai.mobeditor"
version = project.findProperty("plugin_version")?.toString() ?: "1.0.0"
description = "MobEditor - Entity editing and spawn egg extraction tool"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "name" to project.name,
                "description" to project.description
            )
        }
    }
}
