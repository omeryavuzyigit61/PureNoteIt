package com.notes.purenoteit.view

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EdgeEffect
import android.widget.ImageView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notes.purenoteit.adapter.CategoryAdapter
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.databinding.CustomCategoryAddBinding
import com.notes.purenoteit.databinding.CustomPasswordDialogBinding
import com.notes.purenoteit.databinding.FragmentCategorySettingBinding
import com.notes.purenoteit.util.NoteAction
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.onItemClickListener
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.CompositeDisposable

@AndroidEntryPoint
class CategorySettingFragment : Fragment(), onItemClickListener {

    private var _binding: FragmentCategorySettingBinding? = null
    private val binding get() = _binding!!
    private val adapter: CategoryAdapter = CategoryAdapter(this)
    private val categoryViewModel: CategoryViewModel by viewModels()
    private val compositeDisposable = CompositeDisposable()

    private val edgeEffectColor = Color.parseColor("#FFA500")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategorySettingBinding.inflate(inflater, container, false)
        setupUI()
        observeCategoryData()
        return binding.root
    }

    private fun setupUI() = binding.apply {
        categoryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        categoryRecyclerView.adapter = adapter
        categoryRecyclerView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                return EdgeEffect(view.context).apply {
                    color = edgeEffectColor
                }
            }
        }

        addCategoryImageView.setOnClickListener { showCategoryDialog(isEdit = false) }
        backCategoryImageView.setOnClickListener { findNavController().navigateUp() }

        categoryAddEditText.addTextChangedListener {
            categoryViewModel.searchCategory(it.toString().trim())
        }
    }

    private fun observeCategoryData() {
        categoryViewModel.categoryList.observe(viewLifecycleOwner) {
            adapter.submitList(it.toMutableList())
        }
        categoryViewModel.getAllCategories()
    }

    override fun onItemClick(position: Int, action: NoteAction, isPassword: Boolean?, imageView: ImageView?) {
        when (action) {
            NoteAction.DELETE -> categoryViewModel.deleteCategoryById(position)
            NoteAction.EDIT -> showCategoryDialog(isEdit = true, position = position)
            NoteAction.LOCK -> handleLockAction(position)
            NoteAction.LIST -> {}
            NoteAction.PLAY -> {}
            NoteAction.SOUND -> {}
            NoteAction.PIN -> {}
        }
    }

    private fun handleLockAction(position: Int) {
        val disposable = categoryViewModel.getCategoryById(position).subscribe { data ->
            if (data.isPassword == false || data.isPassword == null) {
                showPasswordDialog(data)
            } else {
                data.isPassword = false
                categoryViewModel.update(data)
                requireContext().showToastShort("Kategori kilidi kaldırıldı")
            }
        }
        compositeDisposable.add(disposable)
    }

    private fun showCategoryDialog(isEdit: Boolean, position: Int? = null) {
        val dialog = Dialog(requireContext())
        val dialogBinding = CustomCategoryAddBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.apply {
            categoryTitleTextView.text = if (isEdit) "Düzenleme" else "Ekleme"
            confirmCategoryButton.text = if (isEdit) "Düzenle" else "Ekle"

            position?.let { pos ->
                if (isEdit) {
                    val disposable = categoryViewModel.getCategoryById(pos).subscribe { data ->
                        categoryEditText.setText(data.name)
                        confirmCategoryButton.setOnClickListener {
                            updateCategory(data, categoryEditText.text.toString(), dialog)
                        }
                    }
                    compositeDisposable.add(disposable)
                }
            } ?: run {
                confirmCategoryButton.setOnClickListener {
                    addCategory(categoryEditText.text.toString(), dialog)
                }
            }

            cancelImageView.setOnClickListener { dialog.dismiss() }
        }

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialog.show()
    }

    private fun showPasswordDialog(data: CategoryData) {
        val dialog = Dialog(requireContext())
        val dialogBinding = CustomPasswordDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.apply {
            dialogPasswordConfirmButton.setOnClickListener {
                val pin = dialogPasswordEditText.text.toString().trim()
                if (pin.length == 6 && pin.all { it.isDigit() }) {
                    savePassword(pin, data)
                    dialog.dismiss()
                } else {
                    requireContext().showToastShort("6 Haneli Bir PIN Giriniz")
                }
            }
        }
        dialog.show()
    }

    private fun addCategory(name: String, dialog: Dialog) {
        if (name.isBlank()) {
            requireContext().showToastShort("Lütfen kategori giriniz")
        } else {
            categoryViewModel.upsert(CategoryData(name, isPassword = false))
            dialog.dismiss()
        }
    }

    private fun updateCategory(data: CategoryData, name: String, dialog: Dialog) {
        if (name.isBlank()) {
            requireContext().showToastShort("Lütfen kategori giriniz")
        } else {
            data.name = name
            categoryViewModel.update(data)
            dialog.dismiss()
        }
    }

    private fun savePassword(pin: String, data: CategoryData) {
        if (!SecurityUtil.isPasswordSet(requireContext())) {
            SecurityUtil.savePassword(requireContext(), pin)
            data.isPassword = true
            categoryViewModel.update(data)
            requireContext().showToastShort("Şifre kaydedildi ve kategori kilitlendi")
        } else if (SecurityUtil.verifyPassword(requireContext(), pin)) {
            data.isPassword = true
            categoryViewModel.update(data)
            requireContext().showToastShort("Kategori kilitlendi")
        } else {
            requireContext().showToastShort("Yanlış Şifre")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
        _binding = null
    }
}
