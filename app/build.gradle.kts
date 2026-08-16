plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cocorico"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cocorico"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // Sert à `BuildConfig.DEBUG` : les traces de calibration des capteurs
        // ne doivent exister que dans les versions de débogage.
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Room écrit le schéma généré dans `app/schemas/`, et ce dossier est versionné.
// C'est le seul garde-fou automatique contre une migration incohérente : le
// schéma attendu par Room y devient un fichier lisible en revue, et toute
// divergence avec le SQL de `MIGRATION_1_2` — type, nullabilité, valeur par
// défaut — apparaît dans le diff au lieu de se manifester par un plantage au
// démarrage chez quelqu'un qui met à jour depuis la version précédente.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
