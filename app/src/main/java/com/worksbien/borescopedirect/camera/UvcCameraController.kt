package com.worksbien.borescopedirect.camera

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbConstants
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.usb.USBMonitor
import com.worksbien.borescopedirect.BuildConfig
import com.worksbien.borescopedirect.R
import com.worksbien.borescopedirect.media.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class UvcCameraController(
    context: Context,
    private val mediaRepository: MediaRepository,
    private val requestCameraPermission: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val devices = ConcurrentHashMap<Int, UsbDevice>()
    private val attemptLog = mutableListOf<String>()
    private val stabilityProbe = FrameStabilityProbe()
    private val _state = MutableStateFlow(CameraUiState(message = text(R.string.camera_connect)))
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private var previewView: AspectRatioTextureView? = null
    private var pendingDevice: UsbDevice? = null
    private var currentDevice: UsbDevice? = null
    private var camera: CameraUVC? = null
    private var currentAttempt = 0
    @Volatile private var firstFrameSeen = false
    @Volatile private var cameraGeneration = 0L
    private var started = false
    private var frameTimeout: Runnable? = null
    private var delayedRetry: Runnable? = null
    private var recordingAutoStop: Runnable? = null
    private var captureTimeout: Runnable? = null
    private var retryScheduled = false
    private var captureOperation = 0L
    private var activeVideoFinish: ((Boolean, String?) -> Unit)? = null
    private var activeVideoOperation: Long? = null
    private var pauseRequested = false
    private val passPosted = AtomicBoolean(false)

    private val deviceCallback = object : IDeviceConnectCallBack {
        override fun onAttachDev(device: UsbDevice?) {
            device ?: return
            if (!isUsbVideoDevice(device)) return
            devices[device.deviceId] = device
            publishDevices()
            if (currentDevice == null && pendingDevice == null) beginConnection(device)
        }

        override fun onDetachDec(device: UsbDevice?) {
            device ?: return
            devices.remove(device.deviceId)
            val wasActive = currentDevice?.deviceId == device.deviceId || pendingDevice?.deviceId == device.deviceId
            if (wasActive) {
                stopCurrentCamera()
                pendingDevice = null
                currentDevice = null
                _state.value = CameraUiState(
                    devices = summaries(),
                    phase = CameraPhase.WAITING_FOR_DEVICE,
                    message = text(R.string.camera_unplugged),
                )
                devices.values.firstOrNull()?.let { beginConnection(it) }
            } else {
                publishDevices()
            }
        }

        override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            if (device == null || pendingDevice?.deviceId != device.deviceId) return
            if (ctrlBlock == null) {
                _state.value = _state.value.copy(
                    phase = CameraPhase.ERROR,
                    message = text(R.string.usb_open_failed),
                    technicalDetail = text(R.string.usb_open_retry_detail),
                )
                return
            }
            currentDevice = device
            pendingDevice = null
            cameraGeneration++
            currentAttempt = 0
            attemptLog.clear()
            val nextCamera = runCatching {
                CameraUVC(appContext, device).apply {
                    setUsbControlBlock(ctrlBlock)
                    setCameraStateCallBack(cameraStateCallback)
                }
            }.getOrElse { error ->
                currentDevice = null
                _state.value = _state.value.copy(
                    phase = CameraPhase.ERROR,
                    message = text(R.string.camera_init_failed),
                    technicalDetail = text(R.string.camera_driver_rejected),
                )
                return
            }
            camera = nextCamera
            openProfile()
        }

        override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            if (device?.deviceId == currentDevice?.deviceId) {
                stopCurrentCamera()
                currentDevice = null
                _state.value = _state.value.copy(
                    phase = CameraPhase.ERROR,
                    recording = false,
                    message = text(R.string.usb_lost),
                    technicalDetail = text(R.string.usb_lost_detail),
                )
            }
        }

        override fun onCancelDev(device: UsbDevice?) {
            if (device?.deviceId == pendingDevice?.deviceId) {
                _state.value = _state.value.copy(
                    phase = CameraPhase.USB_PERMISSION_REQUIRED,
                    message = text(R.string.usb_denied),
                    technicalDetail = text(R.string.usb_denied_detail),
                )
            }
        }
    }

    private val client = MultiCameraClient(appContext, deviceCallback)

    private val cameraStateCallback = object : ICameraStateCallBack {
        override fun onCameraState(
            self: MultiCameraClient.ICamera,
            code: ICameraStateCallBack.State,
            msg: String?,
        ) {
            if (self !== camera) return
            when (code) {
                ICameraStateCallBack.State.OPENED -> Unit // A real preview frame completes the test.
                ICameraStateCallBack.State.CLOSED -> Unit
                ICameraStateCallBack.State.ERROR -> tryNextProfile(text(R.string.camera_open_failed))
            }
        }
    }

    private val frameCallback = object : IPreviewDataCallBack {
        override fun onPreviewData(
            data: ByteArray?,
            width: Int,
            height: Int,
            format: IPreviewDataCallBack.DataFormat,
        ) {
            if (firstFrameSeen || data.isNullOrEmpty()) return
            if (!stabilityProbe.offer(width, height, SystemClock.elapsedRealtime(), format.name)) return
            if (!passPosted.compareAndSet(false, true)) return
            val generation = cameraGeneration
            handler.post {
                if (generation != cameraGeneration || camera == null || _state.value.phase != CameraPhase.TESTING) return@post
                firstFrameSeen = true
                clearFrameTimeout()
                runCatching { camera?.removePreviewDataCallBack(this) }
                val profile = RetryPolicy.profileAt(currentAttempt)
                attemptLog += text(R.string.attempt_pass, profile?.label ?: text(R.string.attempt_unknown), width, height)
                _state.value = _state.value.copy(
                    phase = CameraPhase.LIVE,
                    profile = profile,
                    actualWidth = width,
                    actualHeight = height,
                    message = text(R.string.camera_ready),
                    technicalDetail = null,
                )
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            client.register()
            client.getDeviceList().orEmpty().forEach { deviceCallback.onAttachDev(it) }
        }.onFailure { error ->
            runCatching { client.unRegister() }
            started = false
            _state.value = CameraUiState(
                phase = CameraPhase.ERROR,
                message = text(R.string.usb_monitor_start_failed),
                technicalDetail = text(R.string.usb_monitor_start_detail),
            )
        }
    }

    fun stop() {
        val wasStarted = started
        started = false
        stopCurrentCamera()
        pendingDevice = null
        currentDevice = null
        devices.clear()
        if (wasStarted) runCatching { client.unRegister() }
        _state.value = CameraUiState(message = text(R.string.camera_connect))
    }

    fun destroy() {
        stop()
        runCatching { client.destroy() }
    }

    fun attachPreview(view: AspectRatioTextureView) {
        previewView = view
        if (
            camera != null &&
            !firstFrameSeen &&
            _state.value.phase == CameraPhase.TESTING
        ) {
            openProfile()
        }
    }

    fun detachPreview(view: AspectRatioTextureView) {
        if (previewView === view) {
            previewView = null
            if (camera != null && _state.value.phase != CameraPhase.PAUSED_FOR_UNLOCK) {
                cameraGeneration++
                clearFrameTimeout()
                clearDelayedRetry()
                passPosted.set(false)
                stabilityProbe.clear()
                runCatching { camera?.removePreviewDataCallBack(frameCallback) }
                runCatching { camera?.closeCamera() }
                firstFrameSeen = false
                _state.value = _state.value.copy(
                    phase = CameraPhase.TESTING,
                    recording = false,
                    message = text(R.string.camera_paused),
                    technicalDetail = text(R.string.camera_resuming),
                )
            }
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        val device = pendingDevice ?: devices.values.firstOrNull() ?: return
        if (granted) requestUsbPermission(device) else {
            _state.value = _state.value.copy(
                phase = CameraPhase.CAMERA_PERMISSION_REQUIRED,
                message = text(R.string.camera_permission_needed),
                technicalDetail = text(R.string.camera_permission_detail),
            )
        }
    }

    fun performPrimaryAction() {
        when (_state.value.phase) {
            CameraPhase.CAMERA_PERMISSION_REQUIRED -> requestCameraPermission()
            CameraPhase.USB_PERMISSION_REQUIRED -> {
                (pendingDevice ?: devices.values.firstOrNull())?.let(::requestUsbPermission)
            }
            CameraPhase.ERROR -> retry()
            else -> Unit
        }
    }

    fun retry() {
        if (!started) {
            start()
            return
        }
        val device = currentDevice ?: pendingDevice ?: devices.values.firstOrNull()
        if (device == null) {
            _state.value = CameraUiState(message = text(R.string.camera_connect))
            return
        }
        stopCurrentCamera()
        currentDevice = null
        pendingDevice = null
        beginConnection(device)
    }

    fun switchDevice() {
        if (devices.size < 2 || _state.value.recording || _state.value.captureBusy) return
        val ids = devices.keys.toList().sorted()
        val currentId = currentDevice?.deviceId ?: pendingDevice?.deviceId
        val nextIndex = ((ids.indexOf(currentId) + 1).coerceAtLeast(0)) % ids.size
        val next = devices[ids[nextIndex]] ?: return
        stopCurrentCamera()
        currentDevice = null
        pendingDevice = null
        beginConnection(next)
    }

    fun rotate() {
        if (_state.value.phase != CameraPhase.LIVE) return
        val degrees = (_state.value.rotationDegrees + 90) % 360
        val type = when (degrees) {
            90 -> RotateType.ANGLE_90
            180 -> RotateType.ANGLE_180
            270 -> RotateType.ANGLE_270
            else -> RotateType.ANGLE_0
        }
        runCatching { camera?.setRotateType(type) }
            .onSuccess { _state.value = _state.value.copy(rotationDegrees = degrees) }
            .onFailure { error ->
                _state.value = _state.value.copy(
                    message = text(R.string.rotation_failed),
                    technicalDetail = text(R.string.driver_stopped),
                )
            }
    }

    fun takePhoto(onComplete: (Boolean) -> Unit = {}) {
        val activeCamera = camera
        if (
            _state.value.phase != CameraPhase.LIVE || activeCamera == null ||
            _state.value.captureBusy || _state.value.recording
        ) return
        if (!mediaRepository.hasSpaceForPhoto()) {
            _state.value = _state.value.copy(
                message = text(R.string.photo_storage_low),
                technicalDetail = text(R.string.photo_storage_detail),
            )
            onComplete(false)
            return
        }
        val output = runCatching { mediaRepository.newPhotoFile() }.getOrElse { error ->
            _state.value = _state.value.copy(message = text(R.string.photo_folder_unavailable), technicalDetail = text(R.string.driver_stopped))
            onComplete(false)
            return
        }
        val operation = ++captureOperation
        val generation = cameraGeneration
        val finished = AtomicBoolean(false)
        fun finish(success: Boolean, error: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            clearCaptureTimeout()
            val usable = success && mediaRepository.isUsablePhoto(output)
            if (!usable) mediaRepository.discard(output)
            if (operation == captureOperation && generation == cameraGeneration) {
                _state.value = _state.value.copy(
                    captureBusy = false,
                    lastSavedPath = if (usable) output.absolutePath else _state.value.lastSavedPath,
                    message = if (usable) text(R.string.photo_saved) else text(R.string.photo_not_saved),
                    technicalDetail = if (usable) null else error ?: text(R.string.photo_empty),
                )
                onComplete(usable)
                pauseIfReady()
            }
        }
        _state.value = _state.value.copy(captureBusy = true, message = text(R.string.photo_saving))
        captureTimeout = Runnable { finish(false, text(R.string.photo_timeout)) }
            .also { handler.postDelayed(it, PHOTO_TIMEOUT_MILLIS) }
        runCatching {
            activeCamera.captureImage(object : ICaptureCallBack {
                override fun onBegin() = Unit
                override fun onError(error: String?) = finish(false, text(R.string.photo_request_rejected))
                override fun onComplete(path: String?) = finish(true)
            }, output.absolutePath)
        }.onFailure { finish(false, text(R.string.photo_request_rejected)) }
    }

    fun toggleRecording(maxDurationSeconds: Long = 0L, onComplete: (Boolean) -> Unit = {}) {
        val activeCamera = camera ?: return
        if (_state.value.recording) {
            stopRecording()
            return
        }
        if (_state.value.phase != CameraPhase.LIVE || _state.value.captureBusy) return
        if (!mediaRepository.hasSpaceForVideo()) {
            _state.value = _state.value.copy(
                message = text(R.string.video_storage_low),
                technicalDetail = text(R.string.video_storage_detail),
            )
            onComplete(false)
            return
        }
        val output = runCatching { mediaRepository.newVideoFile() }.getOrElse { error ->
            _state.value = _state.value.copy(message = text(R.string.video_folder_unavailable), technicalDetail = text(R.string.driver_stopped))
            onComplete(false)
            return
        }
        val operation = ++captureOperation
        val generation = cameraGeneration
        val finished = AtomicBoolean(false)
        fun finish(success: Boolean, error: String? = null) {
            if (!finished.compareAndSet(false, true)) return
            clearCaptureTimeout()
            clearRecordingAutoStop()
            if (activeVideoOperation == operation) {
                activeVideoFinish = null
                activeVideoOperation = null
            }
            val finalFile = if (success) mediaRepository.finalizeVideo(output) else null
            if (finalFile == null) mediaRepository.discard(output)
            if (operation == captureOperation && generation == cameraGeneration) {
                _state.value = _state.value.copy(
                    recording = false,
                    captureBusy = false,
                    lastSavedPath = finalFile?.absolutePath ?: _state.value.lastSavedPath,
                    message = if (finalFile != null) text(R.string.video_saved) else text(R.string.video_not_saved),
                    technicalDetail = if (finalFile != null) null else error ?: text(R.string.video_incomplete),
                )
                onComplete(finalFile != null)
                pauseIfReady()
            }
        }
        activeVideoOperation = operation
        activeVideoFinish = ::finish
        _state.value = _state.value.copy(captureBusy = true, message = text(R.string.video_starting), technicalDetail = null)
        captureTimeout = Runnable {
            finish(false, text(R.string.video_start_timeout))
        }.also { handler.postDelayed(it, RECORDING_START_TIMEOUT_MILLIS) }
        runCatching {
            activeCamera.captureVideoStart(object : ICaptureCallBack {
                override fun onBegin() {
                    if (finished.get() || operation != captureOperation || generation != cameraGeneration) return
                    clearCaptureTimeout()
                    _state.value = _state.value.copy(recording = true, captureBusy = false, message = text(R.string.video_recording))
                    if (pauseRequested) {
                        stopRecording()
                        return
                    }
                    if (maxDurationSeconds > 0L) {
                        recordingAutoStop = Runnable(::stopRecording).also {
                            handler.postDelayed(it, maxDurationSeconds * 1_000L)
                        }
                    }
                }
                override fun onError(error: String?) = finish(false, text(R.string.video_request_rejected))
                override fun onComplete(path: String?) = finish(true)
            }, output.absolutePath, 0L)
        }.onFailure { finish(false, text(R.string.video_request_rejected)) }
    }

    fun stopRecording() {
        if (_state.value.recording) {
            clearRecordingAutoStop()
            _state.value = _state.value.copy(recording = false, captureBusy = true, message = text(R.string.video_finishing))
            clearCaptureTimeout()
            captureTimeout = Runnable {
                activeVideoFinish?.invoke(true, text(R.string.video_finalize_timeout))
            }.also { handler.postDelayed(it, RECORDING_FINISH_TIMEOUT_MILLIS) }
            runCatching { camera?.captureVideoStop() }.onFailure { error ->
                activeVideoFinish?.invoke(false, text(R.string.video_stop_failed))
            }
        }
    }

    fun pauseForUnlock() {
        pauseRequested = true
        if (_state.value.recording) stopRecording()
        pauseIfReady()
    }

    fun resumeAfterUnlock() {
        if (_state.value.phase != CameraPhase.PAUSED_FOR_UNLOCK) return
        pauseRequested = false
        if (camera == null || currentDevice == null) {
            retry()
            return
        }
        if (previewView != null) openProfile()
    }

    private fun pauseIfReady() {
        if (!pauseRequested || _state.value.recording || _state.value.captureBusy) return
        clearFrameTimeout()
        clearDelayedRetry()
        cameraGeneration++
        passPosted.set(false)
        runCatching { camera?.removePreviewDataCallBack(frameCallback) }
        runCatching { camera?.closeCamera() }
        firstFrameSeen = false
        stabilityProbe.clear()
        _state.value = _state.value.copy(
            phase = CameraPhase.PAUSED_FOR_UNLOCK,
            recording = false,
            captureBusy = false,
            message = text(R.string.compatibility_confirmed),
            technicalDetail = null,
        )
    }

    fun compatibilityReport(): String = buildString {
        appendLine(text(R.string.report_title))
        appendLine(text(R.string.report_app, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        appendLine(text(R.string.report_phone, Build.MANUFACTURER, Build.MODEL))
        appendLine(text(R.string.report_android_api, Build.VERSION.SDK_INT))
        appendLine(text(R.string.report_engine))
        appendLine(text(R.string.report_connected_devices, devices.size))
        devices.values.sortedBy { it.deviceId }.forEachIndexed { index, device ->
            appendLine(
                text(
                    R.string.report_device,
                    index + 1,
                    hex(device.vendorId),
                    hex(device.productId),
                    runCatching { device.interfaceCount }.getOrDefault(-1),
                ),
            )
        }
        appendLine(text(R.string.report_result, phaseLabel(_state.value.phase)))
        _state.value.profile?.let { appendLine(text(R.string.report_working_profile, it.label)) }
        _state.value.resolutionLabel?.let { appendLine(text(R.string.report_observed_output, it)) }
        if (attemptLog.isNotEmpty()) {
            appendLine(text(R.string.report_attempts))
            attemptLog.forEach { appendLine("- $it") }
        }
        _state.value.technicalDetail?.let { appendLine(text(R.string.report_last_detail, it)) }
        appendLine(text(R.string.report_privacy))
    }

    private fun beginConnection(device: UsbDevice) {
        pendingDevice = device
        if (!hasCameraPermission()) {
            _state.value = _state.value.copy(
                phase = CameraPhase.CAMERA_PERMISSION_REQUIRED,
                devices = summaries(),
                activeDeviceId = device.deviceId,
                message = text(R.string.allow_usb_video),
                technicalDetail = text(R.string.allow_usb_video_detail),
            )
        } else {
            requestUsbPermission(device)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        pendingDevice = device
        _state.value = _state.value.copy(
            phase = CameraPhase.USB_PERMISSION_REQUIRED,
            devices = summaries(),
            activeDeviceId = device.deviceId,
            message = text(R.string.allow_usb_scope),
            technicalDetail = text(R.string.usb_system_prompt),
        )
        val requested = started && runCatching { client.requestPermission(device) }.getOrDefault(false)
        if (!requested) {
            _state.value = _state.value.copy(
                phase = CameraPhase.ERROR,
                message = text(R.string.usb_monitor_not_ready),
                technicalDetail = text(R.string.usb_monitor_not_ready_detail),
            )
        }
    }

    private fun openProfile() {
        val activeCamera = camera ?: return
        val view = previewView ?: return
        val profile = RetryPolicy.profileAt(currentAttempt) ?: return failAllProfiles()
        clearDelayedRetry()
        retryScheduled = false
        firstFrameSeen = false
        passPosted.set(false)
        stabilityProbe.clear()
        runCatching { activeCamera.removePreviewDataCallBack(frameCallback) }
        runCatching { activeCamera.addPreviewDataCallBack(frameCallback) }.onFailure { error ->
            failWithDriverError(text(R.string.preview_callback_failed), error)
            return
        }
        attemptLog += text(R.string.attempt_try, profile.label)
        _state.value = _state.value.copy(
            phase = CameraPhase.TESTING,
            profile = profile,
            actualWidth = null,
            actualHeight = null,
            attempt = currentAttempt + 1,
            activeDeviceId = currentDevice?.deviceId,
            devices = summaries(),
            message = text(R.string.testing_profile, profile.label),
            technicalDetail = text(R.string.waiting_stable_frame),
        )
        val request = runCatching {
            CameraRequest.Builder()
                .setPreviewWidth(profile.width)
                .setPreviewHeight(profile.height)
                .setPreviewFormat(
                    if (profile.format == PixelFormat.MJPEG) {
                        CameraRequest.PreviewFormat.FORMAT_MJPEG
                    } else {
                        CameraRequest.PreviewFormat.FORMAT_YUYV
                    },
                )
                .setRenderMode(CameraRequest.RenderMode.OPENGL)
                .setAudioSource(CameraRequest.AudioSource.NONE)
                .setRawPreviewData(true)
                .setAspectRatioShow(true)
                .setDefaultRotateType(rotateTypeFor(_state.value.rotationDegrees))
                .create()
        }.getOrElse { error ->
            tryNextProfile(text(R.string.profile_create_failed))
            return
        }
        clearFrameTimeout()
        clearRecordingAutoStop()
        frameTimeout = Runnable { if (!firstFrameSeen) tryNextProfile(text(R.string.no_stable_video)) }
            .also { handler.postDelayed(it, FRAME_TIMEOUT_MILLIS) }
        runCatching { activeCamera.openCamera(view, request) }
            .onFailure { tryNextProfile(text(R.string.profile_rejected)) }
    }

    private fun tryNextProfile(reason: String) {
        if (retryScheduled || _state.value.phase != CameraPhase.TESTING) return
        retryScheduled = true
        clearFrameTimeout()
        val failed = RetryPolicy.profileAt(currentAttempt)
        attemptLog += text(R.string.attempt_fail, failed?.label ?: text(R.string.attempt_unknown), reason)
        runCatching { camera?.closeCamera() }
        currentAttempt += 1
        val generation = cameraGeneration
        delayedRetry = Runnable {
            if (generation != cameraGeneration || !started || camera == null) return@Runnable
            retryScheduled = false
            if (RetryPolicy.profileAt(currentAttempt) == null) failAllProfiles() else openProfile()
        }.also { handler.postDelayed(it, REOPEN_DELAY_MILLIS) }
    }

    private fun failAllProfiles() {
        retryScheduled = false
        clearDelayedRetry()
        _state.value = _state.value.copy(
            phase = CameraPhase.ERROR,
            message = text(R.string.no_uvc_format),
            technicalDetail = text(R.string.no_uvc_format_detail),
        )
    }

    private fun stopCurrentCamera() {
        clearFrameTimeout()
        clearDelayedRetry()
        clearCaptureTimeout()
        clearRecordingAutoStop()
        cameraGeneration++
        captureOperation++
        retryScheduled = false
        pauseRequested = false
        activeVideoFinish = null
        activeVideoOperation = null
        passPosted.set(false)
        stabilityProbe.clear()
        runCatching { camera?.removePreviewDataCallBack(frameCallback) }
        if (_state.value.recording) runCatching { camera?.captureVideoStop() }
        runCatching { camera?.setCameraStateCallBack(null) }
        runCatching { camera?.closeCamera() }
        camera = null
        firstFrameSeen = false
        _state.value = _state.value.copy(recording = false, captureBusy = false)
    }

    private fun clearFrameTimeout() {
        frameTimeout?.let(handler::removeCallbacks)
        frameTimeout = null
    }

    private fun clearRecordingAutoStop() {
        recordingAutoStop?.let(handler::removeCallbacks)
        recordingAutoStop = null
    }

    private fun clearDelayedRetry() {
        delayedRetry?.let(handler::removeCallbacks)
        delayedRetry = null
    }

    private fun clearCaptureTimeout() {
        captureTimeout?.let(handler::removeCallbacks)
        captureTimeout = null
    }

    private fun rotateTypeFor(degrees: Int): RotateType = when (((degrees % 360) + 360) % 360) {
        90 -> RotateType.ANGLE_90
        180 -> RotateType.ANGLE_180
        270 -> RotateType.ANGLE_270
        else -> RotateType.ANGLE_0
    }

    private fun isUsbVideoDevice(device: UsbDevice): Boolean = runCatching {
        device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
            (0 until device.interfaceCount).any { index ->
                device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_VIDEO
            }
    }.getOrDefault(false)

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        android.Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    private fun publishDevices() {
        _state.value = _state.value.copy(devices = summaries())
    }

    private fun summaries(): List<DeviceSummary> = devices.values.sortedBy { it.deviceId }.map { device ->
        DeviceSummary(
            deviceId = device.deviceId,
            label = runCatching { device.productName }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: text(R.string.usb_camera_label, hex(device.vendorId), hex(device.productId)),
            vendorId = device.vendorId,
            productId = device.productId,
        )
    }

    private fun hex(value: Int): String = String.format(Locale.US, "%04X", value)

    private fun failWithDriverError(message: String, error: Throwable) {
        clearFrameTimeout()
        clearDelayedRetry()
        retryScheduled = false
        _state.value = _state.value.copy(
            phase = CameraPhase.ERROR,
            message = message,
            technicalDetail = text(R.string.driver_stopped),
        )
    }

    private fun phaseLabel(phase: CameraPhase): String = text(
        when (phase) {
            CameraPhase.WAITING_FOR_DEVICE -> R.string.phase_waiting
            CameraPhase.CAMERA_PERMISSION_REQUIRED -> R.string.phase_camera_permission
            CameraPhase.USB_PERMISSION_REQUIRED -> R.string.phase_usb_permission
            CameraPhase.TESTING -> R.string.phase_testing
            CameraPhase.LIVE -> R.string.phase_live
            CameraPhase.PAUSED_FOR_UNLOCK -> R.string.phase_paused_unlock
            CameraPhase.ERROR -> R.string.phase_error
        },
    )

    private fun text(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    companion object {
        private const val FRAME_TIMEOUT_MILLIS = 8_000L
        private const val REOPEN_DELAY_MILLIS = 450L
        private const val PHOTO_TIMEOUT_MILLIS = 8_000L
        private const val RECORDING_START_TIMEOUT_MILLIS = 6_000L
        private const val RECORDING_FINISH_TIMEOUT_MILLIS = 8_000L
    }
}
