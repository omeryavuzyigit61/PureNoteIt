package com.notes.purenoteit.adapter

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.notes.purenoteit.databinding.ItemImageBinding
import com.notes.purenoteit.util.NoteAction
import com.notes.purenoteit.util.onItemClickListener

class ImageAdapter(private val listener: onItemClickListener) :
    RecyclerView.Adapter<ImageAdapter.ListViewHolder>() {

    class ListViewHolder(val binding: ItemImageBinding) :
        RecyclerView.ViewHolder(binding.root)

    private var itemList = ArrayList<String>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ListViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        val currentItem = itemList[position]
        holder.binding.apply {
            Glide.with(this.root)
                .load(currentItem)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(imageView)
            deleteImageView.setOnClickListener {
                listener.onItemClick(position, NoteAction.DELETE)
            }
            holder.itemView.setOnClickListener {
                listener.onItemClick(position, NoteAction.EDIT)
            }

        }
    }

    fun setData(newItemList: List<String>) {
        itemList = newItemList as ArrayList<String>
        Log.i("imagecontrol", "" + itemList)
        notifyDataSetChanged()
    }

    /*    fun submitList(newList: List<NoteData>) {
            val list = DiffUtil(itemList, newList)
            val diffResult = calculateDiff(list)
            itemList.clear()
            itemList.addAll(newList)
            diffResult.dispatchUpdatesTo(this)
        }*/

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun getRealPathFromURI(uri: Uri, context: Context): String? {
        var path: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = it.getString(columnIndex)
            }
        }
        return path
    }
}