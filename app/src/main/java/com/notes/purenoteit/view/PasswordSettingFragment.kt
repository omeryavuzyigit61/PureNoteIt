package com.notes.purenoteit.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.notes.purenoteit.R
import com.notes.purenoteit.databinding.FragmentPasswordSettingBinding
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.hide
import com.notes.purenoteit.util.show
import com.notes.purenoteit.util.showToastLong
import com.notes.purenoteit.util.showToastShort

class PasswordSettingFragment : Fragment() {

    private var _binding: FragmentPasswordSettingBinding? = null
    private val binding get() = _binding!!

    private var clickCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPasswordSettingBinding.inflate(inflater, container, false)
        setupUi()
        setupListeners()
        return binding.root
    }

    private fun setupUi() {
        Glide.with(requireContext())
            .asGif()
            .load(R.drawable.rolling_pin_unscreen)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(binding.passwordSettingImageView)

        if (SecurityUtil.isPasswordSet(requireContext())) {
            binding.currentPasswordEditText.show()
        } else {
            binding.currentPasswordEditText.hide()
        }
    }

    private fun setupListeners() {
        binding.apply {
            backPasswordSettingImageView.setOnClickListener { findNavController().navigateUp() }
            passwordSettingImageView.setOnClickListener { easterEgg() }
            settingPasswordConfirmButton.setOnClickListener { newCreatedPassword() }
        }
    }

    private fun newCreatedPassword() {
        val hasPassword = SecurityUtil.isPasswordSet(requireContext())

        binding.apply {
            val currentPassword = currentPasswordEditText.text.toString().trim()
            val newPassword = passwordEditText.text.toString().trim()
            val repeatPassword = repeatPasswordEditText.text.toString().trim()

            if (newPassword.isBlank() || repeatPassword.isBlank() || (hasPassword && currentPassword.isBlank())) {
                requireContext().showToastShort("Lütfen Boş Alanları Doldurun")
                return
            }

            if (newPassword.length != 6 || !newPassword.all { it.isDigit() }) {
                requireContext().showToastShort("Şifre 6 Haneli Rakamlardan Oluşmalıdır")
                return
            }

            if (hasPassword) {
                if (!SecurityUtil.verifyPassword(requireContext(), currentPassword)) {
                    requireContext().showToastShort(getString(R.string.you_entered_the_current_password_incorrectly))
                    return
                }
                if (currentPassword == newPassword) {
                    requireContext().showToastShort("Yeni Şifre Mevcut Şifreyle Aynı Olamaz")
                    return
                }
            }

            if (newPassword != repeatPassword) {
                requireContext().showToastShort("Yeni Şifreler Eşleşmiyor")
                return
            }

            SecurityUtil.savePassword(requireContext(), newPassword)
            requireContext().showToastShort("Şifre Başarıyla Değiştirildi")
            findNavController().navigateUp()
        }
    }

    private fun easterEgg() {
        clickCount++
        when (clickCount) {
            10 -> {
                requireContext().showToastLong("Neden Tıkladın")
            }
            20 -> {
                requireContext().showToastLong("Verdiğiniz Desteklerden Dolayı Hamur Size Teşekkür Eder\n Fatih Yavuzyiğitoğlu <3")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
