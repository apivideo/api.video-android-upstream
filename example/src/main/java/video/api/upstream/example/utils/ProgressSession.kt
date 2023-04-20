package video.api.upstream.example.utils

import video.api.upstream.models.MultiFileUploader

data class ProgressSession(
    val sessionId: Int,
    var numOfParts: Int,
    var currentPartProgress: PartProgress? = null
) {
    constructor(multiFileUploader: MultiFileUploader) : this(multiFileUploader.hashCode(), multiFileUploader.totalNumOfParts)
}

data class PartProgress(
    var part: Int,
    var progress: Int = 0
)


data class SessionParts(
    val sessionId: Int,
    val numOfParts: Int,
) {
    constructor(
        multiFileUploader: MultiFileUploader,
        numOfParts: Int
    ) : this(multiFileUploader.hashCode(), numOfParts)
}

data class ProgressSessionPart(val sessionId: Int, val part: Int, val progress: Int = 0) {
    constructor(multiFileUploader: MultiFileUploader, partId: Int, progress: Int = 0) : this(
        multiFileUploader.hashCode(),
        partId,
        progress
    )
}