import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.22"
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
}

taboolib {
    env {
        // 安装模块
        install(Basic)
        install(Bukkit)
        install(BukkitUtil)
        install(BukkitUI)
        install(BukkitHook)
        install(BukkitFakeOp)
        install(BungeeCord)
        install(MinecraftChat)
        install(MinecraftEffect)
        install(CommandHelper)
        install(I18n)
        install(Metrics)
        install(Database)
        install(DatabasePlayer)
        install(Kether)
    }
    version {
        taboolib = "6.2.3-20d868d"
        coroutines = "1.8.1"
    }
    relocate("top.maplex.arim","xxx.xxx.arim")
    relocate("kotlinx.serialization", "kotlinx.serialization163")
}


repositories {
    maven { url = uri("https://repo.pcgamingfreaks.at/repository/maven-everything") }
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "adyeshach"
        url = uri("https://repo.tabooproject.org/repository/releases/")
    }
    maven {
        name = "mythicmobs"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }

    maven {
        url = uri("https://mvnrepository.com/")
    }

    maven {
        name = "citizens-repo"
        url = uri("https://maven.citizensnpcs.co/repo")
    }
    mavenCentral()
    //mavenLocal()
    maven { url = uri("https://jitpack.io") }
    maven("https://mvnrepository.com/artifact/")
    maven("https://mvnrepository.com/artifact/net.byteflux/libby-bukkit")
    maven(url = "https://mvn.lumine.io/repository/maven-public/")
}
dependencies {
    compileOnly("net.luckperms:api:5.4")
    implementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT") { isTransitive = false }
    implementation("me.clip:placeholderapi:2.11.5")
    compileOnly("io.lumine:Mythic-Dist:5.6.1")
    implementation("net.kyori:adventure-text-serializer-legacy:4.19.0")
    implementation("com.sk89q.worldguard:worldguard-bukkit:7.0.10-SNAPSHOT")
    implementation("net.kyori:adventure-api:4.19.0")
    implementation("net.kyori:adventure-text-minimessage:4.19.0")
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT") { isTransitive = false }
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
    taboo("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.0") { isTransitive = false }
    taboo("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.0") { isTransitive = false }
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3") { isTransitive = false }
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3"){ isTransitive = false }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}