package video.api.upstream.models

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import video.api.uploader.api.models.Video
import video.api.uploader.api.upload.IProgressiveUploadSession
import video.api.uploader.api.work.*
import video.api.uploader.api.work.stores.VideosApiStore
import video.api.uploader.api.work.workers.AbstractUploadWorker
import video.api.upstream.models.storage.IUpstreamDao
import video.api.upstream.models.storage.Part
import java.io.File
import java.security.InvalidParameterException

class UpstreamSession
/**
 * Manages the upload of a video parts.
 * An [UpstreamSession] uploads one video only. A video is composed of multiple parts.
 *
 * @param context The application context
 * @param sessionStore The session store
 * @param sessionListener The listener for one full video
 * @param sessionUploadPartListener The listener for a part of a video
 */
private constructor(
    private val context: Context,
    val id: String,
    private val sessionStore: IUpstreamDao,
    private val progressiveSession: IProgressiveUploadSession,
    private val sessionListener: SessionListener?,
    private val sessionUploadPartListener: SessionUploadPartListener?
) : MultiFileOutputStream.Listener {
    private constructor(
        context: Context,
        sessionId: String,
        sessionStore: IUpstreamDao,
        videoId: String? = null,
        token: String? = null,
        sessionListener: SessionListener? = null,
        sessionUploadPartListener: SessionUploadPartListener? = null
    ) : this(
        context,
        id = sessionId,
        sessionStore = sessionStore,
        progressiveSession = token?.let {
            VideosApiStore.getInstance().createUploadWithUploadTokenProgressiveSession(it, videoId)
        } ?: VideosApiStore.getInstance().createUploadProgressiveSession(videoId!!),
        sessionListener = sessionListener,
        sessionUploadPartListener = sessionUploadPartListener
    ) {
        require((videoId != null) || (token != null)) { "Token or videoId must be set" }
    }

    // Create a unique tag - to avoid create UUID, use the sessionId
    private val sessionTag = id

    private val workManager = WorkManager.getInstance(context)
    private val operationWithRequests = mutableListOf<OperationWithRequest>()
    private val storedWorkInfoLiveData = mutableListOf<LiveData<WorkInfo>>()

    private val observer = Observer<WorkInfo> { workInfo ->
        val partId = workInfo.tags.first { it.startsWith(PREFIX_PART_ID) }.removePrefix(
            PREFIX_PART_ID
        ).toInt()
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.toProgress()
                /**
                 * As soon as the work is launched it goes to RUNNING state whereas it does not send anything,
                 * so we need to check if the progress is not 0.
                 */
                if (progress != 0) {
                    onUploadProgress(
                        partId,
                        progress
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                onUploadComplete(
                    partId,
                    workInfo.outputData.toFile(),
                    workInfo.outputData.toVideo()
                )
            }
            WorkInfo.State.FAILED -> {
                onUploadError(
                    partId,
                    Exception(
                        workInfo.outputData.getString(AbstractUploadWorker.ERROR_KEY)
                            ?: "Unknown error"
                    )
                )
            }
            WorkInfo.State.CANCELLED -> {
                onUploadCancelled()
            }
            else -> {
                // Do nothing
            }
        }
    }

    init {
        sessionStore.getParts(id).forEach {
            upload(it.index, it.isLast, it.file)
        }
    }

    private val workInfos: List<WorkInfo>
        get() = workManager.getWorkInfosByTag(sessionTag).get()

    private fun onUploadCancelled() {
        if (allPartsFinished && hasLastPart) {
            onEnd()
        }
    }

    private fun onUploadComplete(partId: Int, file: File, video: Video) {
        sessionStore.insertVideoId(id, video.videoId)
        sessionStore.removePart(id, partId)

        file.delete()
        sessionUploadPartListener?.onComplete(this, partId, video)

        if (allPartsFinished && hasLastPart) {
            onEnd()
        }
    }

    private fun onUploadError(partId: Int, e: Exception) {
        sessionUploadPartListener?.onError(this, partId, e)
        if (allPartsFinished && hasLastPart) {
            onEnd()
        }
    }

    private fun onUploadProgress(partId: Int, progress: Int) {
        sessionUploadPartListener?.onProgressChanged(
            this,
            partId,
            progress
        )
    }

    private fun onEnd() {
        release()
        if (!hasRemainingParts) {
            clean()
            sessionListener?.onComplete(this)
        } else {
            sessionListener?.onEndWithError(this)
        }
    }

    override fun onFileCreated(partIndex: Int, isLast: Boolean, file: File) {
        upload(partIndex, isLast, file)
    }

    internal fun upload(partIndex: Int, isLast: Boolean, file: File) {
        sessionStore.insertPart(id, Part(partIndex, isLast, file))

        if (operationWithRequests.isEmpty()) {
            sessionListener?.onNewSessionCreated(this)
        }

        val operationWithRequest = workManager.uploadPart(
            progressiveSession,
            file,
            isLast,
            partIndex,
            tags = listOf(getTagForPartId(partIndex), sessionTag)
        )
        operationWithRequests.add(operationWithRequest)

        val workInfoLiveData = workManager.getWorkInfoByIdLiveData(operationWithRequest.request.id)
        Handler(Looper.getMainLooper()).post {
            workInfoLiveData.observeForever(observer)
            storedWorkInfoLiveData.add(workInfoLiveData)
        }

        sessionListener?.onNumberOfPartsChanged(this, totalNumOfParts)
    }

    private fun release() {
        storedWorkInfoLiveData.forEach {
            Handler(Looper.getMainLooper()).post {
                it.removeObserver(observer)
            }
        }
    }

    /**
     * Cancel the current uploads of this session.
     */
    fun cancel() {
        workManager.cancelProgressiveUploadSession(progressiveSession)
    }

    /**
     * Deletes remaining parts and parent folders.
     */
    fun clean() {
        sessionStore.getParts(id).forEach {
            it.file.delete()
        }
        sessionStore.remove(id)
    }

    /**
     * True if there are no parts to send anymore.
     * This means that all parts have been sent, cancelled or in error.
     */
    private val allPartsFinished: Boolean
        get() = numOfPartsFinished == totalNumOfParts

    /**
     * True if the last part has been received.
     */
    private val hasLastPart: Boolean
        get() = sessionStore.getLastPartId(id) != null

    /**
     * Total number of parts for the video.
     * The number of parts increases as long as upstream is running.
     */
    val totalNumOfParts: Int
        get() = operationWithRequests.size

    /**
     * Number of parts that have been either sent, cancel or in error.
     */
    val numOfPartsFinished: Int
        get() = workInfos.count { it.state.isFinished }

    /**
     * Number of parts that have been cancelled.
     */
    val numOfPartCancelled: Int
        get() = workInfos.count { it.state == WorkInfo.State.CANCELLED }

    /**
     * Number of parts that have been successfully sent.
     */
    val numOfPartsSent: Int
        get() = workInfos.count { it.state == WorkInfo.State.SUCCEEDED }

    /**
     * Number of parts that failed to be sent.
     */
    val numOfPartsFailed: Int
        get() = workInfos.count { it.state == WorkInfo.State.FAILED }

    /**
     * Number of parts that are not finished yet.
     */
    val numOfPartsWaiting: Int
        get() = workInfos.count { !it.state.isFinished }

    /**
     * Check if there are still remaining parts in the storage
     */
    private val hasRemainingParts: Boolean
        get() = sessionStore.hasPart(id)

    companion object {
        private const val PREFIX_PART_ID = "partId="

        private fun getTagForPartId(partId: Int) = "$PREFIX_PART_ID$partId"

        /**
         * Load an existing session from its [sessionId] from the [sessionStore].
         * Use it to resume an upload that failed.
         *
         * Remaining parts will be added to the [WorkManager].
         *
         * @param context The application context
         * @param sessionId The id of session
         * @param sessionStore The session store
         * @param sessionListener The listener of the session events. Could be null.
         * @param sessionUploadPartListener The listener of the parts events. Could be null.
         */
        fun loadExistingSession(
            context: Context,
            sessionId: String,
            sessionStore: IUpstreamDao,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ): UpstreamSession {
            val upstreamSessionEntity = sessionStore.getById(sessionId)
                ?: throw InvalidParameterException("Unknown session $sessionId")

            if (!upstreamSessionEntity.hasParts) {
                throw InvalidParameterException("Session has no more file to upload")
            }

            val videoId = upstreamSessionEntity.videoId
            val token = upstreamSessionEntity.token

            return UpstreamSession(
                context,
                sessionId = sessionId,
                sessionStore = sessionStore,
                videoId = videoId,
                token = token,
                sessionListener = sessionListener,
                sessionUploadPartListener = sessionUploadPartListener
            )
        }

        internal fun createForVideoId(
            context: Context,
            sessionId: String,
            sessionStore: IUpstreamDao,
            videoId: String,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ) = UpstreamSession(
            context,
            sessionId = sessionId,
            sessionStore = sessionStore,
            videoId = videoId,
            token = null,
            sessionListener = sessionListener,
            sessionUploadPartListener = sessionUploadPartListener
        )

        internal fun createForUploadToken(
            context: Context,
            sessionId: String,
            sessionStore: IUpstreamDao,
            token: String,
            videoId: String?,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ) = UpstreamSession(
            context,
            sessionId = sessionId,
            sessionStore = sessionStore,
            videoId = videoId,
            token = token,
            sessionListener = sessionListener,
            sessionUploadPartListener = sessionUploadPartListener
        )
    }
}


