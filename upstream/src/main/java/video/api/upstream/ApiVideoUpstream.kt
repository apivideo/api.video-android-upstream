package video.api.upstream

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RequiresPermission
import androidx.work.WorkManager
import io.github.thibaultbee.streampack.error.StreamPackError
import io.github.thibaultbee.streampack.listeners.OnErrorListener
import io.github.thibaultbee.streampack.streamers.file.CameraFlvFileStreamer
import io.github.thibaultbee.streampack.utils.getCameraCharacteristics
import io.github.thibaultbee.streampack.utils.isBackCamera
import io.github.thibaultbee.streampack.utils.isFrontCamera
import io.github.thibaultbee.streampack.views.getPreviewOutputSize
import video.api.uploader.VideosApi
import video.api.uploader.api.ApiClient
import video.api.uploader.api.models.Environment
import video.api.uploader.api.work.stores.VideosApiStore
import video.api.upstream.enums.CameraFacingDirection
import video.api.upstream.models.*
import video.api.upstream.models.storage.UpstreamStore
import video.api.upstream.views.ApiVideoView
import java.util.*

class ApiVideoUpstream
/**
 * Main API class.
 * A audio and video streamer capture the microphone and the camera stream and generates parts of
 * video.
 *
 * @param context The application context
 * @param apiVideoView where to display preview. Could be null if you don't have a preview.
 * @param apiKey The API key. If null, only upload with upload token will be possible.
 * @param environment The environment to use. Default is [Environment.PRODUCTION].
 * @param timeout The timeout in seconds.
 * @param appName The application name. If set, you also must set the [appVersion].
 * @param appVersion The application version. If srt, you also must set the [appName].
 * @param partSize The part size in bytes (minimum is 5242880 bytes, maximum is 134217728 bytes)
 * @param initialAudioConfig initial audio configuration. Could be change later with [audioConfig] field.
 * @param initialVideoConfig initial video configuration. Could be change later with [videoConfig] field.
 * @param initialCamera initial camera. Could be change later with [camera] field.
 * @param initialSessionListener The listener for one full video
 * @param initialSessionUploadPartListener The listener for a part of a video
 * @param streamerListener The listener for the streamer
 */
@RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
constructor(
    private val context: Context,
    private val apiVideoView: ApiVideoView,
    apiKey: String? = null,
    environment: Environment = Environment.PRODUCTION,
    timeout: Int? = null,
    appName: String? = null,
    appVersion: String? = null,
    private val partSize: Long = ApiClient.DEFAULT_CHUNK_SIZE,
    initialAudioConfig: AudioConfig?,
    initialVideoConfig: VideoConfig?,
    initialCamera: CameraFacingDirection = CameraFacingDirection.BACK,
    initialSessionListener: SessionListener? = null,
    initialSessionUploadPartListener: SessionUploadPartListener? = null,
    private val streamerListener: StreamerListener? = null,
) {
    private val partDir = context.partsDir
    private val upstreamSessionStore = UpstreamStore.getStorage(context)

    var sessionListener: SessionListener? = initialSessionListener
    var sessionUploadPartListener: SessionUploadPartListener? =
        initialSessionUploadPartListener

    /**
     * Set/get audio configuration once you have created the a [ApiVideoUpstream] instance.
     */
    var audioConfig: AudioConfig? = initialAudioConfig
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        set(value) {
            if (isStreaming) {
                throw UnsupportedOperationException("You have to stop streaming first")
            }
            value?.let {
                streamer.configure(it.toSdkConfig())
                field = it
            }
        }

    /**
     * Set/get video configuration once you have created the a [ApiVideoUpstream] instance.
     */
    var videoConfig: VideoConfig? = initialVideoConfig
        /**
         * Set new video configuration.
         * It will restart preview if resolution has been changed.
         * Encoders settings will be applied in next [startStreamingForVideoId] or [startStreamingForToken].
         *
         * @param value new video configuration
         */
        @RequiresPermission(allOf = [Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO])
        set(value) {
            require(value != null) { "Audio config must not be null" }
            if (isStreaming) {
                throw UnsupportedOperationException("You have to stop streaming first")
            }
            if (videoConfig?.resolution != value.resolution) {
                Log.i(
                    this::class.simpleName,
                    "Resolution has been changed from ${videoConfig?.resolution} to ${value.resolution}. Restarting preview."
                )
                stopPreview()
                streamer.configure(value.toSdkConfig())
                try {
                    startPreview()
                } catch (e: UnsupportedOperationException) {
                    Log.i(TAG, "Can't start preview: ${e.message}")
                }
            }
            field = value
        }

    /**
     * Hack for private setter of [isStreaming].
     */
    private var _isStreaming: Boolean = false

    /**
     * Check the streaming state.
     *
     * @return true if you are streaming, false otherwise
     * @see [startStreaming]
     * @see [stopStreaming]
     */
    val isStreaming: Boolean
        get() = _isStreaming

    private val errorListener = object : OnErrorListener {
        override fun onError(error: StreamPackError) {
            _isStreaming = false
            Log.e(TAG, "An error in streamer happened", error)
            streamerListener?.onError(error)
        }
    }

    private val streamer =
        CameraFlvFileStreamer(context, initialOnErrorListener = errorListener)

    /**
     * Mute/Unmute microphone
     */
    var isMuted: Boolean
        /**
         * Get mute value.
         *
         * @return [Boolean.true] if audio is muted, [Boolean.false] if audio is not muted.
         */
        get() = streamer.settings.audio.isMuted
        /**
         * Set mute value.
         *
         * @param value [Boolean.true] to mute audio, [Boolean.false] to unmute audio.
         */
        set(value) {
            streamer.settings.audio.isMuted = value
        }

    /**
     * Get/set current camera facing direction.
     */
    var camera: CameraFacingDirection
        /**
         * Get current camera facing direction.
         *
         * @return facing direction of the current camera
         */
        get() = CameraFacingDirection.fromCameraId(context, streamer.camera)
        /**
         * Set current camera facing direction.
         *
         * @param value camera facing direction
         */
        set(value) {
            if (((value == CameraFacingDirection.BACK) && (context.isFrontCamera(streamer.camera)))
                || ((value == CameraFacingDirection.FRONT) && (context.isBackCamera(streamer.camera)))
            ) {
                streamer.camera = value.toCameraId(context)
            }
        }

    /**
     * [SurfaceHolder.Callback] implementation.
     */
    private val surfaceCallback = object : SurfaceHolder.Callback {
        /**
         * Calls when the provided surface is created. This is for internal purpose only. Do not call it.
         */
        @SuppressLint("MissingPermission")
        override fun surfaceCreated(holder: SurfaceHolder) {
            startPreview()
        }

        /**
         * Calls when the surface size has been changed. This is for internal purpose only. Do not call it.
         */
        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) =
            Unit

        /**
         * Calls when the surface size has been destroyed. This is for internal purpose only. Do not call it.
         */
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            stopStreaming()
            stopPreview()
        }
    }

    init {
        require(partSize >= ApiClient.MIN_CHUNK_SIZE) {
            "Part size must be greater than ${ApiClient.MIN_CHUNK_SIZE}"
        }
        require(partSize <= ApiClient.MAX_CHUNK_SIZE) {
            "Part size must be less than or equal to ${ApiClient.MAX_CHUNK_SIZE}"
        }

        val apiClient = (apiKey?.let {
            ApiClient(it, environment.basePath)
        } ?: ApiClient(environment.basePath)).apply {
            if (appName != null && appVersion != null) {
                setApplicationName(appName, appVersion)
            }
            setSdkName(SDK_NAME, SDK_VERSION)
            timeout?.let {
                readTimeout = it
                writeTimeout = it
            }
        }
        /**
         * Provides the configuration for the API endpoints.
         */
        VideosApiStore.initialize(VideosApi(apiClient))

        apiVideoView.holder.addCallback(surfaceCallback)
        camera = initialCamera
        audioConfig?.let {
            streamer.configure(it.toSdkConfig())
        }
        videoConfig?.let {
            streamer.configure(it.toSdkConfig())
            // Try to start in case [SurfaceView] has already been created
            try {
                startPreview()
            } catch (e: Exception) {
                Log.i(TAG, "Can't start preview in constructor: ${e.message}")
            }
        }
    }

    /**
     * Starts camera preview of [camera].
     *
     * The surface provided in the constructor already manages [startPreview] and [stopPreview].
     * Use this method only if you need to explicitly start preview.
     *
     * @see [stopPreview]
     */
    @RequiresPermission(allOf = [Manifest.permission.CAMERA])
    fun startPreview() {
        if (apiVideoView.display == null) {
            throw UnsupportedOperationException("display is null")
        }
        // Selects appropriate preview size and configures view finder
        streamer.camera.let {
            val previewSize = getPreviewOutputSize(
                apiVideoView.display,
                context.getCameraCharacteristics(it),
                SurfaceHolder::class.java
            )
            Log.d(
                TAG,
                "View finder size: ${apiVideoView.width} x ${apiVideoView.height}"
            )
            Log.d(TAG, "Selected preview size: $previewSize")
            apiVideoView.setAspectRatio(previewSize.width, previewSize.height)

            // To ensure that size is set, initialize camera in the view's thread
            apiVideoView.post {
                streamer.startPreview(
                    apiVideoView.holder.surface,
                    it
                )
            }
        }
    }

    /**
     * Stops camera preview.
     *
     * The surface provided in the constructor already manages [startPreview] and [stopPreview].
     * Use this method only if you need to explicitly stop preview.
     *
     * @see [startPreview]
     */
    fun stopPreview() {
        streamer.stopPreview()
    }

    /**
     * Starts the upstream process for an upload token.
     *
     * @param token The upload token
     * @return The upstream session
     * @see stopStreaming
     */
    fun startStreamingForToken(token: String, videoId: String? = null): UpstreamSession {
        val sessionId = UUID.randomUUID().toString()
        val upstreamSession = UpstreamSession.createForUploadToken(
            context,
            sessionId,
            upstreamSessionStore,
            token,
            videoId,
            sessionListener,
            sessionUploadPartListener
        )

        startStreaming(upstreamSession)

        return upstreamSession
    }

    /**
     * Starts the upstream process for a video id.
     *
     * @param videoId The video id
     * @return The upstream session
     * @see stopStreaming
     */
    fun startStreamingForVideoId(videoId: String): UpstreamSession {
        val sessionId = UUID.randomUUID().toString()
        val upstreamerSession = UpstreamSession.createForVideoId(
            context,
            sessionId,
            upstreamSessionStore,
            videoId,
            sessionListener,
            sessionUploadPartListener
        )

        startStreaming(upstreamerSession)

        return upstreamerSession
    }

    private fun startStreaming(upstreamSession: UpstreamSession) {
        streamer.outputStream =
            MultiFileOutputStream(
                partDir,
                partSize,
                "part_",
                upstreamSession
            )

        streamer.startStream()
        _isStreaming = true
    }

    /**
     * Starts the upstream process.
     *
     * The [WorkManager] will continue to send the generated parts.
     *
     * You won't be able to use this instance after calling this method.
     *
     * @see startStreamingForToken
     * @see startStreamingForVideoId
     */
    fun stopStreaming() {
        streamer.stopStream()
        _isStreaming = false
    }

    /**
     * Release internal elements.
     *
     * You won't be able to use this instance after calling this method.
     */
    fun release() {
        streamer.release()
    }

    /**
     * Load a session from the internal store by the [sessionId].
     *
     * Remaining parts will be automatically add to the queue of the [WorkManager].
     *
     * @param sessionId The session id. It is the id of the [UpstreamSession] returned by [startStreamingForToken],
     * [startStreamingForVideoId].
     * @return The upstream session
     */
    fun loadSessionFromSessionId(sessionId: String) =
        UpstreamSession.loadExistingSession(
            context,
            sessionId,
            upstreamSessionStore,
            sessionListener,
            sessionUploadPartListener
        )

    companion object {
        private const val TAG = "ApiVideoUpstream"
        private const val SDK_NAME = "upstream"
        private const val SDK_VERSION = "1.0.0"
    }
}