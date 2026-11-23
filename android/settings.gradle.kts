pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "QbitPlan"

// :core – czysty Kotlin (bez Androida): modele, API EduPage, parsery, logika.
//         Testy uruchamiają się na zwykłej JVM, szybko i bez emulatora.
// :app  – aplikacja Android: UI (Compose), Hilt, Room, DataStore.
include(":core", ":app")
