package com.notes.purenoteit.view

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.notes.purenoteit.R
import com.notes.purenoteit.adapter.NoteAdapter
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.databinding.FragmentHomeBinding
import com.notes.purenoteit.util.NoteAction
import com.notes.purenoteit.util.SearchOption
import com.notes.purenoteit.util.SharedPrefencesUtil
import com.notes.purenoteit.util.formatDate
import com.notes.purenoteit.util.hide
import com.notes.purenoteit.util.onItemClickListener
import com.notes.purenoteit.util.show
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

@AndroidEntryPoint
class HomeFragment : Fragment(), onItemClickListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteViewModel by viewModels()
    private val categoryViewModel: CategoryViewModel by viewModels()
    private val adapter = NoteAdapter(this)
    private var startDate: Long? = null
    private var endDate: Long? = null
    private lateinit var toggle: ActionBarDrawerToggle
    private var clicked = false

    private val fromAnimation: Animation by lazy {
        AnimationUtils.loadAnimation(requireContext(), R.anim.from_top_anim)
    }
    private val toAnimation: Animation by lazy {
        AnimationUtils.loadAnimation(requireContext(), R.anim.to_top_anim)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupUI()
        setupListeners()
        return binding.root
    }

    private fun setupUI() {
        binding.apply {
            toggle = ActionBarDrawerToggle(
                requireActivity(),
                mainDrawerLayout,
                R.string.open,
                R.string.close
            )
            val drawerSwitch: SwitchCompat =
                navigationView.menu.findItem(R.id.thema_menu).actionView as SwitchCompat
            drawerSwitch.setOnCheckedChangeListener { _, isChecked ->
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isChecked) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        setDarkAndLightTheme(true)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        setDarkAndLightTheme(false)
                    }
                }, 500)
            }
            getDarkAndLightMode(drawerSwitch)
            mainDrawerLayout.addDrawerListener(toggle)
            toggle.syncState()
            noteRecyclerView.layoutManager =
                StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            noteRecyclerView.adapter = adapter
        }

        categoryViewModel.areDefaultCategoriesPresent().observe(viewLifecycleOwner) {
            if (!it) {
                categoryViewModel.upsert((CategoryData("Not", false)))
                categoryViewModel.upsert((CategoryData("Önemli", false)))
                categoryViewModel.upsert((CategoryData("Günlük", false)))
            }
        }
    }

    private fun getDarkAndLightMode(drawerSwitch: SwitchCompat) {
        val prefs = SharedPrefencesUtil(requireContext())
        val thema = prefs.getLocal("night", Boolean)
        drawerSwitch.isChecked = thema == true
    }

    private fun setDarkAndLightTheme(b: Boolean) {
        val prefs = SharedPrefencesUtil(requireContext())
        if (b) {
            prefs.addLocal("night", true)
        } else {
            prefs.addLocal("night", false)
        }
    }

    private fun setupListeners() {
        binding.apply {
            mainMenuImageView.setOnClickListener {
                mainDrawerLayout.openDrawer(GravityCompat.START)
            }

            floatingActionButton.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_addFragment)
            }

            navigationView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.lock_menu -> {
                        findNavController().navigate(R.id.action_homeFragment_to_passwordSettingFragment)
                    }

                    R.id.thema_menu -> { /* İlgili işlem */
                    }

                    R.id.category_setting_menu -> {
                        findNavController().navigate(R.id.action_homeFragment_to_categorySettingFragment)
                    }
                }
                val categoryName = menuItem.title.toString()
                handleCategorySelection(categoryName)
                mainDrawerLayout.closeDrawers()
                true
            }

            searchMenuImageView.setOnClickListener { toggleSearchMenu() }
            searchConfirmButton.setOnClickListener { handleSearchConfirmation() }
            searchClearButton.setOnClickListener { clearSearchFields() }

            categoryRadioButton.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) handleSearchOptionChange(SearchOption.CATEGORY)
            }

            dateRadioButton.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) handleSearchOptionChange(SearchOption.DATE)
            }

            startDateSelectImageView.setOnClickListener {
                showDatePicker(0) { selectedDate ->
                    startDate = selectedDate
                    startDateSelectTextView.formatDate(selectedDate)
                }
            }

            endDateSelectImageView.setOnClickListener {
                showDatePicker(1) { selectedDate ->
                    endDate = selectedDate
                    endDateSelectTextView.formatDate(selectedDate)
                }
            }

            searchEditText.addTextChangedListener { text ->
                viewModel.searchNote(text.toString().trim())
            }
        }

        categoryViewModel.categoryList.removeObservers(viewLifecycleOwner)
        categoryViewModel.categoryList.observe(viewLifecycleOwner) { categories ->
            Log.i("categorydata", "" + categories)
            populateMenuWithCategories(categories.map { it.name })
        }
        categoryViewModel.getAllCategories()

        viewModel.noteList.removeObservers(viewLifecycleOwner)
        viewModel.noteList.observe(viewLifecycleOwner) { notes ->
            adapter.submitList(notes.toMutableList())
            Log.i("notedata", "" + notes)
        }
        viewModel.getAllNotes()
    }

    private fun populateMenuWithCategories(categories: List<String>) {
        val menu = binding.navigationView.menu.findItem(R.id.sub_menu).subMenu
        val dynamicGroupId = 100
        menu?.removeGroup(dynamicGroupId)
        categories.forEach { category ->
            if (category != "Not" && category != "Önemli" && category != "Günlük") {
                val newItem = menu?.add(dynamicGroupId, Menu.NONE, Menu.NONE, category)
                newItem?.setIcon(R.drawable.other)
            }
        }
    }

    private fun handleCategorySelection(categoryName: String) {
        if (categoryName == "Tümü") {
            viewModel.getAllNotes()
        } else {
            categoryViewModel.isPasswordSet(categoryName).observe(viewLifecycleOwner) {
                if (it) {
                    showBiometricPrompt {
                        viewModel.searchNotesByCategory(categoryName)
                    }
                } else {
                    viewModel.searchNotesByCategory(categoryName)
                }
            }
        }
    }

    private fun handleSearchOptionChange(option: SearchOption) {
        binding.apply {
            when (option) {
                SearchOption.CATEGORY -> {
                    categorySelectEditText.show()
                    dateSelectConstraintLayout.hide()
                }

                SearchOption.DATE -> {
                    categorySelectEditText.hide()
                    dateSelectConstraintLayout.show()
                }
            }
        }
    }

    private fun handleSearchConfirmation() {
        binding.apply {
            when {
                categoryRadioButton.isChecked -> {
                    val category = categorySelectEditText.text.toString().trim()
                    if (category.isNotEmpty()) {
                        viewModel.searchNotesByCategory(category)
                        toggleSearchMenu()
                    } else {
                        requireContext().showToastShort("Lütfen kategori giriniz")
                    }
                }

                dateRadioButton.isChecked -> {
                    if (startDate != null && endDate != null) {
                        if (startDate!! > endDate!!) {
                            requireContext().showToastShort("Başlangıç tarihi bitiş tarihinden büyük olamaz")
                        } else {
                            viewModel.getNotesBetweenDates(startDate!!, endDate!!)
                            toggleSearchMenu()
                        }
                    } else {
                        requireContext().showToastShort("Lütfen tarih seçiniz")
                    }
                }

                else -> requireContext().showToastShort("Lütfen bir arama seçeneği seçin")
            }
        }
    }

    private fun clearSearchFields() {
        binding.apply {
            startDate = null
            endDate = null
            startDateSelectTextView.text = getString(R.string.select_date)
            endDateSelectTextView.text = getString(R.string.select_date)
            categorySelectEditText.setText("")
            viewModel.getAllNotes()
        }
    }

    private fun toggleSearchMenu() {
        if (clicked) {
            binding.searchMenuConstraintLayout.startAnimation(toAnimation)
            binding.searchMenuConstraintLayout.hide()
        } else {
            binding.searchMenuConstraintLayout.startAnimation(fromAnimation)
            binding.searchMenuConstraintLayout.show()
        }
        clicked = !clicked
    }

    private fun showDatePicker(action: Int, onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            R.style.CustomDatePickerDialog,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                if (action == 0) calendar.setToStartOfDay() else calendar.setToEndOfDay()
                onDateSelected(calendar.timeInMillis)
            },
            year,
            month,
            day
        )

        if (action == 0 && endDate != null) {
            datePickerDialog.datePicker.maxDate = endDate!!
        } else if (action == 1 && startDate != null) {
            datePickerDialog.datePicker.minDate = startDate!!
        }
        datePickerDialog.show()
    }

    private fun Calendar.setToStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.setToEndOfDay() {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }

    override fun onItemClick(position: Int, action: NoteAction, isPassword: Boolean?) {
        when (action) {
            NoteAction.EDIT -> {
                val navigateAction =
                    HomeFragmentDirections.actionHomeFragmentToAddFragment(position)
                if (isPassword == true) {
                    authenticateWithBiometrics { findNavController().navigate(navigateAction) }
                } else {
                    findNavController().navigate(navigateAction)
                }
            }

            NoteAction.DELETE -> viewModel.deleteNoteById(position)
            NoteAction.LOCK -> { /* Lock işlemleri */
            }

            NoteAction.LIST -> {}
        }
    }

    private fun authenticateWithBiometrics(onSuccess: () -> Unit) {
        val biometricManager = BiometricManager.from(requireContext())

        // Cihazın biyometrik destekleyip desteklemediğini kontrol edin
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Biyometrik destek mevcut
                showBiometricPrompt(onSuccess)
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                requireContext().showToastShort("Cihazda biyometrik doğrulama desteklenmiyor")
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                requireContext().showToastShort("Cihazda biyometrik doğrulama geçici olarak kullanılamıyor")
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                openBiometricSetting()
            }

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                requireContext().showToastShort("Güvenlik güncellemesi yapılması gerekiyor")
            }

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                requireContext().showToastShort("Cihaz biyometrik doğrulamayı desteklemiyor")
            }

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                requireContext().showToastShort("Biyometrik durum bilinmiyor")
            }
        }
    }


    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(requireContext())

        // Doğrulama sonucunu dinleyen callback
        val biometricPrompt =
            BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Doğrulama başarılı olduğunda
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    requireContext().showToastShort("Doğrulama hatası: $errString")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    requireContext().showToastShort("Doğrulama başarısız")
                }
            })
        // BiometricPrompt penceresini yapılandırın
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biyometrik Doğrulama")
            .setSubtitle("Devam etmek için parmak izi doğrulaması yapın")
            .setNegativeButtonText("İptal")
            .build()

        // BiometricPrompt'u göster
        biometricPrompt.authenticate(promptInfo)
    }

    @Suppress("DEPRECATION")
    private fun openBiometricSetting() {
        val intent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                Intent(Settings.ACTION_FINGERPRINT_ENROLL)
            }

            else -> {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
        }
        activity?.startActivity(intent)
    }
}
