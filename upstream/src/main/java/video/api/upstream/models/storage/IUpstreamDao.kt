package video.api.upstream.models.storage

interface IUpstreamDao {
    val allSessions: List<UpstreamSessionEntity>

    fun getById(sessionId: String): UpstreamSessionEntity?
    fun getByVideoId(videoId: String): UpstreamSessionEntity?
    fun getByToken(token: String, videoId: String?): List<UpstreamSessionEntity>

    fun insert(session: UpstreamSessionEntity)
    fun remove(sessionId: String)

    fun insertVideoId(sessionId: String, videoId: String)

    fun getLastPartId(sessionId: String): Int?

    fun hasPart(sessionId: String): Boolean
    fun getParts(sessionId: String): List<Part>

    /**
     * Insert a part of the session
     * If the part is the last part, the last part id have to be insert.
     */
    fun insertPart(sessionId: String, part: Part)
    fun removePart(sessionId: String, partIndex: Int)
}