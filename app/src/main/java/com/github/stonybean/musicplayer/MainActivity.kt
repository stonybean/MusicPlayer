package com.github.stonybean.musicplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import com.github.stonybean.musicplayer.lyrics.LyricsActivity
import com.github.stonybean.musicplayer.lyrics.LyricsItem
import com.github.stonybean.musicplayer.retrofit.MusicData
import com.github.stonybean.musicplayer.retrofit.RetrofitService
import com.squareup.picasso.Picasso
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.content.ServiceConnection
import android.os.Build
import android.support.annotation.RequiresApi
import com.github.stonybean.musicplayer.service.MediaPlayerService

/**
 * Created by stonybean on 04/11/2020
 */


/**
 * 외부 라이브러리 활용
 *
 * 1. retrofit2 (json data 가져오기)
 *
 * 2. picasso (앨범 커버 이미지 표시)
 *
 * */


class MainActivity : AppCompatActivity() {

    private val tag: String = MainActivity::class.java.simpleName

    // View
    private var coverImageView: ImageView? = null       // 앨범명
    private var titleTextView: TextView? = null         // 제목
    private var singerTextView: TextView? = null        // 가수
    private var albumTextView: TextView? = null         // 앨범 커버
    private var lyricsTextView: TextView? = null        // 현재 가사
    private var nextLyricsTextView: TextView? = null    // 다음 가사
    private var musicSeekBar: SeekBar? = null           // 시크바
    private var curTimeTextView: TextView? = null       // 현재 시간(시크바)
    private var maxTimeTextView: TextView? = null       // 최종 시간(시크바)
    private var playButton: Button? = null              // 재생 버튼
    private var pauseButton: Button? = null             // 일시 정지 버튼

    private var lyricsLayout: RelativeLayout? = null

    // MediaPlayer에 사용되는 변수
    private var fileUrl: String? = null
    private var lyricsHashMap: HashMap<Long, String>? = HashMap()       // 가사 찾기 HashMap <시간, 가사>
    private var currentLyricsTime: Long = 0L               // 현재 가사 시간
    private var nextLyricsTime: Long = 0L           // 다음 가사 시간
    private var lyricsTimeList: ArrayList<Long>? = ArrayList()
    private var seekBarThread: Thread? = null      // SeekBar Thread
    private var responseDuration: Int = 0           // 음악 전체 길이

    // 가사 정보 넘겨주기
    val lyricsItemList = ArrayList<LyricsItem>()

    // 서비스
    var mediaPlayerService: MediaPlayerService? = null
    private var mBound: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initView()
        requestMusicData()
    }

    // 뷰 초기화 설정
    private fun initView() {
        // 상단 (앨범명, 제목, 가수)
        albumTextView = findViewById<View>(R.id.album_text_view) as TextView
        titleTextView = findViewById<View>(R.id.title_text_view) as TextView
        singerTextView = findViewById<View>(R.id.singer_text_view) as TextView

        // 앨범 커버 이미지
        coverImageView = findViewById<View>(R.id.cover_image_view) as ImageView

        // 가사
        lyricsLayout = findViewById<View>(R.id.lyrics_layout) as RelativeLayout
        lyricsLayout!!.setOnClickListener { goFullLyrics() }
        lyricsTextView = findViewById<View>(R.id.lyrics_text_view) as TextView
        nextLyricsTextView = findViewById<View>(R.id.next_lyrics_text_view) as TextView

        // SeekBar
        musicSeekBar = findViewById<View>(R.id.music_seek_bar) as SeekBar
        musicSeekBar?.isEnabled = false

        curTimeTextView = findViewById<View>(R.id.cur_time_text_view) as TextView
        maxTimeTextView = findViewById<View>(R.id.max_time_text_view) as TextView

        // 재생, 일시정지 버튼
        playButton = findViewById<View>(R.id.play_button) as Button
        pauseButton = findViewById<View>(R.id.pause_button) as Button
    }

    // 음악 정보 요청
    private fun requestMusicData() {
        Log.d(tag, "requestMusicData")
        // Retrofit 객체 생성
        val retrofit = Retrofit.Builder()
                .baseUrl("https://grepp-programmers-challenges.s3.ap-northeast-2.amazonaws.com")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        // retrofit 객체를 통해 인터페이스 생성
        val retrofitService = retrofit.create(RetrofitService::class.java)
        retrofitService.requestMusicData().enqueue(object : Callback<MusicData> {
            override fun onFailure(call: Call<MusicData>, t: Throwable) {
                Log.d("onFailure::", "Failed API call with call: " + call +
                        " + exception: " + t)
            }

            @SuppressLint("SimpleDateFormat")
            override fun onResponse(call: Call<MusicData>, response: Response<MusicData>) {

                Picasso.get().load(response.body()?.image).into(coverImageView) // 앨범 커버 이미지
                albumTextView?.text = response.body()?.album                    // 앨범 타이틀
                titleTextView?.text = response.body()?.title                    // 음악 제목
                singerTextView?.text = response.body()?.singer                  // 가수
                responseDuration = response.body()?.duration!!                  // 음악 전체 길이
                fileUrl = response.body()?.file.toString()                      // mp3 File url

                if (response.body()?.lyrics!!.isNotEmpty()) {
                    val lyricsList = response.body()?.lyrics!!.split("\n")
                    for (i in 0 until lyricsList.size) {
                        val lyricsTime = lyricsList[i].split("]")[0].replace("[", "")
                        val millisLyrics = transformTime(lyricsTime)    // 시간 형변환 (String -> Long)

                        lyricsHashMap?.put(millisLyrics, lyricsList[i].split("]")[1])   // <시간, 가사>
                        lyricsTimeList?.add(millisLyrics)   // <시간>

                        // LyricsActivity 넘겨줄 데이터
                        val lyricsItem = LyricsItem()
                        lyricsItem.setContent(lyricsList[i].split("]")[1])
                        lyricsItem.setTime(millisLyrics)
                        lyricsItemList.add(lyricsItem)


                    }
                }

                // MediaPlayerService
                onConnectService(fileUrl!!)
            }
        })
    }

    private fun onConnectService(urlPath: String) {
        Log.d(tag, "onConnectService")
        val intent = Intent(this, MediaPlayerService::class.java)
        intent.putExtra("urlPath", urlPath)
        startService(intent)    // Service is null ? onCreate -> onStartCommand() : onStartCommand()

        setSeekBar()
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(tag, "onServiceConnected")
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as MediaPlayerService.ServiceBinder
            mediaPlayerService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    override fun onStart() {
        Log.d(tag, "onStart")
        super.onStart()
        // Bind to LocalService
        Intent(this, MediaPlayerService::class.java).also { intent ->
            bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }
    }

    // 가사 전체화면에서 넘어왔을 때 SeekBar 동기화
    override fun onRestart() {
        super.onRestart()
        Log.d(tag, "onRestart")
        if (mediaPlayerService != null) {
            curTimeTextView?.text = transformTime(musicSeekBar?.progress!!.toLong())

            startSeekBarThread()

            try {
                if (mediaPlayerService!!.mp!!.currentPosition > 0) {
                    musicSeekBar?.isEnabled = true

                    musicSeekBar?.progress = mediaPlayerService!!.mp!!.currentPosition
                    // 시간에 맞는 가사 찾기
                    for (i in 0 until lyricsItemList.size) {
                        if (mediaPlayerService!!.mp!!.currentPosition > lyricsItemList[lyricsItemList.size - 1].getTime()!!) {
                            break
                        }

                        // progress 위치 -> 현재 가사 시간 이상 ~ 다음 가사 시간 미만
                        if (mediaPlayerService!!.mp!!.currentPosition >= lyricsItemList[i].getTime()!! && mediaPlayerService!!.mp!!.currentPosition < lyricsItemList[i + 1].getTime()!!) {
                            Log.d(tag, "가사 변환")
                            if (currentLyricsTime == lyricsItemList[i].getTime()!!) {
                                break
                            }
                            currentLyricsTime = lyricsItemList[i].getTime()!!
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (mediaPlayerService?.isPlaying()!!) {
                playButton?.visibility = View.GONE
                pauseButton?.visibility = View.VISIBLE
            } else {
                playButton?.visibility = View.VISIBLE
                pauseButton?.visibility = View.GONE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(conn)
        mBound = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (seekBarThread != null) seekBarThread!!.interrupt()

        mediaPlayerService?.onDestroy()
    }

    @SuppressLint("PrivateResource")
    private fun setSeekBar() {
        val animation = AnimationUtils.loadAnimation(baseContext, R.anim.abc_grow_fade_in_from_bottom)
//        val animation2 = AnimationUtils.loadAnimation(baseContext, R.anim.abc_slide_out_top)

        musicSeekBar?.max = TimeUnit.SECONDS.toMillis(responseDuration.toLong()).toInt()  // 음악 전체 길이 SeekBar 최대값에 적용
        maxTimeTextView?.text = transformTime(responseDuration)     // seekbar 음악 전체 길이

        musicSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { // SeekBar 변경 감지 리스너
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayerService!!.mp?.seekTo(progress)
                }

                if (progress == seekBar.max) {
                    curTimeTextView?.text = getString(R.string.init_time)   // 00:00
                    lyricsTextView?.text = ""

                    playButton?.visibility = View.VISIBLE
                    pauseButton?.visibility = View.GONE

                    musicSeekBar?.progress = 0
                    seekBarThread!!.interrupt()    // SeekBar Thread 해제
                    return
                }

                curTimeTextView?.text = transformTime(progress.toLong())

                // 시간에 맞는 가사 찾기
                for (i in 0 until lyricsTimeList?.size!!) {
                    if (progress > lyricsTimeList!![lyricsTimeList!!.size - 1]) {
                        lyricsTextView?.text = lyricsHashMap?.get(lyricsTimeList!![lyricsTimeList!!.size - 1])
                        nextLyricsTextView?.text = ""
                        break
                    }

                    // progress 위치 -> 현재 가사 시간 이상 ~ 다음 가사 시간 미만
                    if (progress >= lyricsTimeList!![i] && progress < lyricsTimeList!![i + 1]) {
                        if (currentLyricsTime == lyricsTimeList!![i]) {
                            break       // 아직 다음 가사 순서 아님
                        }
                        currentLyricsTime = lyricsTimeList!![i]
                        nextLyricsTime = lyricsTimeList!![i + 1]

                        Log.d(tag, "가사 변환")
                        lyricsTextView?.text = lyricsHashMap?.get(currentLyricsTime)          // 현재 가사
                        lyricsTextView?.setTextColor(getColor(R.color.colorLyricsPressed))   // 하이라이트 (흰색)

                        nextLyricsTextView?.text = lyricsHashMap?.get(nextLyricsTime)         // 다음 가사
                        nextLyricsTextView?.setTextColor(getColor(R.color.colorLyricsNormal))
                        lyricsLayout?.startAnimation(animation)

                        break
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    // 시작
    fun playClicked(v: View) {
        try {
            if (mediaPlayerService != null) {
                musicSeekBar?.isEnabled = true
                if (currentLyricsTime > 0) {
                    mediaPlayerService!!.mp?.seekTo(musicSeekBar?.progress!!.toInt())
                }
                mediaPlayerService!!.playMusic()

                startSeekBarThread()

                playButton?.visibility = View.GONE
                pauseButton?.visibility = View.VISIBLE
            } else {
                requestMusicData()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 일시정지
    fun pauseClicked(v: View) {
        if (mediaPlayerService != null) {
            mediaPlayerService!!.pauseMusic()

            musicSeekBar?.progress = mediaPlayerService!!.mp!!.currentPosition

            seekBarThread!!.interrupt()    // SeekBar Thread 해제

            playButton?.visibility = View.VISIBLE
            pauseButton?.visibility = View.GONE
        }
    }

    // 전체 가사 보기 (LyricsActivity)
    private fun goFullLyrics() {
        val intent = Intent(this@MainActivity, LyricsActivity::class.java)
        intent.putExtra("title", titleTextView!!.text.toString())
        intent.putExtra("singer", singerTextView!!.text.toString())
        intent.putExtra("lyrics", lyricsItemList)
        intent.putExtra("url", fileUrl)
        intent.putExtra("duration", responseDuration)
        intent.putExtra("currentTime", currentLyricsTime)

        if (seekBarThread != null) {
            seekBarThread!!.interrupt()
        }
        startActivity(intent)
    }

    // String -> Long(millisecond)
    private fun transformTime(value: String): Long {

        val minutes = TimeUnit.MINUTES.toMillis(value.split(":")[0].toLong())
        val seconds = TimeUnit.SECONDS.toMillis(value.split(":")[1].toLong())
        val millis = TimeUnit.MILLISECONDS.toMillis(value.split(":")[2].toLong())

        return minutes + seconds + millis
    }

    // Int(second) -> String(mm:ss) (ex: 198 -> 03:18)
    private fun transformTime(second: Int): String {
        val millis = TimeUnit.SECONDS.toMillis(second.toLong())

        return String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis - TimeUnit.MINUTES.toMillis(TimeUnit.MILLISECONDS.toMinutes(millis))))
    }

    // Long(millisecond) -> String(mm:ss) (ex: 199008 -> 03:18)
    private fun transformTime(millis: Long): String {

        return String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis - TimeUnit.MINUTES.toMillis(TimeUnit.MILLISECONDS.toMinutes(millis))))
    }

    // 실행 중 움직이는 SeekBar (Thread)
    private fun startSeekBarThread() {
        seekBarThread = Thread(Runnable {
            while (mediaPlayerService!!.isPlaying()) {  // 음악 실행 중 계속 돌아가게 함
                try {
                    Thread.sleep(1000) // 1초마다 SeekBar 움직이게 함

                    // 현재 재생 중인 위치 SeekBar에 적용
                    musicSeekBar?.progress = mediaPlayerService!!.mp!!.currentPosition
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
        seekBarThread!!.start()
    }
}