package com.example.hrapp.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.databinding.FragmentSettingsBinding
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var authViewModel: AuthViewModel

    private val pickImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bitmap = getBitmapFromUri(it)
            if (bitmap != null) {
                showCropDialog(bitmap)
            } else {
                Toast.makeText(context, "Failed to load photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var cameraTempFile: java.io.File? = null

    private val captureImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraTempFile?.let { file ->
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    showCropDialog(bitmap)
                } else {
                    Toast.makeText(context, "Failed to load captured photo", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to capture photos.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(true)
        (activity as? MainActivity)?.selectTab(R.id.tab_settings)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        authViewModel = ViewModelProvider(mainAct, factory)[AuthViewModel::class.java]

        setupPreferencesUI()
        setupObservers()
        setupListeners()
    }

    private fun setupPreferencesUI() {
        val mainAct = activity as MainActivity
        val prefs = mainAct.repository.prefs

        // 1. Set Biometrics Switch State
        binding.switchBiometrics.isChecked = prefs.isBiometricsEnabled

        // 2. Set Active Theme Radio Checked
        when (prefs.appTheme) {
            0 -> binding.themeRadioGroup.check(R.id.radio_theme_system)
            1 -> binding.themeRadioGroup.check(R.id.radio_theme_light)
            2 -> binding.themeRadioGroup.check(R.id.radio_theme_dark)
        }
    }

    private fun setupObservers() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.textProfileName.text = user.name
                binding.textProfileId.text = "ID: ${user.employeeId}"
                binding.textProfileDesignation.text = user.designation
                binding.textProfileDept.text = "Department: ${user.department}"
                binding.textProfileMobile.text = "Mobile: ${user.mobile}"
                binding.textProfileEmail.text = "Email: ${user.email}"
                binding.textProfileManager.text = "Manager: ${user.reportingManager}"
                binding.textProfileDate.text = "Joining Date: ${user.joiningDate}"

                if (user.profilePhotoUrl.isNotBlank()) {
                    com.bumptech.glide.Glide.with(this)
                        .load(user.profilePhotoUrl)
                        .placeholder(R.drawable.ic_admin)
                        .error(R.drawable.ic_admin)
                        .into(binding.imgSettingsPhoto)
                } else {
                    binding.imgSettingsPhoto.setImageResource(R.drawable.ic_admin)
                }

                // Show Admin shortcut ONLY if user role is ADMIN
                if (user.role == "ADMIN") {
                    binding.cardAdminConsole.visibility = View.VISIBLE
                } else {
                    binding.cardAdminConsole.visibility = View.GONE
                }
            }
        }
    }

    private fun setupListeners() {
        val mainAct = activity as MainActivity

        binding.switchBiometrics.setOnCheckedChangeListener { _, isChecked ->
            authViewModel.setBiometricEnabled(isChecked)
            val txt = if (isChecked) "Fingerprint Login Enabled" else "Fingerprint Login Disabled"
            Toast.makeText(context, txt, Toast.LENGTH_SHORT).show()
        }

        binding.themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val themeIndex = when (checkedId) {
                R.id.radio_theme_system -> 0
                R.id.radio_theme_light -> 1
                R.id.radio_theme_dark -> 2
                else -> 0
            }
            if (mainAct.repository.prefs.appTheme != themeIndex) {
                mainAct.repository.prefs.appTheme = themeIndex
                mainAct.applyTheme(themeIndex)
                Toast.makeText(context, "Theme updated successfully!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardAdminConsole.setOnClickListener {
            mainAct.replaceFragment(AdminDashboardFragment())
        }

        binding.btnContactSupport.setOnClickListener {
            triggerSupportEmail()
        }

        binding.btnWhatsappSupport.setOnClickListener {
            triggerWhatsappSupport()
        }

        binding.btnPolicyPrivacy.setOnClickListener {
            showPolicyDialog("Privacy Policy", "Your privacy is important to us. This application stores authentication credentials and logs local biometric setups inside secure device directories. Attendance logs and work summaries are kept locally on device storage and synchronized securely using enterprise database rules.")
        }

        binding.btnPolicyTerms.setOnClickListener {
            showPolicyDialog("Terms & Conditions", "By logging in to this corporate portal application, you agree to submit correct work summaries, vehicle odometer readings, and bill claims. Misrepresentation of mileage counts or odometer screenshots will lead to administrative reviews.")
        }

        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            Toast.makeText(context, "Logged out successfully.", Toast.LENGTH_SHORT).show()
            mainAct.replaceFragment(LoginFragment(), addToBackStack = false)
        }

        binding.cardSettingsPhoto.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            val view = requireActivity().layoutInflater.inflate(R.layout.dialog_change_profile_photo, null)
            builder.setView(view)

            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
            dialog.show()

            view.findViewById<View>(R.id.btn_option_gallery).setOnClickListener {
                pickImageLauncher.launch("image/*")
                dialog.dismiss()
            }

            view.findViewById<View>(R.id.btn_option_camera).setOnClickListener {
                checkCameraPermissionAndLaunch()
                dialog.dismiss()
            }

            view.findViewById<View>(R.id.btn_option_remove).setOnClickListener {
                authViewModel.updateProfilePhoto("")
                Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    private fun triggerSupportEmail() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:stubbornpatil6@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "HRMS App Support - EMP ID")
            }
            startActivity(emailIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "No email applications found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerWhatsappSupport() {
        try {
            val url = "https://wa.me/8668842351?text=Hello%20Sir,%20%20I%20am%20facing%20an%20issue%20in%20the%20HR%20App.%20Kindly%20check%20and%20resolve%20the%20issue%20as%20soon%20as%20possible."
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open WhatsApp.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPolicyDialog(title: String, body: String) {
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        val permission = android.Manifest.permission.CAMERA
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCameraPermissionLauncher.launch(permission)
        }
    }

    private fun launchCamera() {
        try {
            val tempFile = java.io.File(requireContext().cacheDir, "profile_temp_${System.currentTimeMillis()}.jpg")
            cameraTempFile = tempFile
            val providerUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "com.example.hrapp.fileprovider",
                tempFile
            )
            captureImageLauncher.launch(providerUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to launch camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getBitmapFromUri(uri: Uri): android.graphics.Bitmap? {
        return try {
            val context = context ?: return null
            val inputStream = context.contentResolver.openInputStream(uri)
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cropToSquare(bitmap: android.graphics.Bitmap, zoomPercent: Int, rotationDegrees: Int): android.graphics.Bitmap {
        var bmp = bitmap
        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bmp = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val width = bmp.width
        val height = bmp.height
        val minDim = Math.min(width, height)

        val scaleFactor = 1.0f - (zoomPercent / 100.0f).coerceIn(0.0f, 0.5f)
        val cropSize = (minDim * scaleFactor).toInt()

        val startX = (width - cropSize) / 2
        val startY = (height - cropSize) / 2

        return android.graphics.Bitmap.createBitmap(bmp, startX, startY, cropSize, cropSize)
    }

    private fun saveBitmapToInternalStorage(bitmap: android.graphics.Bitmap): String? {
        return try {
            val context = context ?: return null
            val file = java.io.File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            val outputStream = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showCropDialog(originalBitmap: android.graphics.Bitmap) {
        val context = requireContext()
        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val frameLayout = android.widget.FrameLayout(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                500,
                500
            ).apply {
                gravity = android.view.Gravity.CENTER
                bottomMargin = 24
            }
        }

        val imageView = android.widget.ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        frameLayout.addView(imageView)

        val overlay = object : android.view.View(context) {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#99000000")
                style = android.graphics.Paint.Style.FILL
            }
            val borderPaint = android.graphics.Paint().apply {
                color = androidx.core.content.ContextCompat.getColor(context, R.color.primary_light)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 6f
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val size = Math.min(w, h) * 0.9f
                val left = (w - size) / 2
                val top = (h - size) / 2
                val right = left + size
                val bottom = top + size

                canvas.drawRect(0f, 0f, w, top, paint)
                canvas.drawRect(0f, bottom, w, h, paint)
                canvas.drawRect(0f, top, left, bottom, paint)
                canvas.drawRect(right, top, w, bottom, paint)
                canvas.drawRect(left, top, right, bottom, borderPaint)
            }
        }.apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        frameLayout.addView(overlay)
        root.addView(frameLayout)

        val zoomLabel = android.widget.TextView(context).apply {
            text = "Zoom: 0%"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }
        root.addView(zoomLabel)

        val seekBar = android.widget.SeekBar(context).apply {
            max = 50
            progress = 0
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }
        root.addView(seekBar)

        val btnRow = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnRotate = android.widget.Button(context).apply {
            text = "Rotate 90°"
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(24, 12, 24, 12)
        }
        btnRow.addView(btnRotate)
        root.addView(btnRow)

        var currentRotation = 0
        var currentZoom = 0

        fun updatePreview() {
            val cropped = cropToSquare(originalBitmap, currentZoom, currentRotation)
            imageView.setImageBitmap(cropped)
        }

        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                currentZoom = progress
                zoomLabel.text = "Zoom: ${progress * 2}%"
                updatePreview()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        btnRotate.setOnClickListener {
            currentRotation = (currentRotation + 90) % 360
            updatePreview()
        }

        updatePreview()

        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Crop Profile Photo")
        builder.setView(root)
        builder.setPositiveButton("Crop & Save") { dialog, _ ->
            val finalBitmap = cropToSquare(originalBitmap, currentZoom, currentRotation)
            val savedUri = saveBitmapToInternalStorage(finalBitmap)
            if (savedUri != null) {
                authViewModel.updateProfilePhoto(savedUri)
                Toast.makeText(context, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save cropped image", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
