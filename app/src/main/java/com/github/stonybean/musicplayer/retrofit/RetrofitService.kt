package com.github.stonybean.musicplayer.retrofit

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers

/**
 * Created by stonybean on 04/11/2020
 */
interface RetrofitService {

    @Headers("accept: application/json",
            "content-type: application/json")
    @GET("/2020-flo/song.json")
    fun requestMusicData(
    ): Call<MusicData>
}