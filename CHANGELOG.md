# Femto Car Launcher - Полный список изменений / Detailed Changelog

---

## 🇷🇺 [RU] Полный список изменений и доработок

### 🚀 Версия 2.1.0 (v2.1.0-ru) — Стабильность, оптимизация энергопотребления и точность навигации
* ⚡ **Оптимизация производительности карточки музыки (0 FPS на паузе):**
  * Устранена постоянная рекомпозиция карточки плеера на частоте 60–120 кадров/с в простое. Бесконечная анимация неонового пульса теперь активна исключительно при воспроизведении, радикально снижая нагрузку на CPU/GPU и расход аккумулятора.
  * Удалена неиспользуемая холостая анимация масштабирования обложки.
* 🧭 **Исправление перехода через север в навигации:**
  * Устранена математическая ошибка в расчёте угла поворота: поворот с курсов 350° на 10° больше не приводит к ложной команде «Крутой поворот налево» вместо плавного правого поворота.
  * Добавлен автоматический аварийный фолбэк с нативного Valhalla на SQLite-движок при отсутствии тайлов или сбоях построения.
* 🛡️ **Устранение утечки системных подписок и краха ViewModel:**
  * Сетевой наблюдатель переведён на изолированный поток с защитой от сбоев прав (`catchAsDefault`), устраняя повторную регистрацию 17 системных слушателей и фоновую утечку ресурсов при выключенном экране.
* 🎨 **Защита от крэша при старте на Android 10/11 (API 29/30):**
  * Добавлен безопасный фолбэк для динамической темы `FemtoTheme` на устройствах без поддержки Material You (API < 31).
* 🇷🇺 **Локализация единиц и исправление опечаток:**
  * Спидометр теперь корректно отображает «км/ч» и «км» в русской локали вместо латинского «km/h».
  * Исправлена польская опечатка `późnego` на `позднего` в настройках записи трека.
  * Скорректированы формулировки форматов времени и единиц («миль/ч», «12-часовой», «24-часовой»).
* 💾 **Оптимизация дискового I/O и защита облачного бэкапа:**
  * Все дисковые проверки доступности офлайн-карт и баз данных вынесены с главного потока UI на `Dispatchers.IO`.
  * Каталоги загруженных шрифтов и баз исключены из Google Auto Backup во избежание переполнения квоты 25 МБ.
  * Устранена нестабильность в системных юнит-тестах сети (`SystemStatusRepositoryTest`).

### 🚀 Версия 2.0.0 (v2.0.0-ru) — Нативный C++ движок навигации Valhalla
* ⚡ **Мгновенный расчёт офлайн-маршрутов (~70-100 мс):** Интегрирована нативная библиотека Valhalla C++ (`io.github.rallista:valhalla-mobile:0.6.1`), заменившая устаревший перебор графа в SQLite. Расчёт маршрутов через весь город и регион происходит мгновенно без зависаний UI и таймаутов.
* 🛑 **Честное соблюдение ПДД и дорожной сети:** Точный учёт одностороннего движения, запретов поворотов, разворотов, кругового движения и сложных дорожных развязок на основе графа OpenStreetMap.
* 🗣️ **Голосовые и визуальные инструкции манёвров:** Локализованные подсказки поворотов на русском языке с указанием улиц, съездов и дистанций.
* 🛠️ **Инструменты и воспроизводимый пайплайн (`tools/`):** В репозиторий добавлены скрипты генерации навигационных тайлов Valhalla (`tools/valhalla/build_tiles.py`) и скрипты защиты батареи (`tools/battery/battery_limiter.sh`).
* 🏷️ **Обновление versionCode и versionName:** Базовый `versionCode` повышен до `20000`, `versionName` до `2.0.0-ru`.

### 🚀 Версия 1.3.0 (v1.3.0-ru) — Гео-поиск ближайших магазинов и заведений
* 📍 **Гео-ранжирование по расстоянию от автомобиля:** При вводе названия магазина или заведения (Магнит, Пятёрочка, КБ, Лента, Аптека и др.) база данных SQLite FTS5 ранжирует результаты по квадрату геодезической дистанции (`ORDER BY dist_sq ASC`), мгновенно выводя самый близкий филиал на первое место в подсказках.
* 📏 **Отображение дистанции в подсказках:** В подзаголовке каждого заведения пишется точное расстояние от текущей позиции автомобиля (`· 180 м`, `· 1.2 км`).
* 🏷️ **Локализация категорий OpenStreetMap:** Служебные теги (`shop:supermarket`, `shop:chemist`, `amenity:pharmacy`, `amenity:fuel` и др.) переведены на понятный русский язык («Супермаркет», «Косметика и бытовая химия», «Аптека», «АЗС»).
* 🔤 **Умный поиск сетей и букв «е» / «ё»:** Автоматическое сопоставление букв («пятерочка» находит все филиалы «Пятёрочки»), поддержка аббревиатур («кб» / «к&б» → «Красное&Белое») и фильтрация служебных стоп-слов («магазин магнит», «аптека фармленд»).
* 🏢 **Сохранение всех близлежащих филиалов:** Исправлена дедупликация в подсказках — теперь отображаются разные филиалы одной сети в порядке удалённости.
* ⚡ **Мгновенный кэш GPS-координат:** Координаты непрерывно кэшируются во ViewModel, исключая задержки поиска при вводе.

### 🚀 Версия 1.2.0 (v1.2.0-ru) — Полноценная офлайн-навигация и поиск
* 🗺️ **Фоновый асинхронный расчёт маршрутов:** Расчёт переведён на фоновые корутины (Dispatchers.Default), лаунчер и карта больше не зависают.
* 🛣️ **Приоритет скоростных артерий (проспект Салавата Юлаева):** Алгоритм роутинга отдаёт высокий приоритет главным городским магистралям.
* 🔍 **Умный офлайн-поиск адресов (SQLite FTS5):** Автоматическое распознавание и раскрытие сокращений улиц («пр-кт», «ул.», «пер.» и др.), приоритет улиц перед организациями в подсказках.
* 🏠 **Точное геокодирование номеров домов:** Исправлены координаты и привязки номеров домов и корпусов, устранены конфликты с устаревшими записями кэша.
* 🚗 **Безопасность вождения (Motion Gating):** Диалоги перезагрузки и выключения блокируются во время движения автомобиля (> 5 км/ч).
* ⚡ **Дебаунс поиска:** 1-секундная задержка и порог от 3 символов для предотвращения лагов интерфейса при наборе.

### 1. 🧭 Встроенная навигация и планирование маршрутов
* **Интерфейс выбора маршрутов на карте:** Встроенное окно расчёта пути с несколькими вариантами маршрутов (время поездки, километраж, альтернативные дороги) и кнопкой **«Поехали»**.
* **История поиска и недавних поездок (Jetpack DataStore):**
  * Постоянное хранение недавних адресов и координат, на которые строился маршрут.
  * Быстрый старт навигации в один клик по любому пункту из списка истории.
  * Умная дедупликация: повторная поездка по тому же адресу поднимает его наверх истории.
  * Индивидуальное удаление отдельных записей и кнопка полной очистки истории.
* **Голосовой и текстовый ввод:** Полноценный поиск адресов с поддержкой голосового ввода через микрофон.
* **Глобальная онлайн-карта и маршруты:** Векторная карта, построение маршрутов и геокодинг работают полностью онлайн через интернет (OpenFreeMap, OSRM, Nominatim) в любой точке мира без привязки к локальным базам данных.

### 2. 🎛️ Системный трей и управление доком
* **Всплывающий системный трей (In-app System Tray):** Вызов панели управления и системного трея прямо по нажатию на статус-бар или кнопку дока — быстрый доступ к статусу сети, громкости, звуку и быстрым настройкам без опускания стандартной шторки Android.
* **Слайдер яркости экрана в доке (Dock Brightness Slider):** Встроенный ползунок регулировки яркости подсветки экрана прямо в доке для мгновенной подстройки одним касанием во время вождения.
* **Кастомизация панели дока:** Возможность настройки порядка и видимости значков, индикаторов связи (Wi-Fi, сотовая связь, Bluetooth, спутники GPS, батарея) и быстрых кнопок.

### 3. ☀️ Сценарии автоматизации день/ночь
* **Астрономический расчёт восхода и заката:** Автоматическое переключение лаунчера и карты между дневным и ночным режимами на основе точного солнечного календаря по GPS-координатам.
* **Исправление бага расписания:** Устранён сбой, из-за которого ночной режим карты не переключался обратно в дневной стиль при восходе солнца.
* **Комплексная адаптация интерфейса:** Автоматическая регулировка контрастности, палитры карты и яркости виджетов при наступлении сумерек и рассвета.

### 4. 🎵 Глубокая интеграция с Яндекс Музыкой (без переключения приложений)
* **Встроенный выбор волн и настроений (Vibe Selector):** Всплывающее модальное окно (BottomSheet) прямо на главном экране для быстрого выбора:
  * «Моя Волна»
  * Настроения («Бодрое», «Спокойное», «Радостное», «Грустное» и др.)
  * Занятия («В дороге», «Тренировка», «Работа» и др.)
  * Музыкальные жанры и персональные потоки.
* **Виджет плеера на главном экране:** Точная синхронизация названия трека, исполнителя, обложки в высоком разрешении, текущей позиции и общей длительности с возможностью перемотки.
* **Кнопки управления:** Быстрый лайк («Любимое»), переключение треков, пауза/воспроизведение.
* **Анимация Ripple Glow:** Пульсирующая неоновая волна вокруг кнопки выбора волны в такт воспроизведению.

### 5. 🎨 Оформление, цветовые схемы и 3D-карта
* **3 новые сбалансированные схемы карты:**
  * `MUTED` (Приглушённая) — мягкий контраст, комфортный для глаз в любое время суток.
  * `WARM` (Тёплая) — тёплые автомобильные оттенки для уютного освещения салона.
  * `SLATE` (Грифельная) — строгий графитовый баланс премиум-класса.
* **Новые гармоничные акценты интерфейса:** `SLATE`, `GRAPHITE`, `WARM`.
* **3D-здания и рельеф:** Отображение трёхмерных зданий и рельефа на WebGL векторной карте с динамической подкраской под активный акцент и тему.

### 6. ⚡ Оптимизация под Android 10/12, MediaTek и автомобильное железо
* **Поддержка Android 10 (API 29) и Android 12 (API 31/32):** Адаптация исходного кода (изначально рассчитанного только на Android 13+) для широкого парка автомобильных магнитол и планшетов.
* **Оптимизация под MediaTek (MTK) и GPU Mali:** Диагностика и устранение просадок FPS, оптимизация многослойного Gaussian Blur (Compose Haze) для плавной отрисовки.
* **Защита от опускания шторки:** Блокировка нежелательного выпадания системной шторки уведомлений Android при запуске лаунчера.
* **Защита аккумулятора (Root Battery Limiter):** Скрипты и политики ограничения уровня заряда батареи для планшетов, постоянно подключённых к питанию автомобиля, предотвращающие вздутие аккумулятора от перегрева.
* **Очистка репозитория:** Удалены устаревшие бинарные файлы (тяжёлые APK по 65 МБ), мусор и неиспользуемые файлы документации.

### 7. 🛡️ Требования к правам суперпользователя (Root / Magisk)
* **Работа без Root:** Все ключевые функции (рабочий стол, карта, навигация, история мест, Яндекс Музыка, виджеты, темы, сценарии) работают на обычных устройствах БЕЗ root-прав.
* **Опциональные функции с Root:** Используются только для привилегированных системных действий:
  - Прямое аппаратное переключение Wi-Fi, Bluetooth, мобильных сетей и GPS из системного трея без открытия системных экранов настроек Android.
  - Кнопки быстрой перезагрузки (Reboot) и выключения устройства (Power Off) прямо из шторки лаунчера.
  - Аппаратная блокировка выпадания стандартной шторки Android (`cmd statusbar collapse`).
  - При отсутствии root-прав эти системные команды безопасно подавляются без ошибок и вылетов.

### 8. 🔋 Оптимизация энергопотребления и спящего режима (Deep Sleep)
* **Мгновенное отключение GPS при выключении экрана:** Добавлен системный слушатель `Intent.ACTION_SCREEN_OFF` и проверка `PowerManager.isInteractive`, моментально снимающий запросы геопозиции при гашении экрана и предотвращающий работу чипа GNSS в спящем режиме.
* **Устранение утечки подписчиков:** Удалены непрерывные фоновые корутины в `HomeViewModel` и `DestinationViewModel`, державшие активными запросы `LocationManager` и `ConnectivityManager` 24/7.
* **Заморозка WebView в режиме ожидания:** Вызовы `pauseTimers()` на `ON_STOP` и `resumeTimers()` на `ON_START` гарантируют полную остановку таймеров Chromium и JS при переходе в спящий режим.

---

## 🇬🇧 [EN] Detailed Changelog and Improvements

### 1. 🧭 In-App Navigation & Route Planning
* **In-Launcher Route Selection Dialog:** Built-in route planning overlay displaying multiple route alternatives (estimated trip time, distance, alternative roads) with a one-tap **"Go / Start"** button directly on the vector map.
* **Persistent Search & Destination History (Jetpack DataStore):**
  * Persistent storage for all recently navigated locations and GPS coordinates.
  * Quick 1-tap navigation launch for any history item.
  * Smart deduplication: navigating again to an existing destination promotes it to the top of the history list.
  * Individual record deletion and bulk history clear button.
* **Voice & Text Input:** Integrated destination search supporting voice transcription via microphone.
* **Global Online Map & Routing:** Live vector map, geocoding, and route calculation work completely online worldwide via OpenFreeMap, OSRM, and Nominatim without local regional database limitations.

### 2. 🎛️ System Tray Bar & Dock Controls
* **In-App System Tray Bar:** Tap status bar or dock button to reveal an in-app system tray with volume control, connectivity toggles, and scenario controls without pulling down the distracting native Android notification shade.
* **Dock Brightness Slider:** Integrated brightness slider directly in the dock for immediate on-the-fly screen backlight adjustment with a single swipe while driving.
* **Customizable Dock:** Configurable button order and visibility for connectivity icons (Wi-Fi, Cellular, Bluetooth, GPS reception, Battery charge) and launcher shortcuts.

### 3. ☀️ Driving Scenarios & Solar Automation
* **Astronomical Sunrise/Sunset Engine:** Automatically transitions launcher and map between daytime and nighttime palettes based on precise solar calculations for current GPS coordinates.
* **Solar Schedule Bug Fix:** Fixed upstream bug where nighttime map styling failed to switch back to daytime styling after sunrise.
* **Full UI Day/Night Sync:** Seamless automatic adaptation of map colors, card contrast, and screen backlight when transitioning through dawn and dusk.

### 4. 🎵 Deep Yandex Music Integration (No App Switching)
* **In-Launcher Vibe & Mood Selector (BottomSheet):** Instant pop-up sheet right on the home screen allowing users to pick:
  * "My Vibe" (Моя Волна)
  * Mood filters (Energetic, Calm, Cheerful, Melancholic, etc.)
  * Activity filters (Driving, Workout, Focus, etc.)
  * Music genres and personalized flows.
* **Home Screen Media Widget:** Real-time synchronization of track name, artist, high-res album cover, playback progress, and seekbar.
* **In-Place Transport Controls:** Favorite / Like button, next/previous track, play/pause.
* **Ripple Glow Animation:** Pulsating neon glow ring effect around the vibe button synced with music playback.

### 5. 🎨 Custom Map Schemes, Visual Themes & 3D Vector Map
* **3 New Balanced Map Color Schemes:**
  * `MUTED`: Soft contrast engineered to reduce eye strain during prolonged driving.
  * `WARM`: Gentle amber/warm tones ideal for cozy car interior lighting.
  * `SLATE`: Elegant graphite/slate palette with high readability.
* **New Cohesive Accent Colors:** `SLATE`, `GRAPHITE`, `WARM`.
* **3D Buildings & Terrain Relief:** Native 3D buildings and elevation styling on WebGL vector map dynamically matching the chosen theme and accent.

### 6. ⚡ Android 10/12, MediaTek & Hardware Optimizations
* **Android 10 (API 29) & Android 12 (API 31/32) Compatibility:** Backported code (originally Android 13+ only) for seamless operation on aftermarket head units and car-mounted tablets.
* **MediaTek (MTK) & Mali GPU Profiling:** Profiling and eliminating UI frame drops by tuning multi-pass Gaussian blur (Compose Haze) for fluid 60 FPS rendering.
* **System Shade Guard:** Blocks accidental native Android notification shade expansion on launcher start.
* **Battery Longevity Management (Root Battery Limiter):** Shell scripts and policies to enforce battery charge thresholds, preventing battery swelling on tablets permanently connected to 12V/ACC power.
* **Repository Hygiene:** Purged obsolete 65MB binaries, temporary build artifacts, and clutter.

### 7. 🛡️ Superuser Permissions (Root / Magisk) Policy
* **Operation Without Root:** All main launcher features (home screen, map, in-app navigation, search history, Yandex Music, widgets, themes, and scenarios) work out-of-the-box on regular unrooted devices.
* **Optional Root Features:** Used strictly for privileged system-level automation:
  - Direct hardware switches for Wi-Fi, Bluetooth, Mobile Data, and GPS from the quick settings panel without opening Android settings menus.
  - Direct Reboot and Power Off actions inside the launcher tray.
  - Hardware lockdown of the native notification shade (`cmd statusbar collapse`).
  - On non-rooted devices, these privileged commands gracefully fail without errors or application crashes.

### 8. 🔋 Power Management & Standby Optimization (Deep Sleep)
* **Immediate GPS teardown on screen off:** Integrated `Intent.ACTION_SCREEN_OFF` / `PowerManager.isInteractive` listener to immediately disconnect platform location listeners and power down the GNSS hardware when display turns off.
* **Subscriber flow leaks resolved:** Eliminated immortal background coroutines in `HomeViewModel` and `DestinationViewModel` that previously held permanent active requests to `LocationManager` and `ConnectivityManager`.
* **WebView timer freeze in standby:** Added `pauseTimers()` on `ON_STOP` and `resumeTimers()` on `ON_START` to completely suspend Chromium JS animation frames and intervals while asleep.

### 9. 🗺️ Hybrid Offline Vector Map & Local Search (MBTiles & SQLite)
* **Online-by-default global operation:** Operates seamlessly via global online APIs (OpenFreeMap, OSRM, Nominatim) across the world out of the box without requiring local map files.
* **Automatic offline map detection:** When an `.mbtiles` vector archive (such as `bashkortostan.mbtiles` or any `.mbtiles` in `/sdcard/Femto/maps/` or app external storage) is detected, tiles are served locally with zero cellular data consumption.
* **Offline routing & geocoding:** When disconnected from the internet, transparently falls back to local `search.db` and the built-in road graph for offline turn-by-turn routing and address search.
* **Bundled fonts & sprites:** Noto Sans SDF glyphs (with full Cyrillic & Latin support) and OpenFreeMap sprites are bundled for complete offline map rendering of street labels, building numbers, and map symbols.


### 10. 🧭 Heading-Up Map Rotation & Enlarged Touch Controls
* **Dynamic Travel Bearing Calculation:** When the hardware GNSS provider does not report Doppler bearing, the launcher automatically computes travel azimuth between consecutive GPS fixes (displacement $\ge 1.5$ m). The map smoothly rotates in the direction of travel with heading-up camera follow.
* **Enlarged Touch Controls for Smartphones:** Scaled map controls (+ / - zoom, recenter, and compass) to 56×56 dp with 28 dp icons for comfortable one-tap interaction on dashboard-mounted smartphones while driving.

