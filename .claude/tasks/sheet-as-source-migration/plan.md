# Миграция: Google Sheet как единственный источник для слайдов

## Цель

Разрезать сегодняшний однопроходный пайплайн `raw inputs → Claude → slides`
на два шага с человеческим чекпоинтом:

1. **Шаг 1 (есть сегодня, `GenerationTarget.SHEET`)** — из сырых вводных собираем
   Google-таблицу: цифры из медиаплана + Claude Batch A (аудитория) и B (по-тактикам
   gender/daypart). Таблица = источник правды. Пользователь её правит.
2. **Шаг 2 (переписываем)** — слайды заполняются **строго из таблицы**. Claude на этом
   шаге добирает только то, чего в таблице нет (нарратив = Batch C + geo/funnel/kpis
   summaries). Графики строятся **из таблицы**, не из BigQuery.

## Три требования пользователя

1. Не дублировать сбор. Каждый Claude-батч и каждый парсинг медиаплана — ровно один раз.
   Шаг 2 не должен повторять то, что уже лежит в таблице.
2. Плейсхолдеры слайдов заполняются **значениями из таблицы** (правки юзера = вход).
3. Чтение таблицы — по **якорям-меткам**, не по адресам ячеек.

## Что переиспользуется (НЕ пишем заново)

- `SheetRowHelperImpl.findLabelValue` — значение **справа** от метки
  (`Client name:`, `Campaign name:`, `Flight dates:`, `Tactics list:`, `KPI:`,
  `Geo:`, `Funnel:`, `Audience age:`, `Segments:`).
- `SheetRowHelperImpl.findLabelValueBelow` / `collectColumnValuesBelow` — значение
  под заголовком / весь столбец.
- `RealSheetDeckProvider` уже читает сгенерённую таблицу в грид (`readGrid`, A1:ZZ) и
  уже держит те же якоря: `SUMMARY_HEADER = [Tactic name, Benchmark, KPI type, KPI]`,
  `MAIN_SLIDE_LABEL = "Main slide N"`, `TOTALS_LABEL = "Total"`.
- `slides.createDeck(flatReplacements)` + `chartHelper.buildCharts(...)` — рендер деки
  **источник-агностичен**, не трогаем.
- Таблица заполняется чистым `findReplace {{token}}→value`, метки остаются на месте →
  якорное чтение устойчиво к правкам структуры.

## Целевой поток (шаг 2)

```
spreadsheetId (+ brief, reportType из payload_json джобы шага 1)
  → sheetGrid = readGrid(spreadsheetId)                       // один раз
  → flatReplacements = SheetPlaceholderReader.read(sheetGrid)  // Req 2 + Req 3
  → chartData        = SheetChartDataReader.read(sheetGrid)    // графики из таблицы
  → sheetCampaign    = SheetCampaignReader.read(sheetGrid)     // контекст для Claude C
  → ccC = claude.batchResults(sheetCampaign, brief, freq)      // ТОЛЬКО нарратив
  → geo/funnel/kpis summaries (по необходимости)
  → flatReplacements += narrative keys
  → slides.createDeck(flatReplacements)
  → chartHelper.buildCharts(chartData)
```

## Симметрия батчей (ядро анти-дублирования, Req 1)

| Шаг | Claude | Источник | Пишет |
|---|---|---|---|
| 1 → Sheet | Batch A + B | сырьё | цифры + A/B в таблицу |
| 2 → Slides | **только** Batch C + summaries | **таблица** | дека |

Суммарно за два шага = тот же A+B+C, что сегодня за один проход. Каждый батч — один раз.

## Карта якорей (Req 3) — что откуда читать

**Верхний инфо-блок (справа от метки, `findLabelValue`):**

| Метка в таблице | Плейсхолдер |
|---|---|
| `Client name:` | `{{client_name}}` |
| `Campaign name:` | `{{Campaign_name}}` |
| `Flight dates:` | `{{flight_dates}}` |
| `KPI:` | `{{primary_kpis}}` |
| `Geo:` | `{{geo_locations}}` |
| `Funnel:` | `{{funnel_stages}}` |
| `Audience age:` | `{{audience_age}}` |
| `Segments:` | `{{audience_segments}}` |
| `Market Volume` | `{{market volume}}` |
| `RFP Input` / `Info` | `{{RFP info}}` |

**Сводная таблица (якорь — строка `Tactic name / Benchmark / KPI type / KPI`, строка на тактику):**
`{{tactic N}}`, `{{tactic N – bench}}`, `{{tactic N KPI type}}`, `{{tactic N KPI}}`.
Строка `Total` → `{{total imps}}`, `{{total spend}}`, `{{total reach}}`.

**Блок «Main slide N» (якорь `Main slide N`, фиксированная сетка меток внутри блока):**
`spend/imps/reach/ctr/vcr/f/goal`, plan-vs-fact (`Spend Plan/Fact`…),
`female/male/weekdays/weekends`, `top creative name/imps/clicks`,
Daily/Monthly pacing (`Date/Amount/Impressions`), Channel/Gender Distribution → данные графиков.

## Пофайловый чек-лист

### Новое
- ✅ Чтение грида таблицы — добавлено как `SheetDeckProvider.readSheetGrid(spreadsheetId, token)`
  (реализация в `RealSheetDeckProvider` переиспользует приватные `readGrid`/`fetchSheetIds`;
  `StubSheetDeckProvider` → пусто) + `ReportSheetHelper.readSheetGrid(sheetUrl, token)`
  (извлекает id, делегирует). Отдельный порт `SheetInputReader` не понадобился.
- ✅ `helpers/SheetPlaceholderReader` (+ `impl/SheetPlaceholderReaderImpl`, тест) — грид →
  `Map<String,String>` по карте якорей выше. Чистый хелпер над гридом, без Google-зависимостей.
- ✅ `helpers/SheetChartDataReader` (+ impl, тест, DTO `SheetChartData`) — грид → per-tactic
  daily/monthly `Pivot` (переиспользован существующий `Pivot`).
  Находки:
  • Distribution-графики читать НЕ надо — `TacticChartBuilder.buildDistributionCharts` уже берёт
    значения из `flatReplacements` (`distTacticImps`/`distTacticNames`/`distTotalImps`).
  • Из BQ приходил ТОЛЬКО daily/monthly `Pivot` — ридер воссоздаёт ровно его.
  • Якорь чтения — токены-маркеры `{{tactic n date/impressions/amount}}` (+` mon`), которые
    переживают `findReplace` и остаются в шапке блока; данные лежат строками ниже. Это обходит
    расхождение меток «Daily pacing» с номером/без (во вложенном xlsx они без номера).
  • kpiType (ctr/vcr) берётся из уже прочитанного `flatReplacements` и решает, идёт метрика в
    clicks или completions.
  • ✅ ИНТЕГРАЦИЯ СДЕЛАНА (см. ниже «Графики-из-таблицы»).
- `helpers/SheetCampaignReader` — грид → усечённый `CampaignData` для Claude C.
- OpenAPI: новый эндпоинт «сгенерировать слайды из джобы-таблицы»
  (принимает `jobId` или `sheetUrl`), `ReportJobsController` его имплементит.

### Меняется
- ✅ Скелет шага 2 собран:
  • `GenerationTarget.SLIDES_FROM_SHEET` + `GenerationTargetV1` (enum в OpenAPI) +
    поле `sheetUrl` в `GenerateRequestV1`/`GeneratePayload` (обновлены 14 позиционных
    конструкторов в тестах; маппер: `sheetUrl` игнор для preview).
  • `ReportGenerationServiceImpl.run` — ранняя ветка `SLIDES_FROM_SHEET` → новый
    `runSlidesFromSheet`: читает грид (`sheetHelper.readSheetGrid`) → `SheetPlaceholderReader`
    → `deriveTacticCount` → `slides.createDeck` → `trimUnusedTactics(url, count, token)`.
    Ни `collectData`, ни Claude A/B/C не вызываются (тест это фиксирует `verifyNoInteractions`).
  • `ReportGenerationChartHelper.trimUnusedTactics(url, tacticCount, token)` — перегрузка
    без медиаплана.
  • `start()` требует `sheetUrl` для шага 2. Контроллер/эндпоинт не менялись — переиспользован
    `/report-jobs` (маппер подхватывает target и sheetUrl).
  • Ветки `SLIDES` и `SHEET` — без изменений.
- ✅ Графики-из-таблицы подключены:
  • `ChartRequest` — два опциональных поля `dailyPivots`/`monthlyPivots` (nullable). `null` = старый
    BQ-путь; заданы = pacing из шита.
  • `TacticChartBuilder.buildDailyCharts`/`buildMonthlyCharts` — если пивоты заданы, используют их и
    минуют gate `headers.valid()`/`lineItemGrouper`/`multiYear`. Distribution-чарты не тронуты (они и
    так из `flatReplacements`).
  • `ReportGenerationChartHelper.buildChartsFromSheet(slideUrl, grid, flat, tacticCount, token)` —
    владеет `tacticExtraction` + `SheetChartDataReader`: считает kpiTypes из имён тактик, читает пивоты
    из грида, строит `ChartRequest` с пустыми `bqRows`/`lineItemMapping`. Общий `populateTacticMaps`
    переиспользован BQ- и sheet-путём.
  • `runSlidesFromSheet` теперь зовёт `buildChartsFromSheet` и кладёт его warnings в `markJobDone`.
- ✅ Нарратив подключён (Batch A-стратегия + Batch C):
  • Новый `SheetCampaignReader` (+ impl, тест) — flat-карта → `CampaignData` (парсит числа) как контекст
    для промптов Claude. Дата-грид больше НЕ читается.
  • `runSlidesFromSheet` теперь: читает шит → `deriveTacticCount` → `sheetCampaign.read` →
    `claude.batchStrategic` (proposal + strategic points) + `claude.batchResults` (Batch C) →
    `placeholders.buildFlatReplacements(...)` даёт нарративную карту → **накладываем sheet-значения
    сверху** (`narrative`, затем `putAll(sheetValues)`), т.е. шит побеждает на пересечениях, а от Claude
    остаются только нарративные ключи, которых в шите нет.
  • Важно: в шите не было НЕ только Batch C, но и «стратегической» части Batch A
    (`{{proposal overview}}`, `{{Strategic overview/point N}}`) — step 1 её не генерил. Поэтому шаг 2
    зовёт A(стратегия)+C. Это НЕ дубль: аудитория (A) и дейпарты (B) уже в шите и не перезапрашиваются;
    числовой грид не пересобирается (`collectData` не вызывается — тест это фиксирует).

## Статус: миграция функционально завершена
Все 4 куска собраны. Осталось только: прогнать сборку/тесты (у меня нет JDK/Maven), регенерить OpenAPI
(бэк) и OpenAPI-типы (фронт), и подключить UI шага 2. Возможные доводки после реального прогона:
- качество промпта Claude на реконструированном из шита `CampaignData` (если контекста мало — расширить
  `SheetCampaignReader`);
- сверить точные метки/геометрию с БОЕВЫМ шаблоном шита (вложенный xlsx мог отличаться от прод-шаблона).
- Шаг 1 при завершении сохраняет `sheetUrl` (сейчас в `slide_url`) — фронт передаёт его в
  запросе шага 2; отдельная колонка `sheet_url` опциональна.

### Требуется пересборка (OpenAPI + фронт)
- Бэкенд: `mvn` регенерит `GenerationTargetV1`/`GenerateRequestV1` (добавлены значение enum
  и поле `sheetUrl`) — маппер и контроллер завяжутся после регена.
- Фронт: регенерить OpenAPI-типы; UI шага 2 шлёт `POST /report-jobs` с
  `target=SLIDES_FROM_SHEET` и `sheetUrl=<урл таблицы из шага 1>`.

### Удаляется из slides-пути (Req 1)
- Вызовы `CampaignDataCollector` и A/B-резолверов в ветке SLIDES.
- Двойной прогон Claude: `batchStrategic` / `batchTactical` в шаге 2 не вызываются.

## Стратегия безопасного перехода (чтобы не страшно)

1. Старый однопроходный SLIDES-путь **оставить рабочим** под старым `GenerationTarget.SLIDES`.
2. Собрать новый путь `SLIDES_FROM_SHEET` рядом. Ридеры покрыть unit-тестами на
   зафиксированном гриде (метки сдвинуты на ±строку/столбец — проверяем устойчивость якорей).
3. Прогнать оба на одном кейсе, сравнить `flatReplacements`.
4. Переключить UI на новый двухшаговый флоу.
5. Удалить старый SLIDES-путь и мёртвый сбор — последним коммитом, когда новый подтверждён.

## Ограничение окружения
Бэкенд здесь не собирается (нет JDK/Maven). Код пишется и ревьюится вручную,
сборку и тесты (`mvn`) запускает пользователь.
