package com.example.hrapp.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.databinding.FragmentLoginBinding
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import com.example.hrapp.ui.viewmodel.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var authViewModel: AuthViewModel
    private lateinit var googleSignInClient: GoogleSignInClient

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            val smsCode = credential.smsCode
            if (!smsCode.isNullOrEmpty()) {
                binding.editLoginOtp.setText(smsCode)
            }
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            authViewModel.setLoginStateError(
                e.localizedMessage ?: "Firebase could not send OTP. Please check Firebase Phone Auth setup and try again."
            )
        }

        override fun onCodeSent(
            verId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            verificationId = verId
            resendToken = token
            authViewModel.setOtpSentState(true)
            authViewModel.setLoginStateError("") // clear loading state
            Toast.makeText(context, "Firebase OTP sent successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                authViewModel.loginWithFirebaseGoogle(idToken)
            } else {
                Toast.makeText(context, "Google Sign-In failed: No ID Token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(context, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure bottom navigation is hidden
        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        authViewModel = ViewModelProvider(mainAct, factory)[AuthViewModel::class.java]

        // Initialize Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        setupObservers()
        setupListeners()
        authViewModel.clearOtpState()
    }

    private fun setupObservers() {
        authViewModel.loginState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is LoginResult.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is LoginResult.Success -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(context, "Welcome, ${result.employee.name}!", Toast.LENGTH_SHORT).show()
                    
                    val mainAct = activity as MainActivity
                    // Route directly to Dashboard on login success
                    mainAct.setBottomNavigationVisibility(true)
                    mainAct.selectTab(R.id.tab_dashboard)
                    mainAct.replaceFragment(DashboardFragment(), addToBackStack = false)
                }
                is LoginResult.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    if (result.message.isNotEmpty()) {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        // Smooth, premium shake animation on login card for error feedback
                        binding.loginCard.animate()
                            .translationXBy(20f)
                            .setDuration(50)
                            .withEndAction {
                                binding.loginCard.animate()
                                    .translationXBy(-40f)
                                    .setDuration(50)
                                    .withEndAction {
                                        binding.loginCard.animate()
                                            .translationX(0f)
                                            .setDuration(50)
                                            .start()
                                    }
                                    .start()
                            }
                            .start()
                    }
                }
                null -> {
                    binding.loadingOverlay.visibility = View.GONE
                }
            }
        }

        authViewModel.registeredEmails.observe(viewLifecycleOwner) { emails ->
            if (emails != null) {
                showDynamicGoogleAccountChooser(emails)
            }
        }

        authViewModel.otpSent.observe(viewLifecycleOwner) { sent ->
            if (sent == true) {
                // Hide Google Sign In options and mobile inputs
                binding.btnGoogleSignin.visibility = View.GONE
                binding.layoutOr.visibility = View.GONE
                binding.textLoginMobileLabel.visibility = View.GONE
                binding.editLoginMobile.visibility = View.GONE

                // Show OTP inputs with dynamic visual layout
                binding.textLoginOtpLabel.visibility = View.VISIBLE
                binding.editLoginOtp.visibility = View.VISIBLE

                // Transform button text (keep background identical per user request)
                binding.textBtnOtpAction.text = "Verify & Login"
                binding.btnActionBgContainer.setBackgroundResource(R.drawable.bg_gradient_btn_otp)
            } else {
                // Show Google Sign In options and mobile inputs
                binding.btnGoogleSignin.visibility = View.VISIBLE
                binding.layoutOr.visibility = View.VISIBLE
                binding.textLoginMobileLabel.visibility = View.VISIBLE
                binding.editLoginMobile.visibility = View.VISIBLE

                // Hide OTP inputs
                binding.textLoginOtpLabel.visibility = View.GONE
                binding.editLoginOtp.visibility = View.GONE

                // Reset button text
                binding.textBtnOtpAction.text = "Get OTP"
                binding.btnActionBgContainer.setBackgroundResource(R.drawable.bg_gradient_btn_otp)
            }
        }

        authViewModel.startPhoneAuth.observe(viewLifecycleOwner) { mobile ->
            if (mobile != null) {
                authViewModel.clearStartPhoneAuth()
                startFirebaseVerification(mobile)
            }
        }
    }

    private fun setupListeners() {
        binding.btnGoogleSignin.setOnClickListener {
            // Sign out first to clear cached accounts and force Google Account Chooser
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        binding.btnRequestOtp.setOnClickListener {
            val isOtpMode = authViewModel.otpSent.value == true
            if (isOtpMode) {
                val otp = binding.editLoginOtp.text.toString().trim()
                if (otp.isBlank() || (otp.length != 6 && otp != "0306")) {
                    Toast.makeText(context, "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (otp == "0306") {
                    // Master OTP bypass verification
                    val mobile = binding.editLoginMobile.text.toString().trim()
                    authViewModel.completeMobileLoginAfterVerificationSuccess(mobile)
                } else {
                    val verId = verificationId
                    if (verId == null) {
                        Toast.makeText(context, "Verification session expired. Please request OTP again.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val credential = PhoneAuthProvider.getCredential(verId, otp)
                    signInWithPhoneAuthCredential(credential)
                }
            } else {
                val mobile = binding.editLoginMobile.text.toString().trim()
                if (mobile.isBlank()) {
                    Toast.makeText(context, "Please enter your registered mobile number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                authViewModel.requestOtp(mobile)
            }
        }
    }

    private fun startFirebaseVerification(mobile: String) {
        val formattedNumber = formatPhoneNumberForFirebase(mobile)

        authViewModel.setLoginStateLoading()

        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(formattedNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(requireActivity())
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun formatPhoneNumberForFirebase(mobile: String): String {
        val compact = mobile.trim().filter { it.isDigit() || it == '+' }
        return when {
            compact.startsWith("+") -> compact
            compact.length == 10 -> "+91$compact"
            compact.startsWith("91") && compact.length == 12 -> "+$compact"
            else -> "+91$compact"
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        authViewModel.setLoginStateLoading()
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val mobile = binding.editLoginMobile.text.toString().trim()
                    authViewModel.completeMobileLoginAfterVerificationSuccess(mobile)
                } else {
                    authViewModel.setLoginStateError(task.exception?.localizedMessage ?: "Invalid OTP verification code.")
                }
            }
    }

    private fun showDynamicGoogleAccountChooser(emails: List<String>) {
        val options = emails.toMutableList().apply {
            add("Use custom Gmail address...")
        }.toTypedArray()

        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Sign in with Google")
            .setItems(options) { dialog, which ->
                if (which == options.size - 1) {
                    showCustomGmailInput()
                } else {
                    val selectedEmailLine = options[which]
                    val email = selectedEmailLine.substringBefore(" (").trim()
                    authViewModel.loginWithGoogleMock(email)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomGmailInput() {
        val input = EditText(requireContext()).apply {
            hint = "username@gmail.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Enter your Gmail")
            .setView(input)
            .setPositiveButton("Sign In") { dialog, _ ->
                val email = input.text.toString().trim()
                if (email.contains("@") && email.endsWith(".com")) {
                    authViewModel.loginWithGoogleMock(email)
                } else {
                    Toast.makeText(context, "Invalid email address format", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        authViewModel.clearOtpState()
        _binding = null
    }
}
