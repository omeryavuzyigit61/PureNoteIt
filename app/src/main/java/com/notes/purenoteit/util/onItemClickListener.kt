package com.notes.purenoteit.util

import android.widget.ImageView
import com.notes.purenoteit.data.db.NoteData

interface onItemClickListener {
    fun onItemClick(position: Int, action: NoteAction,isPassword:Boolean? = null,imageView: ImageView? = null)
}