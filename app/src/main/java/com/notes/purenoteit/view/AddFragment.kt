package com.notes.purenoteit.view

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.notes.purenoteit.R
import com.notes.purenoteit.adapter.ImageAdapter
import com.notes.purenoteit.adapter.SearchCategoryAdapter
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.databinding.CustomDialogBinding
import com.notes.purenoteit.databinding.CustomPasswordDialogBinding
import com.notes.purenoteit.databinding.FragmentAddBinding
import com.notes.purenoteit.util.NoteAction
import com.notes.purenoteit.util.SearchViewUtil
import com.notes.purenoteit.util.hide
import com.notes.purenoteit.util.onItemClickListener
import com.notes.purenoteit.util.show
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.CompositeDisposable

@AndroidEntryPoint
class AddFragment : Fragment(), onItemClickListener {

    private var _binding: FragmentAddBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteViewModel by viewModels()
    private val categoryViewModel: CategoryViewModel by viewModels()
    private val imageAdapter = ImageAdapter(this)
    private val categoryAdapter = SearchCategoryAdapter(this)

    private lateinit var sharedPreferences: SharedPreferences

    private var noteData: NoteData? = null
    private var noteId: Int? = null
    private var checkedCategory: String = "Not"
    private var clicked = false

    private val fromAnimation by lazy {
        AnimationUtils.loadAnimation(
            requireContext(), R.anim.from_top_anim
        )
    }
    private val toAnimation by lazy {
        AnimationUtils.loadAnimation(
            requireContext(), R.anim.to_top_anim
        )
    }

    private val PICK_IMAGE_REQUEST = 101
    private val SPEECH_REQUEST_CODE = 102

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentAddBinding.inflate(inflater, container, false)
        sharedPreferences =
            requireContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        setupUi()
        setupListener()
        loadNoteData()
        initSearchView()
        return binding.root
    }

    private val compositeDisposable = CompositeDisposable()
    private fun loadNoteData() {
        noteId = arguments?.let { AddFragmentArgs.fromBundle(it).noteId }
        if (noteId != null && noteId != -1) {
            val disposable = viewModel.getNoteById(noteId!!).subscribe { note ->
                noteData = note
                binding.apply {
                    binding.constraintLayout.show()
                    binding.confirmButton.text = " Kaydet"
                    titleEditText.setText(note.title)
                    descriptionEditText.setText(note.description)
                    categorySearchView.customCategoryEditText.setText(note.category)
                    isPassword = note.isPassword ?: false
                    imageUriList = note.images!!.toMutableList()
                    checkedCategory = note.category.toString()
                }
                imageAdapter.setData(imageUriList)
            }
            compositeDisposable.add(disposable)
        }
    }

    private fun setupUi() {
        binding.apply {
            imageRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
            imageRecyclerView.adapter = imageAdapter

            categorySearchView.customCategoryRecyclerView.layoutManager =
                LinearLayoutManager(requireContext())
            categorySearchView.customCategoryRecyclerView.adapter = categoryAdapter

            categoryViewModel.categoryList.observe(viewLifecycleOwner) {
                categoryAdapter.submitList(it.toMutableList())
            }
            categoryViewModel.getAllCategories()
        }
    }

    private fun setupListener() {
        binding.apply {
            backImageButton.setOnClickListener { findNavController().navigateUp() }
            menuImageView.setOnClickListener { toggleCategoryMenu() }
            categoryButton.setOnClickListener { handleCategorySelection() }
            confirmButton.setOnClickListener { saveNote() }
            addImageView.setOnClickListener { checkPermissions() }
            soundImageView.setOnClickListener { startVoiceRecognition() }
            securityImageView.setOnClickListener { handleSecurity() }
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                handleRadioButtonChange(checkedId)
            }
            setupSearch()
        }
    }

    private fun handleRadioButtonChange(checkedId: Int) {
        binding.categorySearchView.root.visibility =
            if (checkedId == R.id.otherRadiButton) View.VISIBLE else View.GONE
    }

    private fun toggleCategoryMenu() {
        binding.apply {
            noteRadioButton.isChecked = true
            if (noteData != null) {
                when (noteData?.category) {
                    "Not" -> radioGroup.check(noteRadioButton.id)
                    "Önemli" -> {
                        radioGroup.check(importantRadioButton.id)
                    }

                    "Günlük" -> radioGroup.check(diaryRadioButton.id)
                    else -> radioGroup.check(otherRadiButton.id) // Diğer tüm durumlarda 4. seçeneği aktif et
                }
            }
            setAnimation(clicked)
        }
    }

    private fun handleCategorySelection() {
        binding.apply {
            val customCategory = categorySearchView.customCategoryEditText.text.toString()
            checkedCategory = when (radioGroup.checkedRadioButtonId) {
                R.id.noteRadioButton -> noteRadioButton.text.toString()
                R.id.diaryRadioButton -> diaryRadioButton.text.toString()
                R.id.importantRadioButton -> importantRadioButton.text.toString()
                R.id.otherRadiButton -> {
                    if (customCategory.isEmpty()) {
                        requireContext().showToastShort("Lütfen bir kategori adı giriniz")
                        return
                    }
                    categoryViewModel.isCategoryExists(customCategory)
                        .observe(viewLifecycleOwner) { isExists ->
                            if (!isExists) {
                                categoryViewModel.upsert(CategoryData(customCategory, false))
                                requireContext().showToastShort("Kategori Eklendi")
                            }
                            checkedCategory = customCategory
                            toggleCategoryMenu()
                        }
                    return
                }
                else -> return
            }
            toggleCategoryMenu()
        }
    }

    private fun saveNote() {
        if (binding.topMenuConstraintLayout.visibility == View.VISIBLE) {
            requireContext().showToastShort("Lütfen önce kategoriyi onaylıyınız")
        } else {
            if (binding.titleEditText.text.isNotEmpty() && binding.descriptionEditText.text.isNotEmpty()) {
                val note = NoteData(
                    title = binding.titleEditText.text.toString(),
                    description = binding.descriptionEditText.text.toString(),
                    date = System.currentTimeMillis(),
                    category = checkedCategory,
                    images = imageUriList,
                    isPassword = isPassword
                )
                if (noteData == null) {
                    viewModel.upsert(note)
                } else {
                    noteData?.apply {
                        title = note.title
                        description = note.description
                        date = note.date
                        category = note.category
                        images = note.images
                        isPassword = note.isPassword
                    }
                    viewModel.upsert(noteData!!)
                }
                findNavController().navigateUp()
            } else {
                requireContext().showToastShort("Başlık ve açıklama alanlarını doldurunuz")
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE
            )
        } else {
            openGallery() // İzin varsa galeriyi aç
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Lütfen konuşun")
        }

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(), "Cihazınızda ses tanıma desteklenmiyor", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleSecurity() {
        if (!isPassword) {
            showPasswordCustomDialog()
        } else {
            requireContext().showToastShort("Biometrik Kaldırıldı Ama Notu Kaydetmeniz Gerekiyor")
            isPassword = false
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private var imageUriList = mutableListOf<String>()
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_IMAGE_REQUEST -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val imageUri = data.data
                    imageUri?.let {
                        imageUriList.add(it.toString())
                        binding.constraintLayout.show()
                        imageAdapter.setData(imageUriList)
                    }
                }
            }

            SPEECH_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    val spokenText = results?.get(0) ?: ""
                    when {
                        binding.titleEditText.hasFocus() -> {
                            // editText1 odakta, değeri ekle
                            binding.titleEditText.append(spokenText)
                        }

                        binding.descriptionEditText.hasFocus() -> {
                            // editText2 odakta, değeri ekle
                            binding.descriptionEditText.append(spokenText)
                        }

                        else -> {
                            binding.titleEditText.append(spokenText)
                        }
                    }
                }
            }
        }
    }

    private var REQUEST_CODE = 100
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                // İzin verilmedi

            }
        }
    }

    private fun setAnimation(clicked: Boolean) {
        if (!clicked) {
            binding.topMenuConstraintLayout.startAnimation(fromAnimation)
            binding.topMenuConstraintLayout.show()
        } else {
            binding.topMenuConstraintLayout.startAnimation(toAnimation)
            binding.topMenuConstraintLayout.hide()
        }
        this.clicked = !clicked
    }

    override fun onItemClick(position: Int, action: NoteAction, isPassword: Boolean?) {
        when (action) {
            NoteAction.EDIT -> {
                showCustomDialog(imageUriList[position])
            }

            NoteAction.DELETE -> {
                if (noteData != null) {
                    imageUriList = noteData?.images as MutableList<String>
                }
                imageUriList.removeAt(position)
                imageAdapter.setData(imageUriList)
            }

            NoteAction.LOCK -> {

            }

            NoteAction.LIST -> {
                val disposable = categoryViewModel.getNoteById(position).subscribe { data ->
                    binding.categorySearchView.customCategoryEditText.setText(data.name)
                }
                //toggleCategoryMenu()
                compositeDisposable.add(disposable)
            }
        }
    }

    private fun showCustomDialog(s: String) {
        val dialog = Dialog(requireContext())
        val dialogBinding: CustomDialogBinding = CustomDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        Glide.with(requireContext()).load(s).diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(dialogBinding.dialogImageView)

        dialogBinding.dialogCancelButton.setOnClickListener {
            dialog.dismiss() // Dialog'u kapat
        }
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
        )
        dialog.show()
    }

    private var isPassword: Boolean = false

    private fun showPasswordCustomDialog() {
        // Dialog oluştur
        val dialog = Dialog(requireContext())
        val dialogBinding: CustomPasswordDialogBinding =
            CustomPasswordDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.apply {
            dialogPasswordConfirmButton.setOnClickListener {
                val passwordString = dialogPasswordEditText.text.toString()

                // Girilen şifreyi integer'a dönüştür ve SharedPreferences'a kaydet
                if (passwordString.isNotEmpty()) {
                    val password = passwordString.toIntOrNull()
                    if (password != null && passwordString.length == 6) {
                        savePassword(password)
                        dialog.dismiss()
                    } else {
                        // Geçersiz şifre
                        requireContext().showToastShort("6 Haneli Bir PIN Giriniz")
                    }
                } else {
                    requireContext().showToastShort("Şifre girin.")
                }
            }
        }
        dialog.show()
    }

    private fun savePassword(password: Int) {
        val savedPassword = sharedPreferences.getInt("password_key", -1)
        if (!isPassword && savedPassword == -1) {
            isPassword = true
            sharedPreferences.edit().putInt("password_key", password).apply()
            requireContext().showToastShort("Biometrik doğrulama açıldı")
        } else if (password == savedPassword) {
            isPassword = true
            requireContext().showToastShort("Biometrik doğrulama açıldı")
        } else {
            requireContext().showToastShort("Yanlış Şifre")
        }
    }

    private fun initSearchView() {
        binding.categorySearchView.customCategoryEditText.apply {
            addTextChangedListener {
                SearchViewUtil.searchViewTextChanged(binding.categorySearchView)
            }
            binding.categorySearchView.btnClear.setOnClickListener {
                SearchViewUtil.searchViewClearBtnClicked(binding.categorySearchView)
            }
            binding.categorySearchView.btnSearch.setOnClickListener {
                binding.categorySearchView.customCategoryEditText.requestFocus()
            }
        }
    }

    private fun setupSearch() {
        binding.categorySearchView.customCategoryEditText.addTextChangedListener { text ->
            categoryViewModel.searchCategory(text.toString().trim())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear() // Tüm abonelikleri temizler
    }
}
