# Polityka prywatności aplikacji E-Plan

Wersja z 28 listopada 2026 r. Dotyczy aplikacji E-Plan na Androida (z Google Play
i z GitHub Releases) oraz jej serwera powiadomień.

## 1. Administrator danych

Administratorem danych osobowych opisanych w tej polityce jest twórca aplikacji,
osoba prywatna działająca pod nazwą **MILK** ([milkthe.dev](https://milkthe.dev)).

Kontakt we wszystkich sprawach dotyczących danych osobowych: **milk@milkthe.dev**.
Inspektor ochrony danych nie został wyznaczony (nie jest wymagany).

E-Plan to nieoficjalna, bezpłatna aplikacja open source
([kod źródłowy](https://github.com/MILKv2/EPlan)). Nie jest powiązana ze szkołą ani
z firmą EduPage. Pokazuje plan lekcji i zastępstwa, które szkoła publikuje publicznie
na `elektryk.edupage.org`.

## 2. W skrócie

- Bez konta i logowania, bez reklam, analityki i raportów o awariach.
- Profil i ustawienia zostają na telefonie; administrator nie ma do nich dostępu.
- Poza telefon trafia tylko to, co potrzebne do powiadomień, i to dopiero po ich
  włączeniu (punkt 4).
- Dane nie są sprzedawane ani udostępniane w celach marketingowych.

## 3. Dane zapisane tylko na telefonie

| Dane | Po co |
|---|---|
| Profil: klasa i grupy albo nauczyciel (skrót oraz imię i nazwisko z planu) | żeby pokazać Twój plan |
| Ustawienia: motyw, powiadomienia, cisza w weekend, ręcznie wybrana wersja planu | działanie aplikacji |
| Pobrane plany i zastępstwa | działanie bez internetu |

Te dane są w prywatnej pamięci aplikacji i nie są wysyłane do administratora.
Usuniesz je, zmieniając profil, czyszcząc dane aplikacji w ustawieniach Androida
albo odinstalowując aplikację.

## 4. Powiadomienia (Firebase Cloud Messaging)

**Kiedy:** tylko gdy włączysz powiadomienia w aplikacji. Możesz je wyłączyć w każdej
chwili w Ustawieniach aplikacji albo Androida.

**Jakie dane:**
- identyfikator aplikacji do powiadomień nadawany przez Google (token FCM
  i identyfikator instalacji Firebase),
- tematy, na które zapisuje się telefon: klasy ustawionej w profilu (np. `klasa-1at`)
  albo nauczyciela ustawionego w profilu (temat zawiera jego imię i nazwisko,
  np. `naucz-jan-kowalski`),
  oraz temat nowego planu lekcji (`plan-lekcji`),
- dane techniczne, których Google potrzebuje do doręczenia wiadomości (np. adres IP).

**Cel:** powiadomienia o zmianach w Twoim planie i o nowym planie lekcji.

**Podstawa prawna:** art. 6 ust. 1 lit. b RODO: wykonanie usługi, o którą prosisz,
włączając powiadomienia.

**Odbiorca:** Google (Firebase) jako podmiot przetwarzający na zlecenie administratora,
na podstawie [warunków przetwarzania danych Firebase](https://firebase.google.com/terms/data-processing-terms).
Dane mogą być przekazywane do USA na podstawie programu EU-US Data Privacy Framework
i standardowych klauzul umownych.

**Jak długo:** dopóki powiadomienia są włączone. Po wyłączeniu telefon wypisuje się
z tematów. Token przestaje działać po odinstalowaniu aplikacji; nieaktywne tokeny
Google usuwa samo (obecnie po około 270 dniach).

Administrator nie widzi tokenów ani listy telefonów zapisanych na tematy. Serwer
powiadomień wysyła wiadomość na temat (np. „klasa 1at”), a nie do konkretnej osoby.
Wiadomość zawiera tylko datę dnia ze zmianami albo numer i datę nowego planu.

## 5. Serwer powiadomień (Cloudflare Workers)

Co kilka minut serwer pobiera z `elektryk.edupage.org` publiczne zastępstwa oraz listę
wersji planu i sprawdza, dla których klas i nauczycieli coś się zmieniło.

**Jakie dane:** dla każdej klasy i każdego nauczyciela z listy zastępstw serwer zapisuje
skrót (hash) jego zastępstw na dany dzień, pod nazwą tematu. Nazwa tematu nauczyciela
zawiera jego imię i nazwisko. Zapisuje też numer i datę najnowszej wersji planu.
O użytkownikach aplikacji serwer nie ma żadnych danych.

**Źródło:** publiczna strona szkoły `elektryk.edupage.org` (dotyczy nauczycieli,
których dane pochodzą z planu i zastępstw opublikowanych przez szkołę).

**Cel i podstawa prawna:** wykrywanie zmian w opublikowanym planie i powiadamianie
użytkowników; art. 6 ust. 1 lit. f RODO (prawnie uzasadniony interes: informowanie
użytkowników o zmianach w publicznie dostępnym planie lekcji).

**Odbiorca:** Cloudflare jako podmiot przetwarzający; dane mogą być przekazywane do USA
na podstawie programu EU-US Data Privacy Framework i standardowych klauzul umownych.

**Jak długo:** zapisy dotyczące danego dnia są usuwane automatycznie dzień po tym dniu;
numer wersji planu jest nadpisywany przy kolejnej wersji.

## 6. Inne połączenia

- **EduPage:** aplikacja pobiera publiczny plan i zastępstwa bezpośrednio z
  `elektryk.edupage.org`, jak przeglądarka. Zapytania nie zawierają danych o Tobie.
  Operator EduPage widzi adres IP i dane techniczne zapytania jako osobny administrator,
  według własnych zasad.
- **GitHub (tylko wersja z GitHub Releases):** najwyżej co 12 godzin aplikacja sprawdza
  w `api.github.com`, czy jest nowsza wersja. GitHub widzi adres IP jako osobny
  administrator. Wersja z Google Play tego nie robi; aktualizuje ją Sklep Play.
- **Linki** (kod źródłowy, licencja, ta polityka) otwierają się w przeglądarce, na
  zasadach tych stron.

## 7. Twoje prawa

Masz prawo do dostępu do swoich danych, ich sprostowania, usunięcia, ograniczenia
przetwarzania i przenoszenia, a gdy podstawą jest prawnie uzasadniony interes
(punkt 5), także prawo sprzeciwu. Napisz na **milk@milkthe.dev**; odpowiedź przyjdzie
w ciągu miesiąca.

Aplikacja nie ma kont, więc administrator zwykle nie jest w stanie powiązać danych
z konkretną osobą (art. 11 RODO). Najprościej usuniesz dane z punktu 4, wyłączając
powiadomienia albo odinstalowując aplikację.

Masz też prawo złożyć skargę do Prezesa Urzędu Ochrony Danych Osobowych
(ul. Stawki 2, 00-193 Warszawa, [uodo.gov.pl](https://uodo.gov.pl)).

## 8. Pozostałe informacje

- **Dobrowolność:** podanie danych jest dobrowolne. Bez włączonych powiadomień
  aplikacja działa tak samo, tylko nie powiadamia o zmianach.
- **Profilowanie:** brak; aplikacja nie podejmuje zautomatyzowanych decyzji wobec Ciebie.
- **Bezpieczeństwo:** wszystkie połączenia są szyfrowane (HTTPS); dane na telefonie
  są w prywatnej pamięci aplikacji, niedostępnej dla innych aplikacji.
- **Dzieci:** aplikacja jest przeznaczona dla uczniów i nauczycieli szkoły średniej,
  nie dla dzieci poniżej 13 lat.

## 9. Zmiany polityki

Nowa wersja polityki pojawi się w tym miejscu z nową datą; istotne zmiany zostaną
opisane w informacjach o wydaniu aplikacji. Poprzednie wersje są w historii tego pliku
w [repozytorium](https://github.com/MILKv2/EPlan/commits/main/docs/privacy.md).
