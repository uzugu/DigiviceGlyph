package com.digimon.digiviceglyph.runtime

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log

class DigiviceAudioManager(private val context: Context) {
    companion object {
        private const val TAG = "DigiviceAudio"
    }

    enum class Cue(
        val assetName: String,
        val minGapMs: Long = 50L,
        val preferMediaPlayer: Boolean = false
    ) {
        SELECT("sound_select.wav"),
        CANCEL("sound_cancel.wav"),
        START("sound_start.wav", 120L),
        ALERT_OLD("sound_alert_old.wav", 120L),
        ENCOUNTER("sound_encounter.wav", 120L, true),
        READY_GO("sound_ready_go.wav", 120L, true),
        EVO_SMALL("sound_evo_digivice_small.wav", 120L),
        EVO("sound_evo_digivice.wav", 120L, true),
        ATTACK("sound_attack.wav", 120L),
        ATTACK_DEFENSE("sound_attack_defense.wav", 120L),
        ATTACK_HIT("sound_attack_hit.wav", 120L),
        HIT("sound_hit_digivice.wav", 120L),
        CHANGE("sound_change.wav", 120L),
        HAPPY("sound_happy.wav", 150L),
        SAD("sound_sad.wav", 150L),
        SAD_SMALL("sound_sad_small.wav", 120L),
        SAD_HAPPY("sound_sad_happy.wav", 120L),
        FINISH("sound_finish_game_old.wav", 500L, true),
        CONNECT("sound_connect.wav", 200L),
        SHAKE("sound_shake.wav", 200L)
    }

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val soundIds = mutableMapOf<Cue, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private val lastPlayedAt = mutableMapOf<Cue, Long>()
    private val activeStreams = linkedSetOf<Int>()
    private var mediaPlayer: MediaPlayer? = null

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedIds += sampleId
                Log.d(TAG, "Loaded sampleId=$sampleId")
            } else {
                Log.w(TAG, "Failed to load sampleId=$sampleId status=$status")
            }
        }

        for (cue in Cue.values()) {
            if (cue.preferMediaPlayer) continue
            runCatching {
                Log.d(TAG, "Loading ${cue.assetName}...")
                context.assets.openFd("audio/${cue.assetName}").use { fd ->
                    soundIds[cue] = soundPool.load(fd, 1)
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to queue ${cue.assetName}", error)
            }
        }

        Log.d(TAG, "All sounds queued. Loaded count=${loadedIds.size}/${soundIds.size}")
    }

    fun play(cue: Cue, enabled: Boolean) {
        if (!enabled) {
            Log.d(TAG, "Sound disabled, skipping $cue")
            return
        }

        val now = SystemClock.elapsedRealtime()
        val previous = lastPlayedAt[cue] ?: 0L
        if (previous > 0 && now - previous < cue.minGapMs) {
            Log.d(TAG, "Skip $cue, cooldown (${now - previous}ms < ${cue.minGapMs}ms)")
            return
        }

        lastPlayedAt[cue] = now

        if (cue.preferMediaPlayer) {
            playWithMediaPlayer(cue)
            return
        }

        val soundId = soundIds[cue] ?: run {
            Log.d(TAG, "No soundId for $cue")
            return
        }
        if (soundId !in loadedIds) {
            Log.d(TAG, "Skip $cue, sample not loaded yet (loadedIds=${loadedIds.size})")
            return
        }
        val streamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        if (streamId != 0) {
            activeStreams += streamId
        }
        Log.d(TAG, "Play $cue sampleId=$soundId streamId=$streamId")
    }

    fun stopAll() {
        mediaPlayer?.runCatching {
            setOnCompletionListener(null)
            stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
        activeStreams.forEach(soundPool::stop)
        activeStreams.clear()
        Log.d(TAG, "Stop all active sounds")
    }

    private fun playWithMediaPlayer(cue: Cue) {
        mediaPlayer?.runCatching {
            setOnCompletionListener(null)
            stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null

        runCatching {
            context.assets.openFd("audio/${cue.assetName}").use { fd ->
                MediaPlayer().apply {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    isLooping = false
                    setOnCompletionListener { completed ->
                        completed.release()
                        if (mediaPlayer === completed) {
                            mediaPlayer = null
                        }
                    }
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare()
                    start()
                }.also { player ->
                    mediaPlayer = player
                }
            }
            Log.d(TAG, "Play media cue $cue")
        }.onFailure { error ->
            Log.w(TAG, "Failed to play media cue $cue", error)
        }
    }

    fun release() {
        stopAll()
        soundPool.release()
        soundIds.clear()
        loadedIds.clear()
        lastPlayedAt.clear()
        activeStreams.clear()
    }
}
