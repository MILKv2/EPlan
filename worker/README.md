# worker/: Cloudflare Worker z powiadomieniami

Co 5 minut sprawdza zastępstwa na **dziś i dwa kolejne dni szkolne** i budzi
telefony, których plan się zmienił. Bez własnego serwera i bez bazy danych użytkowników.

## Jak to działa

1. Cron (`*/5 * * * *`) pobiera zastępstwa z EduPage (`mode: "classes"`).
2. Dla każdej klasy i każdego nauczyciela z wierszy liczy skrót (hash) jego wierszy.
3. Porównuje z poprzednim przebiegiem zapisanym w KV (`subst:<data>`, wygasa po 3 dniach,
   zapis tylko przy zmianie). Pierwszy raz widziany dzień tylko zapisuje, bez powiadomień.
4. Dla zmienionych klas i nauczycieli wysyła push FCM na topiki `klasa-<nazwa>` /
   `naucz-<imie-nazwisko>` (do 5 topików w jednej wiadomości). Push zawiera **tylko datę**.
5. Telefon pobiera ten dzień sam, porównuje swój plan przed i po (z grupami ucznia) i pokazuje
   powiadomienie z tym, co zmieniło się u niego. Jeśli nic, nie pokazuje niczego.

Nazwy topików liczą tak samo worker (`src/topics.js`) i apka (`PushTopics.kt`); obie strony
sprawdzają to na `../fixtures/topics.json`.

## Pliki

```
src/index.js     cron: dni, porównanie z KV, wysyłka
src/edupage.js   pobranie i parser zastępstw (port części SubstitutionsParser.kt)
src/changes.js   skróty per topik i lista zmienionych topików
src/topics.js    nazwy topików FCM
src/fcm.js       FCM HTTP v1: JWT konta serwisowego → token → wiadomość
src/calendar.js  daty po polsku (Europe/Warsaw), dni szkolne
test/            testy na ../fixtures (node --test)
```

## Komendy

```bash
npm ci
npm test                   # testy (Node 20+)
npx wrangler deploy --dry-run
```

## Konfiguracja (jednorazowo, w panelu Cloudflare)

- **KV**: Workers & Pages → KV → `qbitplan`; jego ID jest w `wrangler.toml` (`STATE`).
- **Wdrażanie**: Workers & Pages → Create → Import a repository → to repo,
  katalog główny `worker`. Każdy merge do `main` wdraża nową wersję.
- **Sekret** `FCM_SERVICE_ACCOUNT`: cała zawartość klucza konta serwisowego Firebase
  (Ustawienia projektu → Konta usługi → Wygeneruj nowy klucz prywatny).
  Worker → Settings → Variables and Secrets → Add → typ *Secret*. Klucz nie trafia do repo.

Bez sekretu worker nic nie zapisuje i tylko loguje błąd, więc żadna zmiana nie przepada.
Darmowy plan wystarcza: ~864 odczyty KV na dobę, zapisy tylko przy zmianach, do 40 wiadomości
na przebieg (limit 50 zapytań na wywołanie; nadmiar idzie w kolejnym przebiegu).
