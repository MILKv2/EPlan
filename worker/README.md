# worker/: Cloudflare Worker (etap 2, jeszcze nie zaimplementowany)

Planowane działanie:

1. Cron co 5 min pobiera zastępstwa na dziś i jutro (`mode: "classes"`).
2. Liczy hash zastępstw per klasa i per nauczyciel (pełne imię i nazwisko).
3. Porównuje z KV: jeden klucz na dzień, zapis **tylko przy zmianie**,
   `expirationTtl` 3 dni.
4. Przy zmianie wysyła push FCM na topic `klasa-<nazwa>` albo
   `naucz-<imie-nazwisko-bez-polskich-znakow>`.

Apka tylko subskrybuje topic i nic nie robi w tle. Klucz konta serwisowego FCM
trzymamy wyłącznie w `wrangler secret` (lokalnie w `.dev.vars`, które jest
w `.gitignore`).

Tu powstaną `src/index.js`, `src/edupage.js` (parser zastępstw w JS, zgodny
z `docs/edupage-api.md` i testowany na `../fixtures`), `wrangler.toml`
i `package.json`.
