package video.api.upstream.models.storage

import java.io.File

/**
 * Implementation of [IUpstreamDao] that store the session in the file system.
 *
 * The session is stored in a directory named with the session id and contains the following files:
 * - `videoId`: contains the video id
 * - `token`: contains the token
 * - `lastPart`: contains the index of the last part uploaded
 * - `parts`: contains the list of parts uploaded
 *      - `1`: contains the URL of part 1
 *      - `2`: contains the URL of part 2
 *
 * @param workingDir The working directory
 */
class FileUpstreamDao(private val workingDir: File) : IUpstreamDao {
    init {
        workingDir.mkdirs()
    }

    override val allSessions: List<UpstreamSessionEntity>
        get() = workingDir.list()?.mapNotNull { getById(it) } ?: emptyList()

    override fun getById(sessionId: String): UpstreamSessionEntity? {
        val sessionDir = File(workingDir, sessionId)
        if (!sessionDir.exists()) {
            return null
        }
        return sessionDir.upstreamSession
    }

    override fun getByVideoId(videoId: String): UpstreamSessionEntity? {
        return workingDir.listFiles()?.singleOrNull { getVideoId(it) == videoId }?.upstreamSession
    }

    override fun getByToken(token: String, videoId: String?): List<UpstreamSessionEntity> {
        val sessionDirs = workingDir.listFiles()
            ?.filter { getToken(it) == token } ?: emptyList()

        return if (videoId != null) {
            sessionDirs.filter { getVideoId(it) == videoId }.map { it.upstreamSession }
        } else {
            sessionDirs.map { it.upstreamSession }
        }
    }

    override fun insert(sessionId: String) {
        val sessionDir = File(workingDir, sessionId)
        sessionDir.mkdirs()
        val partInfoDir = File(sessionDir, PART_INFO_DIR)
        partInfoDir.mkdirs()
    }

    override fun remove(sessionId: String) {
        File(workingDir, sessionId).deleteRecursively()
    }

    override fun insertVideoId(sessionId: String, videoId: String) {
        val sessionDir = File(workingDir, sessionId)
        val videoIdFile = File(sessionDir, VIDEO_ID_FILE_NAME)
        if (videoIdFile.exists()) {
            return
        }
        videoIdFile.writeText(videoId)
    }

    override fun insertToken(sessionId: String, token: String) {
        val sessionDir = File(workingDir, sessionId)
        val tokenFile = File(sessionDir, TOKEN_FILE_NAME)
        if (tokenFile.exists()) {
            return
        }
        tokenFile.writeText(token)
    }

    override fun getLastPartId(sessionId: String): Int? {
        val sessionDir = File(workingDir, sessionId)
        val lastPartIdFile = File(sessionDir, LAST_PART_ID_FILE_NAME)
        return if (lastPartIdFile.exists()) {
            lastPartIdFile.readText().toInt()
        } else {
            null
        }
    }

    private fun insertLastPartId(sessionId: String, lastPartIndex: Int) {
        val sessionDir = File(workingDir, sessionId)
        val lastPartIdFile = File(sessionDir, LAST_PART_ID_FILE_NAME)
        if (lastPartIdFile.exists()) {
            return
        }
        lastPartIdFile.writeText("$lastPartIndex")
    }

    override fun hasParts(sessionId: String): Boolean {
        val sessionDir = File(workingDir, sessionId)
        val partDir = File(sessionDir, PART_INFO_DIR)
        return partDir.list()?.isNotEmpty() ?: false
    }

    override fun getParts(sessionId: String) = getPartsInfo(File(workingDir, sessionId))

    override fun insertPart(sessionId: String, part: Part) {
        val sessionDir = File(workingDir, sessionId)
        val partInfoDir = File(sessionDir, PART_INFO_DIR)

        if (part.isLast) {
            insertLastPartId(sessionId, part.index)
        }

        val partFile = File(partInfoDir, "${part.index}")
        if (partFile.exists()) {
            throw IllegalStateException("The part ${part.index} already exists")
        }
        partFile.writeText(part.file.path)
    }

    override fun removePart(sessionId: String, partIndex: Int) {
        val sessionDir = File(workingDir, sessionId)
        val partDir = File(sessionDir, PART_INFO_DIR)
        File(partDir, "$partIndex").delete()
    }

    private val File.upstreamSession: UpstreamSessionEntity
        get() {
            require(this.isDirectory) { "The file must be a directory" }
            return UpstreamSessionEntity(
                id = this.name,
                videoId = getVideoId(this),
                token = getToken(this),
                parts = getPartsInfo(this)
            )
        }

    companion object {
        private const val PART_INFO_DIR = "partInfo"

        private const val VIDEO_ID_FILE_NAME = "videoId"
        private const val TOKEN_FILE_NAME = "token"
        private const val LAST_PART_ID_FILE_NAME = "lastPartId"

        /**
         * Gets the video id of the session.
         *
         * @param sessionDir The session directory
         * @return the video id
         */
        private fun getVideoId(sessionDir: File): String? {
            val videoIdFile = File(sessionDir, VIDEO_ID_FILE_NAME)
            return if (videoIdFile.exists()) {
                videoIdFile.readText()
            } else {
                null
            }
        }

        /**
         * Gets the upload token of the session.
         *
         * @param sessionDir The session directory
         * @return the upload token
         */
        private fun getToken(sessionDir: File): String? {
            val tokenFile = File(sessionDir, TOKEN_FILE_NAME)
            return if (tokenFile.exists()) {
                tokenFile.readText()
            } else {
                null
            }
        }


        /**
         * Gets the last part id of the session if it exists.
         *
         * @param sessionDir The session directory
         * @return the last part id
         */
        private fun getLastPartId(sessionDir: File): Int? {
            val lastPartIdFile = File(sessionDir, LAST_PART_ID_FILE_NAME)
            return if (lastPartIdFile.exists()) {
                lastPartIdFile.readText().toInt()
            } else {
                null
            }
        }

        /**
         * Gets all the parts for a session.
         *
         * @param sessionDir The session directory
         * @return the list of parts
         */
        private fun getPartsInfo(sessionDir: File): List<Part> {
            val partDir = File(sessionDir, PART_INFO_DIR)
            return partDir.list()?.mapNotNull { partIndex ->
                getPartInfo(sessionDir, partIndex.toInt())
            } ?: emptyList()
        }

        /**
         * Gets part from [partIndex].
         *
         * @param sessionDir The session directory
         * @param partIndex The index of the part
         * @return the part
         */
        private fun getPartInfo(sessionDir: File, partIndex: Int): Part? {
            val lastPartIndex = getLastPartId(sessionDir)

            val partDir = File(sessionDir, PART_INFO_DIR)
            val file = File(partDir, "$partIndex")
            return if (file.exists()) {
                Part(partIndex, partIndex == lastPartIndex, File(file.readText()))
            } else {
                null
            }
        }


        /**
         * Writes the session to disk.
         *
         * @param directory The directory to write the session to
         */
        fun UpstreamSessionEntity.writeToDisk(directory: File) {
            val sessionDir = File(directory, id)
            sessionDir.mkdirs()

            videoId?.let {
                File(sessionDir, VIDEO_ID_FILE_NAME).writeText(it)
            }
            token?.let {
                File(sessionDir, TOKEN_FILE_NAME).writeText(it)
            }
            parts.singleOrNull { it.isLast }?.let {
                File(sessionDir, LAST_PART_ID_FILE_NAME).writeText("${it.index}")
            }

            val partInfoDir = File(sessionDir, PART_INFO_DIR)
            partInfoDir.mkdirs()

            parts.forEach {
                File(partInfoDir, it.index.toString()).writeText(it.file.path)
            }
        }
    }
}