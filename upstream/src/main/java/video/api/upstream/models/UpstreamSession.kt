package video.api.upstream.models

import android.content.Context
import video.api.uploader.api.models.Video
import video.api.uploader.api.services.UploadService
import video.api.uploader.api.services.UploadServiceListener
import video.api.upstream.getSessionDir
import video.api.upstream.getSessionPartsDir
import java.io.File
import java.security.InvalidParameterException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UpstreamSession
/**
 * Manages the upload of a video parts.
 * An [UpstreamSession] uploads one video only. A video is composed of multiple parts.
 *
 * @param context The application context
 * @param id The unique session id
 * @param uploadService The upload service
 * @param sessionListener The listener for one full video
 * @param sessionUploadPartListener The listener for a part of a video
 */
private constructor(
    context: Context,
    val id: String,
    private val uploadService: UploadService,
    private val progressiveSession: UploadService.ProgressiveUploadSession,
    private val sessionListener: SessionListener?,
    private val sessionUploadPartListener: SessionUploadPartListener?
) {
    private constructor(
        context: Context,
        id: String = UUID.randomUUID().toString(),
        uploadService: UploadService,
        videoId: String? = null,
        token: String? = null,
        sessionListener: SessionListener? = null,
        sessionUploadPartListener: SessionUploadPartListener? = null
    ) : this(
        context,
        id = id,
        uploadService = uploadService,
        progressiveSession = uploadService.createProgressiveUploadSession(videoId ?: token!!),
        sessionListener = sessionListener,
        sessionUploadPartListener = sessionUploadPartListener
    ) {
        require((videoId != null) || (token != null)) { "Token or videoId must be set" }
        videoId?.let {
            storage.videoId = videoId
        }
        token?.let {
            storage.token = token
        }
    }

    private val sessionDir = context.getSessionDir(id)
    private val partsDir = context.getSessionPartsDir(id)

    private val storage = UpstreamSessionStorage(sessionDir)
    private val uploadPartHashMap = ConcurrentHashMap<String, UploadPart>()

    private var created = false

    private val listener = object : UploadServiceListener {
        override fun onLastUpload() {}

        override fun onUploadCancelled(id: String) {
            try {
                uploadPartHashMap[id]!!
            } catch (e: Exception) {
                // The part is not for this session
                return
            }

            if (allPartsSent && hasLastPart) {
                onEnd()
            }
        }

        override fun onUploadComplete(id: String, video: Video) {
            val part = try {
                uploadPartHashMap[id]!!
            } catch (e: Exception) {
                // The part is not for this session
                return
            }

            if (storage.videoId == null) {
                storage.videoId = video.videoId
            }

            part.sent = true
            part.file.delete()
            sessionUploadPartListener?.onComplete(this@UpstreamSession, part.chunkIndex, video)
            if (allPartsSent && hasLastPart) {
                onEnd()
            }
        }

        override fun onUploadError(id: String, e: Exception) {
            val part = try {
                uploadPartHashMap[id]!!
            } catch (e: Exception) {
                // The part is not for this session
                return
            }

            part.sent = true
            sessionUploadPartListener?.onError(this@UpstreamSession, part.chunkIndex, e)
            if (allPartsSent && hasLastPart) {
                onEnd()
            }
        }

        override fun onUploadProgress(id: String, progress: Int) {
            val part = try {
                uploadPartHashMap[id]!!
            } catch (e: Exception) {
                // The part is not for this session
                return
            }

            sessionUploadPartListener?.onProgressChanged(
                this@UpstreamSession,
                part.chunkIndex,
                progress
            )
        }

        override fun onUploadStarted(id: String) {
            val part = try {
                uploadPartHashMap[id]!!
            } catch (e: Exception) {
                // The part is not for this session
                return
            }

            sessionUploadPartListener?.onNewPartStarted(this@UpstreamSession, part.chunkIndex)
        }
    }

    init {
        uploadService.addListener(listener)
    }

    private fun onEnd() {
        release()
        if (!hasRemainingParts) {
            deleteAll()
            sessionListener?.onComplete(this@UpstreamSession)
        } else {
            sessionListener?.onEndWithError(this@UpstreamSession)
        }
    }

    internal fun upload(chunkIndex: Int, isLastPart: Boolean, file: File) {
        if (!created) {
            created = true
            sessionListener?.onNewSessionCreated(this@UpstreamSession)
        }

        if (isLastPart) {
            if (storage.lastPartId == null) {
                storage.lastPartId = chunkIndex
            }
        }

        val uploadId = if (isLastPart) {
            progressiveSession.uploadLastPart(
                file,
                chunkIndex
            )
        } else {
            progressiveSession.uploadPart(
                file,
                chunkIndex
            )
        }
        uploadPartHashMap[uploadId] = UploadPart(chunkIndex, isLastPart, file)
        sessionListener?.onNumberOfPartsChanged(this, numOfParts)
    }

    private fun release() {
        uploadService.removeListener(listener)
    }

    /**
     * Cancel all uploads of this session.
     */
    fun cancelAll() {
        uploadPartHashMap.keys.forEach {
            uploadService.cancel(it)
        }
    }

    /**
     * Deletes remaining parts and [parentDir].
     */
    fun deleteAll() {
        sessionDir.deleteRecursively()
    }

    private val allPartsSent: Boolean
        get() = uploadPartHashMap.none { !it.value.sent }

    private val hasLastPart: Boolean
        get() = uploadPartHashMap.any { it.value.isLast }

    /**
     * Total number of parts for the video.
     * The number of parts increases when parts are still been generated.
     */
    val numOfParts: Int
        get() = storage.lastPartId ?: uploadPartHashMap.size

    /**
     * Number of parts that haven't been send yet.
     */
    val numOfRemainingParts: Int
        get() = partsDir.list()?.size ?: 0

    /**
     * Check if there are still remaining parts
     */
    val hasRemainingParts: Boolean
        get() = numOfRemainingParts > 0

    companion object {
        private const val TAG = "UpstreamSession"

        /**
         * Creates a session from the session id.
         * Mostly use for backup session.
         * Remaining parts will be added to the [UploadService].
         *
         * @param context The application context
         * @param uploadService The upload service
         * @param sessionId The session id
         * @param sessionListener The listener of the session events. Could be null.
         * @param sessionUploadPartListener The listener of the parts events. Could be null.
         */
        fun createFromSessionId(
            context: Context,
            uploadService: UploadService,
            sessionId: String,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ): UpstreamSession {
            if (!context.getSessionDir(sessionId).exists()) {
                throw InvalidParameterException("Unknown session")
            }
            if (!hasRemainingParts(context, sessionId)) {
                throw InvalidParameterException("Session has no more file to upload")
            }

            val storage = UpstreamSessionStorage(context.getSessionDir(sessionId))
            val videoId = storage.videoId
            val token = storage.token
            val lastId = storage.lastPartId
                ?: throw UnsupportedOperationException("Session must have a lastId")

            val upstreamSession = UpstreamSession(
                context,
                sessionId,
                uploadService,
                videoId,
                token,
                sessionListener,
                sessionUploadPartListener
            )

            context.getSessionPartsDir(sessionId).listFiles()?.forEach {
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
            uploadService: UploadService,
            videoId: String,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ) = UpstreamSession(
            context,
            uploadService = uploadService,
            videoId = videoId,
            token = null,
            sessionListener = sessionListener,
            sessionUploadPartListener = sessionUploadPartListener
        )

        internal fun createForUploadToken(
            context: Context,
            uploadService: UploadService,
            token: String,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ) = UpstreamSession(
            context,
            uploadService = uploadService,
            videoId = null,
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


