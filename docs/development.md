# Dla deweloperów

E-Plan (w kodzie i repo: QbitPlan, pakiet `dev.milkthe.qbitplan`).

## Struktura repo

```
android/            aplikacja (Kotlin, Jetpack Compose, Material 3), zob. android/README.md
worker/             Cloudflare Worker: co 5 min sprawdza zastępstwa, push FCM, zob. worker/README.md
docs/               edupage-api.md: opis endpointów EduPage; screenshots/: zrzuty do README
fixtures/           zapisane odpowiedzi EduPage do testów (apka + worker)
.github/workflows/  android.yml: tag v* → APK → GitHub Release
                    tests.yml: testy apki
                    worker.yml: zmiany w worker/** → wrangler deploy
```

Apka pobiera dane bezpośrednio z EduPage, bez własnego backendu. Worker tylko wysyła
powiadomienia (sama data dnia); telefon pobiera ten dzień i sam sprawdza, co się zmieniło.

## Budowanie

Wymagania: JDK 17+ i Android SDK z platformą `android-37.2` (Android Studio
albo `sdkmanager`). Szczegóły: [`android/README.md`](../android/README.md).

```bash
cd android
./gradlew :core:test :app:testDebugUnitTest   # testy jednostkowe
./gradlew :app:assembleDebug                  # APK debug → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest -Proborazzi.record=true   # zrzuty ekranów → app/build/screenshots/
```

Zrzuty w `docs/screenshots/` pochodzą z tego ostatniego polecenia.

## Wydanie

Wersja bierze się z tagu: wypchnięcie `v*` (np. `v1.0.1`) buduje podpisany APK
`E-Plan-v1.0.1.apk` i publikuje go jako GitHub Release. Bez tagu wersja to ta wpisana
w `android/app/build.gradle.kts`.

Workflow potrzebuje sekretów repo: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. Lokalnie podpis release czyta `android/keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Klucz (`*.jks`)
i `keystore.properties` nigdy nie trafiają do repo. Każda aktualizacja musi być podpisana
tym samym kluczem, inaczej trzeba odinstalować apkę.
