package com.worksbien.borescopedirect.camera

enum class CameraPhase {
    WAITING_FOR_DEVICE,
    CAMERA_PERMISSION_REQUIRED,
    USB_PERMISSION_REQUIRED,
    TESTING,
    LIVE,
    PAUSED_FOR_UNLOCK,
    ERROR,
}

enum class PixelFormat { MJPEG, YUYV }

data class CameraProfile(
    val width: Int,
    val height: Int,
    val format: PixelFormat,
) {
    val label: String = "${width}×$height ${format.name}"
}

data class DeviceSummary(
    val deviceId: Int,
    val label: String,
    val vendorId: Int,
    val productId: Int,
)

data class CameraUiState(
    val phase: CameraPhase = CameraPhase.WAITING_FOR_DEVICE,
    val devices: List<DeviceSummary> = emptyList(),
    val activeDeviceId: Int? = null,
    val profile: CameraProfile? = null,
    val actualWidth: Int? = null,
    val actualHeight: Int? = null,
    val attempt: Int = 0,
    val attemptCount: Int = RetryPolicy.safeProfiles.size,
    val recording: Boolean = false,
    val captureBusy: Boolean = false,
    val rotationDegrees: Int = 0,
    val lastSavedPath: String? = null,
    val message: String = "",
    val technicalDetail: String? = null,
) {
    val resolutionLabel: String?
        get() = if (actualWidth != null && actualHeight != null) "${actualWidth}×$actualHeight · UVC" else null
}

object RetryPolicy {
    val safeProfiles = listOf(
        CameraProfile(1280, 720, PixelFormat.MJPEG),
        CameraProfile(640, 480, PixelFormat.MJPEG),
        CameraProfile(640, 480, PixelFormat.YUYV),
    )

    fun profileAt(attempt: Int): CameraProfile? = safeProfiles.getOrNull(attempt)
}
