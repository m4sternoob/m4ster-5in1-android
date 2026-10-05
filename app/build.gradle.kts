plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.m4ster.fiveinone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.m4ster.fiveinone"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Two editions from one codebase: withHints ships the cryptic Hint
    // button in Number Guessing, noHints leaves the game pure.
    flavorDimensions += "edition"
    productFlavors {
        create("withHints") {
            dimension = "edition"
            buildConfigField("boolean", "HINTS_ENABLED", "true")
            resValue("string", "app_name", "5IN1")
        }
        create("noHints") {
            dimension = "edition"
            buildConfigField("boolean", "HINTS_ENABLED", "false")
            applicationIdSuffix = ".nohints"
            resValue("string", "app_name", "5IN1 No Hints")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Persisted stats (guessing wins/streak, snake high score).
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
