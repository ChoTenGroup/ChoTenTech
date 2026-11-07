import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.38"
    kotlin("jvm") version "2.4.21"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
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
            name("Sertraline")
            name("packetevents")
            name("LithiumCarbon").optional(true)
            // name("ProtocolLib").optional(true)
            // 可选依赖.
            // name("XXX").optional(true)
        }
        load("STARTUP")
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
        taboolib = "6.3.0-75b18a2"
        coroutines = "1.8.1"

    }
    relocate("top.maplex.arim","io.github.zzzyyylllty.chotentech.dep.arim")
//    relocate("cn.gtemc.itembridge", "io.github.zzzyyylllty.chotentech.dep.itembridge")
}

// 仓库清单（已整理：去除无效/已停服仓库与重复项，保持原有解析顺序）。
// 清理记录：
//  - mvnrepository.com/artifact/ 是网页目录不是 Maven 仓库，纯浪费一次解析
//  - dl.bintray.com（Bintray）2021 年已停服
//  - jcenter() 已日落（read-only），中文镜像保留在 aliyun jcenter
//  - nexus.maplex.top 原声明两份且带 isAllowInsecureProtocol（URL 实为 https，无需该标记）
//  - phoenixdevt/auxilor/tabooproject/papermc/extendedclip/momirealms/opencollab/
//    mavenCentral 等此前各声明 2~4 次，重复仓库拖慢每次解析并有供应链混淆风险
repositories {
    mavenCentral()

    maven {
        url = uri("https://repo.opencollab.dev/main/")
    }
    maven("https://repo.momirealms.net/releases/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://maven.aliyun.com/repository/google")
    maven("https://repo.fancyinnovations.com/releases")
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
    maven {
        // 枫溪的仓库
        url = uri("https://nexus.maplex.top/repository/maven-public/")
    }
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/jcenter")

    maven("https://repo.tabooproject.org/repository/releases/")
    maven("https://repo.papermc.io/repository/maven-snapshots/")
    maven {
        name = "mythicmobs"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
    maven("https://repo.codemc.org/repository/maven-public")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://repo.gtemc.net/releases/")
    maven("https://r.irepo.space/maven/")
    maven("https://repo.hibiscusmc.com/releases/")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/") {
        name = "sonatype-oss-snapshots"
    }

    maven("https://libraries.minecraft.net")
    maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }
    maven { url = uri("https://repo.codemc.io/repository/maven-snapshots/") }
    maven { url = uri("https://repo.dmulloy2.net/repository/public/") }
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    // 枪械系统需要 ModernSItem.config / ItemProcessorManager 注册能力（3.13 起提供）。
    // 3.13.0.0 未发布到公共仓库，改为使用本地构建产物（由 Sertraline 仓库 `gradlew :project:common:jar` 生成）。
    // implementation("io.github.zzzyyylllty.sertraline:common:3.9.0.3")
    compileOnly(files("$rootDir/libs/common-3.13.0.0.jar"))
    compileOnly(files("$rootDir/libs/sertraline-platform-compat.jar"))
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
//    compileOnly(files("$rootDir/libs/Se-common-3.8.19.4-api.jar"))
    compileOnly("de.oliver:FancyNpcs:2.7.0")
    compileOnly("de.oliver:FancyHolograms:2.9.1")
    compileOnly("ink.ptms.chemdah:api:1.1.34")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.momirealms:craft-engine-core:26.8.1") // 将 "{version}" 替换为插件稳定版本
    compileOnly("net.momirealms:craft-engine-bukkit:26.8.1") // 例如 0.0.60
    implementation("io.papermc.paper:paper-api:26.2.build.133-stable") { isTransitive = false }
    implementation("net.kyori:adventure-text-serializer-legacy:5.2.0")
    implementation("net.kyori:adventure-text-serializer-gson:5.2.0")
    implementation("net.kyori:adventure-api:5.2.0")
    implementation("net.kyori:adventure-text-serializer-plain:5.2.0")
    implementation("net.kyori:adventure-text-minimessage:5.2.0")
    compileOnly("com.google.code.gson:gson:2.10.1")
    implementation("me.clip:placeholderapi:2.11.6")
    taboo("ink.ptms:um:1.2.1")
    // ItemBridge (optional)
    taboo("cn.gtemc:itembridge:1.0.31")
    implementation("com.sk89q.worldguard:worldguard-bukkit:7.0.10-SNAPSHOT")
    compileOnly(kotlin("stdlib"))
    // The root API jar omits common event classes; use the common module API for loot hooks.
    compileOnly(files("$rootDir/libs/LithiumCarbon-common-1.5.6-api.jar"))
    compileOnly(fileTree("libs") { exclude("li2co3-project-1.5.6-api.jar", "LithiumCarbon-1.5.6-api.jar", "LithiumCarbon-common-1.5.6-api.jar") })
    // Quetiapine merge dependencies
    taboo("io.github.zzzyyylllty:EmbianComponent:1.0.5")
    compileOnly("org.tabooproject.fluxon:core:1.5.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

configurations.named("testRuntimeClasspath") {
    exclude(group = "org.spigotmc", module = "spigot-api")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    minHeapSize = "512m"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}