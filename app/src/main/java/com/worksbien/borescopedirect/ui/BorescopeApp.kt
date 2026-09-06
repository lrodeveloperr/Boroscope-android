package com.worksbien.borescopedirect.ui

import android.text.format.Formatter
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.worksbien.borescopedirect.BuildConfig
import com.worksbien.borescopedirect.R
import com.worksbien.borescopedirect.access.PreviewAccessStore
import com.worksbien.borescopedirect.billing.BillingManager
import com.worksbien.borescopedirect.billing.BillingUiState
import com.worksbien.borescopedirect.camera.CameraPhase
import com.worksbien.borescopedirect.camera.CameraUiState
import com.worksbien.borescopedirect.camera.UvcCameraController
import com.worksbien.borescopedirect.media.MediaRepository
import com.worksbien.borescopedirect.media.SavedMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import studio.gooduse.shell.GoodUseAppShell

private enum class AppPage { CAMERA, GALLERY, HELP }

@Composable
fun BorescopeApp(
    controller: UvcCameraController,
    cameraState: CameraUiState,
    billingManager: BillingManager,
    billingState: BillingUiState,
    previewAccess: PreviewAccessStore,
    mediaRepository: MediaRepository,
    onShareReport: () -> Unit,
    onShareMedia: (SavedMedia) -> Unit,
    onOpenMedia: (SavedMedia) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onMaybeReview: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(AppPage.CAMERA) }
    var mediaRevision by remember { mutableStateOf(0) }
    var sessionRecorded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = page != AppPage.CAMERA) { page = AppPage.CAMERA }
    GoodUseAppShell(
        config = borescopeShellConfig(stringResource(R.string.nav_camera)),
        currentRoute = "camera",
        currentScreenId = "camera",
        onNavigate = {},
        label = { it },
        icon = { _, _ -> },
    ) {
        when (page) {
            AppPage.CAMERA -> CameraPage(
                controller = controller,
                cameraState = cameraState,
                billingState = billingState,
                previewAccess = previewAccess,
                onGallery = { page = AppPage.GALLERY },
                onHelp = { page = AppPage.HELP },
                onPurchase = onPurchase,
                onRestore = onRestore,
                onDebugUnlock = billingManager::debugUnlock,
                onMediaChanged = { mediaRevision++ },
                onMaybeReview = onMaybeReview,
                sessionRecorded = sessionRecorded,
                onSessionRecorded = { sessionRecorded = true },
            )
            AppPage.GALLERY -> GalleryPage(
                mediaRepository = mediaRepository,
                mediaRevision = mediaRevision,
                onBack = { page = AppPage.CAMERA },
                onOpen = onOpenMedia,
                onShare = onShareMedia,
                onDelete = {
                    mediaRepository.delete(it).also { deleted ->
                        if (deleted) mediaRevision++
                    }
                },
            )
            AppPage.HELP -> HelpPage(
                report = controller.compatibilityReport(),
                onBack = { page = AppPage.CAMERA },
                onShareReport = onShareReport,
            )
        }
    }
}

@Composable
private fun CameraPage(
    controller: UvcCameraController,
    cameraState: CameraUiState,
    billingState: BillingUiState,
    previewAccess: PreviewAccessStore,
    onGallery: () -> Unit,
    onHelp: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDebugUnlock: () -> Unit,
    onMediaChanged: () -> Unit,
    onMaybeReview: () -> Unit,
    sessionRecorded: Boolean,
    onSessionRecorded: () -> Unit,
) {
    val liveViewDescription = stringResource(R.string.cd_usb_live_view)
    var remaining by remember { mutableLongStateOf(previewAccess.remainingMillis()) }
    var trialPhotoAvailable by remember { mutableStateOf(previewAccess.canTakeTrialPhoto()) }
    var trialVideoAvailable by remember { mutableStateOf(previewAccess.canRecordTrialVideo()) }

    LaunchedEffect(cameraState.phase, billingState.unlocked) {
        if (cameraState.phase != CameraPhase.LIVE || billingState.unlocked) return@LaunchedEffect
        var lastTick = SystemClock.elapsedRealtime()
        var lastSaved = remaining
        try {
            while (remaining > 0L) {
                delay(250L)
                val now = SystemClock.elapsedRealtime()
                remaining = PreviewAccessStore.remainingAfterConsumption(remaining, now - lastTick)
                lastTick = now
                if (lastSaved - remaining >= 1_000L || remaining == 0L) {
                    previewAccess.saveRemainingMillis(remaining)
                    lastSaved = remaining
                }
            }
        } finally {
            previewAccess.saveRemainingMillis(remaining)
        }
    }
    LaunchedEffect(cameraState.phase, sessionRecorded) {
        if (cameraState.phase == CameraPhase.LIVE && !sessionRecorded) {
            delay(MEANINGFUL_SESSION_MILLIS)
            previewAccess.recordSuccessfulSession()
            onSessionRecorded()
        }
    }
    val trialExpired = !billingState.unlocked && remaining <= 0L
    LaunchedEffect(trialExpired, billingState.unlocked) {
        if (trialExpired) controller.pauseForUnlock() else if (billingState.unlocked) controller.resumeAfterUnlock()
    }
    val localView = LocalView.current
    DisposableEffect(cameraState.phase, trialExpired) {
        localView.keepScreenOn = cameraState.phase == CameraPhase.LIVE && !trialExpired
        onDispose { localView.keepScreenOn = false }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                AspectRatioTextureView(context).also(controller::attachPreview)
            },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = liveViewDescription },
            onRelease = controller::detachPreview,
        )

        TopControls(
            cameraState = cameraState,
            onGallery = onGallery,
            onHelp = onHelp,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (cameraState.phase != CameraPhase.LIVE && cameraState.phase != CameraPhase.PAUSED_FOR_UNLOCK) {
            ConnectionCard(
                cameraState,
                controller::performPrimaryAction,
                Modifier.align(Alignment.Center),
            )
        }

        if (cameraState.phase == CameraPhase.LIVE && !trialExpired) {
            if (!billingState.unlocked) {
                TrialBadge(
                    remaining,
                    Modifier.align(Alignment.TopStart).padding(top = 70.dp, start = 12.dp),
                )
            }
            CaptureControls(
                state = cameraState,
                trialPhotoAvailable = trialPhotoAvailable,
                trialVideoAvailable = trialVideoAvailable,
                unlocked = billingState.unlocked,
                onPhoto = {
                    if (billingState.unlocked || trialPhotoAvailable) {
                        controller.takePhoto { success ->
                            if (success) {
                                previewAccess.recordCapture()
                                onMaybeReview()
                                if (!billingState.unlocked) {
                                    previewAccess.markTrialPhotoUsed()
                                    trialPhotoAvailable = false
                                }
                                onMediaChanged()
                            }
                        }
                    }
                },
                onVideo = {
                    if (billingState.unlocked || trialVideoAvailable || cameraState.recording) {
                        controller.toggleRecording(
                            maxDurationSeconds = if (billingState.unlocked) 0L else PreviewAccessStore.TRIAL_VIDEO_SECONDS,
                        ) { success ->
                            if (success) {
                                previewAccess.recordCapture()
                                onMaybeReview()
                                if (!billingState.unlocked) {
                                    previewAccess.markTrialVideoUsed()
                                    trialVideoAvailable = false
                                }
                                onMediaChanged()
                            }
                        }
                    }
                },
                onRotate = controller::rotate,
                onSwitch = controller::switchDevice,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (trialExpired && cameraState.phase == CameraPhase.PAUSED_FOR_UNLOCK) {
            UnlockCard(
                billingState = billingState,
                onPurchase = onPurchase,
                onRestore = onRestore,
                onDebugUnlock = onDebugUnlock,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun TopControls(
    cameraState: CameraUiState,
    onGallery: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color(0xCC071012), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(cameraState.message, cameraState.resolutionLabel).joinToString(" · "),
                    color = Color(0xFFB8D5D0),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            IconButton(onClick = onGallery, enabled = !cameraState.recording && !cameraState.captureBusy) {
                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.cd_saved_media), tint = Color.White)
            }
            IconButton(onClick = onHelp, enabled = !cameraState.recording && !cameraState.captureBusy) {
                Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.cd_help_report), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: CameraUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(24.dp).fillMaxWidth().heightIn(max = 520.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.phase) {
                CameraPhase.TESTING -> CircularProgressIndicator()
                CameraPhase.ERROR -> Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp),
                )
                else -> Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(state.message, style = MaterialTheme.typography.titleLarge)
            state.technicalDetail?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.phase == CameraPhase.TESTING) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.connection_safe_profile, state.attempt, state.attemptCount))
            }
            if (
                state.phase == CameraPhase.ERROR || state.phase == CameraPhase.CAMERA_PERMISSION_REQUIRED ||
                state.phase == CameraPhase.USB_PERMISSION_REQUIRED
            ) {
                Spacer(Modifier.height(16.dp))
                val actionLabel = when (state.phase) {
                    CameraPhase.CAMERA_PERMISSION_REQUIRED -> stringResource(R.string.action_allow_access)
                    CameraPhase.USB_PERMISSION_REQUIRED -> stringResource(R.string.action_allow_usb)
                    else -> stringResource(R.string.action_try_again)
                }
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
            if (state.phase == CameraPhase.WAITING_FOR_DEVICE) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.connection_steps))
            }
        }
    }
}

@Composable
private fun TrialBadge(remainingMillis: Long, modifier: Modifier = Modifier) {
    val seconds = ((remainingMillis + 999L) / 1000L).coerceAtLeast(0L)
    val minutesPart = seconds / 60L
    val secondsPart = seconds % 60L
    Surface(color = Color(0xDD102022), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Text(
            stringResource(R.string.trial_preview, "$minutesPart:${secondsPart.toString().padStart(2, '0')}"),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CaptureControls(
    state: CameraUiState,
    trialPhotoAvailable: Boolean,
    trialVideoAvailable: Boolean,
    unlocked: Boolean,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onRotate: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color(0xDD071012), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(stringResource(R.string.action_rotate), Icons.Default.ScreenRotation, onRotate, modifier = Modifier.weight(1f))
            if (state.devices.size > 1) {
                ControlButton(stringResource(R.string.action_lens), Icons.Default.Cameraswitch, onSwitch, modifier = Modifier.weight(1f))
            }
            ControlButton(
                label = stringResource(if (!unlocked && !trialPhotoAvailable) R.string.action_photo_used else R.string.action_photo),
                icon = Icons.Default.CameraAlt,
                onClick = onPhoto,
                enabled = !state.captureBusy && !state.recording && (unlocked || trialPhotoAvailable),
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                label = when {
                    state.recording -> stringResource(R.string.action_stop)
                    !unlocked && !trialVideoAvailable -> stringResource(R.string.action_video_used)
                    else -> stringResource(R.string.action_record)
                },
                icon = if (state.recording) Icons.Default.Stop else Icons.Default.Videocam,
                onClick = onVideo,
                enabled = state.recording || (!state.captureBusy && (unlocked || trialVideoAvailable)),
                accent = state.recording,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (accent) Color(0xFFE14A42) else Color(0xFF1D3935),
        ) {
            IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp)) {
                Icon(icon, contentDescription = label, tint = if (enabled) Color.White else Color.Gray)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun UnlockCard(
    billingState: BillingUiState,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDebugUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(24.dp).fillMaxWidth().heightIn(max = 520.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.unlock_body))
            Spacer(Modifier.height(18.dp))
            Button(onClick = onPurchase, enabled = billingState.canPurchase) {
                if (billingState.purchaseInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when {
                        billingState.price != null -> stringResource(R.string.unlock_price, billingState.price)
                        billingState.loading -> stringResource(R.string.unlock_checking)
                        else -> stringResource(R.string.unlock_unavailable)
                    },
                )
            }
            TextButton(
                onClick = onRestore,
                enabled = !billingState.restoreInProgress && !billingState.purchaseInProgress,
            ) {
                if (billingState.restoreInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.action_restore_purchase))
            }
            if (billingState.pendingPurchase) {
                Text(stringResource(R.string.payment_pending), style = MaterialTheme.typography.bodySmall)
            }
            billingState.message?.let { message ->
                Text(
                    message,
                    color = if (billingState.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDebugUnlock) { Text(stringResource(R.string.action_developer_unlock)) }
            }
        }
    }
}

@Composable
private fun GalleryPage(
    mediaRepository: MediaRepository,
    mediaRevision: Int,
    onBack: () -> Unit,
    onOpen: (SavedMedia) -> Unit,
    onShare: (SavedMedia) -> Unit,
    onDelete: (SavedMedia) -> Boolean,
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var deleteTarget by remember { mutableStateOf<SavedMedia?>(null) }
    var deleteError by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<List<SavedMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(mediaRevision) {
        loading = true
        media = withContext(Dispatchers.IO) { mediaRepository.list() }
        loading = false
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(stringResource(R.string.gallery_title), onBack)
        if (deleteError) {
            Text(
                stringResource(R.string.gallery_delete_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            media.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.gallery_empty_title))
                    Text(stringResource(R.string.gallery_empty_body), style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        pluralStringResource(
                            R.plurals.gallery_capture_count,
                            media.size,
                            media.size,
                            Formatter.formatShortFileSize(context, media.sumOf { it.sizeBytes }),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(media, key = { it.file.absolutePath }) { item ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(item) }) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            MediaThumbnail(item, mediaRepository)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dateFormatter.format(Date(item.modifiedAt)), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${stringResource(if (item.isVideo) R.string.media_video else R.string.media_photo)} · " +
                                        Formatter.formatShortFileSize(context, item.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            IconButton(onClick = { onShare(item) }) { Icon(Icons.Default.Share, stringResource(R.string.cd_share_file, item.file.name)) }
                            IconButton(onClick = { deleteTarget = item }) { Icon(Icons.Default.Delete, stringResource(R.string.cd_delete_file, item.file.name)) }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_capture_title)) },
            text = { Text(target.file.name) },
            confirmButton = {
                TextButton(onClick = {
                    deleteError = !onDelete(target)
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun MediaThumbnail(media: SavedMedia, repository: MediaRepository) {
    val thumbnail by produceState<android.graphics.Bitmap?>(initialValue = null, media.file.absolutePath) {
        value = withContext(Dispatchers.IO) { repository.loadThumbnail(media) }
    }
    Box(
        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val currentThumbnail = thumbnail
        if (currentThumbnail != null) {
            Image(
                bitmap = currentThumbnail.asImageBitmap(),
                contentDescription = stringResource(if (media.isVideo) R.string.cd_video_thumbnail else R.string.cd_photo_thumbnail),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(if (media.isVideo) Icons.Default.Videocam else Icons.Default.CameraAlt, contentDescription = null)
        }
    }
}

@Composable
private fun HelpPage(report: String, onBack: () -> Unit, onShareReport: () -> Unit) {
    var showTechnicalReport by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageHeader(stringResource(R.string.help_title), onBack) }
        item {
            Card { Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.help_works_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.help_works_body))
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.help_dual_lens_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.help_dual_lens_body))
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.help_privacy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.help_privacy_body))
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.help_safety_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.help_safety_body))
            } }
        }
        item { FilledTonalButton(onClick = onShareReport) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_share_report)) } }
        item {
            TextButton(onClick = { showTechnicalReport = !showTechnicalReport }) {
                Text(stringResource(if (showTechnicalReport) R.string.action_hide_report else R.string.action_show_report))
            }
        }
        if (showTechnicalReport) item { Text(report, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back)) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

private const val MEANINGFUL_SESSION_MILLIS = 30_000L
