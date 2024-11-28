package com.notes.purenoteit.view

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.notes.purenoteit.adapter.SoundAdapter
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.databinding.CustomDialogBinding
import com.notes.purenoteit.databinding.CustomPasswordDialogBinding
import com.notes.purenoteit.databinding.CustomSaveDialogBinding
import com.notes.purenoteit.databinding.FragmentAddBinding
import com.notes.purenoteit.util.AudioRecorder
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
    private val soundAdapter = SoundAdapter(this)
    private val categoryAdapter = SearchCategoryAdapter(this)
    private lateinit var audioRecorder: AudioRecorder


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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentAddBinding.inflate(inflater, container, false)
        sharedPreferences =
            requireContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        audioRecorder = AudioRecorder(requireContext())
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
                    binding.soundListRecyclerView.show()
                    binding.confirmButton.text = getString(R.string.save)
                    titleEditText.setText(note.title)
                    descriptionEditText.setText(note.description)
                    val disposable =
                        categoryViewModel.getCategoryById(note.categoryId!!).subscribe { data ->
                            binding.categorySearchView.customCategoryEditText.setText(data.name)
                            checkedCategory = data.name
                        }
                    compositeDisposable.add(disposable)
                    isPassword = note.isPassword ?: false
                    imageUriList = note.images!!.toMutableList()
                    soundsPathList = note.sounds!!.toMutableList()
                    if (soundsPathList.isEmpty()) {
                        binding.soundListRecyclerView.hide()
                    } else {
                        binding.soundListRecyclerView.show()
                    }
                }
                imageAdapter.setData(imageUriList)
                soundAdapter.setData(soundsPathList)
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

            soundListRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            soundListRecyclerView.adapter = soundAdapter
            soundListRecyclerView.viewTreeObserver.addOnGlobalLayoutListener {
                val maxHeight =
                    resources.getDimensionPixelSize(R.dimen.recycler_max_height) // 130dp
                if (soundListRecyclerView.height > maxHeight) {
                    soundListRecyclerView.layoutParams = soundListRecyclerView.layoutParams.apply {
                        height = maxHeight
                    }
                }
            }

            categoryViewModel.categoryList.removeObservers(viewLifecycleOwner)
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
            soundSaveImageView.setOnClickListener { saveSoundDialog() }
            setupSearch()
        }
    }

    private fun toggleCategoryMenu() {
        binding.apply {
            noteRadioButton.isChecked = true
            if (noteData != null) {
                when (noteData?.categoryId) {
                    1 -> radioGroup.check(noteRadioButton.id)
                    2 -> radioGroup.check(diaryRadioButton.id)
                    3 -> {
                        radioGroup.check(importantRadioButton.id)
                    }

                    else -> radioGroup.check(otherRadiButton.id)
                }
            }
            setAnimation(clicked)
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

    private fun handleCategorySelection() {
        binding.apply {
            val customCategory = categorySearchView.customCategoryEditText.text.toString()
            checkedCategory = when (radioGroup.checkedRadioButtonId) {
                R.id.noteRadioButton -> noteRadioButton.text.toString()
                R.id.diaryRadioButton -> diaryRadioButton.text.toString()
                R.id.importantRadioButton -> importantRadioButton.text.toString()
                R.id.otherRadiButton -> {
                    if (customCategory.isEmpty()) {
                        requireContext().showToastShort(getString(R.string.please_enter_category))
                        return
                    }
                    categoryViewModel.isCategoryExists(customCategory)
                        .removeObservers(viewLifecycleOwner)
                    categoryViewModel.isCategoryExists(customCategory)
                        .observe(viewLifecycleOwner) { isExists ->
                            if (!isExists) {
                                categoryViewModel.upsert(CategoryData(customCategory, false))
                                requireContext().showToastShort(getString(R.string.category_added))
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
            requireContext().showToastShort(getString(R.string.please_confirm_category))
        } else {
            categoryViewModel.getCategoryIdByName(checkedCategory)
                .removeObservers(viewLifecycleOwner)
            categoryViewModel.getCategoryIdByName(checkedCategory).observe(viewLifecycleOwner) {
                if (binding.titleEditText.text.isNotEmpty() && binding.descriptionEditText.text.isNotEmpty()) {
                    val note = NoteData(
                        title = binding.titleEditText.text.toString(),
                        description = binding.descriptionEditText.text.toString(),
                        date = System.currentTimeMillis(),
                        images = imageUriList,
                        isPassword = isPassword,
                        categoryId = it,
                        sounds = soundsPathList
                    )
                    if (noteData == null) {
                        viewModel.upsert(note)
                    } else {
                        noteData?.apply {
                            title = note.title
                            description = note.description
                            date = note.date
                            images = note.images
                            isPassword = note.isPassword
                            categoryId = note.categoryId
                            sounds = note.sounds
                        }
                        viewModel.upsert(noteData!!)
                    }
                    findNavController().navigateUp()
                } else {
                    requireContext().showToastShort(getString(R.string.titleanddescritionenter))
                }
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // İzin zaten verilmiş, galeriyi aç
            openGallery()
        } else {
            // İzin verilmemiş, izin iste
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.please_talk))
        }

        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(), getString(R.string.devicenotsound), Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                matches?.let {
                    val recognizedText = it[0] // Tanınan ilk metin sonucu
                    when {
                        binding.titleEditText.hasFocus() -> {
                            // editText1 odakta, değeri ekle
                            binding.titleEditText.append(recognizedText)
                        }

                        binding.descriptionEditText.hasFocus() -> {
                            // editText2 odakta, değeri ekle
                            binding.descriptionEditText.append(recognizedText)
                        }

                        else -> {
                            binding.titleEditText.append(recognizedText)
                        }
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Ses tanıma başarısız oldu", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    private fun handleRadioButtonChange(checkedId: Int) {
        binding.categorySearchView.root.visibility =
            if (checkedId == R.id.otherRadiButton) View.VISIBLE else View.GONE
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
        pickImageLauncher.launch(intent)
    }

    private var imageUriList = mutableListOf<String>()
    private var soundsPathList = mutableListOf<String>()
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val imageUri = result.data?.data
                imageUri?.let {
                    imageUriList.add(it.toString())
                    binding.constraintLayout.show()
                    imageAdapter.setData(imageUriList)
                }
            }
        }


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openGallery()  // İzin verildiyse galeriyi aç
            } else {
                // İzin verilmedi, kullanıcıya bildirim
                requireContext().showToastShort("İzin verilmedi")
            }
        }

    override fun onItemClick(
        position: Int,
        action: NoteAction,
        isPassword: Boolean?,
        imageView: ImageView?
    ) {
        when (action) {
            NoteAction.EDIT -> {
                showCustomDialog(imageUriList[position])
            }

            NoteAction.DELETE -> {
                /*if (noteData != null) {
                    imageUriList = noteData?.images as MutableList<String>
                }*/
                imageUriList.removeAt(position)
                imageAdapter.setData(imageUriList)
            }

            NoteAction.LOCK -> {

            }

            NoteAction.LIST -> {
                val disposable = categoryViewModel.getCategoryById(position).subscribe { data ->
                    binding.categorySearchView.customCategoryEditText.setText(data.name)
                }
                //toggleCategoryMenu()
                compositeDisposable.add(disposable)
            }

            NoteAction.PLAY -> {
                listenSound(soundsPathList[position], imageView)
            }

            NoteAction.SOUND -> {
                val filePath = soundsPathList[position]
                soundsPathList.removeAt(position)
                soundAdapter.setData(soundsPathList)
                deleteSound(filePath)
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

    private fun saveSoundDialog() {
        val dialog = Dialog(requireContext())
        val dialogBinding: CustomSaveDialogBinding =
            CustomSaveDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        dialogBinding.apply {
            startStopSoundButton.setOnClickListener { startStopSound(dialogBinding) }
            cancelSoundButton.setOnClickListener { cancelSound(dialogBinding) }
            cancelDialogImageView.setOnClickListener {
                cancelSound(dialogBinding)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private var isRecording = false
    private fun startStopSound(customSaveDialogBinding: CustomSaveDialogBinding) {
        Glide.with(requireContext()).load(R.drawable.sound_wave_unscreen)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(customSaveDialogBinding.waveAnimationImageView)
        checkAndRequestPermissions()
        if (!isRecording) {
            val filePath = audioRecorder.startRecording()
            filePath?.let {
                audioRecorder.stopPlaying() // Stop playback if listening without saving
                requireContext().showToastShort(getString(R.string.saving_sound))
            }
            isRecording = true
            customSaveDialogBinding.startStopSoundButton.text = getString(R.string.stop_sond)
        } else {
            val filePath = audioRecorder.stopRecording()
            filePath?.let {
                customSaveDialogBinding.waveAnimationImageView.setImageResource(R.drawable.sound_wave_unscreen)
                soundsPathList.add(filePath.toString())
                soundAdapter.setData(soundsPathList)
                binding.soundListRecyclerView.show()
                Log.i("soundData", "Recorded sound= $filePath")
                requireContext().showToastShort(getString(R.string.saved_sound))
            }
            isRecording = false
            customSaveDialogBinding.startStopSoundButton.text = getString(R.string.start_sound)
        }
    }

    private fun cancelSound(dialogBinding: CustomSaveDialogBinding) {
        if (isRecording) { // If a recording is ongoing
            audioRecorder.cancelRecording(dialogBinding) // Cancel the recording
            isRecording = false // Update the recording state
        }
    }

    private var currentPlayingFilePath: String? = null
    private var isPlaying = false
    private var previousImageView: ImageView? = null // To keep track of the previous ImageView

    private fun listenSound(filePath: String, imageView: ImageView?) {
        // If the same sound is playing, stop it
        if (isPlaying && currentPlayingFilePath == filePath) {
            // Same sound clicked, stop it
            audioRecorder.stopPlaying()
            imageView?.setImageResource(R.drawable.soundwave) // Pause icon
            isPlaying = false
            currentPlayingFilePath = null // Reset the current sound path
            previousImageView = null // Reset the previous ImageView
            Log.i("soundData", "Sound stopped: $filePath")
            return
        }
        // If another sound is playing, stop the previous sound animation
        if (isPlaying) {
            // Stop the animation for the previous sound and set it to default
            previousImageView?.setImageResource(R.drawable.soundwave) // Reset the previous ImageView
            audioRecorder.stopPlaying()
            isPlaying = false
        }
        // Start playing the new sound recording
        audioRecorder.playRecording(filePath, onCompletion = {
            isPlaying = false
            imageView?.setImageResource(R.drawable.soundwave) // Show pause icon when sound ends
            currentPlayingFilePath = null // Reset the sound path when playback completes
            previousImageView = null // Reset the previous ImageView when playback ends
        })
        // Start animation for the new sound
        Glide.with(requireContext())
            .load(R.drawable.soundwave) // Animated GIF
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(imageView!!)
        // Keep track of the new ImageView
        previousImageView = imageView
        isPlaying = true
        currentPlayingFilePath = filePath // Save the current sound path
        Log.i("soundData", "Playing sound= $filePath")
    }

    private fun deleteSound(filePath: String) {
        val isDeleted = audioRecorder.deleteRecording(filePath)
        if (isDeleted) {
            if (soundsPathList.isEmpty()) {
                binding.soundListRecyclerView.hide() // RecyclerView'ı gizle
                requireContext().showToastShort("Ses Silindi")
            }
            Log.i("soundData", "Silinen Ses= $filePath")
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val ungrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (ungrantedPermissions.isNotEmpty()) {
            requestSoundPermissionLauncher.launch(ungrantedPermissions.toTypedArray())
        } else {
            // Tüm izinler zaten verilmiş
            Log.d("Permissions", "All permissions granted")
        }
    }

    private val requestSoundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Kullanıcı izinlere yanıt verdiğinde burası çalışır
        permissions.entries.forEach { entry ->
            val permission = entry.key
            val isGranted = entry.value
            if (isGranted) {
                // İzin verildi
                Log.d("Permissions", "$permission granted")
            } else {
                // İzin reddedildi
                Log.d("Permissions", "$permission denied")
            }
        }
    }
}
