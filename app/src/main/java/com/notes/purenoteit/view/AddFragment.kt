package com.notes.purenoteit.view

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.hide
import com.notes.purenoteit.util.onItemClickListener
import com.notes.purenoteit.util.show
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.CompositeDisposable
import java.io.File

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
        _binding = FragmentAddBinding.inflate(inflater, container, false)
        audioRecorder = AudioRecorder(requireContext())
        setupUi()
        loadNoteData()
        setupListener()
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
                    val catDisposable =
                        categoryViewModel.getCategoryById(note.categoryId!!).subscribe { data ->
                            binding.categorySearchView.customCategoryEditText.setText(data.name)
                            checkedCategory = data.name
                        }
                    compositeDisposable.add(catDisposable)
                    isPassword = note.isPassword ?: false
                    imageUriList = (note.images ?: emptyList()).toMutableList()
                    soundsPathList = (note.sounds ?: emptyList()).toMutableList()
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
                    resources.getDimensionPixelSize(R.dimen.recycler_max_height)
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
            addImageView.setOnClickListener { openGallery() }
            soundImageView.setOnClickListener { startVoiceRecognition() }
            securityImageView.setOnClickListener { handleSecurity() }
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                handleRadioButtonChange(checkedId)
            }
            soundSaveImageView.setOnClickListener { saveSoundDialog() }
            shareButton.setOnClickListener {
                shareNote(
                    titleEditText.text.toString(),
                    descriptionEditText.text.toString(),
                    imageUriList
                )
            }
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
                    3 -> radioGroup.check(importantRadioButton.id)
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
            categoryViewModel.getCategoryIdByName(checkedCategory).observe(viewLifecycleOwner) { catId ->
                if (binding.titleEditText.text.isNotEmpty() && binding.descriptionEditText.text.isNotEmpty()) {
                    val note = NoteData(
                        title = binding.titleEditText.text.toString(),
                        description = binding.descriptionEditText.text.toString(),
                        date = System.currentTimeMillis(),
                        images = imageUriList,
                        isPassword = isPassword,
                        categoryId = catId,
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
                            this.categoryId = note.categoryId
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
                    val recognizedText = it[0]
                    when {
                        binding.titleEditText.hasFocus() -> {
                            binding.titleEditText.append(recognizedText)
                        }
                        binding.descriptionEditText.hasFocus() -> {
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
            requireContext().showToastShort("Kilit Kaldırıldı (Notu Kaydetmeyi Unutmayın)")
            isPassword = false
        }
    }

    private var imageUriList = mutableListOf<String>()
    private var soundsPathList = mutableListOf<String>()

    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { savePickedImage(it) }
        }

    private fun openGallery() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun savePickedImage(uri: Uri) {
        try {
            val imagesDir = File(requireContext().filesDir, "shared_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            val fileName = "note_image_${System.currentTimeMillis()}.jpg"
            val destinationFile = File(imagesDir, fileName)

            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            imageUriList.add(destinationFile.absolutePath)
            binding.constraintLayout.show()
            imageAdapter.setData(imageUriList)
        } catch (e: Exception) {
            e.printStackTrace()
            requireContext().showToastShort("Resim kaydedilemedi")
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
                imageUriList.removeAt(position)
                imageAdapter.setData(imageUriList)
            }

            NoteAction.LOCK -> {}

            NoteAction.LIST -> {
                val disposable = categoryViewModel.getCategoryById(position).subscribe { data ->
                    binding.categorySearchView.customCategoryEditText.setText(data.name)
                }
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
            dialog.dismiss()
        }
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
        )
        dialog.show()
    }

    private var isPassword: Boolean = false

    private fun showPasswordCustomDialog() {
        val dialog = Dialog(requireContext())
        val dialogBinding: CustomPasswordDialogBinding =
            CustomPasswordDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.apply {
            dialogPasswordConfirmButton.setOnClickListener {
                val pin = dialogPasswordEditText.text.toString().trim()
                if (pin.length == 6 && pin.all { it.isDigit() }) {
                    savePassword(pin)
                    dialog.dismiss()
                } else {
                    requireContext().showToastShort("6 Haneli Bir PIN Giriniz")
                }
            }
        }
        dialog.show()
    }

    private fun savePassword(pin: String) {
        if (!SecurityUtil.isPasswordSet(requireContext())) {
            SecurityUtil.savePassword(requireContext(), pin)
            isPassword = true
            requireContext().showToastShort("Şifre kaydedildi ve nota kilit eklendi")
        } else if (SecurityUtil.verifyPassword(requireContext(), pin)) {
            isPassword = true
            requireContext().showToastShort("Nota kilit eklendi")
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

    private val requestRecordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                requireContext().showToastShort("Ses kaydı için mikrofon izni gereklidir")
            }
        }

    private var isRecording = false
    private fun startStopSound(customSaveDialogBinding: CustomSaveDialogBinding) {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            requestRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        Glide.with(requireContext()).load(R.drawable.sound_wave_unscreen)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(customSaveDialogBinding.waveAnimationImageView)

        if (!isRecording) {
            val filePath = audioRecorder.startRecording()
            filePath?.let {
                audioRecorder.stopPlaying()
                requireContext().showToastShort(getString(R.string.saving_sound))
            }
            isRecording = true
            customSaveDialogBinding.startStopSoundButton.text = getString(R.string.stop_sond)
        } else {
            val filePath = audioRecorder.stopRecording()
            filePath?.let {
                customSaveDialogBinding.waveAnimationImageView.setImageResource(R.drawable.sound_wave_unscreen)
                soundsPathList.add(filePath)
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
        if (isRecording) {
            audioRecorder.cancelRecording(dialogBinding)
            isRecording = false
        }
    }

    private var currentPlayingFilePath: String? = null
    private var isPlaying = false
    private var previousImageView: ImageView? = null

    private fun listenSound(filePath: String, imageView: ImageView?) {
        if (isPlaying && currentPlayingFilePath == filePath) {
            audioRecorder.stopPlaying()
            imageView?.setImageResource(R.drawable.soundwave)
            isPlaying = false
            currentPlayingFilePath = null
            previousImageView = null
            Log.i("soundData", "Sound stopped: $filePath")
            return
        }
        if (isPlaying) {
            previousImageView?.setImageResource(R.drawable.soundwave)
            audioRecorder.stopPlaying()
            isPlaying = false
        }
        audioRecorder.playRecording(filePath, onCompletion = {
            isPlaying = false
            imageView?.setImageResource(R.drawable.soundwave)
            currentPlayingFilePath = null
            previousImageView = null
        })
        Glide.with(requireContext())
            .load(R.drawable.soundwave)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(imageView!!)
        previousImageView = imageView
        isPlaying = true
        currentPlayingFilePath = filePath
        Log.i("soundData", "Playing sound= $filePath")
    }

    private fun deleteSound(filePath: String) {
        val isDeleted = audioRecorder.deleteRecording(filePath)
        if (isDeleted) {
            if (soundsPathList.isEmpty()) {
                binding.soundListRecyclerView.hide()
                requireContext().showToastShort("Ses Silindi")
            }
            Log.i("soundData", "Silinen Ses= $filePath")
        }
    }

    private fun shareNote(title: String, description: String, imageUriList: MutableList<String>) {
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$description")
            type = if (imageUriList.isNotEmpty()) "image/*" else "text/plain"

            if (imageUriList.isNotEmpty()) {
                val uriList = ArrayList<Uri>()
                imageUriList.forEach { filePath ->
                    try {
                        val file = File(filePath)
                        if (file.exists()) {
                            val contentUri = FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.provider",
                                file
                            )
                            uriList.add(contentUri)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        requireContext().showToastShort("Resim paylaşılamadı")
                        return@forEach
                    }
                }

                if (uriList.isNotEmpty()) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }

        try {
            startActivity(Intent.createChooser(shareIntent, "Share Note"))
        } catch (e: Exception) {
            e.printStackTrace()
            requireContext().showToastShort("Paylaşım başarısız")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
        _binding = null
    }
}
