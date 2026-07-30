# Задача: починить приём ответа в per-section вызовах Claude

## Зачем

На прогоне job 184 (2 тактики, деку собрали 27 вызовами) **36% входных и 20% выходных
токенов ушли на девять попыток, которые все до одной закончились пустыми полями в слайде**.
Это три секции × три попытки:

| секция | тактика | попыток | вход | выход | результат |
|---|---:|---:|---:|---:|---|
| PublisherSection | 1 | 3 | 4 428 | 644 | поля пустые |
| PublisherSection | 2 | 3 | 4 890 | 726 | поля пустые |
| DeviceSection | 2 | 3 | 3 699 | 460 | поля пустые |
| **итого выброшено** | | **9** | **13 017** | **1 830** | |

Всего в джобе было 36 164 вход / 9 073 выход. То есть при починке приёмки та же дека
стоила бы ~23 100 / ~7 200 без потери качества, плюс перестали бы уезжать пустые слайды.

На больших деках это главная статья: пять `*Section`-лейблов вызываются на каждую тактику,
и каждый умножается на три из-за ретраев.

## Что известно из логов (2026-07-27, job 184)

Единственная строка с причиной:

```
13:11:18.032 WARN AnthropicMessagesClient
[claude:PublisherSection] JSON array parse failed; reply began:
[["Modrinth and Raider.IO alone accounted for 28% of video impressions, anchoring reach in
high-dwell gaming environments where our eco-conscious audience over-indexes.", "Five
gaming-strategy destinations—Champion Select, ProBuilds, TFT Comps, Mobalytics, and Agent
Select—collectively held ~25% of delivery, confirming our audience-first targeting surfaced
Clean Habits' consideration-stage viewers…
```

Что отсюда достоверно следует:

1. Модель отдала осмысленный контент — это не отказ и не пустой ответ.
2. Ответ начинается с `[[` — обёрнутый массив. Код это умеет разворачивать, но **позже**,
   уже в `sectionOnce`; до туда не дошло.
3. `stop_reason != "max_tokens"`. Проверка на `max_tokens` в `callJsonArray` стоит **до**
   этого лога и пишет свою строку `truncated by max_tokens` — её в логе нет. Значит ответ
   завершился штатно и всё равно не распарсился.
4. Хвост `…` — это обрезка логгера (`REPLY_SNIPPET_LIMIT = 400`), а не обрыв ответа.

**Чего установить нельзя:** что именно сломало парсер. Дефект за пределами 400 символов.
Восемь из девяти неудачных попыток вообще не записали причину (см. проблему B ниже).

## Три проблемы в коде

Файлы:
- `backend/external-services/src/main/java/com/aidigital/reportconstructor/externalservices/anthropic/AnthropicMessagesClient.java`
- `backend/external-services/src/main/java/com/aidigital/reportconstructor/externalservices/anthropic/RealClaudeClient.java`

### A. `callJsonArray` — единственный путь без ремонта ответа

`AnthropicMessagesClient.java:117` — `callJsonObject(prompt, maxTokens, timeoutSec, label, allowPartial)`.
При `allowPartial = true` он:
- не отбрасывает ответ с `stop_reason == "max_tokens"`;
- прогоняет `repairTruncatedJson` — дописывает закрывающие скобки и спасает то, что успело прийти.

`AnthropicMessagesClient.java:157` — `callJsonArray(prompt, maxTokens, timeoutSec, label)`.
Параметра `allowPartial` **нет вообще**. Жёстко режет по `max_tokens`, ремонта нет,
`parseJsonArray` умеет только два трюка: чистый `readTree` и вырезка от первой `[` до
последней `]`.

При этом `allowPartial = true` передают почти все остальные вызовы: `BatchC` (стр. 593),
`BatchCampaign` (1171), `BatchConclusions` (889), `BatchTacticThoughts` (1138),
`AlignNarrative` (699), вся компрессия (`ClaudeCompressionService.java:71`).
Секционные вызовы — единственные на строгом пути, и они же чаще всех падают.

### B. Восемь из девяти отказов не логируются

`RealClaudeClient.java:375` — `sectionOnce` молча возвращает `List.of()` в трёх случаях:

```java
if (arr == null || !arr.isArray()) return List.of();   // причина уже залогирована выше
if (arr.size() != count) return List.of();             // молча
if (value.isBlank()) return List.of();                 // молча
```

Поэтому в логе одна причина на девять падений. **Пока это не исправлено, любая правка
парсера — угадывание.** Диагностика идёт первой.

### C. Ретрай отправляет ровно тот же промпт

`RealClaudeClient.java:337` — `runSection` в цикле зовёт `sectionOnce` с тем же `prompt`,
без обратной связи о том, что пошло не так. Причина отказа детерминированная → тот же
отказ трижды. Подтверждается консолью: у трёх групп вызовов одинаковый вход
(1476 ×3, 1630 ×3, 1233 ×3) — байт в байт один промпт.

## Порядок работ

### Шаг 1. Диагностика (делать первым, отдельным коммитом)

Цель — узнать настоящую причину, а не гадать.

1. В `sectionOnce` залогировать `WARN` на каждой ветке отказа, с указанием: лейбл, тактика,
   ожидаемый `count`, фактический размер массива, индекс пустого элемента.
2. Поднять `REPLY_SNIPPET_LIMIT` — 400 мало, дефект дальше. Либо сделать лимит настраиваемым
   через `AnthropicProperties`, либо логировать полный текст на `DEBUG` (а `WARN` оставить
   коротким), чтобы можно было включить точечно на Replit без заливки логов.
3. Прогнать генерацию на той же деке и собрать причины.

Только после этого — шаг 2.

### Шаг 2. Выровнять `callJsonArray` с `callJsonObject`

Добавить `allowPartial` в `callJsonArray` и передавать `true` из `sectionOnce`, а внутри —
ремонт оборванного массива по образцу `repairTruncatedJson` (в нём уже корректно
обрабатываются экранирование и стек открытых скобок — переиспользовать, а не писать заново).

Проверить перед этим по логам шага 1, что `max_tokens` действительно встречается. Если нет —
пункт всё равно полезен как страховка, но приоритет ниже, чем у настоящей причины.

### Шаг 3. Гипотезы для проверки на реальном тексте ответа

Проверять только против данных шага 1, не «на всякий случай»:

- **Сырой перевод строки внутри JSON-строки.** Jackson по умолчанию отвергает управляющие
  символы внутри строк. Модель, пишущая многострочные буллеты, попадает сюда. Дешёвая
  проверка, вероятная причина.
- **Проза после массива, содержащая `]`.** Фолбэк `text.substring(first, last + 1)` берёт
  до **последней** `]` в тексте — комментарий модели с квадратной скобкой ломает вырезку.
- **Вложенность глубже одного уровня или обёртка размером не 1.** Разворот в `sectionOnce:381`
  срабатывает только при `arr.size() == 1 && arr.get(0).isArray()`.
- **`count` не совпал.** Если модель стабильно отдаёт не 4 строки — это дефект промпта,
  а не парсера, и чинить надо промпт секции.

### Шаг 4. Сделать ретрай осмысленным

Если после шагов 2–3 отказы останутся: на второй и третьей попытке добавлять к промпту
короткую приписку о том, что именно не устроило в прошлом ответе (не массив / не N строк /
пустой элемент N). Отправлять третий раз идентичный промпт бессмысленно.

Альтернатива, которую стоит померить: `sectionRetries` в
`backend/application/src/main/resources/application.yml:444` (`CLAUDE_SECTION_RETRIES`,
сейчас 2, то есть до трёх отправок). Снижение до 1 сразу срезает треть расхода на секциях,
но повышает долю пустых слайдов — только после того, как причина устранена.

## Критерии приёмки

- В логе у каждого отказа секции есть причина с лейблом и номером тактики.
- На контрольной деке (2 тактики) ни один `*Section` не доходит до
  `produced no usable copy after 3 attempt(s)`.
- `[report] job N claude usage` показывает вход ≲ 25 000 и выход ≲ 8 000 на дека из ~20 слайдов.
- Слайды breakdown приходят с заполненными буллетами, без
  `slide ships with blank bullets` в логе.

## Ограничения проекта

- Правки только в `backend/external-services/.../anthropic/`, продовый пакет
  `com.aidigital.reportconstructor.*`.
- Никаких `private`-методов в бинах — только package-private и шире (`.claude/rules/00-backend-hard-rules.md`).
- JavaDoc на каждом рукописном методе.
- Тесты по стилю `.claude/rules/20-tests.md`; уже есть `RealClaudeClientTest` и тесты
  `AnthropicMessagesClient` — расширять их.
- Перед пушем: `python3 scripts/lib/check-structure-strict.py backend`
  (деплой на Replit валится на статических методах строже, чем checkstyle).
- Сборка: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` перед `mvn`.
