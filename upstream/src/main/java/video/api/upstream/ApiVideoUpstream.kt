package video.api.upstream

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.ServiceConnection
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RequiresPermission
import io.github.thibaultbee.streampack.error.StreamPackError
import io.github.thibaultbee.streampack.listeners.OnErrorListener
import io.github.thibaultbee.streampack.streamers.file.CameraFlvFileStreamer
import io.github.thibaultbee.streampack.utils.getCameraCharacteristics
import io.github.thibaultbee.streampack.utils.isBackCamera
import io.github.thibaultbee.streampack.utils.isFrontCamera
import io.github.thibaultbee.streampack.views.getPreviewOutputSize
import video.api.uploader.api.ApiClient
import video.api.uploader.api.models.Environment
import video.api.uploader.api.services.UploadService
import video.api.upstream.enums.CameraFacingDirection
import video.api.upstream.models.*
import video.api.upstream.views.ApiVideoView
import java.io.File

class ApiVideoUpstream
/**
 * @param context The application context
 * @param partSize The part size in bytes (minimum is 5242880 bytes, maximum is 134217728 bytes)
 */
@RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
constructor(
    private val context: Context,
    private val uploadService: UploadService,
    private val partSize: Long = ApiClient.DEFAULT_CHUNK_SIZE,
    initialAudioConfig: AudioConfig?,
    initialVideoConfig: VideoConfig?,
    initialCamera: CameraFacingDirection = CameraFacingDirection.BACK,
    private val apiVideoView: ApiVideoView,
    private val sessionListener: SessionListener? = null,
    private val sessionUploadPartListener: SessionUploadPartListener? = null,
    private val streamerListener: StreamerListener? = null,
) {
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
         * Encoders settings will be applied in next [startStreaming].
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
                    Log.w(TAG, "${e.message}", e)
                }
            }
            field = value
        }

    private var upstreamerSession: UpstreamSession? = null

    var token: String? = null
        set(value) {
            if (isStreaming) {
                throw UnsupportedOperationException("You have to stop streaming first")
            }

            value?.let {
                upstreamerSession = UpstreamSession.createForUploadToken(
                    context,
                    uploadService,
                    it,
                    sessionListener,
                    sessionUploadPartListener
                ).apply {
                    streamer.outputStream =
                        ChunkedFileOutputStream(
                            context.getSessionPartsDir(id),
                            partSize,
                            onChunkListener
                        )
                }

                field = value
            }
        }

    var videoId: String? = null
        set(value) {
            if (isStreaming) {
                throw UnsupportedOperationException("You have to stop streaming first")
            }

            value?.let {
                upstreamerSession = UpstreamSession.createForVideoId(
                    context,
                    uploadService,
                    it,
                    sessionListener,
                    sessionUploadPartListener
                ).apply {
                    streamer.outputStream =
                        ChunkedFileOutputStream(
                            context.getSessionPartsDir(id),
                            partSize,
                            onChunkListener
                        )
                }

                field = value
            }
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

    private val onChunkListener = object : ChunkedFileOutputStream.OnChunkListener {
        override fun onChunkReady(chunkIndex: Int, isLastChunk: Boolean, file: File) {
            upstreamerSession!!.upload(chunkIndex, isLastChunk, file)
        }
    }

    private val errorListener = object : OnErrorListener {
        override fun onError(error: StreamPackError) {
            _isStreaming = false
            Log.e(TAG, "An error in streamer happened", error)
            upstreamerSession!!.cancelAll()
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
                Log.i(TAG, "Failed to start preview. Surface might not be created yet", e)
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.CAMERA])
    fun startPreview() {
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

    fun stopPreview() {
        streamer.stopPreview()
    }

    fun startStreaming() {
        require(upstreamerSession != null) { "Set video id or token before" }

        streamer.startStream()
        _isStreaming = true
    }

    fun stopStreaming() {
        streamer.stopStream()
        _isStreaming = false
    }

    fun release() {
        streamer.release()
    }

    fun releaseAndCancelAll() {
        release()
        uploadService.cancelAll()
    }

    /**
     * Create a session to upload parts that have not been sent yet.
     *
     * Remaining files will be automatically add to the queue of the [UploadService].
     *
     * @param sessionId The session id
     * @see getSessionIdList
     * @see getSessionId
     */
    fun createBackupSessionFromSessionId(sessionId: String) =
        UpstreamSession.createFromSessionId(
            context,
            uploadService,
            sessionId,
            sessionListener,
            sessionUploadPartListener
        )

    companion object {
        private const val TAG = "ApiVideoUpstream"

        /**
         * Deletes all internal files generated by the library
         *
         * @param context The application context
         * @see delete
         */
        fun deleteAll(context: Context) {
            context.upstreamWorkDir.deleteRecursively()
        }

        /**
         * Deletes all internal files of an [UpstreamSession]
         *
         * @param context The application context
         * @param sessionId The session id
         */
        fun delete(context: Context, sessionId: String) {
            context.getSessionDir(sessionId).deleteRecursively()
        }

        /**
         * Gets the id of the session that has not been successfully uploaded
         *
         * @param context The application context
         * @return list of session id
         * @see createBackupSessionFromSessionId
         */
        fun getSessionIdList(context: Context): List<String> {
            return context.upstreamWorkDir.listFiles()?.map { it.name } ?: emptyList()
        }

        /**
         * Gets the id of the session that matches the video id
         *
         * @param context The application context
         * @param videoId The video id
         * @return the session id
         * @see createBackupSessionFromSessionId
         */
        fun getSessionId(context: Context, videoId: String) =
            context.upstreamWorkDir.listFiles()
                ?.first { UpstreamSessionStorage.getVideoId(it) == videoId }

        /**
         * Gets the video id of the session
         *
         * @param context The application context
         * @param sessionId The session id
         * @return the video id. Could be null for a session with an upload token that could not send any parts.
         * @see createBackupSessionFromSessionId
         */
        fun getVideoId(context: Context, sessionId: String) =
            UpstreamSessionStorage.getVideoId(context.getSessionDir(sessionId))

        /**
         * Build the [ApiVideoUpstream].
         * Internally, it starts and binds an [UploadService].
         *
         * @see unbindService
         */
        @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
        fun create(
            context: Context,
            serviceClass: Class<out UploadService> = UploadService::class.java,
            apiKey: String? = null,
            environment: Environment = Environment.PRODUCTION,
            timeout: Int? = null,
            partSize: Long = ApiClient.DEFAULT_CHUNK_SIZE,
            initialAudioConfig: AudioConfig? = null,
            initialVideoConfig: VideoConfig? = null,
            initialCamera: CameraFacingDirection = CameraFacingDirection.BACK,
            apiVideoView: ApiVideoView,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null,
            streamerListener: StreamerListener? = null,
            onUpstreamSession: (ApiVideoUpstream) -> Unit,
            onServiceDisconnected: () -> Unit
        ): ServiceConnection {
            return UploadService.startService(
                context = context,
                serviceClass = serviceClass,
                apiKey = apiKey,
                environment = environment,
                timeout = timeout,
                onServiceCreated = { service ->
                    onUpstreamSession(
                        ApiVideoUpstream(
                            context,
                            service,
                            partSize,
                            initialAudioConfig,
                            initialVideoConfig,
                            initialCamera,
                            apiVideoView,
                            sessionListener,
                            sessionUploadPartListener,
                            streamerListener
                        )
                    )
                },
                onServiceDisconnected = { onServiceDisconnected() },
                sdkName = "upstream",
                sdkVersion = "1.0.0"
            )
        }

        fun unbindService(
            context: Context,
            serviceConnection: ServiceConnection
        ) {
            UploadService.unbindService(context, serviceConnection)
        }
    }
}