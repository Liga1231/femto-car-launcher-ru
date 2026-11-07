package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.places.HistoryItem
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SearchHistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class FakeSearchHistoryStore(
    initial: List<HistoryItem> = emptyList(),
) : SearchHistoryStore {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    override val history: Flow<List<HistoryItem>> = state

    val current: List<HistoryItem> get() = state.value

    override suspend fun add(
        label: String,
        target: PlaceTarget,
    ) = state.update { current ->
        val filtered = current.filterNot { it.label == label }
        listOf(HistoryItem(nextId++, label, target)) + filtered
    }

    override suspend fun delete(id: Long) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun clear() = state.update { emptyList() }
}
