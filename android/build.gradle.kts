// Plik główny: tylko deklaruje wtyczki (apply false = "pobierz, ale nie
// włączaj tutaj"). Moduły :app i :core włączają te, których potrzebują.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// Testy obu modułów czytają wspólne fixtures z katalogu repo (../fixtures).
subprojects {
    tasks.withType<Test>().configureEach {
        val fixtures = rootProject.layout.projectDirectory.dir("../fixtures")
        inputs.dir(fixtures).withPropertyName("fixtures")
        systemProperty("fixtures.dir", fixtures.asFile.absolutePath)
    }
}
