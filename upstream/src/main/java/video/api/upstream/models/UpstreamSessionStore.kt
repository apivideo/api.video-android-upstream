package video.api.upstream.models

import android.content.Context
import video.api.upstream.appendPartsDir
import video.api.upstream.getSessionDir
import video.api.upstream.upstreamWorkDir
import java.io.File
import java.io.FileNotFoundException

/**
 * Manages the storage of the [UpstreamSession]
 *
 * @param sessionDir The session directory
 */
class UpstreamSessionStore(private val sessionDir: File) {
    private val videoIdFile = File(sessionDir, VIDEO_ID_FILE_NAME)
    private val tokenFile = File(sessionDir, TOKEN_FILE_NAME)
    private val lastPartIdFIle = File(sessionDir, LAST_PART_ID_FILE_NAME)

    val partsDir = sessionDir.appendPartsDir()
    val numOfRemaingParts: Int
        get() = partsDir.listFiles()?.size ?: 0
    val hasRemainingParts: Boolean
        get() = partsDir.listFiles()?.isNotEmpty() ?: false

    constructor(context: Context, sessionId: String) : this(context.getSessionDir(sessionId))

    init {
        sessionDir.mkdirs()
        partsDir.mkdirs()
    }

    var videoId: String?
        get() =
            try {
                videoIdFile.readText()
            } catch (e: FileNotFoundException) {
                null
            }
        set(value) {
            value?.let {
                videoIdFile.writeText(it)
            } ?: throw IllegalArgumentException("videoId can not be null")
        }
    var token: String?
        get() =
            try {
                tokenFile.readText()
            } catch (e: FileNotFoundException) {
                null
            }
        set(value) {
            value?.let {
                tokenFile.writeText(it)
            } ?: throw IllegalArgumentException("token can not be null")
        }
    var lastPartId: Int?
        get() =
            try {
                lastPartIdFIle.readText().toInt()
            } catch (e: FileNotFoundException) {
                null
            }
        set(value) {
            value?.let {
                lastPartIdFIle.writeText("$it")
            } ?: throw IllegalArgumentException("lastPartId can not be null")
        }

    fun clean() {
        sessionDir.deleteRecursively()
    }

    companion object {
        private const val VIDEO_ID_FILE_NAME = "videoId"
        private const val TOKEN_FILE_NAME = "token"
        private const val LAST_PART_ID_FILE_NAME = "lastPartId"

        /**
         * Gets the video id of the session.
         *
         * @param sessionDir The session directory
         * @return the video id
         */
        fun getVideoId(sessionDir: File) =
            File(sessionDir, VIDEO_ID_FILE_NAME).readText()

        /**
         * Gets the video id of the session.
         *
         * @param context The application context
         * @param sessionId The session id
         * @return the video id
         */
        fun getVideoId(context: Context, sessionId: String) =
            getVideoId(context.getSessionDir(sessionId))

        /**
         * Gets the upload token of the session.
         *
         * @param sessionDir The session directory
         * @return the upload token
         */
        fun getToken(sessionDir: File) =
            File(sessionDir, TOKEN_FILE_NAME).readText()

        /**
         * Gets the upload token of the session.
         *
         * @param context The application context
         * @param sessionId The session id
         * @return the upload token
         */
        fun getToken(context: Context, sessionId: String) =
            getToken(context.getSessionDir(sessionId))

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
         * Deletes all internal files for a session Id.
         *
         * @param context The application context
         * @param sessionId The session id
         */
        fun delete(context: Context, sessionId: String) {
            context.getSessionDir(sessionId).deleteRecursively()
        }

        /**
         * Gets the session id of the sessions that has remaining parts.
         *
         * @param context The application context
         * @return list of session id
         */
        fun getSessionIds(context: Context): List<String> {
            return context.upstreamWorkDir.listFiles()?.map { it.name } ?: emptyList()
        }

        /**
         * Gets the id of the session that matches the video id
         *
         * @param context The application context
         * @param videoId The video id
         * @return the session id
         */
        fun getSessionId(context: Context, videoId: String) =
            context.upstreamWorkDir.listFiles()
                ?.first { getVideoId(it) == videoId }?.name

        /**
         * Gets the id of the session that matches the token and optionally the video id
         *
         * @param context The application context
         * @param token The upload token
         * @param videoId The video id
         * @return the list of session ids
         */
        fun getSessionId(context: Context, token: String, videoId: String?): List<String> {
            val sessionDirs = context.upstreamWorkDir.listFiles()
                ?.filter { getToken(it) == token } ?: emptyList()

            return if (videoId != null) {
                sessionDirs.filter { getVideoId(it) == videoId }.map { it.name }
            } else {
                sessionDirs.map { it.name }
            }
        }
    }
}