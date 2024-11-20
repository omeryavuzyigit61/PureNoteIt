package com.notes.purenoteit.util

import androidx.core.widget.addTextChangedListener
import com.notes.purenoteit.R
import com.notes.purenoteit.databinding.PandaSearchBarBinding

object SearchViewUtil {
    fun searchViewController(pandaSearchLayoutBinding: PandaSearchBarBinding) {
        pandaSearchLayoutBinding.customCategoryEditText.apply {
            hint = context.getString(R.string.search_category)
            addTextChangedListener {
                searchViewTextChanged(pandaSearchLayoutBinding)
            }
            pandaSearchLayoutBinding.btnClear.setOnClickListener {
                searchViewClearBtnClicked(pandaSearchLayoutBinding)
            }
            pandaSearchLayoutBinding.btnSearch.setOnClickListener {
                pandaSearchLayoutBinding.customCategoryEditText.requestFocus()
            }
        }
    }

    fun searchViewClearBtnClicked(pandaSearchLayoutBinding: PandaSearchBarBinding) {
        pandaSearchLayoutBinding.btnClear.setOnClickListener {
            pandaSearchLayoutBinding.customCategoryEditText.apply {
                if (!this.text.isNullOrEmpty()) {
                    this.text?.clear()
                    this.clearFocus()
                } else {
                    pandaSearchLayoutBinding.btnClear.hide()
                    pandaSearchLayoutBinding.categoryDviderView.hide()
                    pandaSearchLayoutBinding.customCategoryRecyclerView.hide()
                    this.clearFocus()
                }
            }
        }
    }

    fun searchViewTextChanged(pandaSearchLayoutBinding: PandaSearchBarBinding) {
        pandaSearchLayoutBinding.customCategoryEditText.apply {
            if (!this.text.isNullOrEmpty()) {
                pandaSearchLayoutBinding.btnClear.show()
                pandaSearchLayoutBinding.categoryDviderView.show()
                pandaSearchLayoutBinding.customCategoryRecyclerView.show()
                pandaSearchLayoutBinding.btnSearch.setTintColor(R.color.orange)
            } else {
                pandaSearchLayoutBinding.btnClear.hide()
                pandaSearchLayoutBinding.categoryDviderView.hide()
                pandaSearchLayoutBinding.customCategoryRecyclerView.hide()
                pandaSearchLayoutBinding.btnSearch.setTintColor(R.color.gray)
                this.clearFocus()
            }
        }
    }
}