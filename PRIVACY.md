# Privacy Policy / Политика конфиденциальности

*Last updated: November 2026 / Последнее обновление: ноябрь 2026 г.*

## English

**Femto Car Launcher RU** is designed around a **local-first, privacy-respecting** principle. The application does not collect, sell, or transmit personal data to tracking or advertising networks.

### 1. Location Data
- **Usage**: Used to render current vehicle position on the map, calculate speedometer readout, determine day/night solar scenario transitions, and provide turn-by-turn navigation.
- **Handling**: Location coordinates are processed locally on the device. For online map rendering and routing, standard HTTPS requests are made to OpenFreeMap / OpenStreetMap and OSRM servers. For offline mode (Valhalla / SQLite), calculations are 100% on-device. Coordinates are not sent to any telemetry or analytics backend.

### 2. Yandex Music & Media Control
- **Usage**: The launcher displays current track info, playback controls, and Wave / Mood station selectors.
- **Handling**: Communication occurs strictly via Android's official `MediaBrowserService` and `MediaSession` APIs, plus the system Notification Listener. The launcher never requests, reads, or stores your account credentials, passwords, or authentication tokens.

### 3. Hardware & Root Privileges
- **Usage**: Optional superuser (root) access may be granted by the user to enable privileged hardware toggles in the Quick Settings panel (system backlight sysfs, immediate reboot, and device power off).
- **Handling**: Root commands run exclusively on the local shell without external communication.

### 4. Storage & Offline Maps
- Offline map vector tiles (`.pmtiles`) and routing graphs are stored in the device's storage (`/sdcard/maps` or app-specific storage). The app accesses these files purely to provide offline capability.

### 5. Third-Party Services
- **Weather**: Forecast data is fetched from MET Norway (Locationforecast API).
- **Online Maps & Routing**: Map tiles and fallback online routing are fetched from OpenFreeMap and public OSRM endpoints.

---

## Русский

**Femto Car Launcher RU** построен по принципу **local-first** («всё на устройстве») и максимального уважения к приватности. Приложение не собирает, не продаёт и не передаёт ваши персональные данные третьим лицам или рекламным сетям.

### 1. Данные геолокации (GPS)
- **Использование**: Отображение положения автомобиля на карте, спидометр, автоматическое переключение дневной/ночной темы по координатам солнца и пошаговая навигация.
- **Обработка**: Координаты обрабатываются локально. В оффлайн-режиме (движок Valhalla / база SQLite) расчёт маршрутов и отображение карты производятся на 100% автономно на устройстве. В онлайн-режиме запросы направляются к публичным серверам OpenFreeMap / OpenStreetMap и OSRM по безопасному протоколу HTTPS.

### 2. Яндекс Музыка и управление мультимедиа
- **Использование**: Отображение текущего трека, управление воспроизведением и переключение станций «Моей Волны».
- **Обработка**: Взаимодействие происходит исключительно через стандартные системные интерфейсы Android (`MediaBrowserService`, `MediaSession`, `NotificationListenerService`). Приложение никогда не запрашивает и не хранит ваши пароли, токены или данные аккаунта Яндекс.

### 3. Системные и Root-привилегии
- **Использование**: Опциональный root-доступ (su) используется исключительно для аппаратного управления в шторке быстрых настроек (прямая регулировка подсветки экрана через sysfs, перезагрузка и выключение головного устройства).
- **Обработка**: Все root-команды выполняются строго локально.

### 4. Оффлайн-карты и память
- Файлы оффлайн-карт (`.pmtiles`) и базы маршрутизации хранятся в памяти устройства (`/sdcard/maps` или каталог приложения). Приложение читает эти файлы исключительно для автономной работы без интернета.

### 5. Сторонние сервисы
- **Погода**: Прогноз погоды загружается с общедоступного API Метеорологического института Норвегии (MET Norway).
- **Онлайн-карты и маршруты**: Тайлы карты и онлайн-маршрутизация используют открытые сервисы OpenFreeMap / OSM и OSRM.
