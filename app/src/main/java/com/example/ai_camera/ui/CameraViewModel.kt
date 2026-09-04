package com.example.ai_camera.ui

import android.app.Application
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_camera.camera.CameraController
import com.example.ai_camera.camera.CameraSpecs
import com.example.ai_camera.camera.CaptureSettings
import com.example.ai_camera.camera.LiveReadout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("ArrayInDataClass")
data class CameraUiState(
    val settings: CaptureSettings = CaptureSettings(),
    val specs: CameraSpecs? = null,
    val liveReadout: LiveReadout = LiveReadout(null, null, null),
    val histogram: IntArray? = null,
    val isCapturing: Boolean = false,
    val timerCountdown: Int = 0,
    val lastSavedUri: Uri? = null,
    val errorMessage: String? = null,
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = CameraController(application)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var currentLensFacing = CameraCharacteristics.LENS_FACING_BACK
    private var activeSurfaceTexture: SurfaceTexture? = null

    init {
        controller.startBackgroundThread()
        viewModelScope.launch {
            controller.liveReadout.collect { readout ->
                _uiState.update { it.copy(liveReadout = readout) }
            }
        }
        viewModelScope.launch {
            controller.histogram.collect { hist ->
                _uiState.update { it.copy(histogram = hist) }
            }
        }
    }

    fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture) {
        activeSurfaceTexture = surfaceTexture
        viewModelScope.launch { openAndStart(currentLensFacing, surfaceTexture) }
    }

    fun onSurfaceTextureDestroyed() {
        activeSurfaceTexture = null
        controller.close()
    }

    fun pause() {
        controller.close()
    }

    fun resume() {
        val texture = activeSurfaceTexture ?: return
        viewModelScope.launch { openAndStart(currentLensFacing, texture) }
    }

    fun switchCamera() {
        val texture = activeSurfaceTexture ?: return
        val newFacing = if (currentLensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        viewModelScope.launch { openAndStart(newFacing, texture) }
    }

    private suspend fun openAndStart(lensFacing: Int, surfaceTexture: SurfaceTexture) {
        try {
            val specs = controller.open(lensFacing)
            currentLensFacing = lensFacing
            val defaults = CaptureSettings.defaultsFor(specs, _uiState.value.settings)
            _uiState.update { it.copy(specs = specs, settings = defaults, errorMessage = null) }
            val previewSize = specs.chooseOptimalPreviewSize()
            controller.startSession(surfaceTexture, previewSize, defaults)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message ?: "Camera error") }
        }
    }

    fun updateSettings(transform: (CaptureSettings) -> CaptureSettings) {
        val updated = transform(_uiState.value.settings)
        _uiState.update { it.copy(settings = updated) }
        controller.updateSettings(updated)
    }

    fun tapToFocus(nx: Float, ny: Float) {
        if (_uiState.value.settings.focusMode != com.example.ai_camera.camera.FocusMode.AUTO) return
        controller.triggerTapToFocus(nx, ny)
    }

    fun capturePhoto() {
        if (_uiState.value.isCapturing) return
        val settings = _uiState.value.settings
        viewModelScope.launch {
            val seconds = settings.timer.seconds
            if (seconds > 0) {
                for (t in seconds downTo 1) {
                    _uiState.update { it.copy(timerCountdown = t) }
                    delay(1000)
                }
                _uiState.update { it.copy(timerCountdown = 0) }
            }
            _uiState.update { it.copy(isCapturing = true) }
            try {
                val result = controller.captureStill(settings)
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        lastSavedUri = result.jpegUri,
                        errorMessage = result.rawError,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCapturing = false, errorMessage = e.message ?: "Capture failed")
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        controller.close()
        controller.stopBackgroundThread()
        super.onCleared()
    }
}
