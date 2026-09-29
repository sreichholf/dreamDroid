package net.reichholf.dreamdroid.video

import net.reichholf.dreamdroid.Profile

/**
 * Single-tuner boxes can stream a service only while they are tuned to that
 * transponder. [Profile.zapAndStream] makes live playback zap first
 * ([net.reichholf.dreamdroid.data.ReceiverRepository.liveStream]). Movie
 * playback does not use this path.
 */
object ZapAndStream {
    fun required(profile: Profile): Boolean = profile.zapAndStream
}
