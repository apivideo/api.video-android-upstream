package video.api.upstream.service

import android.app.Notification
import video.api.uploader.api.models.Video
import video.api.uploader.api.services.UploadService

class UploadServiceWithoutNotifications : UploadService() {
    override fun onUploadStartedNotification(id: String): Notification? {
        return null
    }

    override fun onUploadSuccessNotification(
        id: String,
        video: Video
    ): Notification? {
        return null
    }

    override fun onUploadProgressNotification(id: String, progress: Int): Notification? {
        return null
    }

    override fun onUploadErrorNotification(id: String, e: Exception): Notification? {
        return null
    }

    override fun onUploadCancelledNotification(id: String): Notification? {
        return null
    }

    override fun onLastUploadNotification(): Notification? {
        return null
    }
}
