package com.worksbien.borescopedirect.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedMedia(
    val file: File,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

class MediaRepository(private val context: Context) {
    private val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun newPhotoFile(): File = uniqueCaptureFile(photoDirectory(), ".jpg")

    fun newVideoFile(): File = uniqueCaptureFile(videoDirectory(), ".recording.mp4")

    fun list(): List<SavedMedia> {
        return runCatching {
            discardStalePartialVideos()
            val photos: List<File> = photoDirectory().listFiles()?.toList().orEmpty()
            val videos: List<File> = videoDirectory().listFiles()?.toList().orEmpty()
            photos + videos
        }.getOrDefault(emptyList())
        .distinctBy { it.absolutePath }
        .filter {
            it.isFile && it.length() > 0L && !it.name.contains(".recording.") &&
                (it.extension.equals("jpg", true) || it.extension.equals("mp4", true))
        }
        .map { file ->
            SavedMedia(
                file = file,
                isVideo = file.extension.equals("mp4", true),
                sizeBytes = file.length(),
                modifiedAt = file.lastModified(),
            )
        }
        .sortedByDescending { it.modifiedAt }
    }

    fun delete(media: SavedMedia): Boolean = !media.file.exists() || media.file.delete()

    fun isUsable(file: File, minimumBytes: Long = 1L): Boolean =
        file.isFile && file.length() >= minimumBytes

    fun isUsablePhoto(file: File): Boolean = runCatching {
        if (!isUsable(file)) return@runCatching false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        bounds.outWidth > 0 && bounds.outHeight > 0
    }.getOrDefault(false)

    fun discard(file: File) {
        if (file.exists()) file.delete()
    }

    fun finalizeVideo(file: File): File? {
        if (!isUsable(file, MINIMUM_VALID_VIDEO_BYTES)) return null
        val parent = file.parentFile ?: return null
        if (!file.name.endsWith(".recording.mp4", ignoreCase = true)) return null
        val finalFile = File(parent, file.name.dropLast(".recording.mp4".length) + ".mp4")
        val moved = if (file.renameTo(finalFile)) {
            true
        } else {
            runCatching {
                file.copyTo(finalFile, overwrite = true)
                file.delete()
                true
            }.getOrElse {
                discard(finalFile)
                false
            }
        }
        if (!moved || !isPlayableVideo(finalFile)) {
            discard(finalFile)
            return null
        }
        return finalFile
    }

    fun loadThumbnail(media: SavedMedia, targetPixels: Int = 192): Bitmap? = runCatching {
        val safeTarget = targetPixels.coerceAtLeast(1)
        if (media.isVideo) videoThumbnail(media.file, safeTarget) else photoThumbnail(media.file, safeTarget)
    }.getOrNull()

    fun hasSpaceForVideo(): Boolean = runCatching {
        requireDirectory(videoDirectory()).usableSpace >= MINIMUM_VIDEO_SPACE_BYTES
    }.getOrDefault(false)

    fun hasSpaceForPhoto(): Boolean = runCatching {
        requireDirectory(photoDirectory()).usableSpace >= MINIMUM_PHOTO_SPACE_BYTES
    }.getOrDefault(false)

    @Synchronized
    private fun uniqueCaptureFile(directory: File, suffix: String): File {
        val safeDirectory = requireDirectory(directory)
        val base = "BORE_${timestamp.format(Date())}"
        var candidate = safeDirectory.resolve(base + suffix)
        var duplicate = 1
        while (candidate.exists()) {
            candidate = safeDirectory.resolve("${base}_$duplicate$suffix")
            duplicate++
        }
        return candidate
    }

    private fun photoDirectory(): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, Environment.DIRECTORY_PICTURES),
        "BorescopeDirect",
    ).apply { mkdirs() }

    private fun videoDirectory(): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, Environment.DIRECTORY_MOVIES),
        "BorescopeDirect",
    ).apply { mkdirs() }

    private fun requireDirectory(directory: File): File {
        check((directory.exists() || directory.mkdirs()) && directory.isDirectory) {
            "Local capture folder is unavailable"
        }
        return directory
    }

    private fun discardStalePartialVideos(nowMillis: Long = System.currentTimeMillis()) {
        videoDirectory().listFiles().orEmpty()
            .filter {
                val clockDistance = kotlin.math.abs(nowMillis - it.lastModified())
                it.name.contains(".recording.") && clockDistance > STALE_PARTIAL_AGE_MILLIS
            }
            .forEach(::discard)
    }

    private fun isPlayableVideo(file: File): Boolean = runCatching {
        if (!isUsable(file, MINIMUM_VALID_VIDEO_BYTES)) return@runCatching false
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            duration != null && duration > 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(false)

    private fun photoThumbnail(file: File, targetPixels: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetPixels * 2 || bounds.outHeight / sampleSize > targetPixels * 2) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) },
        )?.scaledToFit(targetPixels)
    }

    private fun videoThumbnail(file: File, targetPixels: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.scaledToFit(targetPixels)
        } finally {
            retriever.release()
        }
    }

    private fun Bitmap.scaledToFit(targetPixels: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= targetPixels || longest <= 0) return this
        val scale = targetPixels.toFloat() / longest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== this) recycle()
        return scaled
    }

    companion object {
        private const val MINIMUM_PHOTO_SPACE_BYTES = 10L * 1024L * 1024L
        private const val MINIMUM_VIDEO_SPACE_BYTES = 150L * 1024L * 1024L
        private const val MINIMUM_VALID_VIDEO_BYTES = 1_024L
        private const val STALE_PARTIAL_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
