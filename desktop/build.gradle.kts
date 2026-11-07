import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.compose") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.sonora"
version = "1.1.0"

// РџСѓС‚СЊ Рє РїСЂРѕРµРєС‚Сѓ СЃРѕРґРµСЂР¶РёС‚ РєРёСЂРёР»Р»РёС†Сѓ вЂ” jpackage Рё WiX СЃ РЅРµР№ РЅРµ РґСЂСѓР¶Р°С‚,
// РїРѕСЌС‚РѕРјСѓ СЃР±РѕСЂРєР° РёРґС‘С‚ РІ РєР°С‚Р°Р»РѕРі СЃ Р»Р°С‚РёРЅСЃРєРёРј РїСѓС‚С‘Рј.
System.getenv("SONORA_BUILD_DIR")?.let { layout.buildDirectory.set(file(it)) }

kotlin {
    jvmToolchain(17)
}

val fx = "21.0.5"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    // РђСѓРґРёРѕ: JavaFX Media (MP3/AAC, РїРѕС‚РѕРєРѕРІРѕРµ РІРѕСЃРїСЂРѕРёР·РІРµРґРµРЅРёРµ РїРѕ HTTP, РїРµСЂРµРјРѕС‚РєР°, СЃРєРѕСЂРѕСЃС‚СЊ).
    implementation("org.openjfx:javafx-base:$fx:win")
    implementation("org.openjfx:javafx-graphics:$fx:win")
    implementation("org.openjfx:javafx-media:$fx:win")
    // РњРµРґРёР°РєР»Р°РІРёС€Рё Рё С‚С‘РјРЅС‹Р№ Р·Р°РіРѕР»РѕРІРѕРє РѕРєРЅР° Windows.
    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
}

compose.desktop {
    application {
        mainClass = "com.sonora.player.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx512m")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Sonora"
            packageVersion = "1.1.0"
            description = "Sonora music player"
            vendor = "Sonora"
            // РўРѕР»СЊРєРѕ РЅСѓР¶РЅС‹Рµ РјРѕРґСѓР»Рё JDK вЂ” СѓСЃС‚Р°РЅРѕРІС‰РёРє РїРѕР»СѓС‡Р°РµС‚СЃСЏ РІ СЂР°Р·С‹ РјРµРЅСЊС€Рµ.
            modules("java.instrument", "jdk.jfr", "jdk.unsupported", "jdk.crypto.ec", "java.naming")
            windows {
                menuGroup = "Sonora"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f1d3c52-9a4e-4f0b-8f2a-5b1c7e9d2a41"
                // jpackage РїРѕСЂС‚РёС‚ РєРёСЂРёР»Р»РёС†Сѓ РІ РїСѓС‚СЏС…, РїРѕСЌС‚РѕРјСѓ Р·РЅР°С‡РѕРє РєР»Р°РґС‘Рј СЂСЏРґРѕРј СЃРѕ СЃР±РѕСЂРєРѕР№.
                val icon = layout.buildDirectory.file("icon.ico").get().asFile
                icon.parentFile.mkdirs()
                project.file("icon.ico").copyTo(icon, overwrite = true)
                iconFile.set(icon)
            }
        }
    }
}
