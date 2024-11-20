package com.notes.purenoteit.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.databinding.ItemSearchCategoryBinding
import com.notes.purenoteit.util.GenericDiffUtil
import com.notes.purenoteit.util.hide
import com.notes.purenoteit.util.show

class SearchCategoryAdapter : RecyclerView.Adapter<SearchCategoryAdapter.ListViewHolder>() {

    class ListViewHolder(val binding: ItemSearchCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    private var itemList = ArrayList<CategoryData>()


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchCategoryAdapter.ListViewHolder {
        val binding =
            ItemSearchCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchCategoryAdapter.ListViewHolder, position: Int) {
        val currentItem = itemList[position]
        holder.binding.apply {
            searchCategoryNameTextView.text = currentItem.name
        }
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

    override fun getItemCount(): Int {
        return itemList.size
    }

}