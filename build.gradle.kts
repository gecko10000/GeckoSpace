plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "1.4.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("de.eldoria.plugin-yml.bukkit") version "0.6.0"
}

sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("res")
        }
    }
}

group = "gecko10000.geckospace"
version = "0.1"

bukkit {
    name = "GeckoSpace"
    main = "$group.$name"
    apiVersion = "1.13"
    depend = listOf("GeckoLib", "Nexo", "Citizens")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://redempt.dev/")
    maven("https://repo.nexomc.com/releases")
    maven("https://maven.citizensnpcs.co/repo")
    mavenLocal()
}

dependencies {
    compileOnly(kotlin("stdlib", version = "2.0.21"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("gecko10000.geckolib:GeckoLib:1.0-SNAPSHOT")
    compileOnly("com.nexomc:nexo:1.1.0")
    compileOnly("net.citizensnpcs:citizensapi:2.0.37-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}


tasks.register("update") {
    dependsOn(tasks.build)
    doLast {
        exec {
            workingDir(".")
            commandLine("../../dot/local/bin/update.sh")
        }
    }
}
