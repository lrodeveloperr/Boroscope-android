package com.worksbien.borescopedirect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.worksbien.borescopedirect.access.PreviewAccessStore
import com.worksbien.borescopedirect.billing.BillingManager
import com.worksbien.borescopedirect.camera.UvcCameraController
import com.worksbien.borescopedirect.media.MediaRepository
import com.worksbien.borescopedirect.media.SavedMedia
import com.worksbien.borescopedirect.ui.BorescopeApp
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private lateinit var controller: UvcCameraController
    private lateinit var billingManager: BillingManager
    private lateinit var mediaRepository: MediaRepository
    private lateinit var previewAccess: PreviewAccessStore
    private val reviewRequestInFlight = AtomicBoolean(false)

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> controller.onCameraPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaRepository = MediaRepository(this)
        previewAccess = PreviewAccessStore(this)
        billingManager = BillingManager(this)
        controller = UvcCameraController(
            context = this,
            mediaRepository = mediaRepository,
            requestCameraPermission = ::requestUsbCameraPermission,
        )

        setContent {
            val cameraState = controller.state.collectAsStateWithLifecycle().value
            val billingState = billingManager.state.collectAsStateWithLifecycle().value
            BorescopeApp(
                controller = controller,
                cameraState = cameraState,
                billingManager = billingManager,
                billingState = billingState,
                previewAccess = previewAccess,
                mediaRepository = mediaRepository,
                onShareReport = { shareText(controller.compatibilityReport()) },
                onShareMedia = ::shareMedia,
                onOpenMedia = ::openMedia,
                onPurchase = { billingManager.launchPurchase(this) },
                onRestore = billingManager::restorePurchases,
                onMaybeReview = ::maybeLaunchReview,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        billingManager.start()
        controller.start()
    }

    override fun onStop() {
        controller.stop()
        super.onStop()
    }

    override fun onDestroy() {
        controller.destroy()
        billingManager.close()
        super.onDestroy()
    }

    private fun shareText(text: String) {
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_report_subject))
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    getString(R.string.share_report_chooser),
                ),
            )
        }.onFailure {
            Toast.makeText(this, getString(R.string.share_report_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsbCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            controller.onCameraPermissionResult(true)
            return
        }
        val prompts = getSharedPreferences("permission_state", MODE_PRIVATE)
        val askedBefore = prompts.getBoolean("camera_asked", false)
        if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    },
                )
            }.onFailure {
                Toast.makeText(this, getString(R.string.settings_camera_access), Toast.LENGTH_LONG).show()
            }
        } else {
            prompts.edit().putBoolean("camera_asked", true).apply()
            runCatching { cameraPermission.launch(Manifest.permission.CAMERA) }.onFailure {
                Toast.makeText(this, getString(R.string.permission_prompt_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareMedia(media: SavedMedia) {
        if (!mediaRepository.isUsable(media.file)) {
            Toast.makeText(this, getString(R.string.capture_missing), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", media.file)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = if (media.isVideo) "video/mp4" else "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    getString(R.string.share_capture_chooser),
                ),
            )
        }.onFailure {
            Toast.makeText(this, getString(R.string.share_capture_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMedia(media: SavedMedia) {
        if (!mediaRepository.isUsable(media.file)) {
            Toast.makeText(this, getString(R.string.capture_missing), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", media.file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (media.isVideo) "video/mp4" else "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, getString(R.string.open_capture_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun maybeLaunchReview() {
        if (!previewAccess.shouldOfferReview()) return
        val reviewState = getSharedPreferences("review_state", MODE_PRIVATE)
        if (reviewState.getBoolean("requested", false) || !reviewRequestInFlight.compareAndSet(false, true)) return
        val manager = runCatching { ReviewManagerFactory.create(this) }.getOrElse {
            reviewRequestInFlight.set(false)
            return
        }
        val requestTask = runCatching { manager.requestReviewFlow() }.getOrElse {
            reviewRequestInFlight.set(false)
            return
        }
        requestTask.addOnCompleteListener { request ->
            if (!request.isSuccessful || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                reviewRequestInFlight.set(false)
                return@addOnCompleteListener
            }
            runCatching { manager.launchReviewFlow(this, request.result) }
                .onSuccess { flow ->
                    flow.addOnCompleteListener {
                        reviewState.edit().putBoolean("requested", true).apply()
                        reviewRequestInFlight.set(false)
                    }
                }
                .onFailure { reviewRequestInFlight.set(false) }
        }
    }
}
