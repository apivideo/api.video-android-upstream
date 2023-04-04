package video.api.upstream.models

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import video.api.uploader.api.models.Video
import video.api.uploader.api.upload.IProgressiveUploadSession
import video.api.uploader.api.work.*
import video.api.uploader.api.work.stores.VideosApiStore
import video.api.uploader.api.work.workers.AbstractUploadWorker
import video.api.upstream.getSessionDir
import java.io.File
import java.security.InvalidParameterException

class UpstreamSession
/**
 * Manages the upload of a video parts.
 * An [UpstreamSession] uploads one video only. A video is composed of multiple parts.
 *
 * @param context The application context
 * @param sessionStorage The session storage
 * @param sessionListener The listener for one full video
 * @param sessionUploadPartListener The listener for a part of a video
 */
private constructor(
    private val context: Context,
    private val sessionStorage: UpstreamSessionStore,
    private val progressiveSession: IProgressiveUploadSession,
    private val sessionListener: SessionListener?,
    private val sessionUploadPartListener: SessionUploadPartListener?
) {
    private constructor(
        context: Context,
        sessionDir: File,
        videoId: String? = null,
        token: String? = null,
        sessionListener: SessionListener? = null,
        sessionUploadPartListener: SessionUploadPartListener? = null
    ) : this(
        context,
        sessionStorage = UpstreamSessionStore(sessionDir),
        progressiveSession = token?.let {
            VideosApiStore.getInstance().createUploadWithUploadTokenProgressiveSession(it, videoId)
        } ?: VideosApiStore.getInstance().createUploadProgressiveSession(videoId!!),
        sessionListener = sessionListener,
        sessionUploadPartListener = sessionUploadPartListener
    ) {
        require((videoId != null) || (token != null)) { "Token or videoId must be set" }
        videoId?.let {
            sessionStorage.videoId = videoId
        }
        token?.let {
            sessionStorage.token = token
        }
    }

    private val sessionTag =
        UploadWorkerHelper.getTagForProgressiveUploadSession(progressiveSession)

    private val workManager = WorkManager.getInstance(context)
    private val operationWithRequests = mutableListOf<OperationWithRequest>()
    private val storedWorkInfoLiveData = mutableListOf<LiveData<WorkInfo>>()
    private val workInfos: List<WorkInfo>
        get() = workManager.getWorkInfosByTag(sessionTag).get()

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
                onUploadCancelled(partId)
            }
            else -> {
                // Do nothing
            }
        }
    }

    private fun onUploadCancelled(partId: Int) {
        if (allPartsFinished && hasLastPart) {
            onEnd()
        }
    }

    private fun onUploadComplete(partId: Int, file: File, video: Video) {
        if (sessionStorage.videoId == null) {
            sessionStorage.videoId = video.videoId
        }

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

    internal fun upload(chunkIndex: Int, isLastPart: Boolean, file: File) {
        if (operationWithRequests.isEmpty()) {
            sessionListener?.onNewSessionCreated(this)
        }

        if (isLastPart) {
            if (sessionStorage.lastPartId == null) {
                sessionStorage.lastPartId = chunkIndex
            }
        }

        val operationWithRequest = workManager.uploadPart(
            progressiveSession,
            file,
            isLastPart,
            chunkIndex,
            tags = listOf(getTagForPartId(chunkIndex))
        )
        operationWithRequests.add(operationWithRequest)

        val workInfoLiveData = workManager.getWorkInfoByIdLiveData(operationWithRequest.request.id)
        Handler(Looper.getMainLooper()).post {
            workInfoLiveData.observeForever(observer)
        }
        storedWorkInfoLiveData.add(workInfoLiveData)

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
    fun cancelAll() {
        workManager.cancelProgressiveUploadSession(progressiveSession)
    }

    /**
     * Deletes remaining parts and parent folders.
     */
    fun clean() {
        sessionStorage.clean()
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
        get() = sessionStorage.lastPartId != null

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
     * Number of parts that haven't been sent yet.
     */
    val numOfPartsWaiting: Int
        get() = workInfos.count { !it.state.isFinished }

    /**
     * Check if there are still remaining parts
     */
    private val hasRemainingParts: Boolean
        get() = sessionStorage.hasRemainingParts

    companion object {
        private const val TAG = "UpstreamSession"
        private const val PREFIX_PART_ID = "partId="

        private fun getTagForPartId(partId: Int) = "$PREFIX_PART_ID$partId"

        /**
         * Creates a session from the session id.
         * Mostly use for backup session.
         * Remaining parts will be added to the [WorkManager].
         *
         * @param context The application context
         * @param sessionDir The session directory
         * @param sessionListener The listener of the session events. Could be null.
         * @param sessionUploadPartListener The listener of the parts events. Could be null.
         */
        fun createFromSessionDir(
            context: Context,
            sessionDir: File,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ): UpstreamSession {
            if (!sessionDir.exists()) {
                throw InvalidParameterException("Unknown session for $sessionDir")
            }

            val storage = UpstreamSessionStore(sessionDir)
            if (!storage.hasRemainingParts) {
                throw InvalidParameterException("Session has no more file to upload")
            }

            val videoId = storage.videoId
            val token = storage.token
            val lastId = storage.lastPartId
                ?: throw UnsupportedOperationException("Session must have a lastId")

            val progressiveSession = token?.let {
                VideosApiStore.getInstance()
                    .createUploadWithUploadTokenProgressiveSession(it, videoId)
            } ?: VideosApiStore.getInstance().createUploadProgressiveSession(videoId!!)

            val upstreamSession = UpstreamSession(
                context,
                storage,
                progressiveSession,
                sessionListener,
                sessionUploadPartListener
            )

            storage.partsDir.listFiles()?.forEach {
                val partId = it.name.toInt()
                if (partId == lastId) {
                    upstreamSession.upload(partId, true, it)
                } else {
                    upstreamSession.upload(partId, false, it)
                }
            }

            return upstreamSession
        }

        internal fun createForVideoId(
            context: Context,
            sessionDir: File,
            videoId: String,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ) = UpstreamSession(
            context,
            sessionDir,
            videoId = videoId,
            token = null,
            sessionListener = sessionListener,
            sessionUploadPartListener = sessionUploadPartListener
        )

        internal fun createForUploadToken(
            context: Context,
            sessionDir: File,
            token: String,
            videoId: String?,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ) = UpstreamSession(
            context,
            sessionDir,
            videoId = videoId,
            token = token,
            sessionListener = sessionListener,
            sessionUploadPartListener = sessionUploadPartListener
        )

        fun numOfRemainingParts(context: Context, sessionId: String) =
            context.getSessionDir(sessionId).list()?.size ?: 0

        fun hasRemainingParts(context: Context, sessionId: String) =
            numOfRemainingParts(context, sessionId) > 0
    }
}


