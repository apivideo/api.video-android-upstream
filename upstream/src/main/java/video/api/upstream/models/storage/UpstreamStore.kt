package video.api.upstream.models.storage

import android.content.Context
import video.api.upstream.sessionsDir

/**
 * A singleton that contains the [IUpstreamDao] database.
 */
class UpstreamStore private constructor(val upstreamDao: IUpstreamDao) {
    companion object {
        @Volatile
        private var instance: UpstreamStore? = null

        fun getStorage(context: Context): IUpstreamDao {
            return instance?.upstreamDao ?: synchronized(this) {
                val instance = UpstreamStore(FileUpstreamDao(context.sessionsDir))
                this.instance = instance
                return instance.upstreamDao
            }
        }
    }
}