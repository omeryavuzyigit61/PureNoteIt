package com.notes.purenoteit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.databinding.ItemNoteBinding
import com.notes.purenoteit.databinding.CustomNotePopupMenuBinding
import com.notes.purenoteit.util.onItemClickListener
import android.graphics.Rect
import android.view.Gravity
import com.notes.purenoteit.util.NoteAction
import com.notes.purenoteit.R
import com.notes.purenoteit.util.GenericDiffUtil
import com.notes.purenoteit.util.formatDate
import com.notes.purenoteit.util.hide


class NoteAdapter(private val listener: onItemClickListener) :
    RecyclerView.Adapter<NoteAdapter.ListViewHolder>() {

    private var itemList = ArrayList<NoteData>()

    class ListViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        val currentItem = itemList[position]
        holder.binding.apply {
            if (currentItem.isPassword == true) {
                titleTextView.text = "******"
                descriptionTextView.text = "***********\n***********"
            } else {
                titleTextView.text = currentItem.title
                descriptionTextView.text = currentItem.description
                noteLockImageView.hide()
            }
            dateTextView.formatDate(currentItem.date)
            menuImageButton.setOnClickListener {
                showPopupWindow(it, currentItem.id, currentItem.isPassword)
            }
            noteMenuConstraintLayout.setOnClickListener {
                listener.onItemClick(currentItem.id, NoteAction.EDIT, currentItem.isPassword)
            }

            imageView3.setImageResource(getIconForCategory(currentItem.categoryId))
        }
    }

    private fun showPopupWindow(anchor: View, position: Int, isPassword: Boolean? = null) {
        val popupBinding = CustomNotePopupMenuBinding.inflate(LayoutInflater.from(anchor.context))
        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true

        popupBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorRect =
            Rect(location[0], location[1], location[0] + anchor.width, location[1] + anchor.height)

        val leftOffset = 60
        val topOffset = 20

        popupBinding.editTextView.setOnClickListener {
            listener.onItemClick(position, NoteAction.EDIT, isPassword)
            popupWindow.dismiss()
        }

        popupBinding.deleteTextView.setOnClickListener {
            listener.onItemClick(position, NoteAction.DELETE)
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(
            anchor,
            anchorRect.width() - popupBinding.root.measuredWidth - leftOffset,
            0 + topOffset,
            Gravity.TOP or Gravity.END
        )
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun submitList(newList: List<NoteData>) {
        val diffUtilCallback = GenericDiffUtil(
            itemList,
            newList,
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )

        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffUtilCallback)
        itemList.clear()
        itemList.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    private fun getIconForCategory(categoryId: Int?): Int {
        return when (categoryId) {
            1 -> R.drawable.note
            2 -> R.drawable.diary
            3 -> R.drawable.important
            else -> R.drawable.other
        }
    }
}
