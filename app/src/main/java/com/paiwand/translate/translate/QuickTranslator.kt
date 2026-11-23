package com.paiwand.translate.translate

import android.content.Context
import com.paiwand.translate.PaiwandApp
import com.paiwand.translate.data.Language
import kotlinx.coroutines.flow.first

/**
 * One shared entry point for the bubble and the popup card, so both use the same
 * auto-direction rule and the same HTTP client.
 */
object QuickTranslator {

    private val engine = TranslationEngine()

    data class Result(val text: String, val target: Language)

    suspend fun translate(context: Context, text: String): Result {
        val app = context.applicationContext as PaiwandApp
        val settings = app.settings.flow.first()

        if (!settings.configured) {
            throw TranslationException("Add your API key in Settings first.")
        }

        val route = Direction.routeFor(text, settings)
        val output = engine.translate(
            text = text,
            source = route.source,
            target = route.target,
            apiKey = settings.apiKey,
            baseUrl = settings.apiBase,
            model = settings.model
        )
        return Result(output, route.target)
    }
}
