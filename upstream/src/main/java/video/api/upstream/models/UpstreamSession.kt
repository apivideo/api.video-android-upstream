package video.api.upstream.models

import android.content.Context
import video.api.uploader.api.models.Video
import video.api.uploader.api.services.UploadService
import video.api.uploader.api.services.UploadServiceListener
import video.api.upstream.getSessionWorkDir
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UpstreamSession(
    private val workDir: File,
    private val uploadService: UploadService,
    private val progressiveSession: UploadService.ProgressiveUploadSession,
    private val sessionListener: SessionListener? = null,
    private val sessionUploadPartListener: SessionUploadPartListener? = null
) {
    private val uploadPartHashMap = ConcurrentHashMap<String, UploadPart>()

    private var wasCreated = false
    private var hasStarted = false

    private val listener = object : UploadServiceListener {
        override fun onLastUpload() {}

        override fun onUploadCancelled(id: String) {
            // Unused
        }

        override fun onUploadComplete(id: String, video: Video) {
            val part = try {
                uploadPartHashMap[id]!!
            } catch (e: Exception) {
                // The part is not for this session
                return
            }

            part.wasSent = true
            part.file.delete()
            sessionUploadPartListener?.onComplete(this@UpstreamSession, part.chunkIndex)
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

            part.wasSent = true
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

            if (!hasStarted) {
                hasStarted = true
                sessionListener?.onSessionStarted(this@UpstreamSession)
            }

            sessionUploadPartListener?.onNewPartStarted(this@UpstreamSession, part.chunkIndex)
        }
    }

    init {
        uploadService.addListener(listener)
    }

    private fun onEnd() {
        if (!hasRemainingFiles) {
            deleteAll()
            sessionListener?.onComplete(this@UpstreamSession)
        } else {
            sessionListener?.onEndWithError(this@UpstreamSession)
        }
    }

    internal fun upload(chunkIndex: Int, isLastPart: Boolean, file: File) {
        if (!wasCreated) {
            wasCreated = true
            sessionListener?.onNewSessionCreated(this@UpstreamSession)
        }

        val renamedFile = if (isLastPart) {
            val lastFile = File(
                file.parentFile, file.name + ".last"
            )
            file.renameTo(lastFile) // rename last file to identify it as last file
            lastFile
        } else {
            file
        }
        uploadImpl(chunkIndex, isLastPart, renamedFile)
    }

    private fun uploadImpl(chunkIndex: Int, isLastPart: Boolean, file: File) {
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
        sessionListener?.onTotalNumberOfPartsChanged(this, uploadPartHashMap.size)
    }

    fun release() {
        uploadService.removeListener(listener)
        cancelAll()
    }

    fun cancelAll() {
        uploadPartHashMap.keys.forEach {
            uploadService.cancel(it)
        }
    }

    /**
     * Deletes remaining parts and [parentDir].
     */
    fun deleteAll() {
        workDir.deleteRecursively()
    }

    /**
     * Retry to upload the parts that has not been uploaded
     */
    fun retryUploads() {
        uploadPartHashMap.clear()

        workDir.listFiles()?.forEach {
            if (it.name.endsWith(".last")) {
                uploadImpl(it.name.replace(".last", "").toInt(), true, it)
            } else {
                uploadImpl(it.name.toInt(), false, it)
            }
        }
    }

    private val allPartsSent: Boolean
        get() = uploadPartHashMap.none { !it.value.wasSent }

    private val hasLastPart: Boolean
        get() = uploadPartHashMap.any { it.value.isLast }

    val numOfParts: Int
        get() = uploadPartHashMap.size

    private val numOfRemainingFiles: Int
        get() = workDir.list()?.size ?: 0

    private val hasRemainingFiles: Boolean
        get() = numOfRemainingFiles > 0

    companion object {
        const val TAG = "UpstreamSession"

        fun createForVideoId(
            workDir: File,
            uploadService: UploadService,
            videoId: String,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ): UpstreamSession =
            UpstreamSession(
                workDir,
                uploadService,
                uploadService.createProgressiveUploadSession(videoId),
                sessionListener,
                sessionUploadPartListener
            )

        fun createForUploadToken(
            workDir: File,
            uploadService: UploadService,
            token: String,
            sessionListener: SessionListener? = null,
            sessionUploadPartListener: SessionUploadPartListener? = null
        ) =
            UpstreamSession(
                workDir,
                uploadService,
                uploadService.createUploadTokenProgressiveUploadSession(token),
                sessionListener,
                sessionUploadPartListener
            )

        fun numOfRemainingFiles(context: Context, folderName: String) =
            context.getSessionWorkDir(folderName).list()?.size ?: 0

        fun hasRemainingFiles(context: Context, folderName: String) =
            numOfRemainingFiles(context, folderName) > 0
    }
}


