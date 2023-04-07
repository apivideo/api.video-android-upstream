package video.api.upstream.mocks

import video.api.upstream.mocks.SingleUpstreamSessionEntityMockStore.Companion.fromUpstreamSessionEntity
import video.api.upstream.models.storage.IUpstreamDao
import video.api.upstream.models.storage.Part
import video.api.upstream.models.storage.UpstreamSessionEntity

class MockUpstreamDao(
    private val sessions: MutableList<SingleUpstreamSessionEntityMockStore> = mutableListOf(),
) : IUpstreamDao {
    override val allSessions: List<UpstreamSessionEntity>
        get() = sessions.map { it.toUpstreamSessionEntity() }

    override fun getById(sessionId: String): UpstreamSessionEntity? =
        sessions.singleOrNull { it.id == sessionId }?.toUpstreamSessionEntity()

    override fun getByVideoId(videoId: String): UpstreamSessionEntity? =
        sessions.singleOrNull { it.videoId == videoId }?.toUpstreamSessionEntity()

    override fun getByToken(token: String, videoId: String?): List<UpstreamSessionEntity> {
        val sessions = sessions.filter { it.token == token }
        return if (videoId != null) {
            sessions.filter { it.videoId == videoId }.map { it.toUpstreamSessionEntity() }
        } else {
            sessions.map { it.toUpstreamSessionEntity() }
        }
    }

    override fun insert(session: UpstreamSessionEntity) {
        sessions.add(fromUpstreamSessionEntity(session))
    }

    override fun remove(sessionId: String) {
        sessions.removeAll { it.id == sessionId }
    }

    override fun insertVideoId(sessionId: String, videoId: String) {
        val session = sessions.single { it.id == sessionId }
        if (session.videoId != null) {
            return
        }
        session.videoId = videoId
    }

    override fun getLastPartId(sessionId: String) =
        sessions.single { it.id == sessionId }.lastPart.index

    override fun hasPart(sessionId: String) = sessions.single { it.id == sessionId }.hasParts

    override fun getParts(sessionId: String) = sessions.single { it.id == sessionId }.parts

    override fun insertPart(sessionId: String, part: Part) {
        val session = sessions.single { it.id == sessionId }
        if (session.parts.any { it.index == part.index }) {
            return
        }
        session.parts.add(part)
    }

    override fun removePart(sessionId: String, partIndex: Int) {
        sessions.single { it.id == sessionId }.parts.removeAll { it.index == partIndex }
    }
}

class SingleUpstreamSessionEntityMockStore(
    val id: String,
    var videoId: String?,
    var token: String?,
    val parts: MutableList<Part> = mutableListOf(),
) {
    var lastPart = parts.single { it.isLast }

    val hasParts: Boolean
        get() = parts.isNotEmpty()

    fun toUpstreamSessionEntity() = UpstreamSessionEntity(id, videoId, token, parts)

    companion object {
        fun fromUpstreamSessionEntity(session: UpstreamSessionEntity) =
            SingleUpstreamSessionEntityMockStore(
                session.id,
                session.videoId,
                session.token,
                session.parts.toMutableList()
            )
    }
}