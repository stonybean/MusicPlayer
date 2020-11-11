package com.github.stonybean.musicplayer.lyrics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.github.stonybean.musicplayer.service.MediaPlayerService
import com.github.stonybean.musicplayer.R
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

/**
 * Created by stonybean on 06/11/2020
 */
class LyricsActivity : AppCompatActivity() {

    private val tag: String = LyricsActivity::class.java.simpleName

    // View
    // 상단
    private var closeButton: Button? = null             // 닫기
    private var titleTextView: TextView? = null         // 제목
    private var singerTextView: TextView? = null        // 가수
    private var lyricsSelectButton: Button? = null      // 특정 가사 선택 On/Off

    // 중단 (가사)
    private lateinit var recyclerView: RecyclerView
    private lateinit var lyricsAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    // 하단
    private var musicSeekBar: SeekBar? = null           // 시크바
    private var playButton: Button? = null              // 재생 버튼
    private var pauseButton: Button? = null             // 일시 정지 버튼

    // 노래 정보
    private var title: String? = null
    private var singer: String? = null
    private var fileUrl: String? = null
    private var duration: Int? = null
    private var lyricsItemList: ArrayList<LyricsItem>? = null
    private var currentTime = 0L

    var currentLyricsTime = 0L       // 선택된 가사 시간
    var isEnabled: Boolean = false   // 가사 선택 가능 여부

    private var seekBarThread: Thread? = null      // SeekBar Thread

    // 서비스
    var mediaPlayerService: MediaPlayerService? = null
    private var mBound: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(tag, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics)

        title = intent.getStringExtra("title")      // 제목
        singer = intent.getStringExtra("singer")    // 가수
        fileUrl = intent.getStringExtra("url")      // 파일 경로
        duration = intent.getIntExtra("duration", 0)    // 최대 시간
        lyricsItemList = intent.getSerializableExtra("lyrics") as ArrayList<LyricsItem> // 가사 정보
        currentTime = intent.getLongExtra("currentTime", 0)    // 최대 시간

        // Adapter
        viewManager = LinearLayoutManager(this)
        lyricsAdapter = LyricsAdapter(this, lyricsItemList!!)

        recyclerView = findViewById<RecyclerView>(R.id.lyrics_recyclerView).apply {
            // use this setting to improve performance if you know that changes
            // in content do not change the layout size of the RecyclerView
            setHasFixedSize(true)
            // use a linear layout manager
            layoutManager = viewManager
            // specify an lyricsAdapter (see also next example)
            adapter = lyricsAdapter
        }

        // MediaPlayerService
        onConnectService(fileUrl!!)
    }

    private fun onConnectService(urlPath: String) {
        Log.d(tag, "onConnectService")
        val intent = Intent(this, MediaPlayerService::class.java)
        intent.putExtra("urlPath", urlPath)
        startService(intent)// Service객체가 없다면 create를 하고 on StartCommand() 메소드를 호출하며, 있다면 onStartCommand()만 실행함.

        //서비스 객체의 참조값을 얻어오기 위해
        //서비스 객체와 연결(bind)하는 메소드를 호출
//        bindService(intent, conn, 0) // bind할 때 flags :0은 onCreate()를 하지 않겠다. (서비스 객체를 자동 만들지 않음.)
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(tag, "onServiceConnected")
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as MediaPlayerService.ServiceBinder
            mediaPlayerService = binder.getService()
            mBound = true

            initView()
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

    override fun onStop() {
        super.onStop()
        unbindService(conn)
        mBound = false
    }

    fun initView() {
        closeButton = findViewById<View>(R.id.close_button) as Button

        lyricsSelectButton = findViewById<View>(R.id.lyrics_select_button) as Button
        lyricsSelectButton?.isEnabled = false

        titleTextView = findViewById<View>(R.id.title_text_view) as TextView
        singerTextView = findViewById<View>(R.id.singer_text_view) as TextView

        musicSeekBar = findViewById<View>(R.id.music_seek_bar) as SeekBar
        musicSeekBar?.isEnabled = false

        titleTextView!!.text = title
        singerTextView!!.text = singer

        playButton = findViewById<View>(R.id.play_button) as Button
        pauseButton = findViewById<View>(R.id.pause_button) as Button

        setSeekBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentLyricsTime = -1

        if (seekBarThread != null) seekBarThread!!.interrupt()
    }

    private fun setSeekBar() {
        musicSeekBar?.max = TimeUnit.SECONDS.toMillis(duration!!.toLong()).toInt()
        musicSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { // SeekBar 변경 감지 리스너
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayerService!!.mp?.seekTo(progress)
                }

                if (progress == seekBar.max) {
                    playButton?.visibility = View.VISIBLE
                    pauseButton?.visibility = View.GONE

                    musicSeekBar?.progress = 0
                    seekBarThread!!.interrupt()    // SeekBar Thread 해제

                    currentLyricsTime = 0
                    lyricsAdapter.notifyDataSetChanged()
                    return
                }

                // 시간에 맞는 가사 찾기
                for (i in 0 until lyricsItemList?.size!!) {
                    if (progress > lyricsItemList!![lyricsItemList!!.size - 1].getTime()!!) {
                        currentLyricsTime = lyricsItemList!![lyricsItemList!!.size - 1].getTime()!!
                        lyricsAdapter.notifyDataSetChanged()
                        break
                    }

                    // progress 위치 -> 현재 가사 시간 이상 ~ 다음 가사 시간 미만
                    if (progress >= lyricsItemList!![i].getTime()!! && progress < lyricsItemList!![i + 1].getTime()!!) {
                        Log.d(tag, "가사 변환")
                        if (currentLyricsTime == lyricsItemList!![i].getTime()!!) {
                            break
                        }
                        currentLyricsTime = lyricsItemList!![i].getTime()!!
                        lyricsAdapter.notifyDataSetChanged()
                        break
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        startSeekBarThread()

        try {
            if (mediaPlayerService!!.mp!!.currentPosition > 0) {

                lyricsSelectButton?.isEnabled = true
                musicSeekBar?.isEnabled = true

                musicSeekBar?.progress = mediaPlayerService!!.mp!!.currentPosition
                // 시간에 맞는 가사 찾기
                for (i in 0 until lyricsItemList?.size!!) {
                    if (mediaPlayerService!!.mp!!.currentPosition > lyricsItemList!![lyricsItemList!!.size - 1].getTime()!!) {
                        currentLyricsTime = lyricsItemList!![lyricsItemList!!.size - 1].getTime()!!
                        lyricsAdapter.notifyDataSetChanged()
                        break
                    }

                    // progress 위치 -> 현재 가사 시간 이상 ~ 다음 가사 시간 미만
                    if (mediaPlayerService!!.mp!!.currentPosition >= lyricsItemList!![i].getTime()!! && mediaPlayerService!!.mp!!.currentPosition < lyricsItemList!![i + 1].getTime()!!) {
                        Log.d(tag, "가사 변환")
                        if (currentLyricsTime == lyricsItemList!![i].getTime()!!) {
                            break
                        }
                        currentLyricsTime = lyricsItemList!![i].getTime()!!
                        lyricsAdapter.notifyDataSetChanged()
                        break
                    }
                }
            } else {
                playButton?.visibility = View.VISIBLE
                pauseButton?.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (mediaPlayerService != null && mediaPlayerService!!.isPlaying()) {
            playButton?.visibility = View.GONE
            pauseButton?.visibility = View.VISIBLE
        }
    }

    // 재생
    fun playClicked(v: View) {
        if (mediaPlayerService != null) {
            lyricsSelectButton?.isEnabled = true
            musicSeekBar?.isEnabled = true
            if (currentLyricsTime > 0) {
                mediaPlayerService!!.mp?.seekTo(musicSeekBar?.progress!!.toInt())
            }

            mediaPlayerService!!.playMusic()

            startSeekBarThread()
            playButton?.visibility = View.GONE
            pauseButton?.visibility = View.VISIBLE
        }
    }

    // 일시 정지
    fun pauseClicked(v: View) {
        if (mediaPlayerService != null) {
            mediaPlayerService!!.pauseMusic()

            musicSeekBar?.progress = mediaPlayerService!!.mp!!.currentPosition

            seekBarThread!!.interrupt()    // SeekBar Thread 해제

            playButton?.visibility = View.VISIBLE
            pauseButton?.visibility = View.GONE
        }
    }

    fun closeLyrics(v: View) {
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setLyricSelect(v: View) {
        if (!isEnabled) {
            lyricsSelectButton?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.colorPrimaryDark))
            isEnabled = true
        } else {
            lyricsSelectButton?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.colorLyricsPressed))
            isEnabled = false
        }
        lyricsAdapter.notifyDataSetChanged()
    }

    fun seekToTime(time: Int) {
        Log.d(tag, "time : $time")

        if (mediaPlayerService != null) {
            currentLyricsTime = time.toLong()
            mediaPlayerService!!.mp?.seekTo(time)
        }
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