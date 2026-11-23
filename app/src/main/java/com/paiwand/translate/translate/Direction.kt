package com.paiwand.translate.translate

import com.paiwand.translate.data.Language
import com.paiwand.translate.data.Languages
import com.paiwand.translate.data.Settings

/**
 * Magic-translate style direction: nothing is chosen by the user, the script of the text decides.
 * Arabic-script text becomes English, everything else becomes the app's target language.
 */
object Direction {

    private val arabicScript = listOf(
        0x0600..0x06FF, // Arabic, Urdu, Persian, Pashto
        0x0750..0x077F, // Arabic Supplement
        0x08A0..0x08FF, // Arabic Extended-A
        0xFB50..0xFDFF, // Arabic Presentation Forms-A
        0xFE70..0xFEFF  // Arabic Presentation Forms-B
    )

    data class Route(val source: Language, val target: Language)

    fun isArabicScript(text: String): Boolean =
        text.any { char -> arabicScript.any { char.code in it } }

    fun routeFor(text: String, settings: Settings): Route {
        if (isArabicScript(text)) {
            return Route(Languages.byCode("auto"), Languages.byCode("en"))
        }
        val targetCode = settings.targetLang.ifBlank { "ur" }.let {
            if (it == "en") "ur" else it
        }
        return Route(Languages.byCode("auto"), Languages.byCode(targetCode))
    }
}
