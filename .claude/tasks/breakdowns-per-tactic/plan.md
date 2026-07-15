# План: per-tactic breakdown-слайды (manual v1)

Статус: черновик плана, готов к реализации.
Дата: 2026-07-12.

## 1. Цель

Дать пользователю на шаге "Breakdowns" (шаг 3 визарда) включать per-tactic разбивки, которые
попадают в итоговую презентацию:

- Top Publishers (`tp`)
- Creative Analysis (`ca`)
- Geo Performance (`geo`)
- Audience Analysis (`aud`)
- Device Breakdown (`dev`)

Логика пользователя: после метчинга план ↔ line-item id он на шаге 3 включает нужные тоглы →
на build-sheet под них создаются блоки в шите → на шаге 4 пользователь **руками** вносит данные →
на генерации выбранные (тактика × breakdown) слайды попадают в деку.

## 2. Ключевое решение: v1 = manual

**Уточнение по creative:** creative — единственный breakdown, где данные ЕСТЬ (`byCreative`),
поэтому его заполняем **АВТО из BQ уже в v1** (см. §5.4). Остальные 4 (publishers/devices/geo/
audience) в v1 — ручной ввод, до расширения выгрузки Elevate (фаза 0). Раскладка блока одинаковая
для обоих режимов — разница лишь в том, кто пишет значения в ячейки: код или человек.

В v1 ручные разбивки **вносятся руками**. Причина — жёсткое ограничение по данным (см. §3):
авто-заполнение сейчас невозможно для 4 из 5 типов. Поэтому v1:

- в презе — только плейсхолдеры;
- в шите — подписанные блоки под ручной ввод;
- на генерации — плейсхолдеры заполняются значениями из шита.

Никакого расчёта из BigQuery в v1 нет. Авто-заполнение — отдельная фаза 2.

## 3. Реальность данных (почему manual)

BigQuery/Elevate-выгрузка, которую читает pipeline, содержит только колонки:
`date, channel, cost, impressions, clicks, completions, day_of_week, line_item_id, creative`
(см. `CampaignDataCollector` ~строки 178-198). Отсюда:

| Breakdown | Данные per-tactic сейчас | Авто в будущем |
|---|---|---|
| Creative (`ca`) | ✅ есть (`byCreative` по `lineItemId`) | фаза 2 — авто из BQ |
| Geo (`geo`) | ⚠️ только campaign-level (вкладка Geo, парсит Claude), не по тактикам | нужен per-LI geo в выгрузке |
| Audience (`aud`) | ⚠️ только campaign-level (вкладка Audience&Inventory) | нужен per-LI audience |
| Publishers (`tp`) | ❌ колонки нет | нужна колонка Publisher/Site в Elevate SQL |
| Devices (`dev`) | ❌ колонки нет | нужна колонка Device в Elevate SQL |

**Фаза 0 (вне этого репо, блокер для авто):** расширить SQL-выгрузку Elevate колонками
`Publisher`, `Device` и per-line-item строками geo/audience. До этого авто-заполнение
publishers/devices/per-tactic-geo/audience технически невозможно. Manual v1 этот блокер обходит.

## 4. Архитектура: асимметрия шит vs слайды

Разная стоимость объекта → разная стратегия:

- **Шит** — блоки лежат в шаблоне на отдельных вкладках; невыбранные **чистятся по якорю**
  (переиспользуем паттерн `trimTactics`). Большой лист в Sheets дёшев.
- **Слайды** — 5 мастер-слайдов; выбранные **дублируются** (`duplicateObject`) и заполняются.
  140 готовых слайдов в шаблоне — тупик (ручная сборка объектов + взрыв object-id в yml).

## 5. Дизайн шита (отдельные вкладки)

### 5.1. Раскладка

- Одна вкладка на тип breakdown: `Publishers`, `Creatives`, `Geo`, `Audience`, `Devices`.
- Внутри вкладки — по блоку на тактику (1..28), каждый блок с якорной подписью, напр.:
  - `Creative analysis 1` / `Creative analysis 2` … (якорь с РЕАЛЬНЫМ номером тактики), под ним
    подписанные строки (`CREATIVES LIVE`, `BEST …`, `AVG. …`, `TOP CREATIVE`) и таблица
    `Creative | Impressions | CTR | VCR | Spend` на 5 строк.

### 5.1b. Раскладка блоков (фактическая, из шаблона EOC_Template → таб `Breakdowns`)

Принятая раскладка: **все breakdown-типы на ОДНОМ табе `Breakdowns`**; типы идут бок о бок по
колонкам (Publishers в A–D, Creative в E–I, …), тактики — вниз (тактика 1 сверху, тактика 2 ниже).
Каждый блок с уникальным якорем и реальным номером: `Top Publishers 15`, `Creative analysis 15`.

Следствия для кода:
- **breakdown-ридер/райтер ищет якорь по ВСЕМУ листу** (любая строка И колонка), а не от колонки A
  (в отличие от `trimTactics`, который сканировал от A). Нашли ячейку якоря → берём её `(row, col)`
  → читаем/пишем значения по фиксированным офсетам ВНУТРИ блока. Не привязываться к конкретной
  колонке — офсеты от найденной ячейки, чтобы сдвиг блока в шаблоне не ломал код.
- **Один общий таб `Breakdowns`** → `readSheetGrid` расширяем всего на ОДИН таб (`General` +
  `Breakdowns`), не на пять. Меньше кода, чем в исходном плане "5 вкладок".

### 5.1d. КРИТИЧНО: якорь — ТОЧНОЕ совпадение ячейки, не "содержит"

`Creative analysis 1` — префикс `Creative analysis 15` (и `Top Publishers 1` ⊂ `Top Publishers 15`).
Поиск якоря обязан быть **полным совпадением** (`cell.trim().equals("Creative analysis 15")`), НЕ
`contains`/`startsWith` — иначе блок тактики 1 зацепит тактики 10–19 и данные поедут не туда.
Это самый опасный баг-риск схемы.

### 5.1c. Уникальные vs повторяющиеся токены в шите

- Уникальный токен с реальным номером (`{{tactic 1}}`, `{{tactic 2}}`) в шапке блока — ОК, его
  заполняет существующий резолвер имён тактик через find/replace.
- Повторяющийся токен с `n` (`{{cr_live_n}}`) в шите — НЕЛЬЗЯ (схлопнется, см. 5.1a). Значения
  внутри блока код пишет/читает по якорю, а не через `n`-токены.

### 5.1a. КРИТИЧНО: в шите НЕ должно быть повторяющихся `{{n}}`-токенов

В шите храним **только**: якорь блока (с номером), подписи строк/колонок и **пустые ячейки под
ручной ввод**. Токены `{{...}}` живут **только на слайде** (§6).

Причина: одинаковые токены в разных блоках схлопываются. Если в блоках "Creative analysis 1" и
"Creative analysis 2" лежат буквально одинаковые `{{cr_live_n}}`, то `createSheet` find/replace
(`setAllSheets(true)`) зальёт во все блоки одно значение — блоки не различимы. Поэтому связь
шит↔слайд идёт **не токен=токен**, а через якорь:

```
Шит:  якорь "Creative analysis 3" → значения читаются по позиции/подписи внутри блока
  ↓  readSheetGrid по breakdown-вкладке + разбор блока
Код:  map для тактики 3: { "{{cr_live_n}}" → "12", "{{tactic n top creative name n.1}}" → "…", … }
  ↓  duplicateObject(master) + scoped replaceAllText(pageObjectIds=[slide3])
Слайд: копия мастера с залитыми значениями тактики 3
```

Это тот же приём, что уже используют top-creative значения и `writePacingTables` (блок ищется
по якорной подписи, не по токену).

### 5.2. Что менять в коде (RealSheetDeckProvider)

1. **`readSheetGrid` — читать таб `Breakdowns` дополнительно.** Сейчас читает только первую
   (`tabSheetIds.keySet().iterator().next()`). Расширить: читать `General` (первую) + `Breakdowns`
   и **склеивать** в один grid (с разделителем-маркером вкладки, по образцу `geoRows`, где вкладки
   разделяются строкой `### TAB: <name> ###`). Не сломать текущий однотабовый EOC-поток (первая
   вкладка остаётся источником основных плейсхолдеров).
   - Файл: `backend/external-services/.../google/RealSheetDeckProvider.java`.
   - Порт: при необходимости расширить `SheetDeckProvider.readSheetGrid` (сигнатуру не менять,
     список breakdown-вкладок — внутренняя константа/конфиг провайдера).
2. **Запись плейсхолдеров** уже идёт по всем вкладкам (`findReplace` с `setAllSheets(true)`),
   доп. правок не требует.
3. **Чистка невыбранных блоков (subtractive).** Научить трим ходить по breakdown-вкладкам:
   для каждой пары (тактика, breakdown), которая НЕ выбрана, найти блок по якорю и вычистить
   значения+форматирование (как `summaryRowClearRequests` / `mainSlideClearRequests`, но per-tab).
   Нужен новый вход: множество выбранных (tacticNum → Set<BreakdownType>).

### 5.3. Google Sheets шаблон (ручная операция)

- В Sheets-шаблон добавить один таб `Breakdowns` с блоками-заготовками на 28 тактик
  (типы бок о бок, тактики вниз) и уникальными якорями `Top Publishers N` / `Creative analysis N` / …
- Зафиксировать имя таба и формат якорей — код ищет блоки по подписи (точное совпадение), не по
  координатам.

### 5.4. Авто-заполнение creative-блока из BQ (v1)

- Сейчас `byCreative` (`CampaignDataCollector`) держит только **top-1** креатив. Расширить до
  **top-5** с полями imps/ctr/vcr/spend, плюс агрегаты на блок: `CREATIVES LIVE` (кол-во
  уникальных креативов), `BEST` / `AVG` по CTR-или-VCR (тип метрики — из channel-маппинга тактики).
- Писать значения в creative-блок шита по якорю (как `writePacingTables` пишет pacing-таблицы):
  код находит `Creative analysis N` и заполняет ячейки блока; пользователь может поправить на
  шаге 4; `readSheetGrid` читает назад.
- Для остальных 4 breakdown'ов ячейки остаются пустыми под ручной ввод (до фазы 0).

## 6. Дизайн слайдов (5 мастеров + duplicate)

### 6.1. Шаблон Google Slides (ручная операция)

- Добавить **5 мастер-слайдов** (по одному на тип breakdown) с генерик-токенами, где номер
  тактики обозначен буквой `n` (она НЕ резолвится отдельно — см. 6.2). Пример creative-мастера:
  - шапка: `DELIVERY BREAKDOWN – {{tactic n}}`, метрики `{{cr_live_n}}`, `{{cr_bKPI_n}}`,
    `{{cr_aKPI_n}}`, `{{tactic n KPI type}}`, `{{tactic n top creative name}}`;
  - таблица (Slides Table) на 5 строк: `{{tactic n top creative name n.1}}`,
    `{{tactic n.1 top creative imps}}`, `{{tactic n.1 top creative ctr}}`,
    `{{tactic n.1 top creative vcr}}`, `{{tactic n.1 top creative spend}}` … n.5;
  - takeaways: `{{cr_takeawey_tactic n_1}}` … `_4` (опечатку "takeawey" сохранить И в коде —
    имя токена должно совпадать байт-в-байт, см. 6.3).
- Прописать object-id этих 5 мастеров в `application.yml` (новая @ConfigurationProperties группа,
  по образцу `dist-slide-object-ids`).

### 6.2. Генерация (RealSlidesProvider — новый код-путь)

Для каждой выбранной пары (тактика N, breakdown B):
1. `duplicateObject(masterSlideId_B)` → новый `slideId`.
2. `replaceAllText` **со scope `pageObjectIds=[slideId]`**: генерик-токены → **сразу значения**
   тактики N (из блока `Creative analysis N` склеенного grid шита). Букву `n` в токене отдельно
   НЕ заменяем — меняем целый токен `{{cr_live_n}}` на значение. Scope гарантирует, что одинаковый
   текст токена на разных копиях заполняется разными значениями — уникальные токены на 140 слайдов
   не нужны.
3. `updateSlidesPosition` — поставить слайд в нужное место (после "своей" тактики / в конце деки).

В конце — удалить 5 мастер-слайдов (`deleteObject`).

### 6.3. Риски слайдов (заложить в реализацию)

- **Имена токенов — байт-в-байт.** Словарь токенов зафиксировать один раз; код кладёт в map ровно
  те же строки, что на слайде (включая опечатки типа `takeawey`). Любое расхождение → токен молча
  не заменится и останется голым `{{...}}` на слайде.
- **Порядок замен: более специфичные токены — первыми.** `{{tactic n top creative name}}`
  (TOP CREATIVE) и `{{tactic n top creative name n.1}}` (строка таблицы) похожи; заменять сначала
  более длинные, чтобы избежать частичного совпадения.
- **Таблица — это Slides Table.** `replaceAllText` заменяет текст и в ячейках таблицы — ок.
- **Порядок слайдов** — `duplicateObject` вставляет копию сразу за оригиналом; нужен явный
  `updateSlidesPosition`.
- **Чарты** — в v1 только текст, живых linked-чартов на breakdown-слайдах НЕТ. Чарты — фаза 2
  (переиспользование `SlideChartSwapper`, отдельная работа).
- **Размер batchUpdate** — 140 duplicate + 140 replace бить на чанки.
- Batch выполняется как расширение существующего slides-flow, ложится в `external-services`.

## 7. Контракт + фронтенд

### 7.1. Контракт (OpenAPI-first)

- В `openapi.yaml`: в `GenerateRequestV1` добавить поле выбора, напр.
  `breakdowns: array of { tacticNum: int, types: [BreakdownTypeV1] }`.
- Новый enum-схема `BreakdownTypeV1` = `TOP_PUBLISHERS | CREATIVE | GEO | AUDIENCE | DEVICE`.
- Регенерировать сорсы (generated sources не редактировать руками).
- `GeneratePayload` (service DTO) — добавить соответствующее поле; тип — **top-level enum**
  `BreakdownType` в `model`/`enums` (hard-rule: no nested types, no magic strings).

### 7.2. Фронтенд (почти всё готово)

- UI тоглов уже есть: `StepBreakdowns.tsx` (5 тоглов × тактика). Сейчас помечен
  "Cosmetic — no backend effect": состояние `breakdowns` в `ReportConstructorPage.tsx:100`
  никуда не отправляется.
- Осталось: прокинуть `breakdowns` в build-sheet и generate запросы через сгенерированный
  OpenAPI-клиент (`frontend/src/shared/api`), без сырых fetch.

## 8. Список изменений (backend), с учётом правил

- `openapi.yaml` — новое поле + enum-схема. (контракт-first, тонкие контроллеры)
- `BreakdownType` enum (top-level, в `model`/`enums`).
- `GeneratePayload` — новое поле.
- `SheetDeckProvider` / `RealSheetDeckProvider`:
  - `readSheetGrid` — мультитаб-чтение breakdown-вкладок;
  - трим — чистка невыбранных блоков per-tab (новый вход: выбор).
- `SlidesProvider` / `RealSlidesProvider` — новый метод "duplicate breakdown slides"
  (duplicate + scoped replace + reposition + delete masters).
- `GoogleProperties` + `application.yml` — @ConfigurationProperties группа с object-id
  5 мастер-слайдов (без @Value).
- Оркестрация в `ReportGenerationServiceImpl` — передать выбор в sheet-trim и slides-duplicate.
- JavaDoc на каждый новый handwritten метод; никаких private-методов в бинах; конструкторная
  инъекция через Lombok.

## 9. Ручные операции (ops)

- Google Sheets шаблон: 5 вкладок с якорными блоками на 28 тактик.
- Google Slides шаблон: 5 мастер-слайдов + собрать их object-id.
- `application.yml`: вставить object-id мастеров (как делали для 28 тактик — см. memory
  `tactics-28-support`).

## 10. Фазирование

- **v1 (этот план):** manual ввод, отдельные вкладки, 5 мастеров, только текст.
- **Фаза 0:** расширить Elevate SQL-выгрузку (Publisher, Device, per-LI geo/audience). Вне репо.
- **Фаза 2:** авто-заполнение блоков из BQ (Creative — сразу, остальное после фазы 0);
  опционально linked-чарты на breakdown-слайдах через `SlideChartSwapper`.

## 11. Открытые вопросы

- Точное имя/формат якорей и колонок в каждой breakdown-вкладке.
- Куда в порядке деки вставлять breakdown-слайды: сразу после "своей" тактики или отдельной
  секцией в конце.
- Кто и когда закрывает фазу 0 (расширение выгрузки Elevate).

## 12. План тестов

- Backend: `readSheetGrid` мультитаб-склейка (given несколько вкладок → then единый grid);
  трим невыбранных блоков (given выбор → then якорные диапазоны очищены, остальное цело);
  slides-duplicate (given выбор → then N дублей с правильными значениями, мастера удалены).
  Стиль по `.claude/rules/20-tests.md` (`should ...`, Given/When/Then).
- Frontend: `breakdowns` уходит в запрос при разных наборах тоглов; UI-состояние по
  `.claude/rules/50-frontend-tests.md`.
- E2E-проверка через `verify`/`run` skill на реальном потоке build-sheet → fill → generate.
