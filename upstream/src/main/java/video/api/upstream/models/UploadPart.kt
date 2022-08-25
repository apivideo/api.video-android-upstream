package video.api.upstream.models

import java.io.File

data class UploadPart(
    val chunkIndex: Int,
    val isLast: Boolean,
    val file: File,
    var wasSent: Boolean = false
)
