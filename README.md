# QbitPlan

Nieoficjalna aplikacja na Androida z planem lekcji i zastępstwami dla
[elektryk.edupage.org](https://elektryk.edupage.org) (ZSP nr 5 w Krośnie).

> **Nieoficjalna.** Ta aplikacja nie jest powiązana ze szkołą ani z firmą
> EduPage (aSc Applied Software Consultants). Korzysta wyłącznie z danych,
> które szkoła publikuje bez logowania.

## Funkcje (plan)

- plan lekcji ucznia (klasa + grupy) albo nauczyciela, z nałożonymi
  zastępstwami, i pasek „teraz” z odliczaniem do dzwonka
- wszystkie zastępstwa dnia: nieobecni nauczyciele, niedostępne sale
- wyszukiwanie: plan nauczyciela, plan sali, wolne sale teraz albo o danej godzinie
- działanie offline na ostatnio pobranych danych
- powiadomienia push o zmianach w Twoim planie (zastępstwa, odwołania, zmiany sal)

Apka pobiera dane **bezpośrednio z EduPage**, bez własnego backendu.
Dystrybucja: plik APK w [Releases](../../releases), bez Sklepu Play.

## Struktura repo

```
android/            aplikacja (Kotlin, Jetpack Compose, Material 3)
worker/             Cloudflare Worker: co 5 min sprawdza zastępstwa, push FCM (zob. worker/README.md)
docs/               edupage-api.md: opis endpointów EduPage
fixtures/           zapisane odpowiedzi EduPage do testów (apka + worker)
.github/workflows/  android.yml: tag v* → APK → GitHub Release
                    worker.yml: zmiany w worker/** → wrangler deploy
```

## Budowanie

Wymagania: JDK 17+ i Android SDK z platformą `android-37.2` (Android Studio
albo `sdkmanager`). Szczegóły: [`android/README.md`](android/README.md).

```bash
cd android
./gradlew :core:test :app:testDebugUnitTest   # testy jednostkowe
./gradlew :app:assembleDebug                  # APK debug → app/build/outputs/apk/debug/
```

### Wydanie

Wypchnięcie tagu `v*` (np. `v1.0.0`) buduje podpisany APK i publikuje go jako
GitHub Release. Workflow potrzebuje sekretów repo: `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Klucz podpisu (`*.jks`)
i `keystore.properties` nigdy nie trafiają do repo.

## Licencja

[GPL-3.0](LICENSE). Autor: Jan ([milkthe.dev](https://milkthe.dev)).
