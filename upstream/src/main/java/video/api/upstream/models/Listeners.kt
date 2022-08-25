package video.api.upstream.models

interface StreamerListener {
    fun onError(error: Exception) {}
}

interface SessionUploadPartListener {
    fun onNewPartStarted(session: UpstreamSession, partId: Int) {}
    fun onError(session: UpstreamSession, partId: Int, error: Exception) {}
    fun onComplete(session: UpstreamSession, partId: Int) {}
    fun onProgressChanged(session: UpstreamSession, partId: Int, progress: Int) {}
}

interface SessionListener {
    fun onNewSessionCreated(session: UpstreamSession) {}
    fun onSessionStarted(session: UpstreamSession) {}
    fun onTotalNumberOfPartsChanged(session: UpstreamSession, totalNumberOfParts: Int) {}
    fun onComplete(session: UpstreamSession) {}
    fun onEndWithError(session: UpstreamSession) {}
}