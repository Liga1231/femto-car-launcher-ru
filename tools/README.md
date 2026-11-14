# Инструменты и скрипты (Tools & Scripts)

Данный каталог содержит служебные скрипты и утилиты для подготовки офлайн-данных и настройки устройства.

## 1. Генерация офлайн-карт Valhalla (`tools/valhalla/`)
Для создания офлайн-графа навигации любого региона мира:
1. Скачайте PBF-файл региона из OpenStreetMap (например, с [Geofabrik](https://download.geofabrik.de/)).
2. Установите Valhalla CLI: `sudo apt install valhalla-bin` или используйте Docker-контейнер (`valhalla/valhalla:latest`)
3. Запустите генерацию:
   ```bash
   python tools/valhalla/build_tiles.py <region.osm.pbf>
   ```
4. Скопируйте получившийся `valhalla_tiles.tar` на устройство в каталог `/sdcard/Femto/maps/` или `/sdcard/Android/data/io.github.seijikohara.femto/files/maps/`.

## 2. Скрипты настройки Root (`tools/battery/`)
Для устройств с Magisk:
* `set_policy.sh` — автоматическая выдача прав Magisk для лаунчера без показа всплывающего окна запроса root.

## 3. Сборка карт через сервис BBBike (`tools/offline_maps/`)
Для быстрой вырезки любого произвольного города или региона мира через веб-интерфейс BBBike:
* `GUIDE_OFFLINE_MAPS_BBBIKE.md` — пошаговое руководство на русском языке.
* `build_from_bbbike.py` — кроссплатформенный скрипт (Docker / CLI) автоматической упаковки выгрузки в `valhalla_tiles.tar`.
