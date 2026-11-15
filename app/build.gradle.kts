plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.openfocus.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.openfocus.app"
        minSdk = 26
        targetSdk = 35
        // CI sets these per build (see .github/workflows/build.yml); local builds use 1 / "<base>.0-dev".
        val build = System.getenv("OPENFOCUS_BUILD_NUMBER")?.toIntOrNull()
        versionCode = build ?: 1
        versionName = "${providers.gradleProperty("openfocus.version").get()}.${build ?: "0-dev"}"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Release signing comes from the environment (CI secrets). Without it, release builds are
        // signed with the debug key so they still install for testing.
        create("release") {
            // CI passes unset secrets as empty strings, so treat blank like missing.
            val store = System.getenv("OPENFOCUS_KEYSTORE")?.takeIf { it.isNotBlank() }
            if (store != null && file(store).exists()) {
                storeFile = file(store)
                storePassword = System.getenv("OPENFOCUS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OPENFOCUS_KEY_ALIAS")
                keyPassword = System.getenv("OPENFOCUS_KEY_PASSWORD")
            }
        }
    }

    androidResources {
        // Builds res/xml/_generated_res_locale_config.xml from the values-<lang> folders, so the
        // per-app language picker (Android 13+) lists every translation automatically.
        generateLocaleConfig = true
    }

    buildTypes {
        debug {
            // en-XA (accented, longer) and ar-XB (right-to-left) pseudolocales for spotting
            // untranslated or clipped text: Settings > System > Languages on a debug build.
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets {
        // Exported Room schemas for MigrationTest (Robolectric reads the tested variant's assets).
        // Debug builds only; release APKs don't carry them.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("screenshots.dir", rootProject.file("docs/screenshots").absolutePath) }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
