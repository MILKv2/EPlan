# android/

Aplikacja E-Plan (`dev.milkthe.qbitplan`): Kotlin, Jetpack Compose,
Material 3, Hilt, Room, DataStore, OkHttp, kotlinx.serialization, Jsoup.
minSdk 26, compileSdk 37.2, AGP 9 (Kotlin wbudowany), Gradle 9.

## Moduły

```
core/   czysty Kotlin, bez Androida; testy na zwykłej JVM
  model/      Timetable, Substitutions: modele domenowe
  edupage/    EdupageApi (OkHttp), parsery: wersje planu, plan stały,
              zastępstwa (HTML → Jsoup)
  schedule/   DayPlanner: nakładanie zastępstw (uczeń, nauczyciel, sala,
              wolne sale), dzwonki (pasek „teraz”), lista nazwisk
app/    aplikacja Android
  data/       SchoolRepository (offline-first), SyncManager, cache Room, ustawienia DataStore
  di/         moduły Hilt
  ui/theme/   tokeny designu (kolory jasny/ciemny, promienie, kolory statusów) – podmiana wyglądu tutaj
  ui/common/  wspólne komponenty (kafelek lekcji, pasek „teraz”, banery)
  ui/<ekran>/ onboarding, plan, substitutions, search, settings (ViewModel + Compose)
```

Szczegóły formatu danych: [`docs/edupage-api.md`](../docs/edupage-api.md).

## Komendy

Wymagania: JDK 17+ i Android SDK z platformą `android-37.2`
(Android Studio zainstaluje ją sam; ręcznie:
`sdkmanager "platforms;android-37.2"`).

```bash
./gradlew :core:test                 # parsery i logika (fixtures z ../fixtures)
./gradlew :app:testDebugUnitTest     # repozytorium + render wszystkich ekranów (Robolectric)
./gradlew :app:testDebugUnitTest -Proborazzi.record=true   # zrzuty ekranów → app/build/screenshots/
./gradlew :app:assembleDebug         # APK → app/build/outputs/apk/debug/

# test na prawdziwym EduPage (wykrywa zmianę formatu po stronie szkoły)
QBIT_LIVE=1 ./gradlew :core:test --tests '*LiveEdupageTest*' --rerun

# wyrocznia na żywo: DayPlanner vs plan bieżący EduPage, wszystkie klasy
QBIT_LIVE=1 ./gradlew :core:test --tests '*LiveOracleTest*' --rerun -i
```

## Podpis wydania

Lokalnie: plik `android/keystore.properties` (w `.gitignore`):

```properties
storeFile=/ścieżka/do/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

W CI te same wartości idą ze zmiennych `QBIT_KEYSTORE_*` (zob.
`.github/workflows/android.yml`). Bez nich `assembleRelease` buduje
niepodpisany APK.
