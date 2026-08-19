import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.31"
    kotlin("jvm") version "2.2.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

taboolib {
    description {
        name("ChoTenTech")
        desc("ChoTen backrooms plugin.")
        contributors {
            // 作者名称
            name("AkaCandyKAngel")
        }
        dependencies {
            name("MythicMobs").optional(true)
            name("Chemdah").optional(true)
            name("WorldGuard").optional(true)
            name("Sertraline").optional(true)
            // name("ProtocolLib").optional(true)
            // 可选依赖.
            // name("XXX").optional(true)
        }
    }
    env {
        repoTabooLib = "https://repo.tabooproject.org/repository/releases"
        fileLibs = "libraries"
        fileAssets = "assets"
        enableIsolatedClassloader = false
        // 安装模块
        install(Basic)
        install(Bukkit)
        install(BukkitUtil)
        install(BukkitUI)
        install(BukkitHook)
        install(BukkitNMSItemTag)
        install(BukkitNMSDataSerializer)
        install(BukkitNMSUtil)
        install(BukkitFakeOp)
        install(BungeeCord)
        install(MinecraftChat)
        install(MinecraftEffect)
        install(CommandHelper)
        install(I18n)
        install(Metrics)
        install(Database)
        install(Kether)
        install(BukkitNMSEntityAI)
        install(PtcObject)
        install(JavaScript)
        install(Jexl)
        install(DatabasePlayer)
    }
    version {
        taboolib = "6.3.0-0e96352"
        coroutines = "1.8.1"

    }
    relocate("top.maplex.arim","io.github.zzzyyylllty.chotentech.dep.arim")
//    relocate("cn.gtemc.itembridge", "io.github.zzzyyylllty.chotentech.dep.itembridge")
}

repositories {
    mavenCentral()

    maven {
        url = uri("https://repo.opencollab.dev/main/")
    }
    maven("https://repo.momirealms.net/releases/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://maven.aliyun.com/repository/google")
    maven("https://repo.fancyinnovations.com/releases")
    jcenter()
    google()
    mavenLocal()
    maven {
        url = uri("https://repo.auroramc.gg/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.aikar.co/content/groups/aikar/")
    }

    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }

    mavenLocal()
    maven("https://repo.auxilor.io/repository/maven-public/")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    maven("https://repo.aeoliancloud.com/release")
    maven("https://repo.aeoliancloud.com/releases")
    maven("https://repo.xiao-jie.top/repository/maven-releases")
    maven("https://jitpack.io")
    maven {
        name = "CodeMC"
        url = uri("https://repo.codemc.io/repository/maven-public/")
    }
    maven("https://central.sonatype.com/repository/maven-snapshots")
    maven("https://mvnrepository.com/artifact/")
    maven {
        // 枫溪的仓库
        url = uri("https://nexus.maplex.top/repository/maven-public/")
        isAllowInsecureProtocol = true
    }
    maven("https://maven.aliyun.com/repository/google")
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/jcenter")
    mavenCentral()
    jcenter()

    maven { url = uri("https://repo.tabooproject.org/repository/releases/") }
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/kotlinx/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.papermc.io/repository/maven-snapshots/")
    maven("https://repo.tabooproject.org/repository/releases/")
    maven {
        name = "mythicmobs"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://repo.gtemc.net/releases/")
    maven("https://repo.opencollab.dev/main/")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    maven("https://r.irepo.space/maven/")
    maven("https://repo.auxilor.io/repository/maven-public/")
    maven("https://repo.hibiscusmc.com/releases/")
    maven("https://repo.tabooproject.org/repository/releases/")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/") {
        name = "sonatype-oss-snapshots"
    }

    maven("https://libraries.minecraft.net")
    maven {
        // 枫溪的仓库
        url = uri("https://nexus.maplex.top/repository/maven-public/")
        isAllowInsecureProtocol = true
    }

    maven("https://repo.momirealms.net/releases/")
    maven("https://repo.momirealms.net/releases/")
    maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }

    maven { url = uri("https://repo.codemc.io/repository/maven-snapshots/") }

    maven { url = uri("https://repo.dmulloy2.net/repository/public/") }
    maven("https://repo.extendedclip.com/releases/")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
}

dependencies {
    implementation("io.github.zzzyyylllty.sertraline:common:3.9.0.3")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
//    compileOnly(files("$rootDir/libs/Se-common-3.8.19.4-api.jar"))
    compileOnly("de.oliver:FancyNpcs:2.7.0")
    compileOnly("de.oliver:FancyHolograms:2.9.1")
    compileOnly("ink.ptms.chemdah:api:1.1.34")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.momirealms:craft-engine-core:0.0.65") // 将 "{version}" 替换为插件稳定版本
    compileOnly("net.momirealms:craft-engine-bukkit:0.0.65") // 例如 0.0.60
    implementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT") { isTransitive = false }
    implementation("net.kyori:adventure-text-serializer-legacy:4.19.0")
    implementation("net.kyori:adventure-text-serializer-gson:4.19.0")
    implementation("net.kyori:adventure-api:4.19.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.19.0")
    implementation("net.kyori:adventure-text-minimessage:4.19.0")
    compileOnly("com.google.code.gson:gson:2.10.1")
    implementation("me.clip:placeholderapi:2.11.6")
    taboo("ink.ptms:um:1.2.1")
    // ItemBridge (optional)
    taboo("cn.gtemc:itembridge:1.0.31")
    implementation("com.sk89q.worldguard:worldguard-bukkit:7.0.10-SNAPSHOT")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
    // Quetiapine merge dependencies
    taboo("io.github.zzzyyylllty:EmbianComponent:1.0.5")
    compileOnly("org.tabooproject.fluxon:core:1.5.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}