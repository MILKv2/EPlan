# Fixtures

Surowe odpowiedzi z elektryk.edupage.org (bajt w bajt, bez formatowania),
pobrane 2026-11-23. Korzystają z nich testy apki (`android/`) i workera
(`worker/`). Opis formatu: [`docs/edupage-api.md`](../docs/edupage-api.md).

| plik | endpoint | co testuje |
|---|---|---|
| `ttviewer_2026.json` | `getTTViewerData` (rok 2026) | lista wersji, `default_num = 189` |
| `regulartt_189.json` | `regularttGetData` (tt 189, od 2026-11-21) | plan stały wszystkich klas |
| `substitutions/2026-11-23.json` | `getSubstViewerDayDataHtml` | zwykły dzień |
| `substitutions/2026-11-24.json` | 〃 | przeniesienia (`Moved to/from period`), odwołania, grupy |
| `substitutions/2026-11-25.json` | 〃 | brak zastępcy (samo `<s>Nauczyciel</s>`) |
| `substitutions/2026-11-20.json` | 〃 | „(Bez klasy 2bdt)”, kilka grup naraz |
| `substitutions/2026-11-21.json` | 〃 | sobota: „W tym dniu nie ma żadnych zastępstw.” |
| `substitutions/2026-10-15.json` | 〃 | „Klasy nieobecne”, `Cały dzień`, `Nieobecność` (starsza wersja planu) |
| `substitutions/2026-09-10.json` | 〃 | zakresy lekcji `3 - 4`, `(0 - 4)` w nagłówku (starsza wersja planu) |
| `currenttt/<klasa>_2026-11-23_27.json` | `curentttGetData` (plan bieżący, tydzień; 1at 1ct 1dt 1fl 2at 2ct 3abt 4fl) | **wyrocznia** do testów nakładania zastępstw (etap 3); apka z tego endpointu nie korzysta |

Zastępstwa z 2026-11-23…25 pasują do planu 189, więc nadają się do testów
nakładania. Aby odświeżyć fixtures, użyj tych samych zapytań co w `docs/`.
Nie formatuj plików: parser ma dostać dokładnie to, co wysyła serwer.
