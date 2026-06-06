package com.example.hrapp.ui.fragments

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Attendance
import com.example.hrapp.databinding.FragmentPunchBinding
import com.example.hrapp.ui.viewmodel.AttendanceViewModel
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import com.example.hrapp.ui.viewmodel.PunchResult
import java.io.File
import java.io.FileOutputStream

class PunchFragment : Fragment() {

    private var _binding: FragmentPunchBinding? = null
    private val binding get() = _binding!!

    private lateinit var authViewModel: AuthViewModel
    private lateinit var attendanceViewModel: AttendanceViewModel

    private var selectedVehicle = "No Vehicle"
    private var isPhotoCaptured = false
    private var todayAttendance: Attendance? = null
    private var isPunchOutMode = false

    private var selectedImageUri: String? = null

    private fun copyUriToInternalStorage(uri: Uri): String? {
        return try {
            val context = context ?: return null
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.cacheDir, "odometer_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val savedPathUri = copyUriToInternalStorage(it)
            if (savedPathUri != null) {
                selectedImageUri = savedPathUri
                isPhotoCaptured = true
                binding.imgPhotoIcon.setImageURI(it)
                binding.imgPhotoIcon.imageTintList = null
                binding.imgPhotoIcon.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                binding.imgPhotoIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.textPhotoLabel.visibility = View.GONE
                Toast.makeText(context, "Photo loaded successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to load photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPunchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide main bottom navigation bar inside forms
        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)

        authViewModel = ViewModelProvider(mainAct, factory)[AuthViewModel::class.java]
        attendanceViewModel = ViewModelProvider(mainAct, factory)[AttendanceViewModel::class.java]

        // Load today's attendance record explicitly to ensure UI state sync
        authViewModel.currentUser.value?.let { user ->
            attendanceViewModel.checkTodayAttendance(user.employeeId)
        }

        setupVehicleSelection()
        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        attendanceViewModel.todayAttendance.observe(viewLifecycleOwner) { att ->
            todayAttendance = att
            if (att != null && att.punchOutTime == null) {
                // If already punched in but not punched out, we are in Punch Out Mode
                isPunchOutMode = true
                configurePunchOutUI(att)
            } else {
                isPunchOutMode = false
                configurePunchInUI()
            }
        }

        attendanceViewModel.punchResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is PunchResult.Loading -> {
                    binding.progressOverlay.visibility = View.VISIBLE
                }
                is PunchResult.Success -> {
                    binding.progressOverlay.visibility = View.GONE
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    attendanceViewModel.resetPunchResult()
                    
                    // Route back to dashboard
                    val mainAct = activity as MainActivity
                    mainAct.setBottomNavigationVisibility(true)
                    mainAct.replaceFragment(DashboardFragment(), addToBackStack = false)
                }
                is PunchResult.Error -> {
                    binding.progressOverlay.visibility = View.GONE
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    attendanceViewModel.resetPunchResult()
                }
                null -> {
                    binding.progressOverlay.visibility = View.GONE
                }
            }
        }
    }

    private fun configurePunchInUI() {
        binding.textPunchTitle.text = "Punch In"
        binding.btnSubmitPunch.text = "SUBMIT INCOMING PUNCH"
        binding.btnSubmitPunch.backgroundTintList = null
        binding.btnSubmitPunch.setBackgroundResource(R.drawable.bg_gradient_btn_otp)
        binding.cardLockedVehicleInfo.visibility = View.GONE
        binding.vehicleScroll.visibility = View.VISIBLE
        binding.textSelectVehicleLabel.visibility = View.VISIBLE
        
        selectVehicleOption("No Vehicle")
    }

    private fun configurePunchOutUI(att: Attendance) {
        binding.textPunchTitle.text = "Punch Out"
        binding.btnSubmitPunch.text = "SUBMIT OUTGOING PUNCH"
        binding.btnSubmitPunch.backgroundTintList = null
        binding.btnSubmitPunch.setBackgroundResource(R.drawable.bg_gradient_btn_verify)
        
        // Lock vehicle type selection
        binding.vehicleScroll.visibility = View.GONE
        binding.textSelectVehicleLabel.visibility = View.GONE
        binding.cardLockedVehicleInfo.visibility = View.VISIBLE
        binding.textLockedVehicle.text = "🔒 Commute vehicle is locked to: ${att.vehicleType}"
        
        selectedVehicle = att.vehicleType
        
        if (selectedVehicle == "No Vehicle") {
            binding.layoutVehicleForm.visibility = View.GONE
        } else {
            binding.layoutVehicleForm.visibility = View.VISIBLE
            
            binding.textOpeningOdometerHint.visibility = View.VISIBLE
            binding.textOpeningOdometerHint.text = "Opening Reading: ${att.punchInReading} KM"
            binding.inputOdometerLayout.hint = "Closing Odometer Reading (KM)"
        }

        binding.inputSummaryLayout.hint = "Update evening work achievement details..."
        binding.editWorkSummary.setText(att.workSummary + "\n\nClosing Work: ")
    }

    private fun setupVehicleSelection() {
        binding.cardCompBike.setOnClickListener { selectVehicleOption("Company Bike") }
        binding.cardCompCar.setOnClickListener { selectVehicleOption("Company Car") }
        binding.cardPersBike.setOnClickListener { selectVehicleOption("Personal Bike") }
        binding.cardPersCar.setOnClickListener { selectVehicleOption("Personal Car") }
        binding.cardNoVehicle.setOnClickListener { selectVehicleOption("No Vehicle") }
    }

    private fun selectVehicleOption(option: String) {
        selectedVehicle = option
        val context = requireContext()
        val activeBg = ContextCompat.getColor(context, R.color.border_dark)
        val inactiveBg = ContextCompat.getColor(context, R.color.surface_dark)
        
        val activeTint = ContextCompat.getColor(context, R.color.primary_light)
        val inactiveTint = ContextCompat.getColor(context, R.color.text_sub_dark)

        // Reset backgrounds
        binding.cardCompBike.setCardBackgroundColor(inactiveBg)
        binding.cardCompCar.setCardBackgroundColor(inactiveBg)
        binding.cardPersBike.setCardBackgroundColor(inactiveBg)
        binding.cardPersCar.setCardBackgroundColor(inactiveBg)
        binding.cardNoVehicle.setCardBackgroundColor(inactiveBg)

        // Reset tints
        binding.imgCompBike.imageTintList = ColorStateList.valueOf(inactiveTint)
        binding.imgCompCar.imageTintList = ColorStateList.valueOf(inactiveTint)
        binding.imgPersBike.imageTintList = ColorStateList.valueOf(inactiveTint)
        binding.imgPersCar.imageTintList = ColorStateList.valueOf(inactiveTint)
        binding.imgNoVehicle.imageTintList = ColorStateList.valueOf(inactiveTint)

        // Apply active selections
        when (option) {
            "Company Bike" -> {
                binding.cardCompBike.setCardBackgroundColor(activeBg)
                binding.imgCompBike.imageTintList = ColorStateList.valueOf(activeTint)
                binding.layoutVehicleForm.visibility = View.VISIBLE
            }
            "Company Car" -> {
                binding.cardCompCar.setCardBackgroundColor(activeBg)
                binding.imgCompCar.imageTintList = ColorStateList.valueOf(activeTint)
                binding.layoutVehicleForm.visibility = View.VISIBLE
            }
            "Personal Bike" -> {
                binding.cardPersBike.setCardBackgroundColor(activeBg)
                binding.imgPersBike.imageTintList = ColorStateList.valueOf(activeTint)
                binding.layoutVehicleForm.visibility = View.VISIBLE
            }
            "Personal Car" -> {
                binding.cardPersCar.setCardBackgroundColor(activeBg)
                binding.imgPersCar.imageTintList = ColorStateList.valueOf(activeTint)
                binding.layoutVehicleForm.visibility = View.VISIBLE
            }
            "No Vehicle" -> {
                binding.cardNoVehicle.setCardBackgroundColor(activeBg)
                binding.imgNoVehicle.imageTintList = ColorStateList.valueOf(activeTint)
                binding.layoutVehicleForm.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val mainAct = activity as MainActivity
            mainAct.setBottomNavigationVisibility(true)
            mainAct.replaceFragment(DashboardFragment(), addToBackStack = false)
        }

        binding.cardPhotoUpload.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSubmitPunch.setOnClickListener {
            handleSubmit()
        }
    }

    private fun handleSubmit() {
        val user = authViewModel.currentUser.value ?: run {
            Toast.makeText(context, "Error: User session not found. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        val workSummary = binding.editWorkSummary.text.toString().trim()

        if (workSummary.isBlank()) {
            Toast.makeText(context, "Please enter your work summary", Toast.LENGTH_SHORT).show()
            return
        }

        var odometerValue = 0.0
        val vehicleNumber = ""

        if (selectedVehicle != "No Vehicle") {
            val odoStr = binding.editOdometer.text.toString().trim()

            if (odoStr.isBlank()) {
                Toast.makeText(context, "Please enter the odometer reading", Toast.LENGTH_SHORT).show()
                return
            }

            odometerValue = odoStr.toDoubleOrNull() ?: 0.0
            if (odometerValue <= 0.0) {
                Toast.makeText(context, "Please enter a valid odometer reading", Toast.LENGTH_SHORT).show()
                return
            }

            if (!isPhotoCaptured) {
                Toast.makeText(context, "Please capture the odometer screen photo log", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val photoUri = selectedImageUri ?: (if (isPhotoCaptured) "content://mock/image_odometer.png" else null)

        if (isPunchOutMode) {
            val att = todayAttendance ?: run {
                Toast.makeText(context, "Error: Daily punch-in attendance record not found.", Toast.LENGTH_LONG).show()
                return
            }
            // Validation is performed inside ViewModel as well, but we can verify here for instant alerts
            if (selectedVehicle != "No Vehicle" && odometerValue <= att.punchInReading) {
                Toast.makeText(context, "Validation Error: Outgoing odometer reading ($odometerValue) must be greater than incoming reading (${att.punchInReading})", Toast.LENGTH_LONG).show()
                return
            }

            attendanceViewModel.punchOut(att, odometerValue, workSummary, photoUri)
        } else {
            attendanceViewModel.punchIn(user.employeeId, selectedVehicle, vehicleNumber, odometerValue, workSummary, photoUri)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
