package com.notes.purenoteit.util

import com.notes.purenoteit.data.db.NoteData

class GenericDiffUtil<T>(
    private val oldList: List<T>,
    private val newList: List<T>,
    private val areItemsTheSame: (T, T) -> Boolean,
    private val areContentsTheSame: (T, T) -> Boolean
) : androidx.recyclerview.widget.DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return areItemsTheSame(oldList[oldItemPosition], newList[newItemPosition])
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return areContentsTheSame(oldList[oldItemPosition], newList[newItemPosition])
    }
}
