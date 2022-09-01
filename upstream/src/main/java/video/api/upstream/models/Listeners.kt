package video.api.upstream.models

import video.api.uploader.api.models.Video
import video.api.upstream.ApiVideoUpstream

/**
 * Streamer event listener
 */
interface StreamerListener {
    /**
     * Called when an error has happened on the streamer.
     * The streamer has stopped to generates video parts.
     *
     * @param error The error that occurred
     */
    fun onError(error: Exception) {}
}

/**
 * Upload parts events listener
 */
interface SessionUploadPartListener {
    /**
     * Called when the upload of a new part of the session has started.
     *
     * @param session The upstream session
     * @param partId The id of the part
     */
    fun onNewPartStarted(session: UpstreamSession, partId: Int) {}

    /**
     * Called when an error occurred during the upload of a part.
     *
     * @param session The upstream session
     * @param partId The id of the part
     * @param error The error that occurred
     */
    fun onError(session: UpstreamSession, partId: Int, error: Exception) {}

    /**
     * Called when a part has been successfully uploaded
     *
     * @param session The upstream session
     * @param partId The id of the part
     * @param video The video
     */
    fun onComplete(session: UpstreamSession, partId: Int, video: Video) {}

    /**
     * Called when the progress has been updated
     *
     * @param session The upstream session
     * @param partId The id of the part
     */
    fun onProgressChanged(session: UpstreamSession, partId: Int, progress: Int) {}
}

/**
 * Upstream session events listener
 */
interface SessionListener {
    /**
     * Called when a new session has been created.
     * It is called when the first file has been added to the upload queue.
     *
     * @param session The upstream session
     */
    fun onNewSessionCreated(session: UpstreamSession) {}

    /**
     * Called when the number of parts in session has changed.
     *
     * @param session The upstream session
     * @param numOfParts The number of parts
     */
    fun onNumberOfPartsChanged(session: UpstreamSession, numOfParts: Int) {}

    /**
     * Called when all the parts of a session has been successfully uploaded.
     *
     * @param session The upstream session
     */
    fun onComplete(session: UpstreamSession) {}

    /**
     * Called when the session uploaded all the parts but few parts weren't uploaded (due to error
     * or cancellation).
     * In this case, you can create a new backup session to upload the remaining files
     * [ApiVideoUpstream.createBackupSessionFromSessionId].
     *
     * @param session The upstream session
     */
    fun onEndWithError(session: UpstreamSession) {}
}