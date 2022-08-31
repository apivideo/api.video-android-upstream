package video.api.upstream.models

import java.io.File
import java.io.FileNotFoundException

/**
 * Manages the storage of the [UpstreamSession]
 *
 * @param sessionDir The session directory
 */
class UpstreamSessionStorage(sessionDir: File) {
    private val videoIdFile = File(sessionDir, VIDEO_ID_FILE_NAME)
    private val tokenFile = File(sessionDir, TOKEN_FILE_NAME)
    private val lastPartIdFIle = File(sessionDir, LAST_PART_ID_FILE_NAME)

    init {
        sessionDir.mkdirs()
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

    companion object {
        private const val VIDEO_ID_FILE_NAME = "videoId"
        private const val TOKEN_FILE_NAME = "token"
        private const val LAST_PART_ID_FILE_NAME = "lastPartId"

        fun getVideoId(sessionDir: File) =
            File(sessionDir, VIDEO_ID_FILE_NAME).readText()

        fun getToken(sessionDir: File) =
            File(sessionDir, TOKEN_FILE_NAME).readText()
    }
}