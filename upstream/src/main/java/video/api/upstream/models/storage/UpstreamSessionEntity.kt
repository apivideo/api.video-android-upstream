package video.api.upstream.models.storage

import video.api.upstream.models.MultiFileUploader

/**
 * The entity of the [MultiFileUploader] in the storage.
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
    val lastPart = parts.singleOrNull { it.isLast }

    /**
     * Whether the session has remaining parts to upload.
     */
    val hasParts: Boolean
        get() = parts.isNotEmpty()
}