package video.api.upstream

import android.content.Context
import java.io.File
import java.util.*

private const val PART_DIR_NAME = "parts"

fun Context.getSessionPartsDir(sessionId: String) =
    File(this.getSessionDir(sessionId), PART_DIR_NAME)

fun File.appendPartsDir() =
    File(this, PART_DIR_NAME)

fun Context.getSessionDir(sessionId: String) =
    File(this.upstreamWorkDir, sessionId)

val Context.upstreamWorkDir
    get() = File(this.cacheDir, "upstream")

