package com.notes.purenoteit.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.databinding.ItemCategoryBinding
import com.notes.purenoteit.util.GenericDiffUtil
import com.notes.purenoteit.util.NoteAction
import com.notes.purenoteit.util.onItemClickListener

class CategoryAdapter(private val listener: onItemClickListener) :
    RecyclerView.Adapter<CategoryAdapter.ListViewHolder>() {

    private var itemList = ArrayList<CategoryData>()

    class ListViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding =
            ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        val currentItem = itemList[position]
        holder.binding.apply {
            categoryTextView.text = currentItem.name
            Log.i("Ktegoriler", "" + currentItem.name)
            if (currentItem.isPassword == false) {
                categoryLockImageView.setImageResource(R.drawable.unlock)
            } else {
                categoryLockImageView.setImageResource(R.drawable.lock)
            }
            categoryDeleteImageView.setOnClickListener {
                listener.onItemClick(currentItem.id, NoteAction.DELETE)
            }
            editNameImageView.setOnClickListener {
                listener.onItemClick(currentItem.id, NoteAction.EDIT)
            }
            categoryLockImageView.setOnClickListener {
                listener.onItemClick(currentItem.id, NoteAction.LOCK)
            }
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun submitList(newList: List<CategoryData>) {
        val diffUtilCallback = GenericDiffUtil(
            itemList,
            newList,
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id }, // CategoryData'da 'id' alanı olmalı
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )

        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffUtilCallback)
        itemList.clear()
        itemList.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }
}