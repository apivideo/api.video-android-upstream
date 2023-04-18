package video.api.upstream.example.main

import android.Manifest
import android.app.Application
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import video.api.client.api.ApiCallback
import video.api.client.api.ApiClient
import video.api.client.api.ApiException
import video.api.client.api.clients.VideosApi
import video.api.client.api.models.Video
import video.api.client.api.models.VideoCreationPayload
import video.api.upstream.ApiVideoUpstream
import video.api.upstream.enums.CameraFacingDirection
import video.api.upstream.enums.Resolution
import video.api.upstream.example.utils.Configuration
import video.api.upstream.example.utils.ProgressSessionPart
import video.api.upstream.example.utils.SessionParts
import video.api.upstream.models.*
import video.api.upstream.views.ApiVideoView

class PreviewViewModel(application: Application) : AndroidViewModel(application) {
    private lateinit var upstream: ApiVideoUpstream
    private val multiFileUploaders = mutableListOf<MultiFileUploader>()

    private val configuration = Configuration(getApplication())
    private val streamerListener = object : StreamerListener {
        override fun onError(error: Exception) {
            Log.e(TAG, "onError: ", error)
            this@PreviewViewModel.error.postValue(error.message ?: "Unknown error")
        }
    }

    private val sessionListener = object : SessionListener {
        override fun onNewSessionCreated(session: MultiFileUploader) {
            Log.i(TAG, "onNewSessionCreated: new session: $session")
            newSession.postValue(session)
        }

        override fun onNumberOfPartsChanged(
            session: MultiFileUploader,
            numOfParts: Int
        ) {
            Log.i(TAG, "onTotalNumberOfPartsChanged: $numOfParts")
            this@PreviewViewModel.numOfParts.postValue(SessionParts(session, numOfParts))
        }

        override fun onEndWithError(session: MultiFileUploader) {
            Log.e(TAG, "onEndWithError: session errors: $session")
            sessionEnded.postValue(session)
            sessionComplete.postValue(false)
        }

        override fun onComplete(session: MultiFileUploader) {
            Log.i(TAG, "onComplete: session completed: $session")
            multiFileUploaders.remove(session)
            sessionEnded.postValue(session)
            sessionComplete.postValue(true)
        }
    }

    private val sessionUploadPartListener = object : SessionUploadPartListener {
        override fun onError(session: MultiFileUploader, partId: Int, error: Exception) {
            Log.e(TAG, "onError: ", error)
            message.postValue(error.message ?: "Unknown error")
        }

        override fun onComplete(
            session: MultiFileUploader,
            partId: Int,
            video: video.api.uploader.api.models.Video
        ) {
            Log.i(TAG, "onComplete: part: $partId: ${video.videoId}")
        }

        override fun onProgressChanged(session: MultiFileUploader, partId: Int, progress: Int) {
            Log.i(TAG, "onProgressChanged: part: $partId progress: $progress")
            this@PreviewViewModel.progress.postValue(ProgressSessionPart(session, partId, progress))
        }
    }

    val error = MutableLiveData<String>()
    val message = MutableLiveData<String>()

    val newSession = MutableLiveData<MultiFileUploader>()
    val sessionEnded = MutableLiveData<MultiFileUploader>()
    val numOfParts = MutableLiveData<SessionParts>()
    val progress = MutableLiveData<ProgressSessionPart>()

    val sessionComplete = MutableLiveData<Boolean>()

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
    fun buildUpstream(apiVideoView: ApiVideoView) {
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

        val apiKey = if (configuration.apiEndpoint.useApiKey) {
            configuration.apiEndpoint.apiKey
        } else {
            null
        }

        upstream = ApiVideoUpstream(
            context = getApplication(),
            apiVideoView = apiVideoView,
            environment = configuration.apiEndpoint.environment,
            apiKey = apiKey,
            timeout = 60000, // 1 min
            partSize = ApiClient.MIN_CHUNK_SIZE,
            initialAudioConfig = audioConfig,
            initialVideoConfig = videoConfig,
            initialSessionListener = sessionListener,
            initialSessionUploadPartListener = sessionUploadPartListener,
            streamerListener = streamerListener
        )
    }

    fun retryAllSessions() {
        try {
            multiFileUploaders.forEach {
                multiFileUploaders += upstream.loadSessionFromSessionId(it.id)
            }
        } catch (e: Exception) {
            error.postValue(e.message)
        }
    }

    private fun createNewVideoId(onNewVideoId: (String) -> Unit) {
        /**
         *  Create new video Id
         *  Use `createAsync` to avoid calling network on main thread
         */
        VideosApi(
            ApiClient(
                configuration.apiEndpoint.apiKey,
                configuration.apiEndpoint.environment.basePath
            )
        ).createAsync(VideoCreationPayload().apply { title = "upstream Video" },
            object : ApiCallback<Video> {
                override fun onFailure(
                    e: ApiException?,
                    statusCode: Int,
                    responseHeaders: MutableMap<String, MutableList<String>>?
                ) {
                    message.postValue(e?.message ?: "Unknown error")
                }

                override fun onSuccess(
                    result: Video?,
                    statusCode: Int,
                    responseHeaders: MutableMap<String, MutableList<String>>?
                ) {
                    onNewVideoId(result!!.videoId)
                }

                override fun onUploadProgress(
                    bytesWritten: Long,
                    contentLength: Long,
                    done: Boolean
                ) {
                    // Nothing to do
                }

                override fun onDownloadProgress(
                    bytesRead: Long,
                    contentLength: Long,
                    done: Boolean
                ) {
                    // Nothing to do
                }
            }
        )
    }

    fun startStream() {
        try {
            if (configuration.apiEndpoint.useApiKey) {
                // Create a new video id each time we start streaming
                createNewVideoId { videoId ->
                    multiFileUploaders += upstream.startStreamingForVideoId(videoId)
                }
            } else {
                multiFileUploaders +=
                    upstream.startStreamingForToken(configuration.apiEndpoint.uploadToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startStream: ", e)
            error.postValue(e.message)
        }
    }

    fun stopStream() {
        upstream.stopStreaming()
    }

    fun switchCamera() {
        if (upstream.camera == CameraFacingDirection.BACK) {
            upstream.camera = CameraFacingDirection.FRONT
        } else {
            upstream.camera = CameraFacingDirection.BACK
        }
    }

    fun toggleMute() {
        upstream.isMuted = !upstream.isMuted
    }

    fun cancel() {
        multiFileUploaders.forEach { it.cancel() }
    }

    override fun onCleared() {
        super.onCleared()
        upstream.release()
    }

    companion object {
        private const val TAG = "PreviewViewModel"
    }
}