package com.paiwand.translate.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val releasePage: String = "",
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val mandatory: Boolean = false,
    val requiresReinstall: Boolean = false,
    val changelog: List<String> = emptyList()
)

enum class UpdatePhase {
    Idle,
    Available,
    Downloading,
    NeedsPermission,
    InstallLaunched,
    ReinstallRequired,
    Failed
}

data class UpdateUiState(
    val checking: Boolean = false,
    val info: UpdateInfo? = null,
    val phase: UpdatePhase = UpdatePhase.Idle,
    val progress: Float = 0f,
    val message: String? = null
) {
    val visible: Boolean
        get() = phase in setOf(
            UpdatePhase.Available,
            UpdatePhase.Downloading,
            UpdatePhase.NeedsPermission,
            UpdatePhase.InstallLaunched,
            UpdatePhase.ReinstallRequired,
            UpdatePhase.Failed
        )
}
