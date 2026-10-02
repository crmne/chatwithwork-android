import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase is configured by google-services.json, which is not committed. Push
// notifications stay off in builds without it; see docs/push-notifications.md.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(
        plugin =
            libs.plugins.google.services
                .get()
                .pluginId
    )
}

// Release versions come from the tag: -PversionName=1.2.3 -PversionCode=10203.
val appVersionName = providers.gradleProperty("versionName").getOrElse("0.1.0")
val appVersionCode = providers.gradleProperty("versionCode").getOrElse("100").toInt()

// Where debug builds point: -PbaseUrl=http://localhost:3000 (with `adb reverse
// tcp:3000 tcp:3000`) for a phone, or the default, the host machine as seen
// from the emulator.
val devBaseUrl = providers.gradleProperty("baseUrl").getOrElse("http://10.0.2.2:3000").trimEnd('/')

// Links to the development server open the debug app. App links need a
// domain name, so a server at "localhost" claims chatwithwork.localhost,
// the name devhost gives it (AGENTS.md), instead.
val devLinkHost = URI(devBaseUrl).host.takeIf { it.contains('.') } ?: "chatwithwork.localhost"

// The release key never lives in this repository. Release machines pass it as
// Gradle properties or environment variables (see README.md); without them,
// release builds are unsigned and staging builds use the debug key.
fun signingValue(name: String): String? = providers
    .gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orNull
    ?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("RELEASE_KEYSTORE_PATH")

android {
    namespace = "com.chatwithwork.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chatwithwork.app"
        minSdk = 28
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "BASE_URL", "\"https://chatwithwork.com\"")
        buildConfigField("String", "ENVIRONMENT", "\"production\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        manifestPlaceholders["appLinkHost"] = "chatwithwork.com"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = signingValue("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "BASE_URL", "\"$devBaseUrl\"")
            buildConfigField("String", "ENVIRONMENT", "\"development\"")
            manifestPlaceholders["appLinkHost"] = devLinkHost
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }

        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            matchingFallbacks += "release"
            buildConfigField("String", "BASE_URL", "\"https://staging.chatwithwork.com\"")
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            manifestPlaceholders["appLinkHost"] = "staging.chatwithwork.com"
            // Staging builds are for the team's own phones: signed with the
            // release key when there is one, otherwise with the debug key.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        lintConfig = file("lint.xml")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.hotwire.core) { version { strictly(libs.versions.hotwire.get()) } }
    implementation(libs.hotwire.navigation.fragments) { version { strictly(libs.versions.hotwire.get()) } }
    implementation(libs.bridge.components)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.webkit)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    // Reads the bundled path configuration the way Hotwire Native does.
    testImplementation(libs.gson)
}
