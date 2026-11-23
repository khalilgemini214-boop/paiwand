package com.paiwand.translate.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class HistoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val serializer = ListSerializer(HistoryItem.serializer())
    private val file = File(context.filesDir, "history.json")
    private val mutex = Mutex()

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val flow: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    init {
        _items.value = read()
    }

    private fun read(): List<HistoryItem> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString(serializer, file.readText())
    }.getOrDefault(emptyList())

    private fun write(items: List<HistoryItem>) {
        runCatching { file.writeText(json.encodeToString(serializer, items)) }
    }

    suspend fun add(item: HistoryItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = (listOf(item) + _items.value.filterNot { it.source.equals(item.source, true) })
                .take(MAX_ITEMS)
            _items.value = next
            write(next)
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = _items.value.filterNot { it.id == id }
            _items.value = next
            write(next)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _items.value = emptyList()
            write(emptyList())
        }
    }

    fun newItem(source: String, result: String, sourceLang: String, targetLang: String) =
        HistoryItem(
            id = UUID.randomUUID().toString(),
            source = source,
            result = result,
            sourceLang = sourceLang,
            targetLang = targetLang,
            createdAt = System.currentTimeMillis()
        )

    private companion object {
        const val MAX_ITEMS = 80
    }
}
