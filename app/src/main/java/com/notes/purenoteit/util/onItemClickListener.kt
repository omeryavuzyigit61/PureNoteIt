package com.notes.purenoteit.util

import com.notes.purenoteit.data.db.NoteData

interface onItemClickListener {
    fun onItemClick(position: Int, action: NoteAction,isPassword:Boolean? = null)
}