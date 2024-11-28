package com.notes.purenoteit.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.notes.purenoteit.R
import com.notes.purenoteit.databinding.ItemSoundsBinding
import com.notes.purenoteit.util.NoteAction
import com.notes.purenoteit.util.onItemClickListener

class SoundAdapter(private val listener: onItemClickListener) :
    RecyclerView.Adapter<SoundAdapter.ListViewHolder>() {

    class ListViewHolder(val binding: ItemSoundsBinding) :
        RecyclerView.ViewHolder(binding.root)

    private var itemList = ArrayList<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding = ItemSoundsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        val currentItem = itemList[position]
        holder.binding.apply {
            playStopImageView.setOnClickListener {
                listener.onItemClick(position, NoteAction.PLAY, imageView = soundAnimatioImageView)
            }
            deleteSoundImageView.setOnClickListener {
                listener.onItemClick(position, NoteAction.SOUND)
            }
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun setData(newItemList: List<String>) {
        itemList = newItemList as ArrayList<String>
        Log.i("imagecontrol", "" + itemList)
        notifyDataSetChanged()
    }
}