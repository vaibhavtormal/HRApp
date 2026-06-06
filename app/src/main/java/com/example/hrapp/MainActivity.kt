package com.example.hrapp

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hrapp.data.PreferencesManager
import com.example.hrapp.data.db.AppDatabase
import com.example.hrapp.data.repository.HRRepository
import com.example.hrapp.data.repository.FirebaseSeeder
import com.example.hrapp.databinding.ActivityMainBinding
import com.example.hrapp.ui.fragments.DashboardFragment
import com.example.hrapp.ui.fragments.ExpenseFragment
import com.example.hrapp.ui.fragments.HistoryFragment
import com.example.hrapp.ui.fragments.LeaveFragment
import com.example.hrapp.ui.fragments.LoginFragment
import com.example.hrapp.ui.fragments.SettingsFragment
import com.example.hrapp.ui.fragments.SplashFragment
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var repository: HRRepository
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize PreferencesManager and Theme BEFORE super.onCreate to prevent flickering
        val prefs = PreferencesManager(applicationContext)
        applyTheme(prefs.appTheme)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply padding to container, but let main container lay out fully
            binding.fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            binding.bottomNavCard.setPadding(0, 0, 0, 0)
            insets
        }

        // Initialize Database and Repository
        val db = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        repository = HRRepository(db, prefs)

        // Seed Firestore Database if empty
        lifecycleScope.launch {
            FirebaseSeeder.seedDatabaseIfEmpty(db)
        }

        // Initialize ViewModels
        val factory = HRViewModelFactory(repository)
        authViewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]

        // Start Notification Listeners Service
        startService(android.content.Intent(this, com.example.hrapp.data.NotificationService::class.java))
        requestNotificationPermission()

        // Setup bottom navigation bar actions
        setupBottomNavigation()

        // Handle possible deep link video passed from push notification click
        handleDeepLinkIntent(intent)

        // Show Splash Screen on launch
        if (savedInstanceState == null) {
            setBottomNavigationVisibility(false)
            replaceFragment(SplashFragment(), addToBackStack = false)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: android.content.Intent?) {
        intent?.getStringExtra("play_video_id")?.let { videoUrl ->
            val learningFrag = com.example.hrapp.ui.fragments.LearningFragment().apply {
                arguments = Bundle().apply {
                    putString("play_video_id", videoUrl)
                }
            }
            replaceFragment(learningFrag)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun setupBottomNavigation() {
        val clickListener = View.OnClickListener { view ->
            val targetFragment = when (view.id) {
                R.id.tab_dashboard -> DashboardFragment()
                R.id.tab_history -> HistoryFragment()
                R.id.tab_leaves -> LeaveFragment()
                R.id.tab_expenses -> ExpenseFragment()
                R.id.tab_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            selectTab(view.id)
            replaceFragment(targetFragment, addToBackStack = false)
        }

        binding.tabDashboard.setOnClickListener(clickListener)
        binding.tabHistory.setOnClickListener(clickListener)
        binding.tabLeaves.setOnClickListener(clickListener)
        binding.tabExpenses.setOnClickListener(clickListener)
        binding.tabSettings.setOnClickListener(clickListener)
    }

    fun selectTab(tabId: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.primary_light)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_sub_dark)

        // Reset all tabs
        resetTabColors(binding.iconDashboard, binding.textDashboard, inactiveColor)
        resetTabColors(binding.iconHistory, binding.textHistory, inactiveColor)
        resetTabColors(binding.iconLeaves, binding.textLeaves, inactiveColor)
        resetTabColors(binding.iconExpenses, binding.textExpenses, inactiveColor)
        resetTabColors(binding.iconSettings, binding.textSettings, inactiveColor)

        // Select the chosen one
        when (tabId) {
            R.id.tab_dashboard -> resetTabColors(binding.iconDashboard, binding.textDashboard, activeColor)
            R.id.tab_history -> resetTabColors(binding.iconHistory, binding.textHistory, activeColor)
            R.id.tab_leaves -> resetTabColors(binding.iconLeaves, binding.textLeaves, activeColor)
            R.id.tab_expenses -> resetTabColors(binding.iconExpenses, binding.textExpenses, activeColor)
            R.id.tab_settings -> resetTabColors(binding.iconSettings, binding.textSettings, activeColor)
        }
    }

    private fun resetTabColors(icon: ImageView, text: TextView, color: Int) {
        icon.imageTintList = ColorStateList.valueOf(color)
        text.setTextColor(color)
    }

    fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()
        // Premium transitions
        transaction.setCustomAnimations(
            R.anim.fade_in,
            R.anim.fade_out,
            R.anim.fade_in,
            R.anim.fade_out
        )
        transaction.replace(R.id.fragment_container, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }

    fun setBottomNavigationVisibility(isVisible: Boolean) {
        binding.bottomNavCard.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    fun applyTheme(themeMode: Int) {
        when (themeMode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}