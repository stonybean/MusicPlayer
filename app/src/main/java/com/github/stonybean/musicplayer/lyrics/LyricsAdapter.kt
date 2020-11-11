package com.github.stonybean.musicplayer.lyrics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.stonybean.musicplayer.R

/**
 * Created by stonybean on 06/11/2020
 */
class LyricsAdapter(private val context: Context, private val lyricsItemList: ArrayList<LyricsItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedPos = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lyrics, parent, false)
        return LyricsViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val lyricsViewHolder = holder as LyricsViewHolder
        lyricsViewHolder.lyricTextView.text = lyricsItemList[position].getContent()
        lyricsViewHolder.lyricTextView.setTextColor(context.getColor(R.color.colorLyricsNormal))

        if ((context as LyricsActivity).isEnabled) {
            for (i in 0 until lyricsItemList.size) {
                if (selectedPos == position) {
                    lyricsViewHolder.lyricTextView.setTextColor(context.getColor(R.color.colorLyricsPressed))
                    selectedPos = -1
                } else {
                    lyricsViewHolder.lyricTextView.setTextColor(context.getColor(R.color.colorLyricsNormal))
                }
            }
        }

        if ((context).currentLyricsTime == lyricsItemList[position].getTime()) {
            Log.d("gdgg", "dfjidfjdo : ${(context).currentLyricsTime}")
            lyricsViewHolder.lyricTextView.setTextColor(context.getColor(R.color.colorLyricsPressed))
        }

        lyricsViewHolder.lyricTextView.setOnClickListener {
            if ((context).isEnabled) {
                (context).seekToTime(lyricsItemList[position].getTime()!!.toInt())
                selectedPos = position
                notifyDataSetChanged()
            } else {
                (context).finish()
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int = lyricsItemList.size

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    private inner class LyricsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lyricTextView: TextView = itemView.findViewById(R.id.lyrics_text_view)

//        init {
//            itemView.setOnClickListener {
//            }
//        }
    }
}