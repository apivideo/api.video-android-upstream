package video.api.upstream

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import video.api.uploader.VideosApi
import video.api.uploader.api.models.Environment
import video.api.uploader.api.upload.IProgressiveUploadSession
import video.api.upstream.enums.CameraFacingDirection
import video.api.upstream.models.AudioConfig
import video.api.upstream.models.ChunkedFileOutputStream
import video.api.upstream.models.VideoConfig
import video.api.upstream.views.ApiVideoView
import java.io.File
import java.util.concurrent.Executors

class ApiVideoUpstream
/**
 * @param context The application context
 * @param chunkSize The chunk size in bytes (minimum is 5242880 bytes)
 */
@RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
private constructor(
    private val context: Context,
    private val videosApi: VideosApi,
    private val chunkSize: Long,
    initialAudioConfig: AudioConfig?,
    initialVideoConfig: VideoConfig?,
    initialCamera: CameraFacingDirection,
    private val apiVideoView: ApiVideoView,
    private val listener: Listener?
) {
    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
    constructor(
        context: Context,
        environment: Environment = Environment.PRODUCTION,
        chunkSize: Long = 5242880,
        initialAudioConfig: AudioConfig? = null,
        initialVideoConfig: VideoConfig? = null,
        initialCamera: CameraFacingDirection = CameraFacingDirection.BACK,
        apiVideoView: ApiVideoView,
        listener: Listener? = null
    ) : this(
        context,
        videosApi = VideosApi(environment.basePath),
        chunkSize,
        initialAudioConfig,
        initialVideoConfig,
        initialCamera,
        apiVideoView,
        listener
    )

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
    constructor(
        context: Context,
        apiKey: String,
        environment: Environment = Environment.PRODUCTION,
        chunkSize: Long = 5242880,
        initialAudioConfig: AudioConfig? = null,
        initialVideoConfig: VideoConfig? = null,
        initialCamera: CameraFacingDirection = CameraFacingDirection.BACK,
        apiVideoView: ApiVideoView,
        listener: Listener? = null
    ) : this(
        context,
        videosApi = VideosApi(apiKey, environment.basePath),
        chunkSize,
        initialAudioConfig,
        initialVideoConfig,
        initialCamera,
        apiVideoView,
        listener
    )

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
            if (isStreaming) {
                throw UnsupportedOperationException("You have to stop streaming first")
            }
            value?.let {
                if (it.resolution != it.resolution) {
                    videoConfig?.let { oldConfig ->
                        Log.i(
                            this::class.simpleName,
                            "Resolution has been changed from ${oldConfig.resolution} to ${it.resolution}. Restarting preview."
                        )
                    }
                    stopPreview()
                    streamer.configure(it.toSdkConfig())
                    streamer.startPreview(apiVideoView.holder.surface)
                }
                field = value
            }
        }

    private var progressiveSession: IProgressiveUploadSession? = null

    var videoToken: String? = null
        set(value) {
            if (isStreaming) {
                throw UnsupportedOperationException("You have to stop streaming first")
            }
            value?.let {
                streamer.outputStream =
                    ChunkedFileOutputStream(
                        "${context.filesDir}/$it",
                        chunkSize,
                        onChunkListener
                    )
                progressiveSession = videosApi.createUploadWithUploadTokenProgressiveSession(it)
                field = value
            }
        }

    var videoId: String? = null
        set(value) {
            if (isStreaming) {
                throw UnsupportedOperationException("You have to stop streaming first")
            }

            value?.let {
                streamer.outputStream =
                    ChunkedFileOutputStream(
                        "${context.filesDir}/$it",
                        chunkSize,
                        onChunkListener
                    )
                progressiveSession = videosApi.createUploadProgressiveSession(it)
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

    private val executor = Executors.newSingleThreadExecutor()
    private val onChunkListener = object : ChunkedFileOutputStream.OnChunkListener {
        override fun onChunkReady(chunkIndex: Int, isLastChunk: Boolean, file: File) {
            listener?.onTotalNumberOfPartsChanged(chunkIndex)
            executor.execute {

                listener?.onPartUploadStarted(chunkIndex)

                if (isLastChunk) {
                    try {
                        progressiveSession?.uploadLastPart(file) { bytesWritten, totalBytes ->
                            listener?.onPartUploadProgressChanged(
                                chunkIndex,
                                bytesWritten.toFloat() / totalBytes
                            )
                        }
                        listener?.onPartUploadEnded(chunkIndex)
                        file.delete()
                        // Delete parent directory only if all files are uploaded
                        file.parentFile?.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error while uploading chunk", e)
                        listener?.onUploadError(chunkIndex, e)
                    } finally {
                        listener?.onUploadStop(!hasRemainingParts(context, videoToken ?: videoId!!))
                    }
                } else {
                    try {
                        progressiveSession?.uploadPart(file) { bytesWritten, totalBytes ->
                            listener?.onPartUploadProgressChanged(
                                chunkIndex,
                                bytesWritten.toFloat() / totalBytes
                            )
                        }
                        listener?.onPartUploadEnded(chunkIndex)
                        file.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error while uploading chunk", e)
                        listener?.onUploadError(chunkIndex, e)
                    }
                }
            }
        }
    }

    private val errorListener = object : OnErrorListener {
        override fun onError(error: StreamPackError) {
            _isStreaming = false
            Log.e(TAG, "An error happened", error)
            listener?.onError(error)
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
                        initialCamera.toCameraId(context)
                    )
                }
            }
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
        apiVideoView.holder.addCallback(surfaceCallback)
        audioConfig?.let {
            streamer.configure(it.toSdkConfig())
        }
        videoConfig?.let {
            streamer.configure(it.toSdkConfig())
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.CAMERA])
    fun startPreview() {
        streamer.startPreview(apiVideoView.holder.surface)
    }

    fun stopPreview() {
        streamer.stopPreview()
    }

    fun startStreaming() {
        require(progressiveSession != null) { "Progressive session is not set" }
        streamer.startStream()
    }

    fun stopStreaming() {
        streamer.stopStream()
    }

    fun release() {
        streamer.release()
    }

    interface Listener {
        fun onError(error: Exception) {}
        fun onUploadError(partId: Int, error: Exception) {}
        fun onTotalNumberOfPartsChanged(totalNumberOfParts: Int) {}
        fun onPartUploadStarted(partId: Int) {}
        fun onPartUploadEnded(partId: Int) {}
        fun onPartUploadProgressChanged(partId: Int, progress: Float) {}
        fun onUploadStop(success: Boolean) {}
    }


    companion object {
        private const val TAG = "ApiVideoUpStream"

        /**
         * @param context The application context
         * @param video The video id or the video token
         */
        fun getNumOfRemainingParts(context: Context, video: String): Int {
            return File("${context.cacheDir}/$video").list()?.size ?: 0
        }

        /**
         * @param context The application context
         * @param video The video id or the video token
         */
        fun hasRemainingParts(context: Context, video: String): Boolean {
            return getNumOfRemainingParts(context, video) > 0
        }
    }
}