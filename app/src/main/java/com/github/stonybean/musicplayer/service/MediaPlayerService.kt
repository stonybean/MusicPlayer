package com.github.stonybean.musicplayer.service

import android.app.Service
import android.media.MediaPlayer
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.Log

/**
 * Created by stonybean on 09/11/2020
 */

class MediaPlayerService : Service() {

    private val tag: String = MediaPlayerService::class.java.simpleName

    internal var mp: MediaPlayer? = null
    private var currentTime = 0

    private var urlPath: String? = null

    // Binder given to clients
    private val binder = ServiceBinder()

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class ServiceBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        intent.let { urlPath = it.getStringExtra("urlPath") }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (mp != null) {
            mp!!.stop()
            mp!!.release()
            mp = null
        }
        super.onDestroy()
    }

    fun isPlaying(): Boolean {
        return (mp != null && mp!!.isPlaying)
    }

    fun pauseMusic() { // 일시정지
        Log.d(tag, "pauseMusic currentTime : $currentTime")
        if (isPlaying()) {
            mp!!.pause()
            currentTime = mp!!.currentPosition
        }
    }

    fun playMusic() { // 재생
        Log.d(tag, "playMusic")

        if (mp == null) {
            Log.d(tag, "mp is null")
            mp = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(urlPath)
                prepare()
            }
        }
        if (!isPlaying()) {
            mp!!.setOnPreparedListener { mp -> mp.start() }
            mp!!.apply {
                Log.d(tag, "mp start")
                start()
            }
        }
    }
}