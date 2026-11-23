package com.paiwand.translate.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paiwand.translate.PaiwandApp
import com.paiwand.translate.data.HistoryItem
import com.paiwand.translate.data.Language
import com.paiwand.translate.data.Languages
import com.paiwand.translate.data.Settings
import com.paiwand.translate.overlay.ScreenCapture
import com.paiwand.translate.translate.TranslationEngine
import com.paiwand.translate.update.UpdateUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = (app as PaiwandApp).settings
    private val historyStore = (app as PaiwandApp).history
    private val updates = (app as PaiwandApp).updates
    private val engine = TranslationEngine()
    private val context = app.applicationContext

    val settings: StateFlow<Settings> =
        settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val history: StateFlow<List<HistoryItem>> =
        historyStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val updateState: StateFlow<UpdateUiState> = updates.state

    var input by mutableStateOf("")
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var sourceCode by mutableStateOf("auto")
        private set
    var targetCode by mutableStateOf("ur")
        private set

    val sourceLanguage: Language get() = Languages.byCode(sourceCode)
    val targetLanguage: Language get() = Languages.byCode(targetCode)

    init {
        viewModelScope.launch {
            val saved = settingsStore.flow.first()
            sourceCode = saved.sourceLang
            targetCode = saved.targetLang
        }
    }

    fun onInputChange(value: String) {
        input = value
        if (value.isBlank()) error = null
    }

    fun clearInput() {
        input = ""
        result = null
        error = null
    }

    fun dismissResult() {
        result = null
    }

    fun setSource(code: String) {
        sourceCode = code
        if (targetCode == code) targetCode = if (code == "en") "ur" else "en"
        persistLanguages()
    }

    fun setTarget(code: String) {
        targetCode = code
        if (sourceCode == code) sourceCode = "auto"
        persistLanguages()
    }

    private fun persistLanguages() {
        viewModelScope.launch { settingsStore.setLanguages(sourceCode, targetCode) }
    }

    fun swapLanguages() {
        val nextTarget = if (sourceCode == "auto") "en" else sourceCode
        val nextSource = targetCode
        sourceCode = nextSource
        targetCode = nextTarget
        persistLanguages()
    }

    fun translate() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        error = null
        result = null

        val current = settings.value
        if (!current.configured) {
            error = "Add your API key in Settings to start translating."
            return
        }

        busy = true
        viewModelScope.launch {
            try {
                val output = engine.translate(
                    text = text,
                    source = sourceLanguage,
                    target = targetLanguage,
                    apiKey = current.apiKey,
                    baseUrl = current.apiBase,
                    model = current.model
                )
                result = output
                historyStore.add(
                    historyStore.newItem(
                        source = text,
                        result = output,
                        sourceLang = sourceCode,
                        targetLang = targetCode
                    )
                )
            } catch (e: Exception) {
                error = e.message ?: "Translation failed"
            } finally {
                busy = false
            }
        }
    }

    fun useHistory(item: HistoryItem) {
        input = item.source
        result = item.result
        error = null
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch { historyStore.remove(item.id) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch { updates.check(manual) }
    }

    fun installUpdate() = updates.install()

    fun requestInstallPermission() = updates.requestInstallPermission()

    fun openDownloadPage() = updates.openDownloadPage()

    fun dismissUpdate() = updates.dismiss()

    fun enableBubble() {
        viewModelScope.launch { settingsStore.setBubbleEnabled(true) }
    }

    fun disableBubble() {
        viewModelScope.launch { settingsStore.setBubbleEnabled(false) }
        ScreenCapture.stop()
    }

    fun saveEngine(apiKey: String, apiBase: String, model: String) {
        viewModelScope.launch {
            settingsStore.setApiKey(apiKey.trim())
            settingsStore.setApiBase(apiBase.trim())
            settingsStore.setModel(model.trim())
            toast("Saved")
        }
    }

    fun setAutoCheckUpdates(value: Boolean) {
        viewModelScope.launch { settingsStore.setAutoCheckUpdates(value) }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
