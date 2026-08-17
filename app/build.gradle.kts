import java.util.Properties

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
        // Exigé par les tests instrumentés, qui sont le seul moyen de jouer
        // une migration Room contre un vrai SQLite.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * La signature vit hors du dépôt : `~/.cocorico-release.jks`, décrit par un
     * `keystore.properties` non versionné, ou par les variables d'environnement
     * de l'intégration continue. Un dépôt qui contient sa propre clé de
     * signature n'en a plus.
     *
     * Absente, la configuration est simplement nulle : `assembleDebug` et les
     * tests continuent de fonctionner sur une machine qui n'a pas la clé, ce
     * qui est le cas de tous les contributeurs sauf le mainteneur.
     */
    val proprietesSignature = rootProject.file("keystore.properties")
    val signatureDisponible =
        proprietesSignature.exists() ||
            System.getenv("COCORICO_KEYSTORE") != null

    signingConfigs {
        if (signatureDisponible) {
            create("release") {
                val props = Properties()
                if (proprietesSignature.exists()) {
                    proprietesSignature.inputStream().use { flux -> props.load(flux) }
                }
                storeFile =
                    file(
                        props.getProperty("storeFile") ?: System.getenv("COCORICO_KEYSTORE"),
                    )
                storePassword = props.getProperty("storePassword")
                    ?: System.getenv("COCORICO_KEYSTORE_PASSWORD")
                keyAlias = props.getProperty("keyAlias") ?: System.getenv("COCORICO_KEY_ALIAS")
                keyPassword = props.getProperty("keyPassword")
                    ?: System.getenv("COCORICO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 et la réduction des ressources. Les règles propres au projet
            // sont dans `proguard-rules.pro` : elles protègent les noms que
            // l'application relit depuis le disque, que R8 ne peut pas deviner.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signatureDisponible) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // Importée directement par le code de production (`AlarmService`,
    // `BootReceiver`) mais jamais déclarée : la compilation reposait sur une
    // remontée transitive de lifecycle/room/datastore, qu'une montée de
    // version de l'une d'elles pouvait retirer sans prévenir.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Fournit `androidx.lifecycle.compose.LocalLifecycleOwner`, qui remplace
    // celui de `compose.ui`, déprécié depuis qu'il a déménagé.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
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

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
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

// `MigrationTestHelper` lit les schémas depuis les ressources du test
// instrumenté : sans cette ligne, il ne trouve pas `1.json` et échoue avec un
// message qui n'a rien à voir avec la migration.
android.sourceSets
    .getByName("androidTest")
    .assets
    .srcDir("$projectDir/schemas")
