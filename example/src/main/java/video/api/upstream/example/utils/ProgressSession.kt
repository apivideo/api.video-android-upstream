package video.api.upstream.example.utils

import video.api.upstream.models.UpstreamSession

data class ProgressSession(
    val sessionId: Int,
    var numOfParts: Int,
    var currentPartProgress: PartProgress? = null
) {
    constructor(upstreamSession: UpstreamSession) : this(upstreamSession.hashCode(), upstreamSession.totalNumOfParts)
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
        upstreamSession: UpstreamSession,
        numOfParts: Int
    ) : this(upstreamSession.hashCode(), numOfParts)
}

data class ProgressSessionPart(val sessionId: Int, val part: Int, val progress: Int = 0) {
    constructor(upstreamSession: UpstreamSession, partId: Int, progress: Int = 0) : this(
        upstreamSession.hashCode(),
        partId,
        progress
    )
}