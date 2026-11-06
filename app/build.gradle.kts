import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Configuración de build desde `.env` (raíz del repo) con fallback a variables
// de entorno (CI). Nada de esto es obligatorio: sin `.env` la app compila igual.
// ---------------------------------------------------------------------------
val dotEnv: Map<String, String> = rootProject.file(".env").let { file ->
    if (!file.exists()) emptyMap() else file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            key to value
        }
}

fun env(key: String, default: String = ""): String =
    dotEnv[key]?.takeIf { it.isNotEmpty() } ?: System.getenv(key)?.takeIf { it.isNotEmpty() } ?: default

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val versionProps = Properties().apply { rootProject.file("gradle.properties").inputStream().use { load(it) } }
val lumenVersionCode = env("LUMEN_VERSION_CODE", versionProps.getProperty("lumen.versionCode", "1")).toInt()
val lumenVersionName = env("LUMEN_VERSION_NAME", versionProps.getProperty("lumen.versionName", "1.0.0"))

// Copia opcional de configuraciones WireGuard (.conf) a los assets del APK.
val bundleVpnConfigs = env("LUMEN_BUNDLE_VPN_CONFIGS", "false").toBoolean()
val vpnConfigDir = rootProject.file(env("LUMEN_VPN_CONFIG_DIR", "vpn-configs"))
val generatedVpnAssets = layout.buildDirectory.dir("generated/vpnAssets")

val copyVpnConfigs by tasks.registering(Sync::class) {
    from(vpnConfigDir) { include("*.conf") }
    into(generatedVpnAssets.map { it.dir("vpn") })
    onlyIf { bundleVpnConfigs && vpnConfigDir.exists() }
}

android {
    namespace = "app.lumentv"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.lumentv"
        versionCode = lumenVersionCode
        versionName = lumenVersionName

        buildConfigField("String", "DEFAULT_PLAYLIST_NAME", env("LUMEN_DEFAULT_PLAYLIST_NAME").asBuildConfigString())
        buildConfigField("String", "DEFAULT_XTREAM_SERVER", env("LUMEN_DEFAULT_XTREAM_SERVER").asBuildConfigString())
        buildConfigField("String", "DEFAULT_XTREAM_USERNAME", env("LUMEN_DEFAULT_XTREAM_USERNAME").asBuildConfigString())
        buildConfigField("String", "DEFAULT_XTREAM_PASSWORD", env("LUMEN_DEFAULT_XTREAM_PASSWORD").asBuildConfigString())
        buildConfigField("String", "DEFAULT_M3U_URL", env("LUMEN_DEFAULT_M3U_URL").asBuildConfigString())
        buildConfigField("String", "DEFAULT_EPG_URL", env("LUMEN_DEFAULT_EPG_URL").asBuildConfigString())
        buildConfigField("String", "UPDATE_GITHUB_REPO", env("LUMEN_UPDATE_GITHUB_REPO").asBuildConfigString())
        buildConfigField("String", "UPDATE_GITHUB_TOKEN", env("LUMEN_UPDATE_GITHUB_TOKEN").asBuildConfigString())
        buildConfigField("String", "UPDATE_MANIFEST_URL", env("LUMEN_UPDATE_MANIFEST_URL").asBuildConfigString())
        buildConfigField("String", "SPEEDTEST_URL", env("LUMEN_SPEEDTEST_URL", "https://speed.cloudflare.com/__down?bytes=25000000").asBuildConfigString())
        buildConfigField("int", "IMPORT_SERVER_PORT", env("LUMEN_IMPORT_SERVER_PORT", "8765"))
    }

    // Dos UIs sobre el mismo núcleo (datos, VPN, reproductor): ver docs/PLAN-MOVIL.md.
    // El sabor `tv` conserva el applicationId `app.lumentv`: cambiarlo rompería las
    // actualizaciones del Fire TV. Ambos sabores se firman con el mismo keystore.
    flavorDimensions += "device"
    productFlavors {
        create("tv") {
            dimension = "device"
            minSdk = 25 // Fire OS 6 (Android 7.1). Fire OS 7 = API 28, Fire OS 8 = API 30.
            targetSdk = 34
        }
        create("mobile") {
            dimension = "device"
            applicationIdSuffix = ".mobile"
            minSdk = 26
            targetSdk = 35
            // OTA: misma Release de GitHub que la TV, pero con su manifiesto `update-mobile.json`
            // (UpdateManager lo elige según BuildConfig.FLAVOR). La opción B tiene URL propia.
            buildConfigField("String", "UPDATE_MANIFEST_URL", env("LUMEN_UPDATE_MOBILE_MANIFEST_URL").asBuildConfigString())
        }
    }

    signingConfigs {
        val keystorePath = env("LUMEN_KEYSTORE_PATH")
        if (keystorePath.isNotEmpty() && rootProject.file(keystorePath).exists()) {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = env("LUMEN_KEYSTORE_PASSWORD")
                keyAlias = env("LUMEN_KEY_ALIAS", "lumen")
                keyPassword = env("LUMEN_KEY_PASSWORD", env("LUMEN_KEYSTORE_PASSWORD"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin keystore configurado, firmamos con la clave debug para que el APK sea instalable.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    sourceSets["main"].assets.srcDir(generatedVpnAssets)

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
    lint {
        abortOnError = false
    }
}

tasks.named("preBuild") { dependsOn(copyVpnConfigs) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)
    "tvImplementation"(libs.tv.material)
    "mobileImplementation"(libs.compose.material3)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.wireguard.tunnel)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
