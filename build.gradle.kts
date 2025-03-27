plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"  // FatJar対応
    application
}

group = "com.github.Rei0925"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // JSONとコルーチン
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")  // JSON処理
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")     // コルーチン対応

    // Discordライブラリ (JDA)
    implementation("net.dv8tion:JDA:5.3.0")

    // Ktor依存関係
    implementation("io.ktor:ktor-client-core:2.3.8")           // クライアント
    implementation("io.ktor:ktor-client-cio:2.3.8")            // HTTP通信

    implementation("org.slf4j:slf4j-api:2.0.0")
    implementation("ch.qos.logback:logback-classic:1.4.6") // Logbackの依存関係を追加

    implementation("org.jline:jline:3.25.1")  // 最新版のJLineを追加
}

kotlin {
    jvmToolchain(21)
}

// アプリケーションのエントリーポイントを指定
application {
    mainClass.set("com.github.Rei0925.MainKt")
}

// FatJarの生成
tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.github.Rei0925.MainKt"  // メインクラス指定
    }
}

// ShadowJarタスクでFatJarを作成
tasks {
    shadowJar {
        archiveClassifier.set("all")
        manifest {
            attributes["Main-Class"] = "com.github.Rei0925.MainKt"  // メインクラス
        }

        archiveFileName.set("HookMaster-for-Discord.jar")  // 出力ファイル名
        mergeServiceFiles()
    }
}

