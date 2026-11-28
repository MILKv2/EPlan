# Dla deweloperów

E-Plan (repo: EPlan; w kodzie pakiet `dev.milkthe.qbitplan`, zostaje dla zgodności z Firebase i zainstalowanymi apkami).

## Struktura repo

```
android/            aplikacja (Kotlin, Jetpack Compose, Material 3), zob. android/README.md
worker/             Cloudflare Worker: co 5 min sprawdza zastępstwa, push FCM, zob. worker/README.md
docs/               edupage-api.md: opis endpointów EduPage; screenshots/: zrzuty do README
fixtures/           zapisane odpowiedzi EduPage do testów (apka + worker)
.github/workflows/  android.yml: nowa wersja w android/version.properties na main → APK → GitHub Release
                    tests.yml: testy apki i workera, raz na PR (worker wdraża Cloudflare z main, zob. worker/README.md)
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

## CI

`tests.yml` uruchamia testy raz na PR: przy otwarciu (albo ponownym otwarciu lub
oznaczeniu jako gotowy), a nie przy każdym pushu. Żeby przetestować nowe commity
w otwartym PR, dodaj etykietę `run tests` albo uruchom workflow ręcznie
(Actions → Tests → Run workflow). Artefakt `screenshots` zawiera zrzuty wszystkich ekranów.

## Wydanie

Wersja jest w jednym miejscu: `android/version.properties` (`versionName=1.0.1`).
Z niej Gradle liczy `versionCode` (`1.2.3` → `10203`), więc każdy build, lokalny i z CI,
ma właściwy numer, widoczny w „O aplikacji”.

Jak wydać nową wersję:

1. W PR zmień `versionName` w `android/version.properties` (np. `1.0.1` → `1.0.2`).
   Poprawka: ostatnia liczba; nowa funkcja: środkowa (`1.1.0`). Każda liczba poniżej 100.
2. Zmerguj PR do `main`. Workflow `android.yml` uruchomi testy, zbuduje podpisany
   `E-Plan-v1.0.2.apk`, utworzy tag `v1.0.2` i Release z listą zmian z PR-ów.
3. Apki sprawdzają ostatni Release najwyżej co 12 h. Gdy jest nowszy, na zakładce
   Ustawienia pojawia się kropka i przycisk „Pobierz” (link do APK z Release).

Tagów nie tworzy się ręcznie. Jeśli workflow padł, uruchom go ponownie („Run workflow”);
wersja, która ma już tag, jest pomijana. Sprawdzanie aktualizacji używa publicznego API
GitHuba bez logowania, więc działa tylko przy publicznym repo.

Workflow potrzebuje sekretów repo: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. Lokalnie podpis release czyta `android/keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Klucz (`*.jks`)
i `keystore.properties` nigdy nie trafiają do repo. Każda aktualizacja musi być podpisana
tym samym kluczem, inaczej trzeba odinstalować apkę.
