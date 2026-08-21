# План: интеграция отдельного EOM-шаблона деки

Дата: 2026-08-17. Обновлено 2026-08-18.

## Статус

- **PR 1 (изоляция) — сделан.** `EomDeckProperties` (`external.eom-deck.*`) + `EomDeckPolicy`;
  EOM-ключи деки убраны из `GoogleProperties`; прод защищён безусловным гашением
  `EOM_SLIDES_TEMPLATE_ID` в `scripts/lib/deploy-env.sh`.
- **PR 2 (сборка деки) — сделан.** Слайды находятся по токенам, а не по object-id
  (`EomSlideFinder`): мастера тактики = слайды с переменной `n`, брейкдаун-мастера = по
  маркерным токенам, лишние дашборды = по минимальному номеру тактики на слайде. Два мастера
  на тактику, брейкдауны после обоих. `reportType` протянут в четыре метода `SlidesProvider`.
- **Шаблон размечен** (23 слайда, 628 токенов) — дамп в `eom-template-dump.txt`.
  Раздел 1 ниже описывает состояние ДО разметки и оставлен как карта соответствия токенов.
- **Слайды 1-10 заполнены.** Cover, north star, pacing dashboards (3-6) и performance vs plan
  (7-10). Дашборды считаются дек-сайдом из токенов сводной таблицы (`EomDashboardResolver`),
  ключевые выводы под таблицами — два массива на strategic-вызове (`pacing_takeaways`,
  `performance_takeaways`). Трим (лишние слайды + лишние строки последнего блока) общий на оба
  дашборда.
- **Осталось:** словарь имён токенов EOM-шаблона (~22 переименования существующих значений),
  ~35 новых токенов нарратива (PR 3-4), чарты брейкдаунов aud/aud-seg/dev на новых книгах.

Разделы 3-4 ниже (конфиг и порядок работ) частично исполнены — см. статус выше.

Вход: `EOM_Report_template.pptx` (18 слайдов, разметки `{{…}}` нет) — выгрузка нового
EOM-дизайна. Референс: `EOC_template.pptx` (22 слайда, размечен, это выгрузка живого
`slides-template-id = 11qzOC7mdbajjlrrVrC8Zv7W5P_SOHpgMV_eWSW56WI0`).
Текстовый дамп EOM-шаблона: `eom-template-dump.txt` в этой же папке.

## 0. TL;DR

1. **Сегодня EOM = EOC-дека минус 2 слайда** (`eom-slides-template-id` пустой,
   `eom-drop-slide-object-ids`). Новый шаблон это ломает: у EOM теперь своя структура —
   не «28 тактик + сторителлинг», а «канал × pacing CTD × месяц N из M».
2. **~70% цифр нового шаблона уже есть в коде.** Токены `{{tactic n imps plan ctd}}`,
   `{{… proj}}`, `{{… vs goal}}`, `{{tactic n spend pace}}`, `{{total imps pace}}`,
   `{{campaign pace status}}`, `{{eom_month_number}}`, `{{eom_flight_months_total}}`,
   `{{eom_next_report_month}}` уже резолвятся. Слайды 1, 3, 4, 6–8 закрываются
   существующими токенами почти целиком.
3. **Брейкдауны (13–18) — копипаста разметки из EOC 17–21**, один в один, включая
   таблицы. Это подтверждается сравнением текста: те же блоки, те же заголовки колонок.
4. **Реально нового — только нарратив** (Claude): ~25 новых токенов на 4 слайдах
   (2 × KEY TAKEAWAY, 3 блока на канал, 3×3 «what we did», 9 + projection на «focus»).
5. **Блокер №1 — конфиг.** Все object-id в `application.yml` описывают ОДИН шаблон.
   Пока `slides-template-id` и `eom-slides-template-id` не получат независимые наборы
   id (мастер тактики, мастера брейкдаунов, thoughts-мастер, книги-источники чартов),
   EOM на своём шаблоне не поедет. Это первый PR и он не меняет поведение EOC.

## 1. Инвентаризация нового шаблона (слайд → что нужно)

Легенда: ✅ токен уже есть в коде · 🆕 новый токен · ⚙️ нужна логика (trim/мастер).

| # | Слайд | Разметка |
|---|---|---|
| 1 | Cover: клиент, месяц, флайт, «MONTH 1 OF 3», Planned/Delivered/Pace | ✅ `{{client_name}}`, `{{eom_report_month}}`, `{{flight_dates}}`, `{{eom_month_number}}`, `{{eom_flight_months_total}}`, `{{total imps plan ctd}}`, `{{total imps}}`, `{{total imps pace}}` |
| 2 | North star: цель, аудитория, priority markets (4 чипа), horizon, «how channels ladder up» | ✅ `{{proposal overview}}`, `{{audience_age}}`, `{{audience_segments}}`, `{{tactic n funnel stage}}` · 🆕 `{{north_star}}`, чипы гео (`{{geo_locations}}` сегодня одна строка), `{{horizon}}` |
| 3 | Pacing dashboard: строка на тактику (budget planned/actual CTD, pacing %, impr goal CTD, delivered, ✓) + Total + KEY TAKEAWAY | ✅ `{{tactic n spend plan ctd}}`, `{{tactic n spend}}`, `{{tactic n spend pace}}`, `{{tactic n imps plan ctd}}`, `{{tactic n imps}}`, Total-строка (`{{total_investment_plan_ctd}}`, `{{total_investment}}`, `{{total_investment_pace}}`, `{{campaign pace status}}`) · 🆕 `{{tactic n pace status}}`, `{{pacing takeaway}}` · ⚙️ trim лишних строк |
| 4 | Performance vs plan: KPI на тактику (goal / CTD actual / vs goal / ✓) + KEY TAKEAWAY | ✅ `{{tactic n KPI type}}`, `{{tactic n goal}}`, `{{tactic n KPI}}` · 🆕 generic `{{tactic n kpi vs goal}}` (сейчас есть только per-metric `ctr/vcr/imps vs goal`), `{{tactic n kpi status}}`, `{{performance takeaway}}` · ⚙️ trim |
| 5 | Дивайдер «channel-by-channel», перечислены каналы | ✅ `{{tactic n}}` · ⚙️ переменное число чипов |
| 6–8 | **Мастер-слайд канала** (по одному на тактику): KEY TAKEAWAYS (What worked / Top window / Optimise), таблица 4 метрики × (CTD GOAL, CTD ACTUAL, VS GOAL, EOC PROJ, EOC GOAL), плитки Impr goal CTD / Delivered / Spend, бейдж «+11% vs goal», NEXT MONTH | ✅ вся таблица собирается из существующих `{{tactic n <metric> plan ctd / … / vs goal / proj / plan}}` для imps, ctr, vcr, reach, cpm, completions, `{{tactic n f}}`, `{{tactic n funnel stage}}` · 🆕 `{{tactic n worked}}`, `{{tactic n window}}`, `{{tactic n optimise}}`, `{{tactic n next month}}` · ⚙️ мастер → N копий `tct_n`; **чартов на слайде нет** (в отличие от EOC-тактики) |
| 9 | Дивайдер «where we go from here» | статика |
| 10 | What we did this month: 3 × (Observation / Action taken / Expected impact) | 🆕 9 токенов; вход — уже существующий дайджест Change Log |
| 11 | Focus next month: carry forward ×3, pivot ×3, new test ×3, updated projection | ✅ `{{eom_next_report_month}}`, `{{eom_next_month_number}}` · 🆕 10 токенов (по форме = `{{recommendation N}}` / `{{recommendation N text}}`) |
| 12 | Дивайдер «SLIDES LIBRARY» | ⚙️ удалять при сборке (в `eom-drop-slide-object-ids`) |
| 13 | Top 15 publishers | ✅ полностью из EOC slide 17 (`{{publisher_n.1..15}}`, `{{pub_imp_…}}`, `{{pub_sov_…}}`, `{{publishers_observation_n_1..4}}`) |
| 14 | Creative analysis | ✅ из EOC slide 18 (`{{cr_live_n}}`, `{{cr_bKPI_n}}`, `{{cr_takeaway_tactic n_1..4}}`, `{{tactic n.1..5 top creative …}}`) |
| 15,16 | Geographic performance — **два одинаковых слайда** | ✅ из EOC slide 19 · ⚙️ один удалить (решение за тобой) |
| 17 | Audience analysis | ✅ из EOC slide 20 (`{{age_n_*}}`, `{{gender_n}}`, `{{aud_n_*}}`, `{{aud_in_n_*}}`) · ⚙️ книги-источники чартов age/segments — новые id |
| 18 | Device breakdown | ✅ из EOC slide 21 · ⚙️ книга-источник device-чарта — новый id |

Чего в EOM-шаблоне **нет** (и это ок): strategic framework, campaign results, awareness/market
share, frequency & velocity, thoughts on campaign performance, thoughts on tactic performance,
recommendations, финальный слайд AI-era. Важное следствие: `thoughts-master-slide-object-id`
для EOM должен быть **пустым**, иначе конфиг EOC-мастера утечёт в EOM-сборку.

## 2. Что уже работает и переиспользуется как есть

- Двухшаговый флоу (Sheet → Deck) и `eom-sheets-template-id` (`1YAZDnMQ…`).
- Мастер-модель `мастер → N копий` (`RealSlidesProvider.addBreakdownSlides` /
  `tactic-master-slide-object-id`) — ровно то, что нужно слайдам 6–8.
- Trim таблиц по контенту (`RealSheetDeckProvider`, summary-header по первым 3 колонкам) —
  переиспользуется для новых таблиц слайдов 3 и 4.
- Вся EOM-математика прораты/проекции: `CampaignResolvers` (`plan ctd`, `pace`,
  `eom_month_number`, `eom_flight_months_total`, `eom_report_month`, `eom_next_*`),
  `PlanUnitTargets`, `RatePlanCalculator`, `LineItemMapping` (rate type / unit price /
  monthly budget с экрана матчинга).
- `EomPromptBuilder` — точка расширения для нового нарратива (EOC-тексты не трогаем).

## 3. Блокер: конфиг под один шаблон

Сейчас report-type-aware только `eom-slides-template-id` + два drop-списка. Остальное —
`tactic-master-slide-object-id`, `breakdown-master-slide-object-ids`, `thoughts-master-…`,
`summary-table-object-ids`, `results-slide-object-ids`, `breakdown-charts.*`,
`charts.*-template-sheet-id` — глобальное и описывает EOC-шаблон.

Предлагаю: вынести в `@ConfigurationProperties` подгруппу `deck-templates` с двумя
экземплярами одного класса — `eoc:` и `eom:` (вложенные property-классы — единственный
разрешённый вложенный тип по `00-backend-hard-rules.md`), плюс бин-резолвер, выбирающий
набор по `ReportFlavor`. Существующие ключи остаются как `eoc:`-дефолты → поведение EOC
не меняется, тесты на EOC остаются зелёными.

## 4. Порядок работ

**PR 0 — разметка шаблона (без кода, ~полдня).**
Скопировать EOM-шаблон в Google Slides, проставить токены по таблице раздела 1, собрать
object-id: мастер канала (6), мастера брейкдаунов (13,14,15|16,17,18), id дивайдера
«SLIDES LIBRARY», книги-источники + chartId для aud / aud-seg / dev. Результат — заполненный
`new-template-env.md` рядом с этим файлом.

**PR 1 — report-type-aware конфиг деки.** Раздел 3. Поведение EOC не меняется, EOM пока
продолжает строиться из EOC-шаблона (обе ветки указывают на один id).

**PR 2 — включение EOM-шаблона на существующих токенах.** `eom-slides-template-id` →
новый шаблон; мастер канала = мастер тактики; брейкдаун-мастера; drop «SLIDES LIBRARY»;
`thoughts-master` для EOM пустой. На выходе дека собирается целиком, новые нарративные
слоты пока прочерки. Это первая точка, где результат можно показать.

**PR 3 — новые per-tactic тексты** (`worked` / `window` / `optimise` / `next month`,
`pace status`, `kpi vs goal`, `kpi status`): строки в EOM-книге, `SheetPlaceholderReaderImpl`,
резолверы, секции в `EomPromptBuilder`.

**PR 4 — новые campaign-тексты**: `pacing takeaway`, `performance takeaway`, «what we did»
3×3, «focus next month» 9 + projection, `north_star`, `horizon`.

**PR 5 — трим и полировка**: строки таблиц слайдов 3–4, чипы каналов на дивайдерах,
дедуп geo-слайда, перелинковка чартов aud/aud-seg/dev на новые книги.

## 5. Решения, которые нужны от тебя

1. **Строки таблицы на слайде канала.** В примере метрики разные по каналам (Display:
   Impressions/CTR/Frequency/Reach; Video: Impressions/VCR/Completed views/Frequency;
   CTV: Impressions/VCR/Reach/CPM). Два пути: (а) generic-слоты
   `{{tactic n metric 1..4 label|goal|actual|vs|proj|eoc goal}}` с выбором метрик по KPI-типу;
   (б) нарисовать в мастере超-набор строк готовыми токенами и прочеркивать неприменимые.
   **Рекомендую (б)** — все per-metric токены уже существуют, кода почти нет.
2. **Сколько максимум тактик у EOM-деки** (для высоты таблиц 3–4)? В примере 3.
   Рекомендую заложить 8.
3. **Geo-слайд задублирован** (15 и 16 идентичны) — какой оставить.
4. **«EOC PROJ» / «EOC GOAL»** — подтвердить, что это проекция на конец флайта
   (`{{… proj}}`) и полный план (`{{… plan}}`), а не что-то месячное.
5. **Чарты на слайде канала.** В шаблоне их нет (в отличие от EOC-тактики с 3 чартами).
   Оставляем без чартов?
