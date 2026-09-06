package com.worksbien.borescopedirect.camera

/** Requires repeated, consistently sized frames over time before compatibility passes. */
class FrameStabilityProbe(
    private val requiredFrames: Int = 6,
    private val minimumSpanMillis: Long = 400L,
    private val maximumGapMillis: Long = 1_500L,
) {
    private var width = 0
    private var height = 0
    private var frames = 0
    private var firstFrameAt = 0L
    private var lastFrameAt = 0L
    private var streamFormat = ""

    init {
        require(requiredFrames > 0) { "requiredFrames must be positive" }
        require(minimumSpanMillis >= 0L) { "minimumSpanMillis cannot be negative" }
        require(maximumGapMillis > 0L) { "maximumGapMillis must be positive" }
    }

    @Synchronized
    fun offer(frameWidth: Int, frameHeight: Int, nowMillis: Long, format: String = ""): Boolean {
        if (frameWidth <= 0 || frameHeight <= 0) return false
        val changedStream = width != frameWidth || height != frameHeight || streamFormat != format
        val stalled = lastFrameAt > 0L && nowMillis - lastFrameAt > maximumGapMillis
        if (changedStream || stalled || nowMillis < lastFrameAt) {
            reset(frameWidth, frameHeight, nowMillis, format)
            return false
        }
        if (frames == 0) reset(frameWidth, frameHeight, nowMillis, format) else frames++
        lastFrameAt = nowMillis
        return frames >= requiredFrames && nowMillis - firstFrameAt >= minimumSpanMillis
    }

    @Synchronized
    fun clear() {
        width = 0
        height = 0
        frames = 0
        firstFrameAt = 0L
        lastFrameAt = 0L
        streamFormat = ""
    }

    private fun reset(frameWidth: Int, frameHeight: Int, nowMillis: Long, format: String) {
        width = frameWidth
        height = frameHeight
        streamFormat = format
        frames = 1
        firstFrameAt = nowMillis
        lastFrameAt = nowMillis
    }
}
