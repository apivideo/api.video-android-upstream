package video.api.upstream

import android.content.Context
import java.io.File

private const val UPSTREAM_DIR_NAME = "upstream"
private const val PART_DIR_NAME = "parts"
private const val SESSION_DIR_NAME = "sessions"

val Context.upstreamWorkDir
    get() = File(this.cacheDir, UPSTREAM_DIR_NAME)

val Context.sessionsDir
    get() = File(this.upstreamWorkDir, SESSION_DIR_NAME)

val Context.partsDir
    get() = File(upstreamWorkDir, PART_DIR_NAME)
