package com.notes.purenoteit.view

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import com.notes.purenoteit.R
import com.notes.purenoteit.databinding.ActivityMainBinding
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.SharedPrefencesUtil
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        supportTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (getAppTheme()) {
            val window = this.window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.gray)
        } else {
            val window = this.window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.background_color)
        }
    }

    private fun supportTheme() {
        if (getAppTheme()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun getAppTheme(): Boolean {
        val prefs = SharedPrefencesUtil(applicationContext)
        val theme = prefs.getLocal("night", Boolean)
        return theme as? Boolean ?: false
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.home_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return super.dispatchTouchEvent(event)
    }

    override fun onStop() {
        super.onStop()
        // Bitwarden Vault Timeout: Lock session when app is backgrounded
        SecurityUtil.lockVault()
    }
}
