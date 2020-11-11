package com.github.stonybean.musicplayer.retrofit

/**
 * Created by stonybean on 04/11/2020
 */
data class MusicData (
    val singer: String,
    val album: String,
    val title: String,
    val duration: Int,
    val image: String,
    val file: String,
    val lyrics: String
)