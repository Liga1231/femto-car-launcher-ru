# Privacy Policy / Политика конфиденциальности

*Last updated: November 2026 / Последнее обновление: ноябрь 2026 г.*

## English

**Femto Car Launcher RU** is designed around a **local-first, privacy-respecting** principle. The application does not collect, sell, or transmit personal data to tracking or advertising networks.

### 1. Location Data & Trip Logging
- **Usage**: Used to render current vehicle position on the map, calculate speedometer readout, determine day/night solar scenario transitions, record local track logs, and provide turn-by-turn navigation.
- **Handling**: Location coordinates and track logs are processed and stored strictly on the local device. Track log databases and SQLite journals are explicitly excluded from Android Auto Backup and device-to-device cloud transfer. For online map rendering and routing, standard HTTPS requests are made to OpenFreeMap / OpenStreetMap and OSRM servers. For offline mode (Valhalla / SQLite), calculations are 100% on-device. Coordinates are not sent to any telemetry or analytics backend.

### 2. Yandex Music & Media Control
- **Usage**: The launcher displays current track info, playback controls, and Wave / Mood station selectors.
- **Handling**: Communication occurs strictly via Android's official `MediaBrowserService` and `MediaSession` APIs, plus the system Notification Listener. The launcher never requests, reads, or stores your account credentials, passwords, or authentication tokens.

### 3. Hardware & Root Privileges
- **Usage**: Optional superuser (root) access may be granted by the user to enable privileged hardware toggles in the Quick Settings panel (system backlight brightness via Android settings provider, immediate reboot, and device power off).
- **Handling**: Root commands run exclusively on the local shell without external communication.

### 4. Storage & Offline Maps
- Offline map vector tiles (`.mbtiles`) and routing graphs (`valhalla_tiles.tar` / `search.db`) are stored in the device's storage (`/sdcard/Femto/maps/` or app-specific storage `/sdcard/Android/data/io.github.seijikohara.femto/files/maps/`). The app accesses these files purely to provide offline capability. Downloaded Google Fonts cache is excluded from system cloud backup to preserve backup quotas.

### 5. Third-Party Services
- **Weather**: Forecast data is fetched from MET Norway (Locationforecast API).
- **Online Maps & Routing**: Map tiles and fallback online routing are fetched from OpenFreeMap and public OSRM endpoints.
- **Google Maps Platform (Optional)**: If the user provides a personal Google Maps API key in Settings, map tiles are fetched directly from Google Maps Platform.
- **Google Fonts (Optional)**: If custom web fonts are selected in typography settings, font glyphs and metadata are fetched from Google Fonts CDN (`fonts.google.com` / `fonts.gstatic.com`).

---

## Русский

**Femto Car Launcher RU** построен по принципу **local-first** («всё на устройстве») и максимального уважения к приватности. Приложение не собирает, не продаёт и не передаёт ваши персональные данные третьим лицам или рекламным сетям.

### 1. Данные геолокации (GPS) и история поездок
- **Использование**: Отображение положения автомобиля на карте, спидометр, автоматическое переключение дневной/ночной темы по координатам солнца, локальная запись трека и пошаговая навигация.
- **Обработка**: Координаты и треки поездок сохраняются строго локально на устройстве. Базы данных треков и их журналы явно исключены из облачного резервного копирования Google Auto Backup и переноса между устройствами. В оффлайн-режиме (движок Valhalla / база SQLite) расчёт маршрутов и отображение карты производятся на 100% автономно. В онлайн-режиме запросы направляются к публичным серверам OpenFreeMap / OpenStreetMap и OSRM по безопасному протоколу HTTPS.

### 2. Яндекс Музыка и управление мультимедиа
- **Использование**: Отображение текущего трека, управление воспроизведением и переключение станций «Моей Волны».
- **Обработка**: Взаимодействие происходит исключительно через стандартные системные интерфейсы Android (`MediaBrowserService`, `MediaSession`, `NotificationListenerService`). Приложение никогда не запрашивает и не хранит ваши пароли, токены или данные аккаунта Яндекс.

### 3. Системные и Root-привилегии
- **Использование**: Опциональный root-доступ (su) используется исключительно для аппаратного управления в шторке быстрых настроек (прямая регулировка подсветки экрана через системный провайдер настроек, перезагрузка и выключение головного устройства).
- **Обработка**: Все root-команды выполняются строго локально.

### 4. Оффлайн-карты и память
- Файлы оффлайн-карт (`.mbtiles`) и архивы маршрутизации (`valhalla_tiles.tar` / `search.db`) хранятся в памяти устройства (`/sdcard/Femto/maps/` или каталог приложения `/sdcard/Android/data/io.github.seijikohara.femto/files/maps/`). Приложение читает эти файлы исключительно для автономной работы без интернета. Кэш шрифтов исключён из резервной копии, чтобы не переполнять квоту бэкапа.

### 5. Сторонние сервисы
- **Погода**: Прогноз погоды загружается с общедоступного API Метеорологического института Норвегии (MET Norway).
- **Онлайн-карты и маршруты**: Тайлы карты и онлайн-маршрутизация используют открытые сервисы OpenFreeMap / OSM и OSRM.
- **Google Maps Platform (опционально)**: При указании пользователем персонального API-ключа карта загружается с официальных серверов Google Maps Platform.
- **Google Fonts (опционально)**: При выборе сторонних шрифтов в настройках гарнитуры загружаются с CDN Google Fonts (`fonts.google.com` / `fonts.gstatic.com`).
