# Слайд 13 (мастер канала): маппинг таблицы METRIC

Дата: 2026-08-21.

Источник в книге — вкладка **General**, блок под «Main slide N»: ячейка `METRIC` + 5 колонок.
28 блоков, сетка 7 тактик в ряд (колонки A, H, O, V, AC, AJ, AQ), полосы строк 64/94/124/154.
Приёмник — таблица на `slide13.xml` шаблона EOM (`{{tactic n …}}`, мастер копируется в `tct_n`).

## 1. Матрица токенов

Имена в книге и в деке совпадают **один в один** — маппинг тождественный, различается только
`n` → номер тактики.

| Строка | MONTH {{mon no}} GOAL | MONTH {{mon no}} ACTUAL | VS GOAL | EOC GOAL | EOC PROJ. |
|---|---|---|---|---|---|
| Impressions | `planned imps` ✅ | `fact imps` ✅ | `imps pacing` 🆕 | `eoc planned imps` 🆕 | `proj imps` 🆕 |
| CTR | `ctr plan` ✅ | `ctr` ✅ | `ctr pacing` 🆕 | `ctr plan` ✅ (дубль) | `ctr proj` ✅ |
| Clicks | `clicks plan` ✅ | `clicks` ✅ | `clicks pacing` 🆕 | `clicks mp` 🆕 | `clicks proj` 🆕 |
| Reach | `reach plan` 🆕 | `reach` ✅ | `reach pacing` 🆕 | `reach plan eoc` 🆕 | `reach proj` ✅ |
| CPM | `planned cpm` 🆕 | `fact cpm` 🆕 | `cpm pacing` 🆕 | `planned cpm` 🆕 (дубль) | `cpm proj` ✅ |
| Spend | `planned budget` ✅ | `fact budget` ✅ | `budget pacing` 🆕 | `spend plan` ✅ | `spend proj` 🆕 |

Все токены — с префиксом `{{tactic n …}}`. ✅ = уже пишется на шаге генерации книги,
🆕 = на write-side пока нет (см. §3).

Два дубля (CTR и CPM в колонках MONTH GOAL и EOC GOAL) — так размечен сам шаблон: для ставки
месячная цель = флайтовая. Обе ячейки заполняются одним токеном, поэтому всегда равны.

## 2. Read-back (сделано)

`SheetPlaceholderReaderImpl.readMetricTables` — новый проход после `readMainSlideBlocks`:

- якорь блока — ячейка `METRIC`;
- номер тактики берётся не по позиции, а от ближайшего сверху в той же колонке
  `Main slide N` (окно 30 строк) — та же схема нумерации, что у детальных блоков;
  блок без такого якоря игнорируется;
- колонки определяются по тексту заголовка (`proj` → EOC PROJ, `eoc` → EOC GOAL,
  `actual` → MONTH ACTUAL, `vs goal` → VS GOAL, иначе `goal` → MONTH GOAL), а не по смещению —
  в двух заголовках стоит номер месяца, плюс пользователь может вставить колонку;
- строки читаются по подписям Impressions / CTR / Clicks / Reach / CPM / Spend, окно 10 строк,
  стоп на следующем `METRIC` / `Main slide N`.

**Приоритет.** Шесть токенов (`ctr`, `ctr plan`, `clicks`, `clicks plan`, `reach`, `spend plan`)
есть и в сводной таблице (строки 15–44), и в таблице METRIC. Читается «первый выигрывает»:
побеждает сводная таблица. Причина — обе ячейки заполняются одним резолвером и совпадают, а
незаполненный токен в новой таблице (см. §3) иначе затирал бы дашем живое значение сводной.
Если нужно наоборот (правка в таблице слайда важнее правки в сводной) — это одна строка:
`emitIfAbsent` → `emit`.

## 3. Что осталось на write-side

Шаг генерации книги эти 🆕 ячейки пока не заполняет — часть решается переименованием
существующих токенов, часть требует нового расчёта:

| Токен слайда | Откуда брать |
|---|---|
| `imps pacing` | = существующий `{{tactic n imps vs goal}}` |
| `proj imps` | = существующий `{{tactic n imps proj}}` (другой порядок слов) |
| `ctr pacing` | = `{{tactic n ctr vs goal}}` |
| `reach plan` | = `{{tactic n reach plan ctd}}` |
| `reach pacing` | = `{{tactic n reach vs goal}}` |
| `planned cpm` | = `{{tactic n cpm plan ctd}}` |
| `fact cpm` | = `{{tactic n cpm}}` |
| `cpm pacing` | = `{{tactic n cpm vs goal}}` |
| `budget pacing` | = `{{tactic n pacing}}` (спенд-пейсинг дашборда) |
| `eoc planned imps` | новый: план показов на весь флайт (сегодня `imps plan` в EOM = план месяца) |
| `clicks pacing` | новый: клики факт vs план месяца |
| `clicks mp` | новый: клики-цель на весь флайт из медиаплана |
| `clicks proj` | новый: проекция кликов на конец флайта |
| `reach plan eoc` | новый: reach-цель на весь флайт |
| `spend proj` | новый: проекция спенда на конец флайта |

Отдельно: плитки слайда 13 `{{tactic n planned imps short}}`, `{{tactic n fact imps short}}`,
`{{tactic n fact budget short}}` ячеек в книге не имеют — это компактный формат тех же чисел,
считается на дек-сайде. Нарратив слайда (`{{what worked pacing n}}`, `{{watch outs pacing n}}`,
`{{actions pacing n}}`, `{{pacing n next month}}`) в эту таблицу не входит.

## Правка шаблона (2026-08-21)

В строке Spend слайда 13 колонки VS GOAL / EOC GOAL были размечены как
`{{tactic n spend plan eoc}}` / `{{tactic n spend plan}}` — то есть pacing потерялся, а
EOC GOAL дублировал MONTH GOAL. Исправлено в шаблоне на
`{{tactic n budget pacing}}` / `{{tactic n spend plan eoc}}` — как в книге и в коде.

Плитки `{{tactic n planned imps short}}` / `{{tactic n fact imps short}}` /
`{{tactic n fact budget short}}` теперь заполняет `EomDashboardResolver.fillTactic`,
сжимая те же строки, что печатает таблица (`Fmt.compactUpper` / `Fmt.moneyCompact`).

## Тримминг неиспользованных блоков (2026-08-21)

`RealSheetDeckProvider.metricBlockClearRequests` чистит METRIC-блоки тактик, которых в
кампании нет — тем же проходом `trimTactics`, что уже чистит блоки «Main slide N», и с той
же геометрией (6 строк вниз, 5 колонок вправо от угловой ячейки `METRIC`, плюс строка
`{{tactic N}}` над ней).

Блок опознаётся по угловой ячейке `METRIC`, а владелец — по токенам внутри: сама метка
номера не несёт (во всех 28 блоках написано просто «METRIC»). Отсюда важное следствие:
блок, в котором токенов уже нет (заполнен цифрами), не чистится никогда — он принадлежит
реальной тактике. Проход идёт после `createSheet`, поэтому именно так и получается:
заполненные блоки без токенов, неиспользованные — с сырыми `{{tactic 17 …}}`.

