package io.github.seijikohara.femto.data.places

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

private const val TAG = "SearchHistoryPreferences"

@Serializable
internal data class HistoryItem(
    val id: Long,
    val label: String,
    val target: PlaceTarget,
    val timestampMs: Long = System.currentTimeMillis(),
)

internal const val SEARCH_HISTORY_STORE_NAME = "search_history"

internal val Context.searchHistoryDataStore: DataStore<Preferences> by
    preferencesDataStore(name = SEARCH_HISTORY_STORE_NAME)

private val HistoryJson = Json { ignoreUnknownKeys = true }
private val HISTORY_KEY = stringPreferencesKey("history_items")
private val NEXT_ID_KEY = longPreferencesKey("next_id")
private const val MAX_HISTORY_ITEMS = 30

internal interface SearchHistoryStore {
    val history: Flow<List<HistoryItem>>

    suspend fun add(
        label: String,
        target: PlaceTarget,
    )

    suspend fun delete(id: Long)

    suspend fun clear()
}

internal class SearchHistoryPreferences(
    private val context: Context,
) : SearchHistoryStore {
    override val history: Flow<List<HistoryItem>> =
        context.searchHistoryDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> prefs.historyOrNull().orEmpty() }

    override suspend fun add(
        label: String,
        target: PlaceTarget,
    ) {
        val cleanLabel = label.trim()
        if (cleanLabel.isBlank()) return

        context.searchHistoryDataStore.editOrLog(TAG) { prefs ->
            prefs.historyOrNull()?.let { current ->
                val filtered =
                    current.filterNot { existing ->
                        isSameTarget(existing.target, target) ||
                            existing.label.equals(cleanLabel, ignoreCase = true)
                    }

                val id = maxOf(prefs[NEXT_ID_KEY] ?: 1L, (current.maxOfOrNull { it.id } ?: 0L) + 1)
                val newItem =
                    HistoryItem(
                        id = id,
                        label = cleanLabel,
                        target = target,
                        timestampMs = System.currentTimeMillis(),
                    )

                val updated = (listOf(newItem) + filtered).take(MAX_HISTORY_ITEMS)
                prefs[HISTORY_KEY] = HistoryJson.encodeToString(updated)
                prefs[NEXT_ID_KEY] = id + 1
            }
        }
    }

    override suspend fun delete(id: Long) {
        context.searchHistoryDataStore.editOrLog(TAG) { prefs ->
            prefs.historyOrNull()?.let { current ->
                prefs[HISTORY_KEY] = HistoryJson.encodeToString(current.filterNot { it.id == id })
            }
        }
    }

    override suspend fun clear() {
        context.searchHistoryDataStore.editOrLog(TAG) { prefs ->
            prefs[HISTORY_KEY] = HistoryJson.encodeToString(emptyList<HistoryItem>())
        }
    }

    private fun Preferences.historyOrNull(): List<HistoryItem>? {
        val raw = this[HISTORY_KEY] ?: return emptyList()
        return runCatching { HistoryJson.decodeFromString<List<HistoryItem>>(raw) }
            .getOrNull()
    }

    private fun isSameTarget(
        a: PlaceTarget,
        b: PlaceTarget,
    ): Boolean =
        when {
            a is PlaceTarget.Query && b is PlaceTarget.Query ->
                a.text.trim().equals(b.text.trim(), ignoreCase = true)
            a is PlaceTarget.Point && b is PlaceTarget.Point ->
                abs(a.latitude - b.latitude) < 0.0003 && abs(a.longitude - b.longitude) < 0.0003
            else -> false
        }
}
