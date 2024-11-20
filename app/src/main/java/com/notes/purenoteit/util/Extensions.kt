package com.notes.purenoteit.util

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

fun View.show() {
    this.visibility = View.VISIBLE
}

fun View.hide() {
    this.visibility = View.GONE
}

fun View.invs() {
    this.visibility = View.INVISIBLE
}

fun Context.showToastLong(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

fun Context.showToastShort(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun TextView.formatDate(millis: Long) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    val dateString = sdf.format(millis)
    this.text = dateString
}

fun ImageView.setTintColor(@ColorRes color: Int) {
    this.setColorFilter(ContextCompat.getColor(context, color))
}

fun Activity.hideKeyboard(event: MotionEvent) {
    if (event.action == MotionEvent.ACTION_DOWN) {
        val view = currentFocus
        if (view != null) {
            view.clearFocus()
            val inputMethodManager =
                getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(
                view.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
    }
}