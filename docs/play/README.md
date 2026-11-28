# Google Play

Wszystko, czego Play Console wymaga przy pierwszym wydaniu. Wariant `play` nie ma
własnego sprawdzania aktualizacji (aktualizuje go Sklep Play), poza tym jest taki sam
jak APK z GitHub Releases: ten sam `applicationId` (`dev.milkthe.qbitplan`) i ten sam klucz.

## Kroki

1. **Konto dewelopera** (jednorazowo 25 USD). Nowe konta osobiste muszą przed
   produkcją przeprowadzić **test zamknięty: co najmniej 12 testerów przez 14 dni**.
2. **Utwórz aplikację**: nazwa z sekcji „Wpis w sklepie”, język domyślny polski,
   aplikacja, bezpłatna.
3. **Podpisywanie (Play App Signing)**: wybierz *użyj istniejącego klucza* i wgraj klucz
   wydania wyeksportowany narzędziem PEPK z Play Console (instrukcja jest na tym ekranie).
   Ten sam klucz co APK z GitHuba, więc użytkownicy mogą przejść na wersję ze sklepu
   bez odinstalowywania. Klucz i hasła nigdy nie trafiają do repo.
4. **Pakiet**: po merge'u podbicia wersji workflow `android.yml` zostawia podpisany
   `E-Plan-v<wersja>.aab` w artefaktach przebiegu (`play-bundle-v<wersja>`).
   Lokalnie: `./gradlew :app:bundlePlayRelease`.
5. **Test zamknięty**: utwórz ścieżkę, dodaj testerów (lista e-mail lub grupa Google),
   wgraj AAB, wyślij do sprawdzenia. Po 14 dniach: wniosek o dostęp do produkcji.
6. Uzupełnij sekcje z „Treść aplikacji” (niżej) i wpis w sklepie.

## Wpis w sklepie (pl-PL)

**Nazwa** (25/30): `E-Plan: plan i zastępstwa`

**Krótki opis** (74/80):
`Plan lekcji z zastępstwami z EduPage szkoły. Powiadomienia i tryb offline.`

**Pełny opis:**

```
Twój plan lekcji z naniesionymi zastępstwami, od razu po otwarciu, także bez internetu.

• Twój plan, już z zastępstwami. Wybierasz klasę i swoje grupy (albo, jeśli uczysz, swoje nazwisko) i widzisz dokładnie swoje lekcje. Odwołania, zmiany sal i nauczycieli są od razu zaznaczone.
• Co teraz i ile do dzwonka. Pasek na górze pokazuje bieżącą lekcję albo przerwę i odlicza czas.
• Powiadomienia o zmianach. Gdy w Twoim planie pojawi się zastępstwo, odwołanie albo zmiana sali, telefon da znać. Tylko o tym, co dotyczy Ciebie. W weekend może milczeć.
• Nowy plan lekcji bez niespodzianek. Gdy szkoła opublikuje nowy plan, aplikacja pokaże go od dnia, w którym zaczyna obowiązywać.
• Wszystkie zastępstwa dnia: nieobecni nauczyciele, zajęte sale, dowolny dzień z kalendarza.
• Szukaj: plan dowolnego nauczyciela, sali albo klasy oraz wolne sale teraz albo na wybranej lekcji.
• Działa offline, jasny i ciemny motyw, szybka także na tańszych telefonach.

Bez konta, bez logowania, bez reklam i bez analityki. Kod źródłowy jest otwarty (GPL-3.0).

Nieoficjalna aplikacja. Nie jest powiązana ze szkołą ani z firmą EduPage. Pokazuje tylko to, co szkoła publikuje publicznie na elektryk.edupage.org.
```

**Kategoria:** Edukacja. **Tagi:** plan lekcji, szkoła.
**Kontakt:** adres e-mail dewelopera (wymagany) i strona: `https://github.com/MILKv2/EPlan`.
**Polityka prywatności:** `https://github.com/MILKv2/EPlan/blob/main/docs/privacy.md`

### Grafiki

Generuje je `StoreGraphicsTest` (CI: artefakt `screenshots`, lokalnie
`./gradlew :app:testGithubDebugUnitTest -Proborazzi.record=true` → `app/build/screenshots/`):

| plik | wymaganie Play |
|---|---|
| `store_icon.png` | ikona 512 × 512 |
| `store_feature.png` | grafika promocyjna 1024 × 500 |
| `store_plan.png`, `store_substitutions.png`, `store_week.png`, `store_free_rooms.png`, `store_dark.png`, `store_teacher.png` | zrzuty telefonu 1080 × 1920 (2–8 sztuk, proporcje do 2:1) |

Zrzuty z `docs/screenshots/` (360 × 760 dp) są za wysokie dla Play (ponad 2:1).

## Treść aplikacji (odpowiedzi do formularzy)

- **Dostęp do aplikacji**: wszystkie funkcje dostępne bez logowania.
- **Reklamy**: nie zawiera reklam.
- **Ocena treści (IARC)**: kategoria „Narzędzia / edukacja”; brak przemocy, treści
  seksualnych, wulgaryzmów, hazardu; brak interakcji między użytkownikami, udostępniania
  lokalizacji i zakupów. Spodziewana ocena: PEGI 3.
- **Grupa docelowa**: 13–15, 16–17, 18+ (uczniowie i nauczyciele szkoły średniej);
  nie jest skierowana do dzieci poniżej 13 lat.
- **Aplikacja z wiadomościami / rządowa / zdrowotna / finansowa**: nie.
- **Bezpieczeństwo danych**:
  - Czy aplikacja zbiera lub udostępnia dane? **Tak** (tylko przy włączonych powiadomieniach).
  - Szyfrowanie przesyłanych danych: **tak** (HTTPS).
  - Usuwanie danych: brak kont; dane znikają po odinstalowaniu lub wyczyszczeniu danych
    aplikacji, tematy powiadomień po ich wyłączeniu.
  - **Identyfikatory urządzenia lub inne**: zbierane, nieudostępniane, opcjonalne,
    cel: działanie aplikacji (identyfikator Firebase Cloud Messaging).
  - **Dane osobowe → imię i nazwisko**: zbierane, nieudostępniane, opcjonalne,
    cel: działanie aplikacji (nauczyciel z włączonymi powiadomieniami zapisuje się na
    temat ze swoim imieniem i nazwiskiem).
  - Nic więcej: brak lokalizacji, kontaktów, zdjęć, aktywności w aplikacji, raportów o awariach.
- **Podszywanie się / własność intelektualna**: opis i aplikacja mówią wprost, że to
  nieoficjalny klient; nie używa logo szkoły ani EduPage.
