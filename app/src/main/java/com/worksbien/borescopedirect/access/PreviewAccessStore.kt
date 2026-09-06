package com.worksbien.borescopedirect.access

import android.content.Context

class PreviewAccessStore(context: Context) {
    private val preferences = context.getSharedPreferences("preview_access", Context.MODE_PRIVATE)

    fun remainingMillis(): Long = preferences.getLong(KEY_REMAINING_MILLIS, TRIAL_DURATION_MILLIS)
        .coerceIn(0L, TRIAL_DURATION_MILLIS)

    fun saveRemainingMillis(value: Long) {
        preferences.edit()
            .putLong(KEY_REMAINING_MILLIS, value.coerceIn(0L, TRIAL_DURATION_MILLIS))
            .remove(LEGACY_KEY_STARTED_AT)
            .apply()
    }

    fun canTakeTrialPhoto(): Boolean = !preferences.getBoolean(KEY_PHOTO_USED, false)

    fun markTrialPhotoUsed() {
        preferences.edit().putBoolean(KEY_PHOTO_USED, true).apply()
    }

    fun canRecordTrialVideo(): Boolean = !preferences.getBoolean(KEY_VIDEO_USED, false)

    fun markTrialVideoUsed() {
        preferences.edit().putBoolean(KEY_VIDEO_USED, true).apply()
    }

    fun recordSuccessfulSession() {
        val next = (successfulSessions().toLong() + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        preferences.edit().putInt(KEY_SUCCESSFUL_SESSIONS, next).apply()
    }

    fun successfulSessions(): Int = preferences.getInt(KEY_SUCCESSFUL_SESSIONS, 0).coerceAtLeast(0)

    fun recordCapture() {
        preferences.edit().putBoolean(KEY_HAS_CAPTURE, true).apply()
    }

    fun shouldOfferReview(): Boolean = successfulSessions() >= 3 && preferences.getBoolean(KEY_HAS_CAPTURE, false)

    companion object {
        const val TRIAL_DURATION_MILLIS = 90_000L
        const val TRIAL_VIDEO_SECONDS = 10L

        fun remainingAfterConsumption(remainingMillis: Long, elapsedLiveMillis: Long): Long =
            (remainingMillis.coerceIn(0L, TRIAL_DURATION_MILLIS) - elapsedLiveMillis.coerceAtLeast(0L))
                .coerceAtLeast(0L)

        private const val KEY_REMAINING_MILLIS = "remaining_millis"
        private const val LEGACY_KEY_STARTED_AT = "started_at"
        private const val KEY_PHOTO_USED = "trial_photo_used"
        private const val KEY_VIDEO_USED = "trial_video_used"
        private const val KEY_SUCCESSFUL_SESSIONS = "successful_sessions"
        private const val KEY_HAS_CAPTURE = "has_capture"
    }
}
