import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: either a keystore.properties file in the project root
// (storeFile, storePassword, keyAlias, keyPassword) or SIGNING_* environment
// variables (used by the GitHub release workflow). Without either, release
// builds are signed with the debug key so they can still be installed locally.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    keystoreProperties.getProperty(property) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val sourceUrl = providers.gradleProperty("passradar.sourceUrl").getOrElse("")

android {
    namespace = "io.github.passradar"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.passradar"
        minSdk = 21
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.1"

        buildConfigField("String", "SOURCE_URL", "\"$sourceUrl\"")
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "SIGNING_STORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
            // Keeps builds reproducible (F-Droid)
            vcsInfo.include = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time on Android 5.0–7.1
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Google Play builds (./gradlew bundleRelease -Pplay) leave out the direct request to
    // skip battery optimisation, which Play restricts. See PUBLISHING.md.
    if (providers.gradleProperty("play").isPresent) {
        sourceSets.getByName("release").manifest.srcFile("src/play/AndroidManifest.xml")
    }

    androidResources {
        localeFilters += listOf("en", "nb")
        generateLocaleConfig = true
    }

    // Google Play would otherwise only install the phone's language, and the
    // language can be changed in the app's settings.
    bundle {
        language {
            enableSplit = false
        }
    }

    // Keeps the APK free of Google's encrypted dependency metadata (F-Droid requirement).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.backdrop)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.osmdroid.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
}
