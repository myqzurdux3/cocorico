plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

// Formatage automatique. Le linter de style est arrivé tard dans la vie du
// projet : il est donc configuré pour épouser les conventions déjà en place
// plutôt que de réécrire des milliers de lignes qui fonctionnent.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                // Les noms de tests sont des phrases entre accents graves :
                // c'est la convention du dépôt, et elle se lit mieux qu'un
                // camelCase dans un rapport d'échec.
                "ktlint_standard_function-naming" to "disabled",
                // Le dépôt commente abondamment le *pourquoi*, y compris à
                // l'intérieur des blocs ; cette règle voudrait tout remonter.
                "ktlint_standard_comment-wrapping" to "disabled",
            ),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
