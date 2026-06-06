package com.example.hrapp.ui.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.databinding.FragmentSplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Make bottom navigation bar invisible during Splash
        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        binding.btnUnlockApp.setOnClickListener {
            tryHardwareBiometric()
        }

        // Run logo scale and title fade animations
        startSplashAnimations()

        // Wait and route session
        lifecycleScope.launch {
            delay(2200) // Brief delay for branding
            routeUserSession()
        }
    }

    private fun startSplashAnimations() {
        binding.imgLogo.scaleX = 0f
        binding.imgLogo.scaleY = 0f
        binding.logoContainer.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(binding.imgLogo, "scaleX", 0f, 1f).apply {
            duration = 1000
            interpolator = OvershootInterpolator(1.2f)
        }
        val scaleY = ObjectAnimator.ofFloat(binding.imgLogo, "scaleY", 0f, 1f).apply {
            duration = 1000
            interpolator = OvershootInterpolator(1.2f)
        }
        val fadeTitle = ObjectAnimator.ofFloat(binding.logoContainer, "alpha", 0f, 1f).apply {
            duration = 800
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, fadeTitle)
            start()
        }
    }

    private fun routeUserSession() {
        if (!isAdded) return
        val mainAct = activity as? MainActivity ?: return
        val prefs = mainAct.repository.prefs

        if (prefs.loggedInEmployeeId == -1L) {
            // User is not logged in
            mainAct.replaceFragment(LoginFragment(), addToBackStack = false)
        } else {
            // User is logged in, check if biometric unlock is enabled
            if (prefs.isBiometricsEnabled) {
                tryHardwareBiometric()
            } else {
                navigateToDashboard()
            }
        }
    }

    private fun tryHardwareBiometric() {
        if (!isAdded) return
        val mainAct = activity as? MainActivity ?: return
        try {
            val executor = ContextCompat.getMainExecutor(requireContext())
            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // Show retry unlock button on the Splash Screen
                        binding.btnUnlockApp.visibility = View.VISIBLE
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        binding.btnUnlockApp.visibility = View.GONE
                        navigateToDashboard()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        binding.btnUnlockApp.visibility = View.VISIBLE
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Stubborn HRMS Unlock")
                .setSubtitle("Verify your identity to open dashboard")
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            // Bypass to dashboard if setup security fails to avoid lockout
            navigateToDashboard()
        }
    }

    private fun navigateToDashboard() {
        val mainAct = activity as? MainActivity ?: return
        mainAct.setBottomNavigationVisibility(true)
        mainAct.selectTab(R.id.tab_dashboard)
        mainAct.replaceFragment(DashboardFragment(), addToBackStack = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
