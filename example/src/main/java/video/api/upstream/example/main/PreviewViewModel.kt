package video.api.upstream.example.main

import android.Manifest
import android.app.Application
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
    private lateinit var upStream: ApiVideoUpstream
    private val configuration = Configuration(getApplication())

    val onError = MutableLiveData<String>()
    val onDisconnect = MutableLiveData<Boolean>()

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
                apiVideoView = apiVideoView
            ).apply {
                videoId = configuration.apiEndpoint.videoId
            }
        } else {
            ApiVideoUpstream(
                context = getApplication(),
                environment = configuration.apiEndpoint.environment,
                initialAudioConfig = audioConfig,
                initialVideoConfig = videoConfig,
                apiVideoView = apiVideoView
            ).apply {
                videoToken = configuration.apiEndpoint.uploadToken
            }
        }
    }

    fun startStream() {
        try {
            upStream.startStreaming()
        } catch (e: Exception) {
            onError.postValue(e.message)
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