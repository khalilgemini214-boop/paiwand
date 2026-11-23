package com.paiwand.translate.data

import kotlinx.serialization.Serializable

@Serializable
data class HistoryItem(
    val id: String,
    val source: String,
    val result: String,
    val sourceLang: String,
    val targetLang: String,
    val createdAt: Long
)

data class Language(
    val code: String,
    val name: String,
    val native: String,
    val rtl: Boolean = false
) {
    val badge: String get() = if (code == "auto") "AUTO" else code.uppercase()
}

object Languages {

    val auto = Language("auto", "Detect language", "Auto")

    val all: List<Language> = listOf(
        auto,
        Language("en", "English", "English"),
        Language("ur", "Urdu", "اردو", rtl = true),
        Language("ps", "Pashto", "پښتو", rtl = true),
        Language("hi", "Hindi", "हिन्दी"),
        Language("sd", "Sindhi", "سنڌي", rtl = true),
        Language("pa", "Punjabi", "ਪੰਜਾਬੀ"),
        Language("ar", "Arabic", "العربية", rtl = true),
        Language("fa", "Persian", "فارسی", rtl = true),
        Language("bn", "Bengali", "বাংলা"),
        Language("tr", "Turkish", "Türkçe"),
        Language("id", "Indonesian", "Bahasa Indonesia"),
        Language("zh", "Chinese", "中文"),
        Language("fr", "French", "Français"),
        Language("es", "Spanish", "Español"),
        Language("de", "German", "Deutsch"),
        Language("ru", "Russian", "Русский")
    )

    val targets: List<Language> = all.filter { it.code != "auto" }

    fun byCode(code: String): Language = all.firstOrNull { it.code == code } ?: auto
}
