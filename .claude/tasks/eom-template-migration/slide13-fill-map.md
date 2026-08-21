# Слайд 13: карта заполнения таблицы METRIC в Google Sheet

Дата: 2026-08-21. Спека — от пользователя, сверена с кодом; расхождения помечены ⚠️.

Читает эту таблицу обратно `SheetPlaceholderReaderImpl.readMetricTables`
(см. `slide13-metric-table-mapping.md`). Здесь — вторая половина: чем шаг генерации книги
заполняет каждую ячейку.

## Что уже подтверждено кодом

- **В EOM плановые колонки сводной таблицы — это план МЕСЯЦА.**
  `CampaignDataCollector.resolveEomPlanByTacticNum` строит план из `monthlyBudget` × ставку с
  экрана матчинга (`RatePlanCalculator.planTargets`), а не из флайтовых Estimates. Значит
  `{{tactic n imps plan}}`, `{{tactic n spend plan}}`, `{{tactic n clicks plan}}`,
  `{{tactic n ctr plan}}` = месячные цели → колонка MONTH GOAL заполняется ими корректно.
- **Флайтовые цели (колонка EOC GOAL) уже собираются** — вкладка Estimates медиаплана даёт
  на тактику `{spend(Total Cost), imps(Impressions), ctr, vcr, maxFreq, —, —, freq/week, reach(Reach)}`
  (`estimatesPlanByTacticNum`). Кроме **Clicks** — эта колонка сегодня не читается (см. «Осталось закрыть», п. 2).
- **`fact cpm` и `planned cpm` уже посчитаны**: `resolveTacticCpm` = spend / imps × 1000,
  `resolveTacticCpmPlanCtd` = плановый spend / плановые imps × 1000. Для CPM-закупки второе
  численно равно Final Unit Price (план показов из неё же и выведен), а для CPC/CPV даёт
  осмысленный эффективный CPM, где Final Unit Price вообще не CPM. Поэтому читать МП руками
  не нужно — берём резолверы.

## Матрица заполнения

### Impressions

| Ячейка | Формула | Источник |
|---|---|---|
| `planned imps` | = `{{tactic n imps plan}}` | есть (план месяца) |
| `fact imps` | = `{{tactic n imps}}` | есть |
| `imps pacing` | `fact imps / planned imps × 100`, формат `101%` | 🆕 (= `imps vs goal` по смыслу) |
| `eoc planned imps` | Estimates → колонка **Impressions** тактики (весь флайт) | 🆕, данные уже собраны |
| `proj imps` | `eoc planned imps × imps pacing`, до целых; **если pacing < 100% → ровно `eoc planned imps`** | 🆕 |

### CTR

| Ячейка | Формула | Источник |
|---|---|---|
| `ctr plan` (MONTH GOAL) | = `{{tactic n ctr plan}}` | есть |
| `ctr` | = `{{tactic n ctr}}` | есть |
| `ctr pacing` | `ctr − ctr plan`, формат `+0.05pp` | 🆕 (= `ctr vs goal`) |
| `ctr plan` (EOC GOAL) | тот же токен — для ставки месячная цель = флайтовой | есть |
| `ctr proj` | среднее(`ctr plan`, `ctr`) если `ctr` > плана; иначе ровно `ctr plan` | есть `ctr proj`, но формула другая → переопределить |

### Clicks

| Ячейка | Формула | Источник |
|---|---|---|
| `clicks plan` | = `{{tactic n clicks plan}}` | есть (план месяца) |
| `clicks` | = `{{tactic n clicks}}` | есть |
| `clicks pacing` | `clicks / clicks plan × 100`, формат `101%` | 🆕 |
| `clicks mp` | Estimates → колонка **Clicks** тактики | 🆕 |
| `clicks proj` | `clicks mp × clicks pacing`, до целых | 🆕 |

### Reach

| Ячейка | Формула | Источник |
|---|---|---|
| `reach plan` | `fact imps ÷ (freq/week из МП × недель в месяце)` — охват, который дают доставленные показы при плановой частоте | 🆕 |
| `reach` | = `{{tactic n reach}}` | есть |
| `reach pacing` | `reach / reach plan × 100`, формат `101%` | 🆕 |
| `reach plan eoc` | Estimates → колонка **Reach** тактики | 🆕, данные уже собраны |
| `reach proj` | Estimates → колонка **Reach** тактики (то же значение, что в EOC GOAL) | 🆕 |

Здесь месячная «цель» считается от факта показов, а не от плана: строка меряет не объём, а
частоту — при плановой частоте доставленный объём должен был дать столько охвата. Поэтому
колонка MONTH GOAL двигается вместе с доставкой, а `reach pacing` по сути = плановая частота
против фактической. Это осознанное отличие от строк Impressions/Clicks/Spend.

### CPM

| Ячейка | Формула | Источник |
|---|---|---|
| `planned cpm` | плановый spend ÷ плановые imps × 1000 (= Final Unit Price при Rate Type = CPM) | есть `cpm plan ctd` |
| `fact cpm` | `spend / imps × 1000`, в долларах | есть `cpm` |
| `cpm pacing` | `planned cpm − fact cpm`, формат `+ $0.30`; знак: «+» = дешевле плана (см. «Осталось закрыть», п. 4) | 🆕 |
| `planned cpm` (EOC GOAL) | тот же токен | — |
| `cpm proj` | среднее(`planned cpm`, `fact cpm`), формат `$11.85` (абсолютное значение, не дельта) | есть `cpm proj`, формула другая |

### Spend (везде доллары с центами)

| Ячейка | Формула | Источник |
|---|---|---|
| `planned budget` | = `{{tactic n spend plan}}` (план месяца) | есть |
| `fact budget` | = `{{tactic n spend}}` | есть |
| `budget pacing` | `fact budget / planned budget × 100`, формат `101%` | есть `pacing` / `spend pace` |
| `spend plan eoc` | Estimates → **Total Cost** тактики (весь флайт) | 🆕 |
| `spend proj` | всегда ровно `spend plan eoc` | 🆕 |

## Решённые расхождения (спека обновлена 2026-08-21)

1. **Clicks pacing.** Подтверждено: внутри блока Clicks имелся в виду `clicks pacing`, не
   `imps pacing` — и в самой строке, и в проекции `clicks proj = clicks mp × clicks pacing`.
2. **Reach.** Формулы переписаны (см. блок Reach): месячная цель — от факта показов и плановой
   частоты, флайтовая цель и проекция — из колонки **Reach** медиаплана.
3. **Spend EOC GOAL.** Ячейка в книге переименована в `{{tactic n spend plan eoc}}`, конфликт
   с месячным `{{tactic n spend plan}}` из сводной таблицы снят. Read-back уже читает новое имя.
4. **Формат денег** в строке CPM: `+ $0.30`.
5. **`{{tactic n spend plan eoc}}` в шаблоне деки** — переименовано.
6. **`clicks mp` читается из данных, а не выводится из CTR** — источник: колонка **Clicks**
   вкладки Estimates медиаплана (вкладки Proposal у парсера сегодня нет).
7. **Проекции не опускаются ниже плана** — подтверждено как задуманное поведение:
   `proj imps` при pacing < 100% печатает план, `ctr proj` при факте ниже плана печатает план,
   `spend proj` всегда равен плану.

## ⚠️ Осталось закрыть

1. **Знак `cpm pacing`** = `план − факт`: «+» = дешевле плана, тогда как в остальных строках «+»
   и >100% = больше факта. Для CPM логично, зафиксировано, чтобы позже не «починили» знак.
2. **Крайние случаи.** Нулевой/пустой план → pacing печатает дефис, не `0%` и не `∞`; нулевые
   показы → `fact cpm` дефис.
3. **`planned imps` для не-CPM тактик** — не «показы из МП», а величина, выведенная из купленных
   юнитов и бенчмарка CTR/VCR (`RatePlanCalculator.planTargets`). Сопоставима с фактом, но
   производная.
4. **Дубли в строках CTR и CPM**: MONTH GOAL и EOC GOAL заполняются одним токеном, так размечен
   шаблон. У Reach EOC GOAL и EOC PROJ тоже одинаковые — это следует из спеки.

## Сделано в коде (2026-08-21)

- **Данные.** `CampaignDataCollector.parseEstimates` читает колонку **Clicks**, и планово-флайтовые
  Total Cost / Impressions / Clicks едут отдельными полями `Tactic.planFlightSpend` /
  `planFlightImps` / `planFlightClicks` — иначе EOM их теряет: слоты 0/1 перезаписываются планом
  месяца. Флайтовый Reach уже доживал в `planReach`.
- **Резолверы.** Новый бин `ChannelSlideResolvers` (19 ячеек таблицы), подключён в
  `PlaceholderSectionBuilderImpl.buildTacticPacingSection` → токены попадают в книгу на шаге
  генерации, а шаг деки читает их обратно ридером. Поддержан ручной override через Adjustments
  («Tactic N imps pacing:» и т.д.), как у соседних резолверов.
- **Форматы**: `101%`, `+0.03pp`, `$12.00`, `+ $0.30`, счётчики с разделителями; любая
  недостающая/нулевая база печатает дефис.
- **Недели для reach plan** считаются как длина отчётного окна (`flightTs`) в днях ÷ 7 — то же
  окно, по которому агрегирован факт показов, поэтому цифры согласованы между собой.
- **Тесты**: `ChannelSlideResolversTest` (9), плюс read-back в `SheetPlaceholderReaderImplTest`.
