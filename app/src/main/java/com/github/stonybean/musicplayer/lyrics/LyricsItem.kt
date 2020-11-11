package com.github.stonybean.musicplayer.lyrics

import java.io.Serializable

/**
 * Created by stonybean on 06/11/2020
 */
class LyricsItem : Serializable {

    private var content: String? = null
    private var time: Long? = null

    fun getContent(): String? {
        return content
    }

    fun setContent(content: String) {
        this.content = content
    }

    fun getTime(): Long? {
        return time
    }

    fun setTime(time: Long) {
        this.time = time
    }

}