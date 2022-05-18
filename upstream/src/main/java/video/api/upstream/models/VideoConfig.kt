package video.api.upstream.models

import video.api.upstream.enums.Resolution

/**
 * Describes video configuration.
 */
class VideoConfig(
    /**
     * Video resolution.
     * @see [Resolution]
     */
    val resolution: Resolution = Resolution.RESOLUTION_720,

    /**
     * Video bitrate in bps.
     */
    val bitrate: Int = getDefaultBitrate(resolution),

    /**
     * Video frame rate.
     */
    val fps: Int = 30
) {
    init {
        require(fps <= 60) { "FPS must be less or equal to 60" }
        if (resolution == Resolution.RESOLUTION_2160) {
            require(bitrate <= 50 * 1000 * 1000) { "Bitrate must be less or equal to 50Mbps for 4K" }
        } else {
            require(bitrate <= 16 * 1000 * 1000) { "Bitrate must be less or equal to 16Mbps" }
        }
    }

    internal fun toSdkConfig(): io.github.thibaultbee.streampack.data.VideoConfig {
        return io.github.thibaultbee.streampack.data.VideoConfig(
            startBitrate = bitrate,
            resolution = resolution.size,
            fps = fps
        )
    }

    companion object {
        internal fun fromSdkConfig(config: io.github.thibaultbee.streampack.data.VideoConfig): VideoConfig {
            return VideoConfig(
                bitrate = config.startBitrate,
                resolution = Resolution.valueOf(config.resolution),
                fps = config.fps
            )
        }

        private fun getDefaultBitrate(resolution: Resolution): Int {
            return when (resolution) {
                Resolution.RESOLUTION_240 -> 1500000
                Resolution.RESOLUTION_360 -> 2400000
                Resolution.RESOLUTION_480 -> 3000000
                Resolution.RESOLUTION_720 -> 5000000
                Resolution.RESOLUTION_1080 -> 7000000
                Resolution.RESOLUTION_2160 -> 10000000
            }
        }
    }
}