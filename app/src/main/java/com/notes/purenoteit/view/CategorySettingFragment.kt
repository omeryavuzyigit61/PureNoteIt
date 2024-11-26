package com.notes.purenoteit.view

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EdgeEffect
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
    private lateinit var sharedPreferences: SharedPreferences
    private val categoryViewModel: CategoryViewModel by viewModels()
    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategorySettingBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)

        setupUI()
        observeCategoryData()

        return binding.root
    }

    val edgeEffectColor = Color.parseColor("#FFA500") // Turuncu renk
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

    override fun onItemClick(position: Int, action: NoteAction, isPassword: Boolean?) {
        when (action) {
            NoteAction.DELETE -> categoryViewModel.deleteCategoryById(position)
            NoteAction.EDIT -> showCategoryDialog(isEdit = true, position = position)
            NoteAction.LOCK -> handleLockAction(position)
            NoteAction.LIST -> {}
        }
    }

    private fun handleLockAction(position: Int) {
        categoryViewModel.getCategoryById(position).subscribe { data ->
            if (data.isPassword == false) {
                showPasswordDialog(data)
            } else {
                data.isPassword = false
                categoryViewModel.update(data)
            }
        }.also { compositeDisposable.add(it) }
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
                        categoryViewModel.getCategoryById(pos).subscribe { data ->
                            categoryEditText.setText(data.name)
                            confirmCategoryButton.setOnClickListener {
                                updateCategory(data, categoryEditText.text.toString(),dialog)
                            }
                        }.also { compositeDisposable.add(it) }
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
        val dialog = Dialog(requireContext()).apply {
            val dialogBinding = CustomPasswordDialogBinding.inflate(layoutInflater)
            setContentView(dialogBinding.root)

            dialogBinding.apply {
                dialogPasswordConfirmButton.setOnClickListener {
                    val password = dialogPasswordEditText.text.toString().toIntOrNull()
                    if (password != null && dialogPasswordEditText.text.length == 6) {
                        savePassword(password, data)
                        dismiss()
                    } else {
                        requireContext().showToastShort("6 Haneli Bir PIN Giriniz")
                    }
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

    private fun savePassword(password: Int, data: CategoryData) {
        val savedPassword = sharedPreferences.getInt("password_key", -1)
        when {
            savedPassword == -1 -> {
                sharedPreferences.edit().putInt("password_key", password).apply()
                data.isPassword = true
                requireContext().showToastShort("Biometrik doğrulama açıldı")
            }
            password == savedPassword -> {
                data.isPassword = true
                requireContext().showToastShort("Biometrik doğrulama açıldı")
            }
            else -> requireContext().showToastShort("Yanlış Şifre")
        }
        categoryViewModel.update(data)
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }
}