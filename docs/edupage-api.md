# EduPage: publiczne API szkoły elektryk.edupage.org

Ustalone metodą prób na żywym serwerze, 2026-11-23. To nieoficjalne,
nieudokumentowane API: może się zmienić bez ostrzeżenia. Zapisane odpowiedzi
leżą w [`fixtures/`](../fixtures).

## Wspólne zasady

- Baza: `https://elektryk.edupage.org`
- Każde wywołanie to `POST` z JSON-em
  `{"__args": [...], "__gsh": "00000000"}` i nagłówkiem
  `Content-Type: application/json; charset=UTF-8`.
- `__gsh` jest **wymagany**: bez niego serwer odpowiada `{"reload":true}`.
  Dla niezalogowanych to zawsze `"00000000"`, a strona osadza go jako
  `gsechash="00000000"` w HTML `/timetable/`.
- Odpowiedź ma postać `{"r": <wynik>}`, a błąd: `{"e": "Error: …", "em": …}`
  albo `{"r": {"error": "…"}}`.
- Nie trzeba logowania ani ciasteczek.

## 1. Lista wersji planu: `getTTViewerData`

```
POST /timetable/server/ttviewer.js?__func=getTTViewerData
{"__args":[null, 2026], "__gsh":"00000000"}
```

`__args[1]` to **rok szkolny** (2026 oznacza 2026/27). Bez niego dostajemy
błąd. Dla zeszłego roku `default_num` jest pusty.
Reguła: `rok = miesiąc >= 8 ? rok : rok - 1`; jeśli dla tego roku nie da się
wybrać wersji (patrz niżej), ponów z rokiem o 1 mniejszym.

```jsonc
{"r": {
  "current": {"allow": false},          // plan bieżący wyłączony w UI (patrz pkt 4)
  "regular": {
    "default_num": "189",               // wersja domyślna; szkoła przełącza ją ręcznie
    "timetables": [
      {"tt_num":"189","year":2026,"text":"listopad2026_RC3 (21.11. - 31.12.2026)",
       "datefrom":"2026-11-21","hidden":false}, …]   // historia wszystkich lat
  }
}}
```

Listę pobieramy przy **każdym** odświeżeniu. `datefrom` mówi, od kiedy
wersja obowiązuje (do następnej). Szkoła publikuje nową wersję kilka dni wcześniej
(`hidden: false`), a `default_num` przełącza osobno, np. o północy w dniu `datefrom`.

Apka (`VersionChoice.kt`) bierze najnowszą widoczną wersję danego roku z
`datefrom` ≤ dzień, który pokazuje plan (po ostatniej lekcji: następny dzień szkolny),
a `default_num` tylko gdy żadna nie pasuje. Ukryte wersje to szkice albo historia.
W Ustawieniach można wybrać wersję ręcznie; wybór przestaje obowiązywać, gdy pojawi
się nowsza wersja. Worker co pół godziny sprawdza tę listę i o nowej wersji
powiadamia raz, rano (topik `plan-lekcji`).

## 2. Plan stały: `regularttGetData`

```
POST /timetable/server/regulartt.js?__func=regularttGetData
{"__args":[null, "189"], "__gsh":"00000000"}
```

Ok. 680 KB, wszystkie klasy naraz. `r.dbiAccessorRes.tables` to lista tabel
`{id, def, cdefs, data_columns, data_rows}`; liczą się tylko `id` i
`data_rows`. Wszystkie ID są stringami (np. `"-210"`, `"*12"`).

| tabela | ważne pola | uwagi |
|---|---|---|
| `globals` | `reg_name`, `edupage_year`, `settings.m_strDateBellowTimeTable` | „Ważność: 21.11.2026-31.12.2026” |
| `periods` | `id`, `period`, `short`, `starttime`, `endtime` | **lekcje 0-10**, 0 = 07:05 |
| `breaks` | `starttime`, `endtime` | tylko info |
| `days` | `id` (0-4), `name`, `short` | Pn-Pt |
| `daysdefs` | `vals: ["10000"]` | maski dni |
| `weeks` | `id`, `name`, `short` | „Tydzień A”/„B”, **nieużywane** (karty mają `"11"`) |
| `weeksdefs` | `typ`, `vals` | maski tygodni |
| `terms`, `termsdefs` | | 1 semestr |
| `classrooms` | `id`, `name`, `short`, `buildingid` | 68 sal |
| `classes` | `id`, `name`, `short`, `teacherid` (wychowawca) | 38, w tym BP/LU/WG/NR |
| `subjects` | `id`, `name`, `short` | w zastępstwach używany jest `short` |
| `teachers` | `id`, `short` | **tylko skróty, bez nazwisk**; skróty **nie są unikalne** (np. 3× `KK`, 3× `MM`), unikalne jest `id` |
| `groups` | `id`, `name`, `classid`, `entireclass`, `ascttdivision` | grupy w obrębie klasy |
| `divisions` | `id` = `"<classid>:<division>"`, `groupids` | podział klasy (np. dz. 1 = 1./2. Grupa) |
| `lessons` | `id`, `subjectid`, `teacherids[]`, `groupids[]`, `classids[]`, `durationperiods`, `weeksdefid`, `daysdefid`, `termsdefid` | „co i kto”, ale bez „kiedy” |
| `cards` | `id`, `lessonid`, `period`, `days`, `weeks`, `classroomids[]` | „kiedy i gdzie” |
| `students`, `bells`, `ttreports`, … | | puste albo nieprzydatne |

`r.rights` pokazuje, co szkoła udostępnia publicznie:
`classes: true, teachers: false, classrooms: false`. Mimo to plan stały
zawiera dane wszystkich nauczycieli i sal, więc plan nauczyciela i sali
składamy sami.

### Składanie planu z kart

```
card ──lessonid──▶ lesson ──▶ subject, teachers[], groups[], classes[]
card.period   = numer lekcji startowej ("" = karta nierozmieszczona, pomiń)
card.days     = maska "00100" → indeks znaku '1' = dzień (0 = poniedziałek)
card.weeks    = maska "11" → w których tygodniach (A,B); "" = pomiń
lesson.durationperiods = ile kolejnych lekcji zajmuje (1-8), czyli
                         karta z period=4 i duration=2 zajmuje lekcje 4 i 5
```

Grupa: jeśli lekcja ma grupę z `entireclass: true`, dotyczy całej klasy.
W przeciwnym razie tylko tej grupy. Grupy z tym samym `ascttdivision` się
wykluczają (np. „1. Grupa” i „2. Grupa”).

## 3. Zastępstwa na dzień: `getSubstViewerDayDataHtml`

```
POST /substitution/server/viewer.js?__func=getSubstViewerDayDataHtml
{"__args":[null, {"date":"2026-11-24", "mode":"classes"}], "__gsh":"00000000"}
```

- `r` to **string z HTML-em**.
- Publiczny jest tylko `mode: "classes"`. Dla `"teachers"` serwer zwraca
  „Zastępstwa nie zostały jeszcze opublikowane.”, więc zastępstwa
  nauczyciela filtrujemy z widoku klas.
- Dostępne są daty od `date_limitfrom` (tu 2026-08-01). Przyszłe dni pojawiają
  się, gdy szkoła je opublikuje.

### Budowa HTML

```html
<div class="_RSS1520"><style>…</style><div data-date="2026-11-24">
  <!-- 0-3 nagłówki, każdy "Etykieta: a , b  (2), c  (0 - 4)" -->
  <div style="text-align:center"><span>Nieobecni nauczyciele: Mariusz Czekański , Justyna Drozd  (2), …</span></div>
  <div style="text-align:center"><span>Niedostępne sale szkolne: A24  (2)</span></div>
  <div style="text-align:center"><span>Klasy nieobecne: 1dt </span></div>
  <table>
    <tbody class="print-nobreak">               <!-- jeden tbody na klasę -->
      <tr><td rowspan="3" class="header">1at</td>   <!-- tylko w 1. wierszu -->
          <td class="period"><span>4</span></td>
          <td class="what"><span>2. Grupa: <s>PEiE</s> ➔ inf</span></td>
          <td class="info"><span>Zastępstwa: <s>Wojciech Książkiewicz</s> ➔ Anna Grzebień, Zmień salę lekcyjną: <s>A10</s> ➔ B23</span></td></tr>
      <tr><td class="period">…</td>…</tr>
    </tbody>
  </table>
</div></div>
```

Nawias przy nazwisku albo sali w nagłówku to lekcje: `(2)` albo `(0 - 4)`.
Brak nawiasu oznacza cały dzień.

### Komórka `period`

| wartość | znaczenie |
|---|---|
| `4` | lekcja 4 |
| `3 - 4` | lekcje 3-4 (podwójna) |
| `(4)`, `(1 - 2)` | w nawiasie: lekcja **odwołana** albo **przeniesiona stąd** |
| `Cały dzień` | cały dzień (np. nieobecność klasy) |
| pusta | tylko w wierszu „brak zastępstw” |

### Komórka `what`

`[grupy: ]przedmiot`, gdzie:
- grupy to opcjonalny prefiks przed `: `, kilka po przecinku
  (`1. Grupa, 2. Grupa: WF`). Nazwy pochodzą z `groups.name`
  (`Chłopcy`, `ang2`, `D-automatyk`, `Religia` …);
- przedmiot to `short` z planu albo zmiana `<s>stary</s> ➔ nowy`;
- `<img src=".../absent_32.svg">Nieobecność` oznacza nieobecność klasy.

### Komórka `info`

Części rozdzielone `, `, każda z etykietą. **Część etykiet nie jest
przetłumaczona** (angielskie „Moved …”).

| wzorzec | znaczenie |
|---|---|
| `Zastępstwa: <s>A</s> ➔ B` | nauczyciel A zastąpiony przez B |
| `<s>A</s>` (samo przekreślenie) | **brak zastępcy** (jeszcze nie przydzielono) |
| `Nauczyciel: A` | nauczyciel bez zmian (zwykle przy zmianie sali) |
| `Zmień salę lekcyjną: <s>X</s> ➔ Y` | zmiana sali |
| `A, Anulowano` | lekcja odwołana (A = nauczyciel); period w nawiasie |
| `A, Moved to period: 4` | lekcja przeniesiona **na** lekcję 4; period w nawiasie |
| `Moved from period: 0, Nauczyciel: A` | lekcja przeniesiona **z** lekcji 0 |
| `(Bez klasy 1dt)` | lekcja odbywa się bez klasy 1dt |
| `W tym dniu nie ma żadnych zastępstw.` | jedyny wiersz, puste header/period/what |
| pusta | przy `Nieobecność` |

Przeniesienie występuje w parze wierszy: `(0)` z „Moved to period: 4” oraz
`4` z „Moved from period: 0”.

Pełne imiona i nazwiska nauczycieli są **tylko tutaj**, nie ma ich w
planie stałym.

### Dopasowanie wiersza zastępstwa do planu stałego

Implementacja: `android/core/.../schedule/DayPlanner.kt`, zweryfikowana
z planem bieżącym (pkt 4) dla wszystkich klas.

1. Lekcje klasy w danym dniu rozbijamy na pojedyncze godziny, bo
   zastępstwo może dotyczyć części lekcji (PEiE 4-5 → inf na 4, jang na 5).
2. Wiersz dopasowujemy po: klasie (`short`), godzinie, **starym** skrócie
   przedmiotu i grupach.
3. **Grupy w wierszu to nazwy grup CAŁEJ lekcji łączonej**, także z innych
   klas. Np. dla 4fl „Dziew1, Dziew2: WF” to lekcja 4bl/Dziew1 + 4fl/Dziew2,
   a nie lekcja 4fl/Dziew1. Gdy pasuje kilka lekcji, wygrywa ta, której
   zbiór grup (ze wszystkich klas) jest dokładnie równy grupom z wiersza.
4. Niedopasowany wiersz staje się osobnym kafelkiem (nic nie ginie).
5. „Przeniesiona tutaj” bierze salę i grupy z lekcji źródłowej
   (`Moved from period: N`).

Nauczyciela z zastępstw (pełne nazwisko) łączymy ze skrótem z planu przez
parowanie ustawione przez użytkownika w onboardingu. Podpowiedzi biorą się z
samych zastępstw: wiersz, w którym ktoś jest nauczycielem planowym, leży na
lekcji z planu, a ta mówi, czyj to skrót (`teacherIdsByName`; liczą się tylko
skróty pasujące do inicjałów, bo starsze dni mogą dotyczyć innej wersji planu).

## 4. Plan bieżący: `curentttGetData` (UI wyłączone, endpoint działa)

`ttviewer` zwraca `current.allow: false` i strona go nie pokazuje, ale endpoint
**odpowiada dla klas** (dla `teachers`/`classrooms` zwraca „Insuficient
priviledges”):

```
POST /timetable/server/currenttt.js?__func=curentttGetData
{"__args":[null,{"year":2026,"datefrom":"2026-11-24","dateto":"2026-11-24",
  "table":"classes","id":"-210","showColors":true,"showIgroupsInClasses":false,
  "showOrig":true,"log_module":"CurrentTTView"}],"__gsh":"00000000"}
```

`r.ttitems[]`: `{type:"card", date, uniperiod, starttime, endtime, subjectid,
classids[], groupnames[], teacherids[], classroomids[], durationperiods?,
changed?: true, removed?: true}`. Zastępstwa są już nałożone, a nauczyciele
zastępujący mają ID (np. Anna Grzebień = `-161` = `AG`).

Uwagi:
- `groupnames` to nazwy grup całej lekcji łączonej (bez rozróżnienia klas);
- odwołane i przeniesione stąd: `removed: true`; zmienione i przeniesione
  tutaj: `changed: true`; zastąpiona część lekcji wielogodzinnej znika,
  a w jej miejscu pojawiają się jednogodzinne karty `changed`;
- `r.week_name` = „Tydzień A/B” (szkoła numeruje tygodnie, choć wszystkie
  lekcje są w obu).

Apka go **nie** używa (szkoła wyłączyła ten widok, więc może przestać
działać). Służy jako wyrocznia w testach: `CurrentTimetableOracleTest`
(fixtures) i `LiveOracleTest` (wszystkie klasy, na żywo).
