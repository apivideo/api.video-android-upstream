package video.api.upstream

import android.content.Context
import java.io.File
import java.util.*

fun Context.createSessionWorkDir() =
    this.getSessionWorkDir(UUID.randomUUID().toString())

fun Context.getSessionWorkDir(folderName: String) =
    File(this.upstreamWorkDir, folderName)

val Context.upstreamWorkDir
    get() = File(this.cacheDir, "upstream")

