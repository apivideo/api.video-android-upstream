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
import video.api.uploader.api.ApiClient
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
 * @param partSize The part size in bytes (minimum is 5242880 bytes, maximum is 134217728 bytes)
 */
@RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
private constructor(
    private val context: Context,
    private val videosApi: VideosApi,
    private val partSize: Long,
    initialAudioConfig: AudioConfig?,
    initialVideoConfig: VideoConfig?,
    initialCamera: CameraFacingDirection,
    private val apiVideoView: ApiVideoView,
    private val listener: Listener?,
    private val maxNumOfParallelUploads: Int
) {
    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
    constructor(
        context: Context,
        environment: Environment = Environment.PRODUCTION,
        chunkSize: Long = ApiClient.DEFAULT_CHUNK_SIZE,
        initialAudioConfig: AudioConfig? = null,
        initialVideoConfig: VideoConfig? = null,
        initialCamera: CameraFacingDirection = CameraFacingDirection.BACK,
        apiVideoView: ApiVideoView,
        listener: Listener? = null,
        timeout: Int = 60000, // 1 min
    ) : this(
        context,
        videosApi = VideosApi(ApiClient(environment.basePath).apply {
            this.writeTimeout = timeout
            this.readTimeout = timeout
            this.connectTimeout = timeout
        }),
        chunkSize,
        initialAudioConfig,
        initialVideoConfig,
        initialCamera,
        apiVideoView,
        listener,
        1
    )

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA])
    constructor(
        context: Context,
        apiKey: String,
        environment: Environment = Environment.PRODUCTION,
        chunkSize: Long = ApiClient.DEFAULT_CHUNK_SIZE,
        initialAudioConfig: AudioConfig? = null,
        initialVideoConfig: VideoConfig? = null,
        initialCamera: CameraFacingDirection = CameraFacingDirection.BACK,
        apiVideoView: ApiVideoView,
        listener: Listener? = null,
        maxNumOfParallelUploads: Int = 1,
        timeout: Int = 60000 // 1 min
    ) : this(
        context,
        videosApi = VideosApi(ApiClient(apiKey, environment.basePath).apply {
            this.writeTimeout = timeout
            this.readTimeout = timeout
            this.connectTimeout = timeout
        }),
        chunkSize,
        initialAudioConfig,
        initialVideoConfig,
        initialCamera,
        apiVideoView,
        listener,
        maxNumOfParallelUploads
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
            require(maxNumOfParallelUploads == 1) { "Parallel uploads is only supported for videoId" }

            value?.let {
                streamer.outputStream =
                    ChunkedFileOutputStream(
                        "${context.cacheDir}/$it",
                        partSize,
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
                        "${context.cacheDir}/$it",
                        partSize,
                        onChunkListener
                    )
                progressiveSession = videosApi.createUploadProgressiveSession(it)
                field = value
            }
        }

    private val video: String
        get() = videoId ?: videoToken ?: throw IllegalStateException("Video token or id is not set")

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

    private val executor = Executors.newFixedThreadPool(maxNumOfParallelUploads)
    private val onChunkListener = object : ChunkedFileOutputStream.OnChunkListener {
        override fun onChunkReady(chunkIndex: Int, isLastChunk: Boolean, file: File) {
            listener?.onTotalNumberOfPartsChanged(chunkIndex)
            launchUpload(chunkIndex, isLastChunk, file)
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
        require(partSize >= ApiClient.MIN_CHUNK_SIZE) {
            "Part size must be greater than ${ApiClient.MIN_CHUNK_SIZE}"
        }
        require(partSize <= ApiClient.MAX_CHUNK_SIZE) {
            "Part size must be less than or equal to ${ApiClient.MAX_CHUNK_SIZE}"
        }
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
        _isStreaming = true
    }

    fun stopStreaming() {
        streamer.stopStream()
        _isStreaming = false
    }

    fun release() {
        streamer.release()
    }

    private var numOfParts = 0
    private var numOfPartsUploaded = 0

    private fun launchUpload(chunkIndex: Int, isLastChunk: Boolean, file: File) {
        numOfParts++
        executor.execute {
            try {
                listener?.onPartUploadStarted(chunkIndex)

                progressiveSession?.uploadPart(
                    file,
                    chunkIndex,
                    isLastChunk
                ) { bytesWritten, totalBytes ->
                    listener?.onPartUploadProgressChanged(
                        chunkIndex,
                        bytesWritten.toFloat() / totalBytes
                    )
                }
                listener?.onPartUploadEnded(chunkIndex)
                file.delete()
            } catch (e: Exception) {
                if (isLastChunk && !file.name.endsWith(".last")) {
                    file.renameTo(File(file.parentFile, file.name + ".last")) // Identify last
                }
                Log.e(TAG, "Error while uploading chunk: $chunkIndex", e)
                listener?.onUploadError(chunkIndex, e)
            } finally {
                numOfPartsUploaded++
                if (!isStreaming && numOfPartsUploaded == numOfParts) {
                    onLastPart(file)
                }
            }
        }
    }

    private fun onLastPart(file: File) {
        // Delete parent directory only if all files are uploaded
        file.parentFile?.delete()
        listener?.onUploadStop(!hasRemainingParts(context, video))
    }

    /**
     * Retry to upload the chunk of the current video that has not been uploaded.
     */
    fun retry() {
        retry(video)
    }

    /**
     * Retry to upload the chunk that has not been uploaded.
     *
     * @param video The video id or the video token
     */
    fun retry(video: String) {
        File("${context.cacheDir}/$video").listFiles()?.forEach {
            if (it.name.endsWith(".last")) {
                launchUpload(it.name.replace(".last", "").toInt(), true, it)
            } else {
                launchUpload(it.name.toInt(), false, it)
            }
        }
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
        private const val TAG = "ApiVideoUpstream"

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