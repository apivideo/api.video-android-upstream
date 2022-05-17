package video.api.upstream.example.main

import android.Manifest
import android.app.Application
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import video.api.upstream.ApiVideoUpstream
import video.api.upstream.enums.CameraFacingDirection
import video.api.upstream.enums.Resolution
import video.api.upstream.example.utils.Configuration
import video.api.upstream.models.AudioConfig
import video.api.upstream.models.VideoConfig
import video.api.upstream.views.ApiVideoView

class PreviewViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "PreviewViewModel"
    }

    private lateinit var upStream: ApiVideoUpstream
    private val configuration = Configuration(getApplication())
    private val listener = object : ApiVideoUpstream.Listener {
        override fun onError(error: Exception) {
            Log.e(TAG, "onError: ", error)
            this@PreviewViewModel.error.postValue(error.message ?: "Unknown error")
        }

        override fun onUploadError(partId: Int, error: Exception) {
            Log.e(TAG, "onUploadError: ", error)
            message.postValue(error.message ?: "Unknown error")
        }

        override fun onPartUploadStarted(partId: Int) {
            Log.i(TAG, "onNewPartsUploadStarted: $partId")
            currentPartId.postValue(partId)
        }

        override fun onTotalNumberOfPartsChanged(totalNumberOfParts: Int) {
            Log.i(TAG, "onTotalNumberOfPartsChanged: $totalNumberOfParts")
            totalNumOfParts.postValue(totalNumberOfParts)
        }

        override fun onPartUploadProgressChanged(partId: Int, progress: Float) {
            // Log.i(TAG, "onPartUploadProgressChanged: $partId: $progress")
            currentPartProgress.postValue(progress)
        }

        override fun onUploadStop(success: Boolean) {
            showProgress.postValue(false)
            uploadStopped.postValue(success)
        }
    }

    val error = MutableLiveData<String>()
    val message = MutableLiveData<String>()
    val currentPartId = MutableLiveData<Int>()
    val totalNumOfParts = MutableLiveData<Int>()
    val currentPartProgress = MutableLiveData<Float>()
    val showProgress = MutableLiveData<Boolean>()
    val uploadStopped = MutableLiveData<Boolean>()

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
    fun buildUpStream(apiVideoView: ApiVideoView) {
        val audioConfig = AudioConfig(
            bitrate = configuration.audio.bitrate,
            sampleRate = configuration.audio.sampleRate,
            stereo = configuration.audio.numberOfChannels == 2,
            echoCanceler = configuration.audio.enableEchoCanceler,
            noiseSuppressor = configuration.audio.enableEchoCanceler
        )
        val videoConfig = VideoConfig(
            bitrate = configuration.video.bitrate,
            resolution = Resolution.valueOf(configuration.video.resolution),
            fps = configuration.video.fps,
        )
        upStream = if (configuration.apiEndpoint.useApiKey) {
            ApiVideoUpstream(
                context = getApplication(),
                environment = configuration.apiEndpoint.environment,
                apiKey = configuration.apiEndpoint.apiKey,
                initialAudioConfig = audioConfig,
                initialVideoConfig = videoConfig,
                apiVideoView = apiVideoView,
                listener = listener
            ).apply {
                videoId = configuration.apiEndpoint.videoId
            }
        } else {
            ApiVideoUpstream(
                context = getApplication(),
                environment = configuration.apiEndpoint.environment,
                initialAudioConfig = audioConfig,
                initialVideoConfig = videoConfig,
                apiVideoView = apiVideoView,
                listener = listener
            ).apply {
                videoToken = configuration.apiEndpoint.uploadToken
            }
        }
    }

    fun retry() {
        try {
            upStream.retry()
            showProgress.postValue(true)
        } catch (e: Exception) {
            error.postValue(e.message)
        }
    }

    fun startStream() {
        try {
            upStream.startStreaming()
            showProgress.postValue(true)
        } catch (e: Exception) {
            error.postValue(e.message)
        }
    }

    fun stopStream() {
        upStream.stopStreaming()
    }

    fun switchCamera() {
        if (upStream.camera == CameraFacingDirection.BACK) {
            upStream.camera = CameraFacingDirection.FRONT
        } else {
            upStream.camera = CameraFacingDirection.BACK
        }
    }

    fun toggleMute() {
        upStream.isMuted = !upStream.isMuted
    }
}