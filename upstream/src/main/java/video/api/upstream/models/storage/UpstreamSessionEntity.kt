package video.api.upstream.models.storage

import video.api.upstream.models.UpstreamSession

/**
 * The entity of the [UpstreamSession] in the storage.
 */
data class UpstreamSessionEntity(
    val id: String,
    val videoId: String?,
    val token: String?,
    val parts: List<Part>
) {
    /**
     * The last part of the session.
     */
    val lastPart = parts.single { it.isLast }

    /**
     * Whether the session has remaining parts to upload.
     */
    val hasParts: Boolean
        get() = parts.isNotEmpty()
}