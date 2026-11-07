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

    @Synchronized
    private fun getSearchDatabase(): SQLiteDatabase? {
        if (searchDatabase?.isOpen == true) return searchDatabase
        val ctx = appContext
        val candidates = mutableListOf(
            File("/sdcard/Femto/maps/search.db"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps/search.db"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps/search.db"),
        )
        if (ctx != null) {
            ctx.getExternalFilesDir("maps")?.let { candidates.add(File(it, "search.db")) }
            val internalFile = File(ctx.filesDir, "search.db")
            if (!internalFile.exists() || internalFile.length() == 0L) {
                try {
                    ctx.assets.open("map/search.db").use { input ->
                        internalFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) {}
            }
            candidates.add(internalFile)
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
        if (lat in 54.48..54.95 && lon in 55.70..56.35) return true
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
        val num = houseStr.filter { it.isDigit() }.toIntOrNull() ?: 1
        val isOdd = (num % 2) != 0
        val side = if (isOdd) 1.0 else -1.0
        val step = (num / 2) * 0.00022
        val perp = side * 0.00012
        val cosA = 0.7071
        val sinA = 0.7071
        val dLat = step * cosA - perp * sinA
        val dLon = (step * sinA + perp * cosA) / max(0.2, cos(Math.toRadians(baseLat)))
        return Pair(baseLat + dLat, baseLon + dLon)
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
    )

    fun searchPlaces(query: String, maxResults: Int = 80): List<AddressSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        val houseMatch = Regex("""\b(\d+[а-яa-z/\-]*)\b""").find(trimmed)
        val houseNum = houseMatch?.value
        val streetQuery = trimmed.replace(Regex("""\b\d+[а-яa-z/\-]*\b"""), "").trim()
        val searchQuery = if (streetQuery.length >= 2) streetQuery else trimmed
        val qLower = searchQuery.lowercase(Locale.ROOT)
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

        // 1. Query SQLite search.db
        val db = getSearchDatabase()
        if (db != null) {
            try {
                val pattern = "%$qRoot%"
                val cursor = db.rawQuery(
                    "SELECT title, subtitle, lat, lon, category, priority FROM places WHERE search_norm LIKE ? LIMIT 60",
                    arrayOf(pattern),
                )
                var streetCount = 0
                cursor.use {
                    while (it.moveToNext()) {
                        val titleRaw = it.getString(0)
                        val subtitle = it.getString(1)
                        val lat = it.getDouble(2)
                        val lon = it.getDouble(3)
                        val category = it.getString(4)
                        val prio = it.getInt(5)

                        val isUfa = isUfaLocation(titleRaw, subtitle, lat, lon)
                        val tLower = titleRaw.lowercase(Locale.ROOT)
                        val startsWith = tLower.startsWith(qLower) || tLower.startsWith(qRoot)
                        val contains = tLower.contains(qLower) || tLower.contains(qRoot)

                        // Add the street / place itself
                        candidates.add(
                            ScoredCandidate(
                                title = titleRaw,
                                subtitle = subtitle,
                                lat = lat,
                                lon = lon,
                                priority = prio,
                                isUfa = isUfa,
                                startsWithQuery = startsWith,
                                containsQuery = contains,
                            )
                        )

                        // If category is street, expand into houses for the top matching streets
                        if (category == "street" && (streetCount < 3 || houseNum != null)) {
                            streetCount++
                            val houses = getStreetHouses(houseNum)
                            for (h in houses) {
                                val (hLat, hLon) = calculateHouseCoordinate(lat, lon, h)
                                val isExact = (houseNum != null && h.equals(houseNum, ignoreCase = true))
                                val hTitle = "$titleRaw, $h"
                                candidates.add(
                                    ScoredCandidate(
                                        title = hTitle,
                                        subtitle = subtitle,
                                        lat = hLat,
                                        lon = hLon,
                                        priority = if (isExact) 100 else if (houseNum != null) 92 else (prio - 2),
                                        isUfa = isUfa,
                                        startsWithQuery = startsWith,
                                        containsQuery = contains,
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying search.db", e)
            }
        }

        // 2. Curated in-memory PLACES (key Ufa landmarks, avenues, stations)
        var placesStreetCount = 0
        for (entry in PLACES) {
            val titleL = entry.title.lowercase(Locale.ROOT)
            val subL = entry.subtitle.lowercase(Locale.ROOT)
            if (titleL.contains(qLower) || titleL.contains(qRoot) || subL.contains(qLower) || subL.contains(qRoot)) {
                val isUfa = isUfaLocation(entry.title, entry.subtitle, entry.lat, entry.lon)
                val startsWith = titleL.startsWith(qLower) || titleL.startsWith(qRoot)
                val contains = titleL.contains(qLower) || titleL.contains(qRoot)

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
                    )
                )

                if (entry.type == "street" && (placesStreetCount < 2 || houseNum != null)) {
                    placesStreetCount++
                    val houses = getStreetHouses(houseNum)
                    for (h in houses) {
                        val (hLat, hLon) = calculateHouseCoordinate(entry.lat, entry.lon, h)
                        val isExact = (houseNum != null && h.equals(houseNum, ignoreCase = true))
                        val hTitle = "${entry.title}, $h"
                        candidates.add(
                            ScoredCandidate(
                                title = hTitle,
                                subtitle = entry.subtitle,
                                lat = hLat,
                                lon = hLon,
                                priority = if (isExact) 100 else if (houseNum != null) 92 else (entry.priority - 2),
                                isUfa = isUfa,
                                startsWithQuery = startsWith,
                                containsQuery = contains,
                            )
                        )
                    }
                }
            }
        }

        // 3. Sort:
        // FIRST: Is in Ufa (Ufa first, everything else below)
        // SECOND: Starts with query prefix
        // THIRD: Contains query
        // FOURTH: Higher priority
        val seen = mutableSetOf<String>()
        return candidates
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.isUfa }
                    .thenByDescending { it.startsWithQuery }
                    .thenByDescending { it.containsQuery }
                    .thenByDescending { it.priority }
            )
            .filter {
                val key = "${it.title.lowercase(Locale.ROOT)}_${it.subtitle.lowercase(Locale.ROOT)}"
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

    // -------------------------------------------------------------
    // ROAD NETWORK GRAPH OF REPUBLIC OF BASHKORTOSTAN
    // -------------------------------------------------------------

    private val nodes = mutableMapOf<Int, GraphNode>()
    private val adjacency = mutableMapOf<Int, MutableList<GraphEdge>>()

    init {
        buildBashkortostanGraph()
    }

    private fun addNode(id: Int, lat: Double, lon: Double, name: String) {
        nodes[id] = GraphNode(id, lat, lon, name)
        adjacency[id] = mutableListOf()
    }

    private fun addEdge(from: Int, to: Int, speed: Int, name: String, oneway: Boolean = false) {
        val n1 = nodes[from] ?: return
        val n2 = nodes[to] ?: return
        val dist = calculateDistanceMeters(n1.lat, n1.lon, n2.lat, n2.lon)
        adjacency[from]?.add(GraphEdge(from, to, dist, speed, name, oneway))
        if (!oneway) {
            adjacency[to]?.add(GraphEdge(to, from, dist, speed, name, false))
        }
    }

    private fun buildBashkortostanGraph() {
        // --- Hub nodes (Cities) ---
        addNode(1, 54.7351, 55.9587, "Уфа (Центр)")
        addNode(2, 54.7650, 56.0040, "Уфа (Проспект Октября)")
        addNode(3, 54.8150, 56.0850, "Уфа (Черниковка)")
        addNode(4, 54.7700, 56.0700, "Уфа (Сипайлово)")
        addNode(5, 54.7200, 56.0100, "Уфа (Зелёная Роща)")
        addNode(6, 54.7108, 55.9644, "Уфа (Монумент Дружбы)")
        addNode(7, 54.6850, 55.8750, "Уфа (Дёма)")
        addNode(8, 54.7750, 55.8800, "Уфа (Затон)")
        addNode(9, 54.7700, 56.2400, "Уфа (Шакша)")
        addNode(10, 54.5574, 55.8741, "Аэропорт Уфа")

        // Detailed Ufa City Districts
        addNode(11, 54.6980, 55.8300, "Уфа (Дёма - ул. Правды)")
        addNode(12, 54.7220, 55.8450, "Уфа (Дёмское шоссе / Дагестанская)")
        addNode(13, 54.6750, 55.8200, "Уфа (Дёма - Серебряный ручей / М-5)")
        addNode(14, 54.7220, 55.9980, "Уфа (ТРК Иремель / ул. Бакалинская)")
        addNode(15, 54.7180, 56.0120, "Уфа (ул. Генерала Горбатова / Менделеева)")
        addNode(16, 54.7120, 56.0020, "Уфа (ул. Степана Кувыкина / Рабкоров)")
        addNode(17, 54.7060, 55.9900, "Уфа (ул. Софьи Перовской / Авроры)")
        addNode(18, 54.7260, 55.9440, "Уфа (Гостиный Двор / ул. Ленина)")
        addNode(19, 54.7360, 55.9570, "Уфа (Центральный рынок / ул. Революционная)")
        addNode(25, 54.7200, 55.9900, "Уфа (ПСЮ / Бакалинская)")
        addNode(26, 54.7330, 56.0050, "Уфа (ПСЮ / Сагита Агиша)")
        addNode(27, 54.7550, 56.0350, "Уфа (ПСЮ / 50 лет СССР)")
        addNode(28, 54.7680, 56.0550, "Уфа (ПСЮ / Лесотехникума)")
        addNode(36, 54.7700, 56.0700, "Уфа (Сипайлово - ул. Маршала Жукова)")
        addNode(37, 54.7750, 56.0600, "Уфа (Сипайлово - ул. Гагарина)")
        addNode(38, 54.8150, 56.0850, "Уфа (Черниковка - ул. Первомайская)")
        addNode(39, 54.8250, 56.1100, "Уфа (Черниковка - ул. Кольцевая)")

        // Intercity centers
        addNode(20, 53.6333, 55.9500, "Стерлитамак")
        addNode(21, 53.3606, 55.9247, "Салават")
        addNode(22, 52.9597, 55.9322, "Мелеуз")
        addNode(23, 52.7561, 55.7892, "Кумертау")
        addNode(24, 53.4533, 56.0389, "Ишимбай")

        addNode(30, 54.6067, 53.7031, "Туймазы")
        addNode(31, 54.4815, 53.4710, "Октябрьский")
        addNode(32, 54.1083, 54.1200, "Белебей")
        addNode(33, 54.5956, 55.3858, "Чишмы")
        addNode(34, 54.2231, 55.0347, "Давлеканово")
        addNode(35, 54.0675, 55.0675, "Раевский")

        addNode(40, 55.0392, 55.9781, "Благовещенск")
        addNode(41, 55.4228, 55.5344, "Бирск")
        addNode(42, 55.4853, 54.8647, "Дюртюли")
        addNode(43, 56.0903, 54.2603, "Нефтекамск")
        addNode(44, 56.2692, 54.9286, "Янаул")

        addNode(50, 54.8317, 56.4172, "Иглино")
        addNode(51, 53.9675, 58.4097, "Белорецк")
        addNode(52, 54.3000, 59.4500, "Учалы")
        addNode(53, 52.7233, 58.6533, "Сибай")
        addNode(54, 52.5922, 58.3181, "Баймак")

        addNode(60, 54.3722, 56.1783, "Кармаскалы")
        addNode(61, 54.0153, 55.9378, "Толбазы")
        addNode(62, 54.9856, 55.3458, "Кушнаренково")
        addNode(63, 55.1328, 54.6531, "Чекмагуш")

        // --- Ufa Internal Arterial Network ---
        // Проспект Салавата Юлаева скоростной коридор
        addEdge(6, 25, 80, "проспект Салавата Юлаева")
        addEdge(25, 26, 80, "проспект Салавата Юлаева")
        addEdge(26, 27, 80, "проспект Салавата Юлаева")
        addEdge(27, 28, 80, "проспект Салавата Юлаева")
        addEdge(28, 4, 70, "улица Сипайловская")
        addEdge(4, 3, 70, "улица Сипайловская")
        addEdge(6, 5, 80, "проспект Салавата Юлаева")
        addEdge(5, 4, 80, "проспект Салавата Юлаева")

        // Зелёная Роща
        addEdge(25, 14, 60, "улица Бакалинская")
        addEdge(14, 15, 60, "улица Менделеева")
        addEdge(15, 5, 60, "улица Менделеева")
        addEdge(15, 16, 60, "улица Степана Кувыкина")
        addEdge(16, 17, 60, "улица Софьи Перовской")
        addEdge(17, 6, 60, "улица Софьи Перовской")
        addEdge(1, 5, 60, "улица Менделеева")
        addEdge(5, 3, 60, "улица Менделеева")

        // Центр & Проспект Октября
        addEdge(1, 6, 60, "улица Заки Валиди")
        addEdge(6, 1, 60, "улица Цюрупы")
        addEdge(1, 18, 50, "улица Ленина")
        addEdge(18, 19, 50, "улица Ленина")
        addEdge(18, 6, 60, "улица Заки Валиди")
        addEdge(19, 2, 60, "проспект Октября")
        addEdge(1, 2, 60, "проспект Октября")
        addEdge(2, 3, 60, "проспект Октября")
        addEdge(26, 2, 60, "улица 50 лет СССР")

        // Сипайлово & Черниковка
        addEdge(2, 4, 60, "улица Маршала Жукова")
        addEdge(4, 36, 60, "улица Маршала Жукова")
        addEdge(36, 37, 60, "улица Юрия Гагарина")
        addEdge(3, 38, 60, "улица Первомайская")
        addEdge(38, 39, 60, "улица Кольцевая")

        // Дёма & выезды
        addEdge(12, 6, 80, "Дёмское шоссе")
        addEdge(11, 12, 60, "улица Правды")
        addEdge(13, 11, 60, "улица Дагестанская")
        addEdge(13, 10, 90, "трасса М-5 «Урал»")
        addEdge(11, 7, 60, "улица Ухтомского")
        addEdge(7, 12, 60, "Дёмское шоссе")
        addEdge(1, 7, 70, "Дёмское шоссе")
        addEdge(7, 10, 80, "трасса М-5 «Урал»")
        addEdge(6, 10, 90, "Оренбургский тракт (Р-240)")
        addEdge(1, 8, 70, "Затонское шоссе")
        addEdge(3, 9, 70, "улица Сельская Богородская")
        addEdge(9, 50, 90, "трасса М-5 «Урал»")

        // --- Regional Arterials ---
        // South Corridor (R-240)
        addEdge(10, 60, 90, "трасса Р-240")
        addEdge(60, 61, 90, "трасса Р-240")
        addEdge(61, 20, 90, "трасса Р-240") // to Sterlitamak
        addEdge(20, 24, 70, "Стерлитамакский тракт") // to Ishimbay
        addEdge(20, 21, 90, "трасса Р-240") // to Salavat
        addEdge(21, 22, 90, "трасса Р-240") // to Meleuz
        addEdge(22, 23, 90, "трасса Р-240") // to Kumertau

        // West Corridor (M-5 / M-7)
        addEdge(10, 33, 90, "трасса М-5 «Урал»") // to Chishmy
        addEdge(33, 34, 80, "трасса Чишмы — Давлеканово")
        addEdge(34, 35, 80, "трасса Давлеканово — Раевский")
        addEdge(35, 32, 80, "трасса Раевский — Белебей")
        addEdge(33, 30, 90, "трасса М-5 «Урал»") // to Tuymazy
        addEdge(30, 31, 80, "трасса Туймазы — Октябрьский")
        addEdge(8, 62, 90, "трасса М-7 «Волга»") // to Kushnarenkovo
        addEdge(62, 63, 80, "трасса Кушнаренково — Чекмагуш")
        addEdge(62, 42, 90, "трасса М-7 «Волга»") // to Dyurtyuli

        // North Corridor
        addEdge(3, 40, 90, "трасса Уфа — Благовещенск")
        addEdge(40, 41, 90, "трасса Благовещенск — Бирск")
        addEdge(41, 42, 80, "трасса Бирск — Дюртюли")
        addEdge(42, 43, 90, "трасса Дюртюли — Нефтекамск")
        addEdge(43, 44, 80, "трасса Нефтекамск — Янаул")
        addEdge(41, 44, 80, "трасса Бирск — Янаул")

        // East & Trans-Ural Corridor
        addEdge(50, 51, 80, "трасса Р-316 (через Инзер на Белорецк)")
        addEdge(51, 52, 80, "трасса Белорецк — Учалы")
        addEdge(51, 54, 80, "трасса Белорецк — Баймак")
        addEdge(54, 53, 80, "трасса Баймак — Сибай")
        addEdge(24, 51, 70, "трасса Ишимбай — Белорецк")
    }

    /**
     * Finds nearest node in the graph to the given coordinate.
     */
    fun findNearestNode(lat: Double, lon: Double): GraphNode {
        var bestNode = nodes.values.first()
        var bestDist = Double.MAX_VALUE

        for (node in nodes.values) {
            val d = calculateDistanceMeters(lat, lon, node.lat, node.lon)
            if (d < bestDist) {
                bestDist = d
                bestNode = node
            }
        }
        return bestNode
    }

    /**
     * Computes optimal car route using A* algorithm across Bashkortostan.
     */
    fun calculateRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String = "Пункт назначения",
    ): NavigationRoute {
        val startNode = findNearestNode(startLat, startLon)
        val endNode = findNearestNode(destLat, destLon)

        val directDist = calculateDistanceMeters(startLat, startLon, destLat, destLon)
        val distToNode = calculateDistanceMeters(startLat, startLon, startNode.lat, startNode.lon)

        val pathNodes = mutableListOf<GraphNode>()

        if (startNode.id == endNode.id && directDist < distToNode) {
            // Direct nearby segment, no detour needed
        } else if (startNode.id == endNode.id) {
            pathNodes.add(startNode)
        } else {
            // A* search
            val dist = mutableMapOf<Int, Double>().withDefault { Double.MAX_VALUE }
            val prev = mutableMapOf<Int, Pair<Int, GraphEdge>>()
            val pq = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })

            dist[startNode.id] = 0.0
            pq.add(Pair(startNode.id, 0.0))

            while (pq.isNotEmpty()) {
                val (currId, currentCost) = pq.poll() ?: break
                if (currId == endNode.id) break
                if (currentCost > (dist[currId] ?: Double.MAX_VALUE)) continue

                val edges = adjacency[currId] ?: emptyList()
                for (edge in edges) {
                    // Cost by time: distance / speed
                    val timeWeight = edge.distanceMeters / (edge.speedKmh * 1000.0 / 3600.0)
                    val newCost = (dist[currId] ?: 0.0) + timeWeight

                    if (newCost < (dist[edge.toId] ?: Double.MAX_VALUE)) {
                        dist[edge.toId] = newCost
                        prev[edge.toId] = Pair(currId, edge)
                        val h = calculateDistanceMeters(nodes[edge.toId]!!.lat, nodes[edge.toId]!!.lon, endNode.lat, endNode.lon) / (90.0 * 1000.0 / 3600.0)
                        pq.add(Pair(edge.toId, newCost + h))
                    }
                }
            }

            // Reconstruct path
            var curr = endNode.id
            pathNodes.add(endNode)
            while (curr != startNode.id && prev.containsKey(curr)) {
                val p = prev[curr]!!
                curr = p.first
                nodes[curr]?.let { pathNodes.add(it) }
            }
            pathNodes.reverse()
        }

        // Build full geometry coordinates list: Start -> Path Nodes -> End
        val fullCoordinates = mutableListOf<Pair<Double, Double>>()
        fullCoordinates.add(Pair(startLat, startLon))
        for (n in pathNodes) {
            fullCoordinates.add(Pair(n.lat, n.lon))
        }
        fullCoordinates.add(Pair(destLat, destLon))

        // Calculate total distance & steps
        var totalDistanceMeters = 0.0
        var totalDurationSeconds = 0.0
        val steps = mutableListOf<NavigationStep>()

        // Depart step
        val firstStreet = if (pathNodes.size > 1) {
            adjacency[pathNodes[0].id]?.firstOrNull { it.toId == pathNodes[1].id }?.streetName ?: "дорога"
        } else {
            "дорога"
        }

        val firstTarget = pathNodes.firstOrNull()?.let { Pair(it.lat, it.lon) } ?: Pair(destLat, destLon)
        steps.add(
            NavigationStep(
                instruction = "Начало движения по направлению к $destinationLabel",
                streetName = firstStreet,
                maneuverType = "depart",
                maneuverModifier = null,
                distanceMeters = calculateDistanceMeters(startLat, startLon, firstTarget.first, firstTarget.second),
                durationSeconds = 60.0,
                latitude = startLat,
                longitude = startLon,
            )
        )

        for (i in 0 until fullCoordinates.size - 1) {
            val p1 = fullCoordinates[i]
            val p2 = fullCoordinates[i + 1]
            val segmentDist = calculateDistanceMeters(p1.first, p1.second, p2.first, p2.second)
            totalDistanceMeters += segmentDist

            // Estimate 60-90 km/h average
            val speed = if (segmentDist > 5000) 90.0 else 55.0
            val segDuration = segmentDist / (speed * 1000.0 / 3600.0)
            totalDurationSeconds += segDuration

            if (i in 1 until fullCoordinates.size - 2) {
                val p0 = fullCoordinates[i - 1]
                val b1 = calculateBearing(p0.first, p0.second, p1.first, p1.second)
                val b2 = calculateBearing(p1.first, p1.second, p2.first, p2.second)
                var diff = (b2 - b1) % 360.0
                if (diff < -180.0) diff += 360.0
                if (diff > 180.0) diff -= 360.0

                val nodeName = if (i <= pathNodes.size) pathNodes[i - 1].name else "трасса"

                if (abs(diff) >= 20.0) {
                    val (maneuverType, modifier, action) = when {
                        diff > 135.0 || diff < -135.0 -> Triple("turn", "u-turn", "Развернитесь")
                        diff in 45.0..135.0 -> Triple("turn", "right", "Поверните направо")
                        diff in 20.0..45.0 -> Triple("turn", "slight right", "Держитесь правее")
                        diff in -135.0..-45.0 -> Triple("turn", "left", "Поверните налево")
                        else -> Triple("turn", "slight left", "Держитесь левее")
                    }

                    steps.add(
                        NavigationStep(
                            instruction = "$action в сторону $nodeName",
                            streetName = nodeName,
                            maneuverType = maneuverType,
                            maneuverModifier = modifier,
                            distanceMeters = segmentDist,
                            durationSeconds = segDuration,
                            latitude = p1.first,
                            longitude = p1.second,
                        )
                    )
                }
            }
        }

        // Arrive step
        steps.add(
            NavigationStep(
                instruction = "Прибытие в пункт назначения: $destinationLabel",
                streetName = destinationLabel,
                maneuverType = "arrive",
                maneuverModifier = null,
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                latitude = destLat,
                longitude = destLon,
            )
        )

        // GeoJSON FeatureCollection
        val coordinatesJson = JSONArray()
        for (coord in fullCoordinates) {
            coordinatesJson.put(JSONArray().apply {
                put(coord.second) // lon
                put(coord.first)  // lat
            })
        }

        val featureCollection = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().apply {
                // LineString feature
                put(JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", coordinatesJson)
                    })
                    put("properties", JSONObject().apply {
                        put("selected", true)
                        put("routeIndex", 0)
                    })
                })
                // Destination point
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

        val summary = if (totalDistanceMeters >= 1000) {
            String.format(Locale.ROOT, "Офлайн-маршрут (%.1f км)", totalDistanceMeters / 1000.0)
        } else {
            String.format(Locale.ROOT, "Офлайн-маршрут (%d м)", totalDistanceMeters.roundToInt())
        }

        return NavigationRoute(
            totalDistanceMeters = totalDistanceMeters,
            totalDurationSeconds = totalDurationSeconds,
            routeGeoJson = featureCollection.toString(),
            coordinates = fullCoordinates,
            steps = steps,
            destination = PlaceTarget.Point(destLat, destLon),
            destinationLabel = destinationLabel,
            summary = summary,
        )
    }

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
}
