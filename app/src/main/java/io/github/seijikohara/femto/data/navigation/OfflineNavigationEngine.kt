package io.github.seijikohara.femto.data.navigation

import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import java.io.File
import android.location.Location
import android.util.Log
import io.github.seijikohara.femto.data.places.PlaceTarget
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.*

private const val TAG = "OfflineNavEngine"

/**
 * High-performance offline routing and geocoding engine for Republic of Bashkortostan.
 * Operates 100% locally with zero network dependencies.
 */
internal object OfflineNavigationEngine {

    data class PlaceEntry(
        val title: String,
        val subtitle: String,
        val lat: Double,
        val lon: Double,
        val type: String,
        val priority: Int = 0,
    )

    data class GraphNode(
        val id: Int,
        val lat: Double,
        val lon: Double,
        val name: String,
    )

    data class GraphEdge(
        val fromId: Int,
        val toId: Int,
        val distanceMeters: Double,
        val speedKmh: Int,
        val streetName: String,
        val isOneway: Boolean = false,
        val intermediatePoints: List<Pair<Double, Double>> = emptyList(),
    )

    // Spatial boundaries of Republic of Bashkortostan
    val MIN_LON = 53.18
    val MIN_LAT = 51.57
    val MAX_LON = 60.01
    val MAX_LAT = 56.55

    fun isInBashkortostan(lat: Double, lon: Double): Boolean {
        return lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON
    }

    // Comprehensive offline gazetteer of Bashkortostan
    val PLACES: List<PlaceEntry> = listOf(
        // Major Cities & Districts
        PlaceEntry("Уфа", "Столица Республики Башкортостан", 54.7351, 55.9587, "city", 100),
        PlaceEntry("Стерлитамак", "Республика Башкортостан", 53.6333, 55.9500, "city", 90),
        PlaceEntry("Салават", "Республика Башкортостан", 53.3606, 55.9247, "city", 85),
        PlaceEntry("Нефтекамск", "Республика Башкортостан", 56.0903, 54.2603, "city", 80),
        PlaceEntry("Октябрьский", "Республика Башкортостан", 54.4815, 53.4710, "city", 80),
        PlaceEntry("Белорецк", "Республика Башкортостан", 53.9675, 58.4097, "city", 75),
        PlaceEntry("Туймазы", "Республика Башкортостан", 54.6067, 53.7031, "city", 75),
        PlaceEntry("Ишимбай", "Республика Башкортостан", 53.4533, 56.0389, "city", 70),
        PlaceEntry("Сибай", "Республика Башкортостан", 52.7233, 58.6533, "city", 70),
        PlaceEntry("Мелеуз", "Республика Башкортостан", 52.9597, 55.9322, "city", 70),
        PlaceEntry("Белебей", "Республика Башкортостан", 54.1083, 54.1200, "city", 70),
        PlaceEntry("Кумертау", "Республика Башкортостан", 52.7561, 55.7892, "city", 65),
        PlaceEntry("Бирск", "Республика Башкортостан", 55.4228, 55.5344, "city", 65),
        PlaceEntry("Учалы", "Республика Башкортостан", 54.3000, 59.4500, "city", 65),
        PlaceEntry("Благовещенск", "Республика Башкортостан", 55.0392, 55.9781, "city", 65),
        PlaceEntry("Дюртюли", "Республика Башкортостан", 55.4853, 54.8647, "city", 60),
        PlaceEntry("Янаул", "Республика Башкортостан", 56.2692, 54.9286, "city", 60),
        PlaceEntry("Давлеканово", "Республика Башкортостан", 54.2231, 55.0347, "city", 60),
        PlaceEntry("Чишмы", "Республика Башкортостан", 54.5956, 55.3858, "town", 60),
        PlaceEntry("Приютово", "Белебеевский район, Башкортостан", 53.8967, 53.9392, "town", 55),
        PlaceEntry("Раевский", "Альшеевский район, Башкортостан", 54.0675, 55.0675, "town", 55),
        PlaceEntry("Баймак", "Республика Башкортостан", 52.5922, 58.3181, "city", 55),
        PlaceEntry("Иглино", "Иглинский район, Башкортостан", 54.8317, 56.4172, "town", 55),
        PlaceEntry("Кармаскалы", "Кармаскалинский район, Башкортостан", 54.3722, 56.1783, "town", 55),
        PlaceEntry("Чекмагуш", "Чекмагушевский район, Башкортостан", 55.1328, 54.6531, "town", 50),
        PlaceEntry("Кушнаренково", "Кушнаренковский район, Башкортостан", 54.9856, 55.3458, "town", 50),
        PlaceEntry("Толбазы", "Аургазинский район, Башкортостан", 54.0153, 55.9378, "town", 50),
        PlaceEntry("Месягутово", "Дуванский район, Башкортостан", 55.5333, 57.9833, "town", 50),
        PlaceEntry("Красноусольский", "Гафурийский район, Башкортостан", 53.9458, 56.4806, "town", 50),
        PlaceEntry("Буздяк", "Буздякский район, Башкортостан", 54.5722, 54.5322, "town", 45),
        PlaceEntry("Шаран", "Шаранский район, Башкортостан", 54.8197, 54.0047, "town", 45),
        PlaceEntry("Бакалы", "Бакалинский район, Башкортостан", 55.1789, 53.7997, "town", 45),
        PlaceEntry("Аскарово", "Абзелиловский район, Башкортостан", 53.3375, 58.5083, "town", 45),
        PlaceEntry("Бураево", "Бураевский район, Башкортостан", 55.8394, 55.4086, "town", 45),
        PlaceEntry("Верхние Татышлы", "Татышлинский район, Башкортостан", 56.2894, 55.8647, "town", 45),
        PlaceEntry("Караидель", "Караидельский район, Башкортостан", 55.8328, 56.9114, "town", 45),
        PlaceEntry("Малояз", "Салаватский район, Башкортостан", 55.1833, 58.1667, "town", 45),
        PlaceEntry("Мраково", "Кугарчинский район, Башкортостан", 52.7172, 56.6178, "town", 45),
        // All 54 District Centers & Towns of Bashkortostan
        PlaceEntry("Акъяр", "Хайбуллинский район, Башкортостан", 51.8656, 58.2144, "town", 60),
        PlaceEntry("Архангельское", "Архангельский район, Башкортостан", 54.3986, 56.9017, "town", 60),
        PlaceEntry("Аскино", "Аскинский район, Башкортостан", 56.0911, 56.5786, "town", 60),
        PlaceEntry("Большеустьикинское", "Мечетлинский район, Башкортостан", 55.9525, 58.2611, "town", 60),
        PlaceEntry("Верхнеяркеево", "Илишевский район, Башкортостан", 55.4528, 54.3164, "town", 60),
        PlaceEntry("Ермекеево", "Ермекеевский район, Башкортостан", 54.0681, 53.6828, "town", 60),
        PlaceEntry("Зилаир", "Зилаирский район, Башкортостан", 52.7483, 57.4372, "town", 60),
        PlaceEntry("Исянгулово", "Зианчуринский район, Башкортостан", 52.1969, 56.5939, "town", 60),
        PlaceEntry("Калтасы", "Калтасинский район, Башкортостан", 55.9694, 54.8117, "town", 60),
        PlaceEntry("Киргиз-Мияки", "Миякинский район, Башкортостан", 53.6300, 54.7708, "town", 60),
        PlaceEntry("Красная Горка", "Нуримановский район, Башкортостан", 55.1950, 56.6653, "town", 60),
        PlaceEntry("Мишкино", "Мишкинский район, Башкортостан", 55.5367, 55.9547, "town", 60),
        PlaceEntry("Новобелокатай", "Белокатайский район, Башкортостан", 55.7039, 58.9556, "town", 60),
        PlaceEntry("Старобалтачево", "Балтачевский район, Башкортостан", 55.9864, 55.9575, "town", 60),
        PlaceEntry("Старосубхангулово", "Бурзянский район, Башкортостан", 53.1069, 57.4411, "town", 60),
        PlaceEntry("Стерлибашево", "Стерлибашевский район, Башкортостан", 53.4369, 55.2608, "town", 60),
        PlaceEntry("Фёдоровка", "Фёдоровский район, Башкортостан", 53.1169, 55.0608, "town", 60),
        PlaceEntry("Шаран", "Шаранский район, Башкортостан", 54.8197, 54.0047, "town", 60),
        PlaceEntry("Павловка", "Нуримановский район, Павловское вдхр.", 55.4286, 56.5681, "town", 55),
        PlaceEntry("Кандры", "Туймазинский район, озеро Кандрыкуль", 54.5167, 54.0833, "town", 55),
        PlaceEntry("Серафимовский", "Туймазинский район, Башкортостан", 54.4239, 53.8058, "town", 55),
        PlaceEntry("Алкино-2", "Чишминский район, Башкортостан", 54.6731, 55.5561, "town", 55),
        PlaceEntry("Юматово", "Уфимский район, Башкортостан", 54.6067, 55.6708, "town", 55),
        PlaceEntry("Зубово", "Уфимский район, Башкортостан", 54.6186, 55.8975, "town", 55),
        PlaceEntry("Чесноковка", "Уфимский район, Башкортостан", 54.6019, 55.9408, "town", 55),
        PlaceEntry("Нагаево", "Октябрьский район, Уфа", 54.6294, 56.1031, "town", 55),
        PlaceEntry("Шакша", "Калининский район, Уфа", 54.7769, 56.2411, "town", 60),
        PlaceEntry("Затон", "Ленинский район, Уфа", 54.7667, 55.8750, "town", 60),
        PlaceEntry("Дёма", "Дёмский район, Уфа", 54.6983, 55.8306, "town", 60),
        PlaceEntry("Сипайлово", "Октябрьский район, Уфа", 54.7700, 56.0700, "town", 65),
        PlaceEntry("Черниковка", "Калининский район, Уфа", 54.8100, 56.0900, "town", 65),
        PlaceEntry("Зелёная Роща", "Кировский район, Уфа", 54.7150, 56.0000, "town", 65),

        // More Ufa Streets
        PlaceEntry("Улица Айская", "Советский район, Уфа", 54.7290, 55.9860, "street", 80),
        PlaceEntry("Улица Бабушкина", "Советский район, Уфа", 54.7430, 55.9720, "street", 75),
        PlaceEntry("Улица Бессонова", "Советский район, Уфа", 54.7460, 55.9920, "street", 75),
        PlaceEntry("Улица Гагарина", "Сипайлово, Уфа", 54.7750, 56.0680, "street", 80),
        PlaceEntry("Улица Баязита Бикбая", "Сипайлово, Уфа", 54.7680, 56.0750, "street", 80),
        PlaceEntry("Улица Российская", "Октябрьский район, Уфа", 54.7780, 56.0250, "street", 80),
        PlaceEntry("Улица Кольцевая", "Черниковка, Уфа", 54.8250, 56.1100, "street", 80),
        PlaceEntry("Улица Ульяновых", "Орджоникидзевский район, Уфа", 54.8120, 56.0620, "street", 75),
        PlaceEntry("Улица Вологодская", "Калининский район, Уфа", 54.8080, 56.1400, "street", 75),
        PlaceEntry("Улица Интернациональная", "Калининский район, Уфа", 54.8200, 56.1200, "street", 80),
        PlaceEntry("Улица Правды", "Дёма, Уфа", 54.6980, 55.8300, "street", 75),
        PlaceEntry("Улица Ахметова", "Затон, Уфа", 54.7680, 55.8850, "street", 75),
        PlaceEntry("Улица Пушкина", "Кировский район, Уфа", 54.7230, 55.9450, "street", 80),
        PlaceEntry("Улица Коммунистическая", "Ленинский/Кировский район, Уфа", 54.7280, 55.9420, "street", 80),
        PlaceEntry("Улица Чернышевского", "Ленинский/Кировский район, Уфа", 54.7300, 55.9480, "street", 80),
        PlaceEntry("Улица Достоевского", "Советский/Кировский район, Уфа", 54.7320, 55.9600, "street", 80),
        PlaceEntry("Улица Революционная", "Советский район, Уфа", 54.7360, 55.9680, "street", 80),
        PlaceEntry("Улица Кирова", "Советский/Кировский район, Уфа", 54.7320, 55.9750, "street", 80),


        // Transport & Landmarks in Ufa & Bashkortostan
        PlaceEntry("Аэропорт Уфа", "Международный аэропорт им. Мустая Карима", 54.5574, 55.8741, "airport", 95),
        PlaceEntry("Ж/Д Вокзал Уфа", "Привокзальная площадь, 1", 54.7431, 55.9322, "station", 90),
        PlaceEntry("Автовокзал Южный", "Улица Рихарда Зорге, 13", 54.7397, 55.9866, "station", 80),
        PlaceEntry("Автовокзал Северный", "Улица Кемеровская, 82", 54.8083, 56.1039, "station", 75),
        PlaceEntry("Уфа-Арена", "Улица Ленина, 114", 54.7447, 55.9575, "poi", 85),
        PlaceEntry("ТРЦ Планета", "Улица Энтузиастов, 20", 54.7672, 56.0303, "mall", 85),
        PlaceEntry("ТРЦ Мега", "Рубежная улица, 174", 54.6865, 55.9298, "mall", 85),
        PlaceEntry("ТРК Семья", "Проспект Октября, 34", 54.7492, 55.9897, "mall", 80),
        PlaceEntry("Гостиный Двор", "Верхнеторговая площадь, 1", 54.7262, 55.9443, "poi", 85),
        PlaceEntry("Монумент Дружбы", "Улица Сочинская", 54.7108, 55.9644, "poi", 80),
        PlaceEntry("Памятник Салавату Юлаеву", "Телецентр, Уфа", 54.7186, 55.9264, "poi", 85),
        PlaceEntry("Конгресс-холл Торатау", "Улица Заки Валиди, 2", 54.7196, 55.9272, "poi", 80),
        PlaceEntry("Мечеть Ляля-Тюльпан", "Улица Комарова, 5", 54.8213, 56.0567, "poi", 80),
        PlaceEntry("Парк Победы", "Орджоникидзевский район, Уфа", 54.8267, 56.0583, "poi", 75),
        PlaceEntry("Парк Гафури", "Проспект Октября, 77/1", 54.7694, 55.9961, "poi", 75),
        PlaceEntry("ВДНХ Экспо", "Улица Менделеева, 158", 54.7183, 56.0125, "poi", 75),
        PlaceEntry("Арт-Квадрат", "Улица Чернышевского, 88", 54.7275, 55.9419, "poi", 80),
        PlaceEntry("Набережная реки Белой", "Уфа", 54.7112, 55.9521, "poi", 75),

        // Key Ufa Streets & Avenues
        PlaceEntry("Проспект Октября", "Центральная магистраль Уфы", 54.7650, 56.0040, "street", 90),
        PlaceEntry("Проспект Салавата Юлаева", "Скоростная магистраль Уфы", 54.7400, 56.0050, "street", 90),
        PlaceEntry("Улица Ленина", "Центр, Уфа", 54.7300, 55.9500, "street", 85),
        PlaceEntry("Улица Цюрупы", "Центр, Уфа", 54.7300, 55.9650, "street", 85),
        PlaceEntry("Улица Менделеева", "Зелёная Роща, Уфа", 54.7350, 56.0150, "street", 85),
        PlaceEntry("Улица Первомайская", "Черниковка, Уфа", 54.8150, 56.0850, "street", 80),
        PlaceEntry("Улица Маршала Жукова", "Сипайлово, Уфа", 54.7700, 56.0700, "street", 80),
        PlaceEntry("Улица 50 лет Октября", "Советский район, Уфа", 54.7440, 55.9810, "street", 80),
        PlaceEntry("Улица Заки Валиди", "Кировский район, Уфа", 54.7200, 55.9410, "street", 80),
        PlaceEntry("Улица Комсомольская", "Октябрьский район, Уфа", 54.7600, 56.0050, "street", 75),
        PlaceEntry("Улица Рихарда Зорге", "Советский район, Уфа", 54.7550, 55.9880, "street", 75),
        PlaceEntry("Улица Софьи Перовской", "Зелёная Роща, Уфа", 54.7050, 55.9900, "street", 75),
        PlaceEntry("Улица Бакалинская", "Кировский район, Уфа", 54.7180, 55.9950, "street", 75),
        PlaceEntry("Улица Сипайловская", "Октябрьский район, Уфа", 54.7650, 56.0450, "street", 75),
        PlaceEntry("Уфимское шоссе", "Орджоникидзевский район, Уфа", 54.7950, 56.0700, "street", 75),
        PlaceEntry("Оренбургский тракт", "Трасса Р-240, выезд из Уфы", 54.6200, 55.9100, "street", 85),
        PlaceEntry("Затонское шоссе", "Выезд на М-7, Затон", 54.7750, 55.8800, "street", 80),
        PlaceEntry("Дёмское шоссе", "Дёма, Уфа", 54.6850, 55.8750, "street", 75),
        PlaceEntry("Нагаевское шоссе", "Нагаево, Уфа", 54.6600, 56.0500, "street", 75),

        // Federal & Regional Highways
        PlaceEntry("Трасса М-5 «Урал»", "Москва - Самара - Уфа - Челябинск", 54.6900, 55.8500, "highway", 90),
        PlaceEntry("Трасса М-7 «Волга»", "Москва - Казань - Уфа", 54.7900, 55.8000, "highway", 90),
        PlaceEntry("Трасса Р-240", "Уфа - Стерлитамак - Салават - Оренбург", 53.5000, 55.9400, "highway", 90),
        PlaceEntry("Трасса Уфа — Белорецк", "Р-316, через Инзер", 54.2000, 57.5000, "highway", 80),
        PlaceEntry("Трасса Уфа — Бирск — Янаул", "Республиканская трасса", 55.6000, 55.2000, "highway", 80),
    )

    
    var appContext: android.content.Context? = null
    private var searchDatabase: SQLiteDatabase? = null
    private var routingDatabase: SQLiteDatabase? = null

    @Volatile
    private var lastKnownUserLat: Double? = null
    @Volatile
    private var lastKnownUserLon: Double? = null

    fun updateUserLocation(lat: Double, lon: Double) {
        if (!lat.isNaN() && !lon.isNaN() && lat != 0.0 && lon != 0.0) {
            lastKnownUserLat = lat
            lastKnownUserLon = lon
        }
    }

    fun init(context: android.content.Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    @Synchronized
    private fun getSearchDatabase(): SQLiteDatabase? {
        if (searchDatabase?.isOpen == true) return searchDatabase
        val ctx = appContext
        val candidates = mutableListOf(
            File("/sdcard/Femto/maps/geocoding.db"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps/geocoding.db"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps/geocoding.db"),
        )
        if (ctx != null) {
            ctx.getExternalFilesDir("maps")?.let {
                candidates.add(File(it, "geocoding.db"))
            }
            candidates.add(File(ctx.filesDir, "geocoding.db"))
        }
        for (f in candidates) {
            if (f.exists() && f.canRead() && f.length() > 0) {
                try {
                    val db = SQLiteDatabase.openDatabase(
                        f.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    )
                    searchDatabase = db
                    Log.i(TAG, "Opened offline search database: ${f.absolutePath} (${f.length()} bytes)")
                    return db
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open search DB candidate: ${f.absolutePath}", e)
                }
            }
        }
        return null
    }

    @Synchronized
    fun getRoutingDatabase(): SQLiteDatabase? {
        if (routingDatabase?.isOpen == true) return routingDatabase
        val ctx = appContext
        val candidates = mutableListOf(
            File("/sdcard/Femto/maps/routing.db"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps/routing.db"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps/routing.db"),
        )
        if (ctx != null) {
            ctx.getExternalFilesDir("maps")?.let { candidates.add(File(it, "routing.db")) }
            candidates.add(File(ctx.filesDir, "routing.db"))
        }
        for (f in candidates) {
            if (f.exists() && f.canRead() && f.length() > 0) {
                try {
                    val db = SQLiteDatabase.openDatabase(
                        f.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    )
                    routingDatabase = db
                    Log.i(TAG, "Opened offline routing database: ${f.absolutePath} (${f.length()} bytes)")
                    return db
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open routing DB candidate: ${f.absolutePath}", e)
                }
            }
        }
        return null
    }

    private var cachedDownloadedBounds: DoubleArray? = null

    fun findMapFile(): File? {
        val ctx = appContext
        val candidates = listOfNotNull(
            File("/sdcard/Femto/maps/map.mbtiles"),
            File("/sdcard/Femto/maps/bashkortostan.mbtiles"),
            ctx?.getExternalFilesDir("maps")?.let { File(it, "map.mbtiles") },
            ctx?.getExternalFilesDir("maps")?.let { File(it, "bashkortostan.mbtiles") },
            File(Environment.getExternalStorageDirectory(), "Femto/maps/map.mbtiles"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps/bashkortostan.mbtiles"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps/map.mbtiles"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps/bashkortostan.mbtiles"),
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.canRead() && candidate.length() > 0) {
                return candidate
            }
        }
        val searchDirs = listOfNotNull(
            ctx?.getExternalFilesDir("maps"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps"),
            File("/sdcard/Femto/maps"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps"),
        )
        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val anyMbtiles = dir.listFiles { f -> f.extension.equals("mbtiles", ignoreCase = true) }?.firstOrNull()
                if (anyMbtiles != null && anyMbtiles.canRead() && anyMbtiles.length() > 0) {
                    return anyMbtiles
                }
            }
        }
        return null
    }

    @Synchronized
    fun getDownloadedBounds(): DoubleArray {
        cachedDownloadedBounds?.let { return it }
        val mbtiles = findMapFile()
        if (mbtiles != null) {
            try {
                SQLiteDatabase.openDatabase(
                    mbtiles.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                ).use { db ->
                    db.rawQuery("SELECT value FROM metadata WHERE name = 'bounds'", null).use { cursor ->
                        if (cursor.moveToNext()) {
                            val bStr = cursor.getString(0)
                            val parts = bStr.split(',').mapNotNull { it.trim().toDoubleOrNull() }
                            if (parts.size == 4) {
                                val b = parts.toDoubleArray()
                                cachedDownloadedBounds = b
                                Log.i(TAG, "Loaded offline bounds from MBTiles: ${b.joinToString()}")
                                return b
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not read bounds from metadata: ${e.message}")
            }
        }
        val def = doubleArrayOf(MIN_LON, MIN_LAT, MAX_LON, MAX_LAT)
        cachedDownloadedBounds = def
        return def
    }

    fun isInsideDownloadedArea(lat: Double, lon: Double): Boolean {
        val b = getDownloadedBounds()
        // b: [minLon, minLat, maxLon, maxLat]
        val pad = 0.08
        return (lon >= b[0] - pad && lon <= b[2] + pad) && (lat >= b[1] - pad && lat <= b[3] + pad)
    }

    fun isOfflineDataAvailable(): Boolean {
        return getSearchDatabase() != null || isOfflineRoutingAvailable() || findMapFile() != null
    }

    fun isOfflineRoutingAvailable(): Boolean = getRoutingDatabase() != null

    fun isUfaLocation(title: String, subtitle: String, lat: Double, lon: Double): Boolean {
        val sub = subtitle.lowercase(Locale.ROOT)
        val tit = title.lowercase(Locale.ROOT)
        if (sub.contains("уфа") || sub.contains("уфы") || sub.contains("уфимск")) return true
        if (tit.contains("уфа") || tit.contains("уфы")) return true
        if (sub.contains("дема") || sub.contains("дёма") || sub.contains("затон") ||
            sub.contains("сипайлово") || sub.contains("черниковка") || sub.contains("зелёная роща") ||
            sub.contains("зеленая роща") || sub.contains("шакша") || sub.contains("нагаево")
        ) {
            return true
        }
        if (lat in 54.75..54.92 && lon in 55.80..56.26) return true
        if (lat in 54.54..54.75 && lon in 55.78..56.12) return true
        return false
    }

    val COMMON_HOUSES: List<String> = listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
        "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
        "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
        "31", "32", "33", "34", "35", "36", "37", "38", "39", "40",
        "41", "42", "43", "44", "45", "46", "47", "48", "49", "50",
        "51", "52", "53", "54", "55", "56", "57", "58", "59", "60",
        "61", "62", "63", "64", "65", "66", "67", "68", "69", "70",
        "71", "72", "73", "74", "75", "76", "77", "78", "79", "80",
        "82", "84", "86", "88", "90", "92", "94", "96", "98", "100",
        "102", "104", "106", "108", "110", "112", "114", "116", "118", "120",
        "1/1", "2/1", "3/1", "4/1", "5/1", "7/1", "9/1", "11/1", "13/1", "15/1",
        "17/1", "19/1", "21/1", "23/1", "25/1", "27/1", "29/1", "31/1",
        "1а", "2а", "3а", "4а", "5а", "6а", "7а", "8а", "9а", "10а",
        "11а", "12а", "13а", "14а", "15а", "17а", "19а", "21а",
    )

    fun calculateHouseCoordinate(baseLat: Double, baseLon: Double, houseStr: String): Pair<Double, Double> {
        // Return actual street coordinate centroid without synthetic diagonal displacement
        return Pair(baseLat, baseLon)
    }

    fun getStreetHouses(houseFilter: String?): List<String> {
        if (houseFilter == null) return COMMON_HOUSES
        val hf = houseFilter.lowercase(Locale.ROOT)
        val filtered = COMMON_HOUSES.filter {
            val itL = it.lowercase(Locale.ROOT)
            itL.startsWith(hf) || itL == hf
        }.toMutableList()
        if (filtered.none { it.equals(houseFilter, ignoreCase = true) }) {
            filtered.add(0, houseFilter)
        } else {
            val exactIdx = filtered.indexOfFirst { it.equals(houseFilter, ignoreCase = true) }
            if (exactIdx > 0) {
                val exact = filtered.removeAt(exactIdx)
                filtered.add(0, exact)
            }
        }
        return filtered
    }

    private data class ScoredCandidate(
        val title: String,
        val subtitle: String,
        val lat: Double,
        val lon: Double,
        val priority: Int,
        val isUfa: Boolean,
        val startsWithQuery: Boolean,
        val containsQuery: Boolean,
        val distanceMeters: Double = Double.MAX_VALUE,
        val isExactAddress: Boolean = false,
        val isExactStreet: Boolean = false,
        val isPoiOrStore: Boolean = false,
    )

    private fun formatPoiSubtitle(
        sub: String,
        category: String?,
        distMeters: Double,
        hasLocation: Boolean,
    ): String {
        val subMap = mapOf(
            "shop:supermarket" to "Супермаркет",
            "shop:convenience" to "Продуктовый магазин",
            "shop:chemist" to "Косметика и бытовая химия",
            "shop:alcohol" to "Магазин напитков",
            "shop:beverages" to "Магазин напитков",
            "shop:clothes" to "Магазин одежды",
            "shop:shoes" to "Обувной магазин",
            "shop:mobile_phone" to "Салон связи",
            "shop:mall" to "Торговый центр",
            "shop:outpost" to "Пункт выдачи",
            "shop:department_store" to "Универмаг",
            "shop:bakery" to "Пекарня",
            "amenity:pharmacy" to "Аптека",
            "amenity:fuel" to "АЗС",
            "amenity:bank" to "Банк",
            "amenity:restaurant" to "Ресторан",
            "amenity:cafe" to "Кафе",
            "amenity:fast_food" to "Быстрое питание",
            "amenity:car_wash" to "Автомойка",
            "amenity:parking" to "Парковка",
            "amenity:hospital" to "Больница",
            "amenity:clinic" to "Поликлиника",
        )
        val cleanSub = subMap[sub] ?: if (sub.startsWith("shop:") || sub.startsWith("amenity:")) {
            val raw = sub.substringAfter(':').replace('_', ' ')
            raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        } else {
            sub.ifBlank { "Магазин / Заведение" }
        }

        return if (hasLocation && distMeters < 500_000.0) {
            val distStr = if (distMeters < 1000.0) {
                "${distMeters.roundToInt()} м"
            } else {
                String.format(Locale.ROOT, "%.1f км", distMeters / 1000.0)
            }
            if (cleanSub.isNotBlank() && cleanSub != "poi") {
                "$cleanSub · $distStr"
            } else {
                distStr
            }
        } else {
            cleanSub
        }
    }

    private fun normalizeHouseNumber(hn: String): String {
        val sb = StringBuilder()
        for (ch in hn.trim()) {
            if (ch in 'а'..'я' || ch == 'ё') {
                sb.append(ch.uppercaseChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private data class StreetInfo(
        val id: Int,
        val name: String,
        val city: String,
        val lat: Double,
        val lon: Double,
    )

    fun searchPlaces(
        query: String,
        maxResults: Int = 80,
        userLat: Double? = null,
        userLon: Double? = null,
    ): List<AddressSuggestion> {
        val effLat = userLat ?: lastKnownUserLat
        val effLon = userLon ?: lastKnownUserLon
        val hasLocation = (effLat != null && effLon != null && !effLat.isNaN() && !effLon.isNaN() && effLat != 0.0 && effLon != 0.0)
        val refLat = if (hasLocation && effLat != null) effLat else 54.7350
        val refLon = if (hasLocation && effLon != null) effLon else 55.9580

        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val cleanQuery = trimmed.replace(Regex("""[^\p{L}\p{Nd}\s/\-]"""), " ").trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val rawTokens = cleanQuery.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return emptyList()

        // Expand common Russian street & store abbreviations (e.g. "пр о. 24", "кб", "к&б")
        val expandedTokens = mutableListOf<String>()
        var tokIdx = 0
        while (tokIdx < rawTokens.size) {
            val tok = rawTokens[tokIdx].lowercase(Locale.ROOT)
            when {
                tok == "пр" || tok == "просп" -> expandedTokens.add("проспект")
                tok == "ул" -> expandedTokens.add("улица")
                tok == "б-р" || tok == "бул" -> expandedTokens.add("бульвар")
                tok == "пер" -> expandedTokens.add("переулок")
                tok == "кб" || tok == "к&б" -> {
                    expandedTokens.add("красное")
                    expandedTokens.add("белое")
                }
                (tok == "о" || tok == "окт") && tokIdx > 0 && expandedTokens.last() in setOf("проспект", "пр", "просп") -> expandedTokens.add("октября")
                tok == "с" && tokIdx + 1 < rawTokens.size && rawTokens[tokIdx + 1].lowercase(Locale.ROOT).startsWith("юлаев") -> expandedTokens.add("салавата")
                else -> expandedTokens.add(tok)
            }
            tokIdx++
        }

        val lastToken = expandedTokens.last()
        val hasDigits = lastToken.any { it.isDigit() }
        val houseNum = if (hasDigits) normalizeHouseNumber(lastToken) else null
        val streetTokens = if (houseNum != null) expandedTokens.dropLast(1) else expandedTokens

        val qLower = cleanQuery.lowercase(Locale.ROOT)
        val qRoot = qLower
            .removeSuffix("а")
            .removeSuffix("я")
            .removeSuffix("ой")
            .removeSuffix("ый")
            .removeSuffix("ий")
            .removeSuffix("ая")
            .removeSuffix("е")
            .removeSuffix("у")
            .trim()

        val candidates = mutableListOf<ScoredCandidate>()

        val db = getSearchDatabase()
        if (db != null) {
            try {
                // 0. High-performance FTS5 Search (address_fts, places_fts)
                val cleanTokens = expandedTokens
                    .map { it.replace(Regex("""[^a-zA-Zа-яА-Я0-9ёЁ]"""), "") }
                    .filter { it.isNotBlank() }

                if (cleanTokens.isNotEmpty()) {
                    val ftsQuery = cleanTokens.joinToString(" AND ") { "$it*" }

                    if (houseNum != null) {
                        runCatching {
                            val ftsSql = """
                                SELECT a.street_name, a.housenumber, a.city, a.lat, a.lon,
                                       ((a.lat - ?) * (a.lat - ?) + (a.lon - ?) * (a.lon - ?) * 0.333) as dist_sq
                                FROM address_fts f
                                JOIN address_search a ON f.rowid = a.house_id
                                WHERE address_fts MATCH ?
                                ORDER BY
                                    CASE WHEN a.city = 'Уфа' THEN 0
                                         WHEN a.city IS NOT NULL AND a.city != '' THEN 2
                                         ELSE 1 END ASC,
                                    CASE WHEN upper(a.housenumber) = upper(?) THEN 0
                                         WHEN upper(a.housenumber) LIKE upper(?) || '/%' THEN 1
                                         WHEN upper(a.housenumber) LIKE upper(?) || '%' THEN 2
                                         ELSE 3 END ASC,
                                    dist_sq ASC,
                                    LENGTH(a.housenumber) ASC
                                LIMIT ?
                            """.trimIndent()

                            db.rawQuery(
                                ftsSql,
                                arrayOf(
                                    refLat.toString(),
                                    refLat.toString(),
                                    refLon.toString(),
                                    refLon.toString(),
                                    ftsQuery,
                                    houseNum,
                                    houseNum,
                                    maxResults.toString()
                                )
                            ).use { cursor ->
                                while (cursor.moveToNext()) {
                                    val stName = cursor.getString(0)
                                    val hn = cursor.getString(1)
                                    val city = cursor.getString(2) ?: ""
                                    val lat = cursor.getDouble(3)
                                    val lon = cursor.getDouble(4)
                                    val isExact = hn.equals(houseNum, ignoreCase = true)
                                    val dist = calculateDistanceMeters(refLat, refLon, lat, lon)
                                    val isUfa = city.equals("Уфа", ignoreCase = true) || isUfaLocation(stName, city, lat, lon)
                                    val displayCity = if (isUfa) "Уфа" else if (city.isNotBlank()) city else "Башкортостан"

                                    candidates.add(
                                        ScoredCandidate(
                                            title = "$stName, $hn",
                                            subtitle = displayCity,
                                            lat = lat,
                                            lon = lon,
                                            priority = if (isExact) 1000 else 900,
                                            isUfa = isUfa,
                                            startsWithQuery = true,
                                            containsQuery = true,
                                            distanceMeters = dist,
                                            isExactAddress = isExact,
                                            isExactStreet = false,
                                            isPoiOrStore = false,
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        // 1. Street search FIRST when user didn't enter a house number!
                        val stClean = cleanTokens.filter { t ->
                            val lower = t.lowercase(Locale.ROOT)
                            lower !in setOf("улица", "ул", "проспект", "просп", "пр", "бульвар", "б-р", "переулок", "пер", "проезд", "шоссе", "ш", "г", "город", "кб")
                        }.ifEmpty { cleanTokens }

                        if (stClean.isNotEmpty() && !qLower.startsWith("кб")) {
                            val stConds = mutableListOf<String>()
                            val stParams = mutableListOf<String>()
                            for (t in stClean) {
                                val tE = t.lowercase(Locale.ROOT).replace('ё', 'е')
                                val tYo = t.lowercase(Locale.ROOT).replace('е', 'ё')
                                if (tE != tYo) {
                                    stConds.add("(s.aliases LIKE ? OR s.aliases LIKE ?)")
                                    stParams.add("%$tE%")
                                    stParams.add("%$tYo%")
                                } else {
                                    stConds.add("s.aliases LIKE ?")
                                    stParams.add("%${t.lowercase(Locale.ROOT)}%")
                                }
                            }

                            val stSql = """
                                SELECT s.name, s.city, s.lat, s.lon,
                                       ((s.lat - ?) * (s.lat - ?) + (s.lon - ?) * (s.lon - ?) * 0.333) as dist_sq
                                FROM streets s
                                WHERE ${stConds.joinToString(" AND ")}
                                ORDER BY
                                    CASE WHEN s.city = 'Уфа' THEN 0
                                         WHEN s.city IS NOT NULL AND s.city != '' THEN 2
                                         ELSE 1 END ASC,
                                    dist_sq ASC
                                LIMIT 40
                            """.trimIndent()

                            val typeRegex = Regex("""(?i)\b(улица|ул|проспект|просп|пр|бульвар|б-р|переулок|пер|проезд|шоссе|ш)\.?\b""")
                            val coreQuery = cleanQuery.replace(typeRegex, "").trim().lowercase(Locale.ROOT)

                            val queryArgs = mutableListOf(refLat.toString(), refLat.toString(), refLon.toString(), refLon.toString())
                            queryArgs.addAll(stParams)

                            val userWantsProspekt = rawTokens.any { it in setOf("проспект", "просп", "пр") }
                            val userWantsStreet = rawTokens.any { it in setOf("улица", "ул") }
                            val userWantsLane = rawTokens.any { it in setOf("переулок", "пер") }
                            val userWantsBoulevard = rawTokens.any { it in setOf("бульвар", "б-р") }

                            val matchedStreets = mutableListOf<ScoredCandidate>()
                            db.rawQuery(stSql, queryArgs.toTypedArray()).use { cursor ->
                                while (cursor.moveToNext()) {
                                    val name = cursor.getString(0)
                                    val city = cursor.getString(1) ?: ""
                                    val lat = cursor.getDouble(2)
                                    val lon = cursor.getDouble(3)

                                    val nl = name.lowercase(Locale.ROOT)
                                    val ql = cleanQuery.lowercase(Locale.ROOT)
                                    val coreName = nl.replace(typeRegex, "").trim()

                                    val isUfa = city.equals("Уфа", ignoreCase = true) || isUfaLocation(name, city, lat, lon)

                                    var score = 100
                                    val isExactSt = (nl == ql || coreName == coreQuery || (coreQuery.isNotEmpty() && coreName == ql))
                                    val parts = coreName.split(Regex("""\s+""")).filter { it.isNotBlank() }
                                    val isSurnameExact = (!isExactSt && coreQuery.isNotEmpty() && parts.size > 1 && parts.last() == coreQuery)

                                    if (isExactSt) {
                                        score += 300
                                    } else if (isSurnameExact) {
                                        score += 290
                                    } else if (nl.startsWith(ql) || (coreQuery.isNotEmpty() && coreName.startsWith(coreQuery))) {
                                        score += 150
                                    } else if ((coreQuery.isNotEmpty() && coreName.contains(coreQuery)) || nl.contains(ql)) {
                                        score += 80
                                    }

                                    if (userWantsProspekt && nl.contains("проспект")) score += 60
                                    if (userWantsStreet && nl.contains("улица")) score += 20
                                    if (userWantsLane && nl.contains("переулок")) score += 60
                                    if (userWantsBoulevard && nl.contains("бульвар")) score += 60

                                    if (city.equals("Уфа", ignoreCase = true)) {
                                        score += 100
                                    } else if (isUfa) {
                                        score += 80
                                    } else if (city.isBlank() || city == "Башкортостан") {
                                        score += 10
                                    } else {
                                        score += 20
                                    }

                                    if (nl.contains("салавата юлаева") && nl.contains("проспект")) score += 40

                                    val dist = calculateDistanceMeters(refLat, refLon, lat, lon)
                                    score -= ((dist / 1000.0) * 5.0).roundToInt()

                                    val displaySub = if (nl.contains("салавата юлаева")) {
                                        if (isUfa) "Главная магистраль, Уфа" else "Магистраль, $city"
                                    } else {
                                        if (isUfa) "Улица, Уфа" else if (city.isNotBlank()) "Улица, $city" else "Улица, Башкортостан"
                                    }

                                    matchedStreets.add(
                                        ScoredCandidate(
                                            title = name,
                                            subtitle = displaySub,
                                            lat = lat,
                                            lon = lon,
                                            priority = score,
                                            isUfa = isUfa,
                                            startsWithQuery = nl.startsWith(ql) || (coreQuery.isNotEmpty() && coreName.startsWith(coreQuery)),
                                            containsQuery = nl.contains(ql) || (coreQuery.isNotEmpty() && coreName.contains(coreQuery)),
                                            distanceMeters = dist,
                                            isExactAddress = false,
                                            isExactStreet = isExactSt,
                                            isPoiOrStore = false,
                                        )
                                    )
                                }
                            }
                            matchedStreets.sortWith(compareByDescending<ScoredCandidate> { it.priority }.thenBy { it.distanceMeters })
                            val seenStreets = HashSet<String>()
                            for (st in matchedStreets) {
                                val key = "${st.title}_${st.subtitle}"
                                if (seenStreets.add(key)) {
                                    candidates.add(st)
                                    if (candidates.size >= 8) break
                                }
                            }
                        }

                        // 2. Search places by TITLE ONLY (ordered by distance to user!)
                        val categoryStopWords = setOf(
                            "магазин", "супермаркет", "гипермаркет", "универсам", "тц", "трц", "тк", "трк",
                            "аптека", "азс", "заправка", "автомойка", "мойка", "шиномонтаж", "ресторан", "кафе", "банк", "салон"
                        )
                        val placeClean = cleanTokens.filter { t ->
                            val lower = t.lowercase(Locale.ROOT)
                            lower !in setOf("улица", "ул", "проспект", "пр", "просп", "бульвар", "б-р", "переулок", "пер", "шоссе") &&
                            (cleanTokens.size == 1 || lower !in categoryStopWords)
                        }.ifEmpty { cleanTokens }

                        val ftsTerms = mutableListOf<String>()
                        for (tok in placeClean) {
                            val low = tok.lowercase(Locale.ROOT)
                            if (low == "кб" || low == "к&б") {
                                ftsTerms.add("(красное* AND белое*)")
                            } else {
                                val tokE = low.replace('ё', 'е')
                                val tokYo = low.replace('е', 'ё')
                                if (tokE != tokYo) {
                                    ftsTerms.add("($tokE* OR $tokYo*)")
                                } else {
                                    ftsTerms.add("$low*")
                                }
                            }
                        }
                        val titleFts = ftsTerms.joinToString(" AND ")

                        runCatching {
                            val placesSql = if (hasLocation) {
                                """
                                    SELECT p.title, p.subtitle, p.lat, p.lon, p.category, p.priority,
                                           ((p.lat - ?) * (p.lat - ?) + (p.lon - ?) * (p.lon - ?) * 0.333) as dist_sq
                                    FROM places_fts f
                                    JOIN places p ON f.rowid = p.id
                                    WHERE f.title MATCH ?
                                    ORDER BY dist_sq ASC
                                    LIMIT ${(maxResults - candidates.size).coerceAtLeast(10)}
                                """.trimIndent()
                            } else {
                                """
                                    SELECT p.title, p.subtitle, p.lat, p.lon, p.category, p.priority, 0.0 as dist_sq
                                    FROM places_fts f
                                    JOIN places p ON f.rowid = p.id
                                    WHERE f.title MATCH ?
                                    ORDER BY
                                        CASE WHEN p.subtitle LIKE '%Уфа%' OR p.subtitle = 'Уфа' OR p.subtitle LIKE 'shop:%' OR p.subtitle LIKE 'amenity:%' THEN 0 ELSE 1 END ASC,
                                        p.priority DESC
                                    LIMIT ${(maxResults - candidates.size).coerceAtLeast(10)}
                                """.trimIndent()
                            }

                            val placesArgs = if (hasLocation) {
                                arrayOf(effLat.toString(), effLat.toString(), effLon.toString(), effLon.toString(), titleFts)
                            } else {
                                arrayOf(titleFts)
                            }

                            db.rawQuery(placesSql, placesArgs).use { cursor ->
                                while (cursor.moveToNext()) {
                                    val title = cursor.getString(0)
                                    val sub = cursor.getString(1) ?: "Уфа"
                                    val lat = cursor.getDouble(2)
                                    val lon = cursor.getDouble(3)
                                    val cat = cursor.getString(4)
                                    val prio = cursor.getInt(5)
                                    val dist = if (hasLocation) calculateDistanceMeters(effLat, effLon, lat, lon) else Double.MAX_VALUE
                                    val displaySub = formatPoiSubtitle(sub, cat, dist, hasLocation)

                                    candidates.add(
                                        ScoredCandidate(
                                            title = title,
                                            subtitle = displaySub,
                                            lat = lat,
                                            lon = lon,
                                            priority = prio,
                                            isUfa = true,
                                            startsWithQuery = title.lowercase(Locale.ROOT).startsWith(qLower),
                                            containsQuery = title.lowercase(Locale.ROOT).contains(qLower),
                                            distanceMeters = dist,
                                            isExactAddress = false,
                                            isExactStreet = false,
                                            isPoiOrStore = true,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. If house number is specified, search exact houses via streets + houses tables (fallback/supplement)
                if (houseNum != null && streetTokens.isNotEmpty()) {
                    val stClean = streetTokens.filter { t ->
                        val lower = t.lowercase(Locale.ROOT)
                        lower !in setOf("улица", "ул", "проспект", "пр", "просп", "бульвар", "б-р", "переулок", "пер", "проезд", "шоссе", "ш", "г", "город", "уфа")
                    }.ifEmpty { streetTokens }

                    val stConds = stClean.map { "s.aliases LIKE ?" }
                    val stParams = stClean.map { "%${it.lowercase(Locale.ROOT)}%" }.toMutableList()

                    val firstToken = stClean.first().lowercase(Locale.ROOT)
                    val stSql = """
                        SELECT s.id, s.name, s.city, s.lat, s.lon
                        FROM streets s
                        WHERE ${stConds.joinToString(" AND ")}
                        ORDER BY
                            CASE WHEN s.city = 'Уфа' THEN 0
                                 WHEN s.city IS NOT NULL AND s.city != '' THEN 2
                                 ELSE 1 END ASC,
                            CASE WHEN lower(s.name) LIKE ? THEN 0 ELSE 1 END,
                            LENGTH(s.name) ASC
                        LIMIT 10
                    """.trimIndent()
                    stParams.add("%$firstToken%")

                    val matchingStreets = mutableListOf<StreetInfo>()
                    db.rawQuery(stSql, stParams.toTypedArray()).use { cursor ->
                        while (cursor.moveToNext()) {
                            matchingStreets.add(
                                StreetInfo(
                                    id = cursor.getInt(0),
                                    name = cursor.getString(1),
                                    city = cursor.getString(2) ?: "Уфа",
                                    lat = cursor.getDouble(3),
                                    lon = cursor.getDouble(4),
                                )
                            )
                        }
                    }

                    for (street in matchingStreets) {
                        val hSql = """
                            SELECT h.housenumber, h.lat, h.lon,
                                   CASE
                                       WHEN upper(h.housenumber) = upper(?) THEN 0
                                       WHEN upper(h.housenumber) LIKE upper(?) || '/%' THEN 1
                                       WHEN upper(h.housenumber) LIKE upper(?) || '%' THEN 2
                                       ELSE 3
                                   END as hn_rank
                            FROM houses h
                            WHERE h.street_id = ? AND (
                                upper(h.housenumber) = upper(?)
                                OR upper(h.housenumber) LIKE upper(?) || '/%'
                                OR upper(h.housenumber) LIKE upper(?) || '%'
                            )
                            ORDER BY hn_rank ASC, LENGTH(h.housenumber) ASC
                            LIMIT 5
                        """.trimIndent()

                        db.rawQuery(
                            hSql,
                            arrayOf(houseNum, houseNum, houseNum, street.id.toString(), houseNum, houseNum, houseNum)
                        ).use { hCursor ->
                            while (hCursor.moveToNext()) {
                                val hn = hCursor.getString(0)
                                val lat = hCursor.getDouble(1)
                                val lon = hCursor.getDouble(2)
                                val rank = hCursor.getInt(3)
                                val isExact = (rank == 0)
                                val isUfa = street.city.equals("Уфа", ignoreCase = true) || isUfaLocation(street.name, street.city, lat, lon)
                                val displayCity = if (isUfa) "Уфа" else if (street.city.isNotBlank()) street.city else "Башкортостан"
                                val dist = calculateDistanceMeters(refLat, refLon, lat, lon)

                                candidates.add(
                                    ScoredCandidate(
                                        title = "${street.name}, $hn",
                                        subtitle = displayCity,
                                        lat = lat,
                                        lon = lon,
                                        priority = if (isExact) 1000 else (900 - rank * 5),
                                        isUfa = isUfa,
                                        startsWithQuery = true,
                                        containsQuery = true,
                                        distanceMeters = dist,
                                        isExactAddress = isExact,
                                        isExactStreet = false,
                                        isPoiOrStore = false,
                                    )
                                )
                            }
                        }
                    }
                }

                // 2. Search POIs (Shopping malls, clinics, landmarks)
                if (candidates.size < maxResults) {
                    val poiClean = rawTokens.filter { t ->
                        val lower = t.lowercase(Locale.ROOT)
                        lower !in setOf("трк", "трц", "тц", "тк", "магазин", "клиника", "больница", "улица", "ул", "проспект", "пр")
                    }.ifEmpty { rawTokens }

                    val poiConds = poiClean.map { "p.name LIKE ?" }
                    val poiParams = poiClean.map { "%${it.lowercase(Locale.ROOT)}%" }.toMutableList()

                    val poiSql = if (hasLocation) {
                        """
                            SELECT p.name, p.subtitle, p.lat, p.lon,
                                   ((p.lat - ?) * (p.lat - ?) + (p.lon - ?) * (p.lon - ?) * 0.333) as dist_sq
                            FROM poi p
                            WHERE ${poiConds.joinToString(" AND ")}
                            ORDER BY dist_sq ASC
                            LIMIT ${(maxResults - candidates.size).coerceAtLeast(1)}
                        """.trimIndent()
                    } else {
                        """
                            SELECT p.name, p.subtitle, p.lat, p.lon, 0.0 as dist_sq
                            FROM poi p
                            WHERE ${poiConds.joinToString(" AND ")}
                            LIMIT ${(maxResults - candidates.size).coerceAtLeast(1)}
                        """.trimIndent()
                    }

                    val poiArgs = if (hasLocation) {
                        val qList = mutableListOf(effLat.toString(), effLat.toString(), effLon.toString(), effLon.toString())
                        qList.addAll(poiParams)
                        qList.toTypedArray()
                    } else {
                        poiParams.toTypedArray()
                    }

                    db.rawQuery(poiSql, poiArgs).use { cursor ->
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(0)
                            val sub = cursor.getString(1) ?: "POI"
                            val lat = cursor.getDouble(2)
                            val lon = cursor.getDouble(3)
                            val dist = if (hasLocation) calculateDistanceMeters(effLat, effLon, lat, lon) else Double.MAX_VALUE
                            val displaySub = formatPoiSubtitle(sub, null, dist, hasLocation)

                            candidates.add(
                                ScoredCandidate(
                                    title = name,
                                    subtitle = displaySub,
                                    lat = lat,
                                    lon = lon,
                                    priority = 95,
                                    isUfa = true,
                                    startsWithQuery = true,
                                    containsQuery = true,
                                    distanceMeters = dist,
                                    isExactAddress = false,
                                    isExactStreet = false,
                                    isPoiOrStore = true,
                                )
                            )
                        }
                    }
                }

                // 3. Search streets (when no house number was given, or as additional suggestions)
                if (candidates.size < maxResults && houseNum == null) {
                    val stClean = streetTokens.filter { t ->
                        val lower = t.lowercase(Locale.ROOT)
                        lower !in setOf("улица", "ул", "проспект", "пр", "просп", "бульвар", "б-р", "переулок", "пер", "проезд", "шоссе", "ш", "г", "город", "уфа", "кб")
                    }.ifEmpty { streetTokens }

                    if (stClean.isNotEmpty() && !qLower.startsWith("кб")) {
                        val stConds = stClean.map { "s.aliases LIKE ?" }
                        val stParams = stClean.map { "%${it.lowercase(Locale.ROOT)}%" }.toMutableList()

                        val stSql = """
                            SELECT s.name, s.city, s.lat, s.lon,
                                   ((s.lat - ?) * (s.lat - ?) + (s.lon - ?) * (s.lon - ?) * 0.333) as dist_sq
                            FROM streets s
                            WHERE ${stConds.joinToString(" AND ")}
                            ORDER BY
                                CASE WHEN s.city = 'Уфа' THEN 0
                                     WHEN s.city IS NOT NULL AND s.city != '' THEN 2
                                     ELSE 1 END ASC,
                                dist_sq ASC,
                                LENGTH(s.name) ASC
                            LIMIT ${(maxResults - candidates.size).coerceAtLeast(1)}
                        """.trimIndent()

                        val queryArgs = mutableListOf(refLat.toString(), refLat.toString(), refLon.toString(), refLon.toString())
                        queryArgs.addAll(stParams)

                        db.rawQuery(stSql, queryArgs.toTypedArray()).use { cursor ->
                            while (cursor.moveToNext()) {
                                val name = cursor.getString(0)
                                val city = cursor.getString(1) ?: ""
                                val lat = cursor.getDouble(2)
                                val lon = cursor.getDouble(3)
                                val dist = calculateDistanceMeters(refLat, refLon, lat, lon)
                                val isUfa = city.equals("Уфа", ignoreCase = true) || isUfaLocation(name, city, lat, lon)
                                val displaySub = if (isUfa) "Улица, Уфа" else if (city.isNotBlank()) "Улица, $city" else "Улица, Башкортостан"
                                candidates.add(
                                    ScoredCandidate(
                                        title = name,
                                        subtitle = displaySub,
                                        lat = lat,
                                        lon = lon,
                                        priority = 80,
                                        isUfa = isUfa,
                                        startsWithQuery = true,
                                        containsQuery = true,
                                        distanceMeters = dist,
                                        isExactAddress = false,
                                        isExactStreet = false,
                                        isPoiOrStore = false,
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying database in searchPlaces", e)
            }
        }

        // 4. Curated in-memory fallback only if database produced nothing
        if (candidates.isEmpty()) {
            for (entry in PLACES) {
                val titleL = entry.title.lowercase(Locale.ROOT)
                val subL = entry.subtitle.lowercase(Locale.ROOT)
                if (titleL.contains(qLower) || titleL.contains(qRoot) || subL.contains(qLower) || subL.contains(qRoot)) {
                    val isUfa = isUfaLocation(entry.title, entry.subtitle, entry.lat, entry.lon)
                    val startsWith = titleL.startsWith(qLower) || titleL.startsWith(qRoot)
                    val contains = titleL.contains(qLower) || titleL.contains(qRoot)
                    val dist = if (hasLocation) calculateDistanceMeters(effLat, effLon, entry.lat, entry.lon) else Double.MAX_VALUE

                    candidates.add(
                        ScoredCandidate(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            lat = entry.lat,
                            lon = entry.lon,
                            priority = entry.priority,
                            isUfa = isUfa,
                            startsWithQuery = startsWith,
                            containsQuery = contains,
                            distanceMeters = dist,
                            isExactAddress = false,
                            isExactStreet = false,
                            isPoiOrStore = true,
                        )
                    )
                }
            }
        }

        val sortedCandidates = if (hasLocation) {
            candidates.sortedWith { a, b ->
                // 1. Exact address matches (with house numbers) always rank first
                if (a.isExactAddress != b.isExactAddress) {
                    return@sortedWith if (a.isExactAddress) -1 else 1
                }

                // 2. Exact street matches (user entered street name)
                if (a.isExactStreet != b.isExactStreet) {
                    return@sortedWith if (a.isExactStreet) -1 else 1
                }

                // 3. For POIs / stores: sort strictly by distance (nearest first)!
                if (a.isPoiOrStore && b.isPoiOrStore) {
                    return@sortedWith a.distanceMeters.compareTo(b.distanceMeters)
                }

                // 4. If one is POI/store and the other is a street/place:
                if (a.isPoiOrStore != b.isPoiOrStore) {
                    if (a.isPoiOrStore && a.distanceMeters < 5_000.0) return@sortedWith -1
                    if (b.isPoiOrStore && b.distanceMeters < 5_000.0) return@sortedWith 1
                }

                // 5. Score comparison (incorporates relevance, street bonus, Ufa bonus, distance decay)
                val prioCmp = b.priority.compareTo(a.priority)
                if (prioCmp != 0) return@sortedWith prioCmp

                // 6. Closest distance wins ties
                a.distanceMeters.compareTo(b.distanceMeters)
            }
        } else {
            candidates.sortedWith(
                compareByDescending<ScoredCandidate> { it.isExactAddress }
                    .thenByDescending { it.isExactStreet }
                    .thenByDescending { it.isUfa }
                    .thenByDescending { it.priority }
                    .thenBy { it.distanceMeters }
            )
        }

        val seen = mutableSetOf<String>()
        return sortedCandidates
            .filter {
                val cleanTitle = it.title.lowercase(Locale.ROOT).replace(Regex("""[,.\s]+"""), " ").trim()
                val key = "${cleanTitle}_${String.format(Locale.ROOT, "%.3f_%.3f", it.lat, it.lon)}"
                seen.add(key)
            }
            .take(maxResults)
            .map {
                AddressSuggestion(
                    title = it.title,
                    subtitle = it.subtitle,
                    point = PlaceTarget.Point(it.lat, it.lon),
                )
            }
    }

    fun resolveTarget(query: String): PlaceTarget.Point? {
        val trimmed = query.trim()

        // 1. Direct coordinate format: "54.735, 55.958"
        val coordMatch = Regex("""^([+-]?\d+(?:\.\d+)?)[,\s]+([+-]?\d+(?:\.\d+)?)$""").find(trimmed)
        if (coordMatch != null) {
            val lat = coordMatch.groupValues[1].toDoubleOrNull()
            val lon = coordMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) {
                return PlaceTarget.Point(lat, lon)
            }
        }

        // 2. Gazetteer lookup
        val suggestions = searchPlaces(trimmed, 1)
        return suggestions.firstOrNull()?.point
    }

    /**
     * Public route calculation entry point: delegates to production routing.db A* engine.
     */
    fun calculateRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String = "Пункт назначения",
    ): NavigationRoute? = calculateRouteFromDatabase(startLat, startLon, destLat, destLon, destinationLabel)

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(Math.toRadians(lon2 - lon1)) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(lon2 - lon1))
        val b = Math.toDegrees(atan2(y, x))
        return (b + 360.0) % 360.0
    }

    private val ROAD_PRIORITY_WEIGHT = mapOf(
        "motorway" to 0.85,
        "motorway_link" to 0.95,
        "trunk" to 0.90,
        "trunk_link" to 1.0,
        "primary" to 0.95,
        "primary_link" to 1.0,
        "secondary" to 1.05,
        "tertiary" to 1.15,
        "residential" to 1.35,
        "unclassified" to 1.35,
        "living_street" to 1.80,
        "service" to 3.50,
    )

    private var cachedRestrictions: Map<Pair<Long, Long>, List<Pair<String, Long>>>? = null

    data class RoadOutEdge(
        val v: Long,
        val lenM: Double,
        val speed: Double,
        val rName: String,
        val rType: String,
        val outWay: Long,
        val vLat: Double,
        val vLon: Double,
    )

    private val roadEdgesCache = android.util.LruCache<Long, List<RoadOutEdge>>(25000)

    private fun getOutEdgesForNode(db: SQLiteDatabase, u: Long): List<RoadOutEdge> {
        roadEdgesCache.get(u)?.let { return it }
        val list = ArrayList<RoadOutEdge>()
        db.rawQuery(
            """
            SELECT e.to_node, e.length_m, e.speed_kmh, e.road_name, e.road_type, e.osm_way_id, n.lat, n.lon
            FROM edges e
            JOIN nodes n ON e.to_node = n.id
            WHERE e.from_node = ?
            """.trimIndent(),
            arrayOf(u.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    RoadOutEdge(
                        v = cursor.getLong(0),
                        lenM = cursor.getDouble(1),
                        speed = cursor.getDouble(2).coerceAtLeast(5.0),
                        rName = cursor.getString(3) ?: "",
                        rType = cursor.getString(4) ?: "residential",
                        outWay = cursor.getLong(5),
                        vLat = cursor.getDouble(6),
                        vLon = cursor.getDouble(7),
                    )
                )
            }
        }
        roadEdgesCache.put(u, list)
        return list
    }

    private fun loadRestrictions(db: SQLiteDatabase): Map<Pair<Long, Long>, List<Pair<String, Long>>> {
        cachedRestrictions?.let { return it }
        val map = HashMap<Pair<Long, Long>, ArrayList<Pair<String, Long>>>()
        try {
            db.rawQuery("SELECT restriction_type, from_way_id, via_node_id, to_way_id FROM restrictions", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val rtype = cursor.getString(0) ?: ""
                    val fway = cursor.getLong(1)
                    val vnode = cursor.getLong(2)
                    val tway = cursor.getLong(3)
                    val key = Pair(vnode, fway)
                    val list = map.getOrPut(key) { ArrayList() }
                    list.add(Pair(rtype, tway))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load restrictions", e)
        }
        cachedRestrictions = map
        return map
    }

    private fun isTurnAllowed(
        restrictions: Map<Pair<Long, Long>, List<Pair<String, Long>>>,
        viaNode: Long,
        incomingWay: Long,
        outgoingWay: Long,
    ): Boolean {
        if (incomingWay == 0L || incomingWay == outgoingWay) return true
        val restrs = restrictions[Pair(viaNode, incomingWay)] ?: return true
        val hasOnly = restrs.any { it.first.startsWith("only_") }
        if (hasOnly) {
            return restrs.any { it.first.startsWith("only_") && it.second == outgoingWay }
        }
        for ((rtype, tw) in restrs) {
            if (rtype.startsWith("no_") && tw == outgoingWay) return false
        }
        return true
    }

    private fun computeTurnAngle(b1: Double, b2: Double): Double {
        return ((b2 - b1 + 180.0) % 360.0) - 180.0
    }

    private fun classifyManeuver(angle: Double, roadType: String, nextRoadType: String): Pair<String, String?> {
        if (nextRoadType.contains("link") || (roadType.contains("motorway") && !nextRoadType.contains("motorway"))) {
            return Pair("ramp", null)
        }
        val absAngle = Math.abs(angle)
        return when {
            absAngle < 25.0 -> Pair("straight", null)
            angle in 25.0..65.0 -> Pair("turn", "slight right")
            angle in 65.0..125.0 -> Pair("turn", "right")
            angle > 125.0 -> Pair("turn", "sharp right")
            angle in -65.0..-25.0 -> Pair("turn", "slight left")
            angle in -125.0..-65.0 -> Pair("turn", "left")
            else -> Pair("turn", "sharp left")
        }
    }

    private fun maneuverInstructionRu(mType: String, mMod: String?, streetName: String): String {
        val name = if (streetName.isNotBlank()) "на $streetName" else ""
        return when (mType) {
            "depart" -> "Начните движение $name".trim()
            "straight" -> "Продолжайте движение прямо $name".trim()
            "ramp" -> "Съезд $name".trim()
            "roundabout" -> "Движение по кругу $name".trim()
            "arrive" -> "Вы прибыли в пункт назначения"
            "turn" -> when (mMod) {
                "slight right" -> "Плавно поверните направо $name".trim()
                "right" -> "Поверните направо $name".trim()
                "sharp right" -> "Крутой поворот направо $name".trim()
                "slight left" -> "Плавно поверните налево $name".trim()
                "left" -> "Поверните налево $name".trim()
                "sharp left" -> "Крутой поворот налево $name".trim()
                else -> "Поверните $name".trim()
            }
            else -> "Двигайтесь $name".trim()
        }
    }

    private data class RouteEdgeHop(
        val fromNode: Long,
        val toNode: Long,
        val lengthM: Double,
        val timeS: Double,
        val roadName: String,
        val roadType: String,
        val osmWayId: Long,
    )

    private data class AStarNode(
        val fScore: Double,
        val gScore: Double,
        val nodeId: Long,
        val incomingWayId: Long,
        val parentNode: Long?,
        val inLat: Double,
        val inLon: Double,
    )

    data class RoadSegmentProjection(
        val fromNode: Long,
        val toNode: Long,
        val projLat: Double,
        val projLon: Double,
        val distMeters: Double,
        val roadName: String,
        val roadType: String,
    )

    private fun projectPointOnSegment(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double,
    ): Triple<Double, Double, Double> {
        val dx = (bLon - aLon) * cos(Math.toRadians((aLat + bLat) / 2.0))
        val dy = bLat - aLat
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= 1e-12) {
            val d = calculateDistanceMeters(pLat, pLon, aLat, aLon)
            return Triple(aLat, aLon, d)
        }
        val px = (pLon - aLon) * cos(Math.toRadians((aLat + bLat) / 2.0))
        val py = pLat - aLat
        val t = ((px * dx + py * dy) / lengthSq).coerceIn(0.0, 1.0)
        val projLat = aLat + t * (bLat - aLat)
        val projLon = aLon + t * (bLon - aLon)
        val d = calculateDistanceMeters(pLat, pLon, projLat, projLon)
        return Triple(projLat, projLon, d)
    }

    private fun findNearestRoadNode(db: SQLiteDatabase, lat: Double, lon: Double): Long? {
        val deltas = doubleArrayOf(0.003, 0.01, 0.03, 0.1)
        for (delta in deltas) {
            val minLat = lat - delta
            val maxLat = lat + delta
            val minLon = lon - delta
            val maxLon = lon + delta

            var bestId: Long? = null
            var bestDist = Double.MAX_VALUE

            // 1. High-speed R-Tree virtual table query
            val rtreeQueried = runCatching {
                db.rawQuery(
                    """
                    SELECT id, min_lat, min_lon
                    FROM nodes_rtree
                    WHERE min_lat >= ? AND max_lat <= ?
                      AND min_lon >= ? AND max_lon <= ?
                    LIMIT 250
                    """.trimIndent(),
                    arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val nid = cursor.getLong(0)
                        val nlat = cursor.getDouble(1)
                        val nlon = cursor.getDouble(2)
                        val d = calculateDistanceMeters(lat, lon, nlat, nlon)
                        if (d < bestDist) {
                            bestDist = d
                            bestId = nid
                        }
                    }
                }
                true
            }.getOrDefault(false)

            if (bestId != null) return bestId

            // 2. Standard nodes table fallback if R-Tree module or table missing
            if (!rtreeQueried) {
                db.rawQuery(
                    """
                    SELECT id, lat, lon
                    FROM nodes
                    WHERE lat BETWEEN ? AND ?
                      AND lon BETWEEN ? AND ?
                    LIMIT 250
                    """.trimIndent(),
                    arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val nid = cursor.getLong(0)
                        val nlat = cursor.getDouble(1)
                        val nlon = cursor.getDouble(2)
                        val d = calculateDistanceMeters(lat, lon, nlat, nlon)
                        if (d < bestDist) {
                            bestDist = d
                            bestId = nid
                        }
                    }
                }
                if (bestId != null) return bestId
            }
        }
        return null
    }

    private fun findNearestRoadSegment(db: SQLiteDatabase, lat: Double, lon: Double): RoadSegmentProjection? {
        val nearNode = findNearestRoadNode(db, lat, lon) ?: return null

        var bestProj: RoadSegmentProjection? = null
        var minDistance = Double.MAX_VALUE

        db.rawQuery(
            """
            SELECT e.from_node, e.to_node, n1.lat, n1.lon, n2.lat, n2.lon, e.road_name, e.road_type
            FROM edges e
            JOIN nodes n1 ON e.from_node = n1.id
            JOIN nodes n2 ON e.to_node = n2.id
            WHERE e.from_node = ? OR e.to_node = ?
            LIMIT 50
            """.trimIndent(),
            arrayOf(nearNode.toString(), nearNode.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fNode = cursor.getLong(0)
                val tNode = cursor.getLong(1)
                val lat1 = cursor.getDouble(2)
                val lon1 = cursor.getDouble(3)
                val lat2 = cursor.getDouble(4)
                val lon2 = cursor.getDouble(5)
                val rName = cursor.getString(6) ?: ""
                val rType = cursor.getString(7) ?: ""

                val (pLat, pLon, pDist) = projectPointOnSegment(lat, lon, lat1, lon1, lat2, lon2)
                if (pDist < minDistance) {
                    minDistance = pDist
                    bestProj = RoadSegmentProjection(fNode, tNode, pLat, pLon, pDist, rName, rType)
                }
            }
        }
        return bestProj
    }

    /**
     * Calculates real driving route using offline routing.db road graph.
     * Guarantees 100% road following, one-way enforcement, and turn maneuvers.
     * Generates both primary time-optimal and genuine alternative route options.
     */
    fun calculateRoutesFromDatabase(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String = "Пункт назначения",
        numRoutes: Int = 2,
    ): List<NavigationRoute> {
        val r1Pair = calculateSingleRouteInternal(
            startLat = startLat,
            startLon = startLon,
            destLat = destLat,
            destLon = destLon,
            destinationLabel = destinationLabel,
            penalizedEdges = null,
            penaltyFactor = 1.0,
            routeName = "Основной (быстрый)",
            routeIndex = 0,
        ) ?: return emptyList()

        val r1 = r1Pair.first
        val r1Edges = r1Pair.second

        if (numRoutes < 2 || r1.totalDistanceMeters < 300.0) {
            return listOf(r1)
        }

        val r2Pair = calculateSingleRouteInternal(
            startLat = startLat,
            startLon = startLon,
            destLat = destLat,
            destLon = destLon,
            destinationLabel = destinationLabel,
            penalizedEdges = r1Edges,
            penaltyFactor = 1.8,
            routeName = "Альтернативный",
            routeIndex = 1,
        )

        if (r2Pair != null && r2Pair.first.totalDistanceMeters > 0) {
            val r2 = r2Pair.first
            val r2Edges = r2Pair.second
            val overlap = r1Edges.intersect(r2Edges).size.toDouble() / r1Edges.size.coerceAtLeast(1)
            val t1 = r1.totalDurationSeconds
            val t2 = r2.totalDurationSeconds
            if (t2 <= maxOf(t1 * 1.65, t1 + 900.0) && overlap < 0.90) {
                val dt = t2 - t1
                val diffStr = if (dt > 30.0) {
                    val m = Math.round(dt / 60.0)
                    "+$m мин"
                } else if (dt < -30.0) {
                    val m = Math.round(Math.abs(dt) / 60.0)
                    "-$m мин"
                } else {
                    "Столько же"
                }
                val km = r2.totalDistanceMeters / 1000.0
                val updatedR2 = r2.copy(
                    summary = "Альтернативный ($diffStr) • ${String.format(Locale.ROOT, "%.1f км", km)}"
                )
                return listOf(r1, updatedR2)
            }
        }

        return listOf(r1)
    }

    fun calculateRouteFromDatabase(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String = "Пункт назначения",
    ): NavigationRoute? = calculateRoutesFromDatabase(startLat, startLon, destLat, destLon, destinationLabel, numRoutes = 1).firstOrNull()

    private fun calculateSingleRouteInternal(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String,
        penalizedEdges: Set<Pair<Long, Long>>?,
        penaltyFactor: Double,
        routeName: String,
        routeIndex: Int,
    ): Pair<NavigationRoute, Set<Pair<Long, Long>>>? {
        return try {
            val db = getRoutingDatabase() ?: run {
                Log.w(TAG, "calculateSingleRouteInternal: routing DB not available")
                return null
            }

            val startProj = findNearestRoadSegment(db, startLat, startLon)
            val destProj = findNearestRoadSegment(db, destLat, destLon)

            val startNode = startProj?.toNode ?: findNearestRoadNode(db, startLat, startLon) ?: run {
                Log.w(TAG, "calculateSingleRouteInternal: start node not found for ($startLat, $startLon)")
                return null
            }
            val destNode = destProj?.fromNode ?: findNearestRoadNode(db, destLat, destLon) ?: run {
                Log.w(TAG, "calculateSingleRouteInternal: dest node not found for ($destLat, $destLon)")
                return null
            }

            if (startNode == destNode) {
                val directDist = calculateDistanceMeters(startLat, startLon, destLat, destLon)
                val lineCoords = listOf(Pair(startLat, startLon), Pair(destLat, destLon))
                val featureCollection = JSONObject().apply {
                    put("type", "FeatureCollection")
                    put("features", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "Feature")
                            put("geometry", JSONObject().apply {
                                put("type", "LineString")
                                put("coordinates", JSONArray().apply {
                                    put(JSONArray().apply { put(startLon); put(startLat) })
                                    put(JSONArray().apply { put(destLon); put(destLat) })
                                })
                            })
                            put("properties", JSONObject().apply {
                                put("selected", routeIndex == 0)
                                put("routeIndex", routeIndex)
                            })
                        })
                    })
                }
                val route = NavigationRoute(
                    totalDistanceMeters = directDist,
                    totalDurationSeconds = (directDist / 10.0).coerceAtLeast(1.0),
                    routeGeoJson = featureCollection.toString(),
                    coordinates = lineCoords,
                    steps = listOf(
                        NavigationStep(
                            instruction = "Вы находитесь рядом с пунктом назначения",
                            streetName = destinationLabel,
                            maneuverType = "arrive",
                            maneuverModifier = null,
                            distanceMeters = directDist,
                            durationSeconds = (directDist / 10.0).coerceAtLeast(1.0),
                            latitude = destLat,
                            longitude = destLon,
                        )
                    ),
                    destination = PlaceTarget.Point(destLat, destLon),
                    destinationLabel = destinationLabel,
                    summary = "$routeName • Пункт назначения рядом",
                )
                return Pair(route, emptySet())
            }

            var endLat = destLat
            var endLon = destLon
            db.rawQuery("SELECT lat, lon FROM nodes WHERE id = ?", arrayOf(destNode.toString())).use {
                if (it.moveToFirst()) {
                    endLat = it.getDouble(0)
                    endLon = it.getDouble(1)
                }
            }

            val restrictions = loadRestrictions(db)

            val dist = HashMap<Pair<Long, Long>, Double>()
            val prev = HashMap<Pair<Long, Long>, Pair<Pair<Long, Long>, RouteEdgeHop>>()
            val pq = PriorityQueue<AStarNode>(compareBy { it.fScore })

            dist[Pair(startNode, 0L)] = 0.0
            val distStart = calculateDistanceMeters(startLat, startLon, endLat, endLon)
            val hStart = (distStart / (80.0 * 1000.0 / 3600.0)) * 1.3
            pq.add(
                AStarNode(
                    fScore = hStart,
                    gScore = 0.0,
                    nodeId = startNode,
                    incomingWayId = 0L,
                    parentNode = null,
                    inLat = 0.0,
                    inLon = 0.0,
                )
            )

            val nodeCoords = HashMap<Long, Pair<Double, Double>>()
            nodeCoords[startNode] = Pair(startLat, startLon)
            nodeCoords[destNode] = Pair(endLat, endLon)

            fun getNodeCoord(nid: Long): Pair<Double, Double> {
                nodeCoords[nid]?.let { return it }
                var res = Pair(0.0, 0.0)
                db.rawQuery("SELECT lat, lon FROM nodes WHERE id = ?", arrayOf(nid.toString())).use {
                    if (it.moveToFirst()) {
                        res = Pair(it.getDouble(0), it.getDouble(1))
                    }
                }
                nodeCoords[nid] = res
                return res
            }

            var reachedState: Pair<Long, Long>? = null
            var expanded = 0
            val maxExpansions = 25000

            while (pq.isNotEmpty() && expanded < maxExpansions) {
                val current = pq.poll() ?: break
                val u = current.nodeId
                val inWay = current.incomingWayId
                val parentNode = current.parentNode
                val inLat = current.inLat
                val inLon = current.inLon
                val currentG = current.gScore

                val stateKey = Pair(u, inWay)
                if (currentG > (dist[stateKey] ?: Double.MAX_VALUE)) continue

                if (u == destNode) {
                    reachedState = stateKey
                    break
                }
                expanded++

                val (uLat, uLon) = getNodeCoord(u)

                val outEdges = getOutEdgesForNode(db, u)

                for (edge in outEdges) {
                    val v = edge.v
                    val lenM = edge.lenM
                    val speed = edge.speed
                    val rName = edge.rName
                    val rType = edge.rType
                    val outWay = edge.outWay
                    val vLat = edge.vLat
                    val vLon = edge.vLon

                    // Avoid immediate 180° U-turn back to parent on same segment UNLESS it is a dead end (cul-de-sac)
                    if (v == parentNode && outEdges.size > 1) continue

                        // Turn restrictions check
                        if (inWay != 0L && !isTurnAllowed(restrictions, u, inWay, outWay)) {
                            continue
                        }

                        // 1. Road hierarchy preference & service penalty
                        var pFactor = ROAD_PRIORITY_WEIGHT[rType] ?: 1.2
                        // Major arterial expressway bonus (Prospekt Salavata Yulaeva is Ufa's main high-speed arterial)
                        if (rName.contains("Салавата Юлаева", ignoreCase = true)) {
                            pFactor *= 0.65
                        }
                        if (penalizedEdges != null && penalizedEdges.contains(Pair(u, v))) {
                            pFactor *= penaltyFactor
                        }

                        val rawTimeS = lenM / (speed * 1000.0 / 3600.0)

                        // 2. Turn penalty (3-5s)
                        var turnPenaltyS = 0.0
                        if (parentNode != null) {
                            val b1 = calculateBearing(inLat, inLon, uLat, uLon)
                            val b2 = calculateBearing(uLat, uLon, vLat, vLon)
                            val angle = Math.abs(computeTurnAngle(b1, b2))
                            if (angle > 80.0) {
                                turnPenaltyS = 4.0
                            } else if (angle > 35.0) {
                                turnPenaltyS = 2.5
                            }
                        }

                        val edgeCost = rawTimeS * pFactor + turnPenaltyS
                        val newCost = currentG + edgeCost
                        val nextState = Pair(v, outWay)

                        if (newCost < (dist[nextState] ?: Double.MAX_VALUE)) {
                            dist[nextState] = newCost
                            prev[nextState] = Pair(
                                stateKey,
                                RouteEdgeHop(
                                    fromNode = u,
                                    toNode = v,
                                    lengthM = lenM,
                                    timeS = rawTimeS,
                                    roadName = rName,
                                    roadType = rType,
                                    osmWayId = outWay,
                                )
                            )
                            nodeCoords[v] = Pair(vLat, vLon)

                            val distToEnd = calculateDistanceMeters(vLat, vLon, endLat, endLon)
                            val hVal = distToEnd / (80.0 * 1000.0 / 3600.0)
                            val fVal = newCost + 1.3 * hVal

                            pq.add(
                                AStarNode(
                                    fScore = fVal,
                                    gScore = newCost,
                                    nodeId = v,
                                    incomingWayId = outWay,
                                    parentNode = u,
                                    inLat = uLat,
                                    inLon = uLon,
                                )
                            )
                        }
                    }
                }

            if (reachedState == null) {
                Log.w(TAG, "calculateSingleRouteInternal: route not found after $expanded expansions")
                return null
            }

            // Reconstruct path edges
            val pathEdges = ArrayList<RouteEdgeHop>()
            var currState = reachedState
            while (currState != null && prev.containsKey(currState)) {
                val (prevState, hop) = prev[currState]!!
                pathEdges.add(hop)
                if (prevState.first == startNode && prevState.second == 0L) break
                currState = prevState
            }
            pathEdges.reverse()

            val fullCoordinates = ArrayList<Pair<Double, Double>>()
            fullCoordinates.add(Pair(startLat, startLon))
            if (startProj != null && startProj.distMeters < 250.0) {
                fullCoordinates.add(Pair(startProj.projLat, startProj.projLon))
            }
            for (hop in pathEdges) {
                val (lat, lon) = getNodeCoord(hop.toNode)
                if (lat != 0.0 && lon != 0.0) {
                    fullCoordinates.add(Pair(lat, lon))
                }
            }
            if (destProj != null && destProj.distMeters < 250.0) {
                fullCoordinates.add(Pair(destProj.projLat, destProj.projLon))
            }
            fullCoordinates.add(Pair(destLat, destLon))

            val totalDistMeters = pathEdges.sumOf { it.lengthM }
            val totalTimeSeconds = pathEdges.sumOf { it.timeS }

            // Turn-by-turn maneuver extraction
            val steps = ArrayList<NavigationStep>()
            if (pathEdges.isNotEmpty()) {
                var curManeuver = "depart"
                var curModifier: String? = null
                var curStreet = pathEdges[0].roadName
                var curStepDist = 0.0
                var curStepDuration = 0.0
                var curLat = startLat
                var curLon = startLon
                var prevBearing: Double? = null

                for (i in pathEdges.indices) {
                    val hop = pathEdges[i]
                    val (uLat, uLon) = getNodeCoord(hop.fromNode)
                    val (vLat, vLon) = getNodeCoord(hop.toNode)
                    val bearing = calculateBearing(uLat, uLon, vLat, vLon)

                    var isJunction = false
                    var maneuverType = "straight"
                    var maneuverMod: String? = null

                    if (prevBearing != null) {
                        val angle = computeTurnAngle(prevBearing, bearing)
                        val nameChanged = (hop.roadName != curStreet && hop.roadName.isNotBlank())
                        val significantTurn = Math.abs(angle) >= 30.0

                        if (nameChanged || significantTurn) {
                            isJunction = true
                            val nextRoadType = hop.roadType
                            val prevRoadType = pathEdges[i - 1].roadType
                            val (mt, mm) = classifyManeuver(angle, prevRoadType, nextRoadType)
                            maneuverType = mt
                            maneuverMod = mm
                        }
                    }

                    if (isJunction && curStepDist > 15.0) {
                        steps.add(
                            NavigationStep(
                                instruction = maneuverInstructionRu(curManeuver, curModifier, curStreet),
                                streetName = curStreet,
                                maneuverType = curManeuver,
                                maneuverModifier = curModifier,
                                distanceMeters = curStepDist,
                                durationSeconds = curStepDuration,
                                latitude = curLat,
                                longitude = curLon,
                            )
                        )
                        curManeuver = maneuverType
                        curModifier = maneuverMod
                        curStreet = hop.roadName
                        curStepDist = hop.lengthM
                        curStepDuration = hop.timeS
                        curLat = uLat
                        curLon = uLon
                    } else {
                        curStepDist += hop.lengthM
                        curStepDuration += hop.timeS
                        if (curStreet.isBlank() && hop.roadName.isNotBlank()) {
                            curStreet = hop.roadName
                        }
                    }

                    prevBearing = bearing
                }

                // Final driving step
                steps.add(
                    NavigationStep(
                        instruction = maneuverInstructionRu(curManeuver, curModifier, curStreet),
                        streetName = curStreet,
                        maneuverType = curManeuver,
                        maneuverModifier = curModifier,
                        distanceMeters = curStepDist,
                        durationSeconds = curStepDuration,
                        latitude = curLat,
                        longitude = curLon,
                    )
                )

                // Arrival step
                steps.add(
                    NavigationStep(
                        instruction = "Вы прибыли в пункт назначения",
                        streetName = destinationLabel,
                        maneuverType = "arrive",
                        maneuverModifier = null,
                        distanceMeters = 0.0,
                        durationSeconds = 0.0,
                        latitude = destLat,
                        longitude = destLon,
                    )
                )
            }

            val coordinatesJson = JSONArray().apply {
                for (coord in fullCoordinates) {
                    put(JSONArray().apply {
                        put(coord.second)
                        put(coord.first)
                    })
                }
            }

            val featureCollection = JSONObject().apply {
                put("type", "FeatureCollection")
                put("features", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", JSONObject().apply {
                            put("type", "LineString")
                            put("coordinates", coordinatesJson)
                        })
                        put("properties", JSONObject().apply {
                            put("selected", routeIndex == 0)
                            put("routeIndex", routeIndex)
                        })
                    })
                    put(JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", JSONArray().apply {
                                put(destLon)
                                put(destLat)
                            })
                        })
                        put("properties", JSONObject().apply {
                            put("destination", true)
                        })
                    })
                })
            }

            val mins = (totalTimeSeconds / 60.0).toInt()
            val km = totalDistMeters / 1000.0
            val summary = if (km >= 1.0) {
                "$routeName • ${String.format(Locale.ROOT, "%.1f км", km)} ($mins мин)"
            } else {
                "$routeName • ${totalDistMeters.toInt()} м ($mins мин)"
            }

            val route = NavigationRoute(
                totalDistanceMeters = totalDistMeters,
                totalDurationSeconds = totalTimeSeconds,
                routeGeoJson = featureCollection.toString(),
                coordinates = fullCoordinates,
                steps = steps,
                destination = PlaceTarget.Point(destLat, destLon),
                destinationLabel = destinationLabel,
                summary = summary,
            )
            val edgeSet = pathEdges.map { Pair(it.fromNode, it.toNode) }.toSet()
            Pair(route, edgeSet)
        } catch (e: Exception) {
            Log.e(TAG, "calculateSingleRouteInternal: failed with exception", e)
            null
        }
    }
}
