package video.api.upstream.models.storage

import java.io.File

/**
 * A part of a file.
 */
data class Part(val index: Int, var isLast: Boolean, val file: File)