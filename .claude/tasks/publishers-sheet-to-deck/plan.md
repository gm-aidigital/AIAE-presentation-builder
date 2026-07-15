# План: Top Publishers — EOC Sheet → презентация

Статус: план, готов к реализации.
Дата: 2026-07-15.
Скоуп: только `tp` (Top Publishers). Остальные 4 breakdown'а — по этому же образцу позже.

## 1. Цель

Значения таблицы Top Publishers на слайде забираются **1 в 1** из блока `Top Publishers N`
на вкладке `Breakdowns` EOC-шита, куда пользователь вносит их руками на шаге 4.
`{{publishers_observation_n_1..4}}` — целиком Claude-генерация.

## 2. Поток

```
Шаг 1 (build sheet):  createSheet find/replace заливает {{tactic N}} / {{tactic N imps}}
                      ТАКЖЕ на вкладку Breakdowns (сейчас — только первая вкладка)
        ↓
Шаг 2 (руки юзера):   юзер вносит Publisher / Impressions / Share of voice в блок
        ↓
Шаг 3 (generate):     читаем блок по якорю → строим concrete-token map
                      → Claude пишет observations → заливаем в дубли мастер-слайдов
```

## 3. Решения (подтверждены пользователем 2026-07-15)

| Вопрос | Решение |
|---|---|
| Источник `{{tactic n imps}}` на слайде | Главная вкладка, как сейчас (в `flatReplacements` уже есть) |
| Юзер заполнил < 15 строк | Незаполненные строки → прочерк `—` (как `DASH` в `trimTactics`) |
| Батчинг Claude | Чанки по ~5 тактик на вызов |
| Таблица пустая, но тогл включён | Слайд добавляем, observations пустые, Claude НЕ зовём |

## 4. КРИТИЧНО: баг порядка в текущем коде

`ReportGenerationServiceImpl.runSlidesFromSheet`:

```
272:  slides.createDeck(..., flatReplacements, ...)   ← единственный fill плейсхолдеров
277:  chartHelper.addBreakdownSlides(...)             ← дубли + renumber ПОСЛЕ fill
```

`addBreakdownSlides` дублирует мастера и переименовывает `{{publisher_n.1}}` → `{{publisher_3.1}}`
**после** того, как fill уже отработал. Значит конкретные токены на копиях **никогда не заливаются**
и уедут в деку сырыми. Заметка в `master-slide-tokens.md` («once renumbered, concrete tokens can be
filled by the existing global placeholder map — no new fill code path») при текущем порядке неверна.

**Фикс:** `addBreakdownSlides` принимает map значений и для каждого токена мастера делает
**одну** scoped-замену `generic → финальное значение` (renumber остаётся фолбэком, когда значения
для токена нет). Замены scoped на `pageObjectIds=[copyId]` — одинаковые токены на разных копиях
не коллидируют. Порядок внутри batchUpdate и между чанками последовательный, так что фаза fill
после фазы duplicate валидна.

## 5. Изменения

### 5.1. Шит: залить {{tactic N}} / {{tactic N imps}} на вкладку Breakdowns

`RealSheetDeckProvider.createSheet` сейчас скоупит find/replace на первую вкладку
(осознанно — фикс таймаута, см. memory `28-tactic-timeout-fix`). Расширить: та же пачка
find/replace дополнительно применяется к вкладке `Breakdowns`, но **только для токенов, которые
реально есть на этой вкладке** (`{{tactic N}}`, `{{tactic N imps}}`) — не гнать все ~800 токенов
по второй вкладке, иначе вернём тот самый таймаут.

### 5.2. Шит: чтение блока Top Publishers

Новый метод порта `SheetDeckProvider`:
`Map<Integer, List<PublisherRow>> readPublisherTables(String spreadsheetId, Set<Integer> tacticNums, String token)`

- Один `readGrid(BREAKDOWN_TAB)` на все тактики (не N чтений).
- Якорь `Top Publishers N` — **точное совпадение** ячейки (§5.1d исходного плана: `Top Publishers 1`
  — префикс `Top Publishers 15`).
- От найденной `(row, col)` — окно 17 строк вниз × 4 колонки вправо.
- Внутри окна найти строку заголовков и взять колонки **по именам** `Publisher` / `Impressions` /
  `Share of voice`, а не по жёстким офсетам — сдвиг колонки в шаблоне не должен молча
  разъехать данные.
- `PublisherRow(String name, String impressions, String shareOfVoice)` — top-level record в `model`.

Отдельный метод порта, а не расширение `readSheetGrid`: склейка вкладок в один grid отдала бы
`readPlaceholders` строки Breakdowns и он мог бы подцепить лишние токены.

### 5.3. Claude: observations

- Новый батч в `ClaudeClient` / `RealClaudeClient`, чанки по 5 тактик.
- Вход на тактику: имя тактики, таблица паблишеров (name/imps/sov), доля от общего, бриф/RFP.
- Выход: 4 булета × **155 символов**.
- Лимит: prompt-таргет = `155 * 0.8 ≈ 124` → `ClaudeCompressionService` (сжатие Claude'ом) →
  `ClaudeResponseNormalizer` (обрезка по точке / границе слова). Всё это уже есть, переиспользуем.
- Тон/контент: разрешён лёгкий ресёрч и связка каналов с RFP — **не более ~20%** от вывода;
  остальное — строго из данных таблицы.
- Таблица пустая → батч для этой тактики не отправляем, токены → пустая строка.

### 5.4. Слайды

- `SlidesProvider.addBreakdownSlides` — новый параметр: `Map<String, String> breakdownValues`
  (concrete token → значение).
- `buildBreakdownRequests` фаза 1: для каждого токена мастера `concrete = renumber(token, n)`;
  если `breakdownValues` содержит `concrete` → заменить `token` → значение; иначе → renumber
  (текущее поведение как фолбэк).

### 5.5. Оркестрация

`runSlidesFromSheet`: перед `addBreakdownSlides` — прочитать таблицы, позвать Claude, собрать
`breakdownValues` (паблишеры + прочерки + `{{tactic N}}` + `{{tactic N imps}}` из
`flatReplacements` + observations), передать в `addBreakdownSlides`.

## 6. Правила репозитория

JavaDoc на каждый новый метод; никаких private-методов в бинах; `PublisherRow` — top-level record;
имена токенов — байт-в-байт из `master-slide-tokens.md`; тесты по `.claude/rules/20-tests.md`
(`should ...`, Given/When/Then).

## 7. Тесты

- Якорь `Top Publishers 1` не цепляет `Top Publishers 15` (exact match).
- Колонки находятся по заголовку при сдвиге раскладки.
- < 15 строк → прочерки в хвосте.
- Пустая таблица → Claude не зовётся, токены пустые.
- `buildBreakdownRequests` заливает значение, когда оно есть, и renumber'ит, когда нет.
- Observations ≤ 155 символов после нормализации.
