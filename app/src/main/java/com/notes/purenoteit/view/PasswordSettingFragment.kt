package com.notes.purenoteit.view

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.notes.purenoteit.R
import com.notes.purenoteit.databinding.FragmentPasswordSettingBinding
import com.notes.purenoteit.util.hide
import com.notes.purenoteit.util.show
import com.notes.purenoteit.util.showToastLong
import com.notes.purenoteit.util.showToastShort

class PasswordSettingFragment : Fragment() {

    private var _binding: FragmentPasswordSettingBinding? = null
    private val binding get() = _binding!!

    private var clickCount = 0
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentPasswordSettingBinding.inflate(layoutInflater, container, false)
        sharedPreferences =
            requireContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
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

        val savedPassword = sharedPreferences.getInt("password_key", -1)
        if (savedPassword != -1) {
            binding.currentPasswordEditText.show()
        } else
            binding.currentPasswordEditText.hide()
    }

    private fun setupListeners() {
        binding.apply {
            backPasswordSettingImageView.setOnClickListener { findNavController().navigateUp() }
            passwordSettingImageView.setOnClickListener { easterEgg() }
            settingPasswordConfirmButton.setOnClickListener { newCreatedPassword() }
        }
    }

    private fun newCreatedPassword() {
        val savedPassword = sharedPreferences.getInt("password_key", -1)

        binding.apply {
            val currentPassword = currentPasswordEditText.text.toString()
            val newPassword = passwordEditText.text.toString()
            val repeatPassword = repeatPasswordEditText.text.toString()

            // Alanların boş olup olmadığını kontrol edin
            if (newPassword.isBlank() || repeatPassword.isBlank() || (savedPassword != -1 && currentPassword.isBlank())) {
                requireContext().showToastShort("Lütfen Boş Alanları Doldurun")
                return
            }
            // Mevcut şifre kontrolü sadece şifre -1 değilse yapılır
            if (savedPassword != -1) {
                if (currentPassword.toIntOrNull() != savedPassword) {
                    requireContext().showToastShort(getString(R.string.you_entered_the_current_password_incorrectly))
                    return
                }
                // Yeni şifrenin mevcut şifreyle aynı olup olmadığını kontrol edin
                if (newPassword.toIntOrNull() == savedPassword) {
                    requireContext().showToastShort("Yeni Şifre Mevcut Şifreyle Aynı Olamaz")
                    return
                }
            }
            // Yeni şifrelerin eşleşip eşleşmediğini kontrol edin
            if (newPassword != repeatPassword) {
                requireContext().showToastShort("Yeni Şifreler Eşleşmiyor")
                return
            }
            // Şifre başarıyla değiştirildi
            requireContext().showToastShort("Şifre Başarıyla Değiştirildi")
            sharedPreferences.edit().putInt("password_key", newPassword.toInt()).apply()
        }
    }

    private fun easterEgg() {
        clickCount++ // Her tıklamada sayacı bir artır

        when (clickCount) {
            10 -> {
                requireContext().showToastLong("Neden Tıkladın")
            }

            20 -> {
                requireContext().showToastLong("Verdiğiniz Desteklerden Dolayı Hamur Size Teşekkür Eder\n Fatih Yavuzyiğitoğlu <3")
            }

            else -> {
                // Başka tıklamalar için gerekirse işlem yapabilirsiniz
            }
        }
    }

}