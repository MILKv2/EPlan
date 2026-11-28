# Polityka prywatności E-Plan

Obowiązuje od 28 listopada 2026 r.

E-Plan to nieoficjalna aplikacja z planem lekcji i zastępstwami z publicznego
EduPage szkoły (`elektryk.edupage.org`). Nie ma kont, reklam, analityki ani
zbierania raportów o błędach. Kod jest otwarty: [github.com/MILKv2/EPlan](https://github.com/MILKv2/EPlan).

## Co zostaje na telefonie

- Wybrany profil: klasa i grupy albo nauczyciel (skrót i imię i nazwisko z planu).
- Ustawienia: motyw, powiadomienia, cisza w weekend, ręcznie wybrana wersja planu.
- Pobrane plany i zastępstwa (żeby aplikacja działała bez internetu).

Te dane nie opuszczają telefonu. Znikają po odinstalowaniu aplikacji
albo wyczyszczeniu jej danych w ustawieniach Androida.

## Z czym aplikacja się łączy

- **EduPage (`elektryk.edupage.org`)**: pobiera publiczny plan lekcji i zastępstwa,
  tak jak przeglądarka na stronie szkoły. Zapytania nie zawierają danych o Tobie;
  serwer EduPage widzi adres IP, jak przy każdej wizycie na stronie.
- **Firebase Cloud Messaging (Google)**, tylko gdy włączysz powiadomienia: telefon
  dostaje od Google identyfikator do powiadomień i zapisuje się na tematy: swojej klasy
  albo nauczyciela (np. `klasa-1at`) oraz nowego planu lekcji (`plan-lekcji`).
  Wyłączenie powiadomień wypisuje telefon z tematów. Google przetwarza te dane według
  [zasad Firebase](https://firebase.google.com/support/privacy).
- **Serwer powiadomień (Cloudflare Workers)**: co kilka minut sprawdza publiczne
  zastępstwa i wysyła powiadomienie na temat klasy lub nauczyciela. Nie zna telefonów
  ani identyfikatorów; zapisuje tylko skróty (hashe) zastępstw i numer ostatniej
  wersji planu.
- **GitHub (`api.github.com`)**, tylko w wersji z GitHub Releases: najwyżej co 12 godzin
  sprawdza, czy jest nowsza wersja aplikacji. Wersja ze Sklepu Play tego nie robi.

Aplikacja nie sprzedaje ani nie udostępnia danych nikomu innemu.

## Dzieci

Aplikacja jest przeznaczona dla uczniów i nauczycieli szkoły średniej. Nie zbiera
danych osobowych poza tym, co opisano wyżej.

## Kontakt i zmiany

Pytania: [zgłoszenia na GitHubie](https://github.com/MILKv2/EPlan/issues).
Zmiany tej polityki są widoczne w historii tego pliku w repozytorium.
