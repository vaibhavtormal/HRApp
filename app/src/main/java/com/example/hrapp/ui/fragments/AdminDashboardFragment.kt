package com.example.hrapp.ui.fragments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Employee
import com.example.hrapp.data.db.Holiday
import com.example.hrapp.data.db.Video
import com.example.hrapp.data.db.Shift
import com.example.hrapp.databinding.FragmentAdminDashboardBinding
import com.example.hrapp.ui.viewmodel.AdminViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var adminViewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        adminViewModel = ViewModelProvider(mainAct, factory)[AdminViewModel::class.java]

        setupObservers()
        setupListeners()

        adminViewModel.loadAdminData()
    }

    private fun setupObservers() {
        adminViewModel.pendingLeaves.observe(viewLifecycleOwner) { list ->
            binding.textAdminPendingLeaves.text = list.size.toString()
        }

        adminViewModel.pendingExpenses.observe(viewLifecycleOwner) { list ->
            binding.textAdminPendingExpenses.text = list.size.toString()
        }

        adminViewModel.pendingRegularizations.observe(viewLifecycleOwner) { list ->
            binding.textAdminPendingRegs.text = list.size.toString()
        }

        adminViewModel.allEmployees.observe(viewLifecycleOwner) { list ->
            binding.textAdminTotalEmployees.text = list.size.toString()
        }
    }

    private fun setupListeners() {
        val mainAct = activity as MainActivity

        binding.btnBack.setOnClickListener { returnToEmployeeSettings() }
        binding.btnReturnEmployee.setOnClickListener { returnToEmployeeSettings() }

        binding.cardPendingLeavesShortcut.setOnClickListener { openApprovals(0) }
        binding.cardPendingExpensesShortcut.setOnClickListener { openApprovals(1) }
        binding.cardPendingRegsShortcut.setOnClickListener { openApprovals(2) }
        binding.cardTotalEmployeesShortcut.setOnClickListener {
            mainAct.replaceFragment(StaffListFragment())
        }

        binding.actionReports.setOnClickListener {
            mainAct.replaceFragment(AdminReportsFragment())
        }

        binding.actionPostNotif.setOnClickListener { showPostNotifDialog() }
        binding.actionAddVideo.setOnClickListener { showVideoManagementOptions() }
        binding.actionAddHoliday.setOnClickListener { showAddHolidayDialog() }
        binding.actionAddEmployee.setOnClickListener { showAddEmployeeDialog() }
        binding.actionManageShifts.setOnClickListener { showManageShiftsDialog() }
    }

    private fun openApprovals(tabIndex: Int) {
        val mainAct = activity as MainActivity
        val approvalsFrag = AdminApprovalsFragment().apply {
            arguments = Bundle().apply {
                putInt("start_tab_index", tabIndex)
            }
        }
        mainAct.replaceFragment(approvalsFrag)
    }

    private fun returnToEmployeeSettings() {
        val mainAct = activity as MainActivity
        mainAct.setBottomNavigationVisibility(true)
        mainAct.selectTab(R.id.tab_settings)
        mainAct.replaceFragment(SettingsFragment(), addToBackStack = false)
    }

    private fun showPostNotifDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_admin_post_notif, null)
        builder.setView(view)

        val editTitle = view.findViewById<EditText>(R.id.dialog_notif_title)
        val editDesc = view.findViewById<EditText>(R.id.dialog_notif_desc)
        val spinnerType = view.findViewById<Spinner>(R.id.dialog_spinner_notif_type)

        val types = arrayOf("Announcement", "Meeting", "Holiday", "Alert")
        spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)

        builder.setPositiveButton("Post Broadcast", null)
        builder.setNegativeButton("Cancel", null)
        
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        dialog.show()

        // Apply Premium Custom Styled Buttons Programmatically
        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        posBtn?.text = "Post Broadcast"
        posBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_primary)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }
        negBtn?.text = "Cancel"
        negBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }

        posBtn?.setOnClickListener {
            val title = editTitle.text.toString().trim()
            val desc = editDesc.text.toString().trim()
            val type = spinnerType.selectedItem.toString()

            if (title.isBlank() || desc.isBlank()) {
                Toast.makeText(context, "Fields cannot be blank", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            adminViewModel.postNotification(title, desc, type)
            Toast.makeText(context, "Notification broadcasted successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun showAddVideoDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_admin_add_video, null)
        builder.setView(view)

        val editTitle = view.findViewById<EditText>(R.id.dialog_video_title)
        val editDesc = view.findViewById<EditText>(R.id.dialog_video_desc)
        val editUrl = view.findViewById<EditText>(R.id.dialog_video_url)
        val spinnerCategory = view.findViewById<Spinner>(R.id.dialog_spinner_video_cat)

        val categories = arrayOf("Culture", "Training", "HR Help")
        spinnerCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        builder.setPositiveButton("Save Video", null)
        builder.setNegativeButton("Cancel", null)
        
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        dialog.show()

        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        posBtn?.text = "Save Video"
        posBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_primary)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }
        negBtn?.text = "Cancel"
        negBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }

        posBtn?.setOnClickListener {
            val title = editTitle.text.toString().trim()
            val desc = editDesc.text.toString().trim()
            val url = editUrl.text.toString().trim()
            val cat = spinnerCategory.selectedItem.toString()

            if (title.isBlank() || desc.isBlank() || url.isBlank()) {
                Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var thumbnail = "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=500"
            val lowerUrl = url.lowercase()

            if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
                var videoId = "dQw4w9WgXcQ"
                val patterns = listOf("v=([^&]+)", "youtu.be/([^?]+)", "youtube.com/shorts/([^?]+)")
                for (p in patterns) {
                    val regex = p.toRegex()
                    val matchResult = regex.find(url)
                    if (matchResult != null) {
                        videoId = matchResult.groupValues[1]
                        break
                    }
                }
                if (videoId == "dQw4w9WgXcQ" && url.length == 11 && !url.contains("/")) {
                    videoId = url
                }
                thumbnail = "https://img.youtube.com/vi/$videoId/0.jpg"
            } else if (lowerUrl.contains("vimeo.com")) {
                val regex = "vimeo.com/(\\d+)".toRegex()
                val match = regex.find(url)
                val videoId = match?.groupValues?.get(1) ?: "123456789"
                thumbnail = "https://vumbnail.com/$videoId.jpg"
            } else if (lowerUrl.contains("instagram.com")) {
                thumbnail = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=500"
            } else if (lowerUrl.contains("facebook.com")) {
                thumbnail = "https://images.unsplash.com/photo-1611162616305-c69b3fa7fbe0?w=500"
            } else if (lowerUrl.contains("linkedin.com")) {
                thumbnail = "https://images.unsplash.com/photo-1611944212129-43b230d51137?w=500"
            } else if (lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com")) {
                thumbnail = "https://images.unsplash.com/photo-1611605698335-8b15d27e03f9?w=500"
            }
            
            adminViewModel.addVideo(Video(title = title, description = desc, youtubeUrl = url, thumbnailUrl = thumbnail, category = cat))
            Toast.makeText(context, "Video added to library!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun showAddHolidayDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_admin_add_holiday, null)
        builder.setView(view)

        val editName = view.findViewById<EditText>(R.id.dialog_holiday_name)
        val textDate = view.findViewById<TextView>(R.id.dialog_holiday_date)
        val spinnerType = view.findViewById<Spinner>(R.id.dialog_spinner_holiday_type)

        val types = arrayOf("National", "State", "Company")
        spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)

        var selectedDateStr = ""
        textDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosen.time)
                textDate.text = "Date Selected: $selectedDateStr"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        builder.setPositiveButton("Add Date", null)
        builder.setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        dialog.show()

        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        posBtn?.text = "Add Date"
        posBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_primary)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }
        negBtn?.text = "Cancel"
        negBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }

        posBtn?.setOnClickListener {
            val name = editName.text.toString().trim()
            val type = spinnerType.selectedItem.toString()

            if (name.isBlank() || selectedDateStr.isBlank()) {
                Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            adminViewModel.addHoliday(Holiday(name = name, date = selectedDateStr, type = type))
            Toast.makeText(context, "Holiday added successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun showAddEmployeeDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_admin_add_employee, null)
        builder.setView(view)

        val editName = view.findViewById<EditText>(R.id.dialog_emp_name)
        val editEmail = view.findViewById<EditText>(R.id.dialog_emp_email)
        val editEmpId = view.findViewById<EditText>(R.id.dialog_emp_id)
        val spinnerType = view.findViewById<Spinner>(R.id.dialog_spinner_emp_type)
        val spinnerDesign = view.findViewById<Spinner>(R.id.dialog_spinner_emp_designation)
        val spinnerDept = view.findViewById<Spinner>(R.id.dialog_spinner_emp_dept)
        val spinnerReporting = view.findViewById<Spinner>(R.id.dialog_spinner_emp_reporting_manager)
        val editMobile = view.findViewById<EditText>(R.id.dialog_emp_mobile)
        val spinnerRole = view.findViewById<Spinner>(R.id.dialog_spinner_emp_role)
        val editJoiningDate = view.findViewById<EditText>(R.id.dialog_emp_joining_date)
        val editBirthday = view.findViewById<EditText>(R.id.dialog_emp_birthday)
        val spinnerShift = view.findViewById<Spinner>(R.id.dialog_spinner_emp_shift)

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        editJoiningDate.setText(todayStr)

        editJoiningDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosen.time)
                editJoiningDate.setText(dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        editBirthday.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosen.time)
                editBirthday.setText(dateStr)
            }, 1998, 0, 1).show()
        }

        val roles = arrayOf("EMPLOYEE", "ADMIN")
        spinnerRole.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)

        // 1. Employee Types
        val types = arrayOf("Permanent", "Apprentice")
        spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)

        // 2. Fetch existing data for auto-suggestions
        val existingEmployees = adminViewModel.allEmployees.value ?: emptyList()

        // 3. Dynamic Lists
        val permanentDesignations = existingEmployees
            .filter { it.employeeId.startsWith("EMP", ignoreCase = true) && !it.employeeId.startsWith("EMPA", ignoreCase = true) }
            .map { it.designation }
            .filter { it.isNotBlank() }
            .distinct()
            .toCollection(ArrayList())
        if (permanentDesignations.isEmpty()) {
            permanentDesignations.addAll(listOf("Developer", "Manager", "Senior Developer", "Director", "Designer"))
        }

        val apprenticeDesignations = existingEmployees
            .filter { it.employeeId.startsWith("EMPA", ignoreCase = true) }
            .map { it.designation }
            .filter { it.isNotBlank() }
            .distinct()
            .toCollection(ArrayList())
        if (apprenticeDesignations.isEmpty()) {
            apprenticeDesignations.addAll(listOf("Apprentice Trainee", "Apprentice Engineer", "Apprentice Technician"))
        }

        val departmentsList = existingEmployees
            .map { it.department }
            .filter { it.isNotBlank() }
            .distinct()
            .toCollection(ArrayList())
        if (departmentsList.isEmpty()) {
            departmentsList.addAll(listOf("Product Engineering", "Human Resources", "Finance", "Sales & Marketing", "Quality Assurance"))
        }

        // 3a. Shift Spinner setup
        val existingShifts = adminViewModel.allShifts.value ?: emptyList()
        val shiftNames = existingShifts.map { it.name }.toCollection(ArrayList())
        if (shiftNames.isEmpty()) {
            shiftNames.addAll(listOf("General Shift", "Morning Shift", "Night Shift"))
        }
        val dispShifts = ArrayList(shiftNames).apply { add("+ Add New Shift...") }
        val shiftAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, dispShifts)
        spinnerShift.adapter = shiftAdapter

        spinnerShift.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = spinnerShift.selectedItem.toString()
                if (selected == "+ Add New Shift...") {
                    showAddEditShiftDialog(null) { newShift ->
                        dispShifts.add(dispShifts.size - 1, newShift.name)
                        shiftAdapter.notifyDataSetChanged()
                        spinnerShift.setSelection(dispShifts.indexOf(newShift.name))
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Add special "+ Add New" item to selection arrays
        val dispPermanentDesignations = ArrayList(permanentDesignations).apply { add("+ Add New Designation...") }
        val dispApprenticeDesignations = ArrayList(apprenticeDesignations).apply { add("+ Add New Designation...") }
        val dispDepartments = ArrayList(departmentsList).apply { add("+ Add New Department...") }

        // Setup adapters
        val designAdapterPerm = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, dispPermanentDesignations)
        val designAdapterAppr = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, dispApprenticeDesignations)
        val deptAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, dispDepartments)

        spinnerDept.adapter = deptAdapter

        // 4. Setup Reporting Managers list (designation contains Manager, or role is ADMIN)
        val managersList = ArrayList<String>().apply { add("None") }
        existingEmployees.filter { 
            it.designation.contains("Manager", ignoreCase = true) || it.role.equals("ADMIN", ignoreCase = true)
        }.map { 
            "${it.name} (${it.employeeId})"
        }.distinct().let { 
            managersList.addAll(it)
        }
        spinnerReporting.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, managersList)

        // Helper function to generate IDs
        fun autoGenerateId(isApprentice: Boolean) {
            if (isApprentice) {
                val lastNum = existingEmployees
                    .filter { it.employeeId.startsWith("EMPA", ignoreCase = true) }
                    .mapNotNull {
                        val suffix = it.employeeId.drop(4) // safely drop "EMPA"
                        suffix.toIntOrNull()
                    }.maxOrNull() ?: 0
                editEmpId.setText("EMPA${lastNum + 1}")
            } else {
                val lastNum = existingEmployees
                    .filter { it.employeeId.startsWith("EMP", ignoreCase = true) && !it.employeeId.startsWith("EMPA", ignoreCase = true) }
                    .mapNotNull {
                        val suffix = it.employeeId.drop(3) // safely drop "EMP"
                        suffix.toIntOrNull()
                    }.maxOrNull() ?: 0
                editEmpId.setText("EMP${lastNum + 1}")
            }
        }

        // Type selection listener
        spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val type = types[position]
                if (type == "Apprentice") {
                    autoGenerateId(true)
                    spinnerDesign.adapter = designAdapterAppr
                } else {
                    autoGenerateId(false)
                    spinnerDesign.adapter = designAdapterPerm
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Designation selection listener for "+ Add New"
        spinnerDesign.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = spinnerDesign.selectedItem.toString()
                if (selected == "+ Add New Designation...") {
                    showCustomAddDialog("New Designation Title") { name ->
                        val currentType = spinnerType.selectedItem.toString()
                        if (currentType == "Apprentice") {
                            dispApprenticeDesignations.add(dispApprenticeDesignations.size - 1, name)
                            designAdapterAppr.notifyDataSetChanged()
                            spinnerDesign.setSelection(dispApprenticeDesignations.indexOf(name))
                        } else {
                            dispPermanentDesignations.add(dispPermanentDesignations.size - 1, name)
                            designAdapterPerm.notifyDataSetChanged()
                            spinnerDesign.setSelection(dispPermanentDesignations.indexOf(name))
                        }
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Department selection listener for "+ Add New"
        spinnerDept.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = spinnerDept.selectedItem.toString()
                if (selected == "+ Add New Department...") {
                    showCustomAddDialog("New Department Division") { name ->
                        dispDepartments.add(dispDepartments.size - 1, name)
                        deptAdapter.notifyDataSetChanged()
                        spinnerDept.setSelection(dispDepartments.indexOf(name))
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        builder.setPositiveButton("Register Account", null)
        builder.setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        dialog.show()

        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        posBtn?.text = "Register Account"
        posBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_primary)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }
        negBtn?.text = "Cancel"
        negBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }

        posBtn?.setOnClickListener {
            val name = editName.text.toString().trim()
            val email = editEmail.text.toString().trim()
            val empId = editEmpId.text.toString().trim()
            val design = spinnerDesign.selectedItem?.toString() ?: ""
            val dept = spinnerDept.selectedItem?.toString() ?: ""
            val repManagerRaw = spinnerReporting.selectedItem?.toString() ?: "None"
            val mobile = editMobile.text.toString().trim()
            val role = spinnerRole.selectedItem.toString()
            val joiningDateVal = editJoiningDate.text.toString().trim()
            val birthdayVal = editBirthday.text.toString().trim()
            val empType = spinnerType.selectedItem.toString()
            val shiftTypeVal = spinnerShift.selectedItem?.toString() ?: ""

            if (name.isBlank() || empId.isBlank() || design.isBlank() || dept.isBlank() || joiningDateVal.isBlank() || birthdayVal.isBlank() || shiftTypeVal.isBlank()) {
                Toast.makeText(context, "All required fields are mandatory", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email.isBlank() && mobile.isBlank()) {
                Toast.makeText(context, "Either Corporate Email or Mobile Number must be provided", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalEmail = email.ifBlank { "${empId.lowercase()}@company.local" }

            if (design == "+ Add New Designation..." || dept == "+ Add New Department..." || shiftTypeVal == "+ Add New Shift...") {
                Toast.makeText(context, "Please select or create a valid designation/department/shift", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val repManager = if (repManagerRaw == "None") {
                ""
            } else {
                repManagerRaw.substringBefore(" (").trim()
            }

            adminViewModel.addEmployee(
                Employee(
                    name = name,
                    email = finalEmail,
                    employeeId = empId,
                    designation = design,
                    department = dept,
                    mobile = mobile,
                    reportingManager = repManager,
                    joiningDate = joiningDateVal,
                    birthday = birthdayVal,
                    employeeType = empType,
                    profilePhotoUrl = "",
                    role = role,
                    shiftType = shiftTypeVal
                )
            )
            Toast.makeText(context, "Registered Employee Account!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun showCustomAddDialog(title: String, onAdded: (String) -> Unit) {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle(title)

        val input = EditText(requireContext()).apply {
            hint = "Enter name..."
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_sub_dark))
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.border_dark)
            setPadding(32, 32, 32, 32)
        }

        val container = android.widget.FrameLayout(requireContext()).apply {
            setPadding(40, 20, 40, 20)
            addView(input)
        }

        builder.setView(container)
        builder.setPositiveButton("Add") { _, _ ->
            val text = input.text.toString().trim()
            if (text.isNotBlank()) {
                onAdded(text)
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showVideoManagementOptions() {
        val options = arrayOf("Add New Video", "Manage Existing Videos")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Video Content Management")
        builder.setItems(options) { dialog, which ->
            if (which == 0) {
                showAddVideoDialog()
            } else {
                showManageVideosListDialog()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        dialog.show()

        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        negBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
        }
    }

    private fun showManageVideosListDialog() {
        lifecycleScope.launch {
            val mainAct = activity as MainActivity
            val allVideos = mainAct.repository.getAllVideos()
            if (allVideos.isEmpty()) {
                Toast.makeText(context, "No videos available to manage", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val videoTitles = allVideos.map {
                val status = if (it.isEnabled) "Active" else "Disabled"
                "${it.title} ($status)"
            }.toTypedArray()

            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Select Video to Manage")
            builder.setItems(videoTitles) { dialog, which ->
                val selectedVideo = allVideos[which]
                showEditVideoDetailsDialog(selectedVideo)
                dialog.dismiss()
            }
            builder.setNegativeButton("Back", null)
            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
            dialog.show()

            val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            negBtn?.run {
                setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
                setTextColor(ContextCompat.getColor(context, R.color.white))
                val params = layoutParams as android.widget.LinearLayout.LayoutParams
                params.setMargins(16, 0, 16, 16)
                layoutParams = params
            }
        }
    }

    private fun showEditVideoDetailsDialog(video: Video) {
        val builder = AlertDialog.Builder(requireContext())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_admin_add_video, null)
        builder.setView(view)

        // Pre-fill header titles
        val headerTitle = view.findViewById<TextView>(R.id.dialog_header_title)
        headerTitle?.text = "Edit Video Details"

        val editTitle = view.findViewById<EditText>(R.id.dialog_video_title)
        val editDesc = view.findViewById<EditText>(R.id.dialog_video_desc)
        val editUrl = view.findViewById<EditText>(R.id.dialog_video_url)
        val spinnerCategory = view.findViewById<Spinner>(R.id.dialog_spinner_video_cat)

        val categories = arrayOf("Culture", "Training", "HR Help")
        spinnerCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        editTitle.setText(video.title)
        editDesc.setText(video.description)
        editUrl.setText(video.youtubeUrl)
        spinnerCategory.setSelection(categories.indexOf(video.category).coerceAtLeast(0))

        val enableDisableText = if (video.isEnabled) "Disable Video" else "Enable Video"

        builder.setPositiveButton("Save", null)
        builder.setNeutralButton(enableDisableText, null)
        builder.setNegativeButton("Delete", null)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        dialog.show()

        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val neuBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        posBtn?.text = "Save"
        posBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_primary)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(8, 0, 8, 16)
            layoutParams = params
        }
        neuBtn?.text = enableDisableText
        neuBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(8, 0, 8, 16)
            layoutParams = params
        }
        negBtn?.text = "Delete"
        negBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_delete)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(8, 0, 8, 16)
            layoutParams = params
        }

        posBtn?.setOnClickListener {
            val title = editTitle.text.toString().trim()
            val desc = editDesc.text.toString().trim()
            val url = editUrl.text.toString().trim()
            val cat = spinnerCategory.selectedItem.toString()

            if (title.isBlank() || desc.isBlank() || url.isBlank()) {
                Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var thumbnail = "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=500"
            val lowerUrl = url.lowercase()

            if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
                var videoId = "dQw4w9WgXcQ"
                val patterns = listOf("v=([^&]+)", "youtu.be/([^?]+)", "youtube.com/shorts/([^?]+)")
                for (p in patterns) {
                    val regex = p.toRegex()
                    val matchResult = regex.find(url)
                    if (matchResult != null) {
                        videoId = matchResult.groupValues[1]
                        break
                    }
                }
                if (videoId == "dQw4w9WgXcQ" && url.length == 11 && !url.contains("/")) {
                    videoId = url
                }
                thumbnail = "https://img.youtube.com/vi/$videoId/0.jpg"
            } else if (lowerUrl.contains("vimeo.com")) {
                val regex = "vimeo.com/(\\d+)".toRegex()
                val match = regex.find(url)
                val videoId = match?.groupValues?.get(1) ?: "123456789"
                thumbnail = "https://vumbnail.com/$videoId.jpg"
            } else if (lowerUrl.contains("instagram.com")) {
                thumbnail = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=500"
            } else if (lowerUrl.contains("facebook.com")) {
                thumbnail = "https://images.unsplash.com/photo-1611162616305-c69b3fa7fbe0?w=500"
            } else if (lowerUrl.contains("linkedin.com")) {
                thumbnail = "https://images.unsplash.com/photo-1611944212129-43b230d51137?w=500"
            } else if (lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com")) {
                thumbnail = "https://images.unsplash.com/photo-1611605698335-8b15d27e03f9?w=500"
            }

            val updatedVideo = video.copy(
                title = title,
                description = desc,
                youtubeUrl = url,
                thumbnailUrl = thumbnail,
                category = cat
            )
            adminViewModel.updateVideo(updatedVideo)
            Toast.makeText(context, "Video updated successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        neuBtn?.setOnClickListener {
            val updatedVideo = video.copy(isEnabled = !video.isEnabled)
            adminViewModel.updateVideo(updatedVideo)
            val msg = if (updatedVideo.isEnabled) "Video Enabled!" else "Video Disabled!"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        negBtn?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Video")
                .setMessage("Are you sure you want to delete this video?")
                .setPositiveButton("Delete") { _, _ ->
                    adminViewModel.deleteVideo(video.id)
                    Toast.makeText(context, "Video deleted!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showManageShiftsDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_admin_manage_shifts, null)
        builder.setView(view)

        val recycler = view.findViewById<RecyclerView>(R.id.dialog_shifts_recycler)
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        val initialShifts = adminViewModel.allShifts.value ?: emptyList()
        val adapter = ShiftAdapter(initialShifts) { shift ->
            showAddEditShiftDialog(shift)
        }
        recycler.adapter = adapter

        val observer = Observer<List<Shift>> { shifts ->
            adapter.submitList(shifts)
        }
        adminViewModel.allShifts.observe(viewLifecycleOwner, observer)

        builder.setPositiveButton("Create Shift", null)
        builder.setNegativeButton("Close", null)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        dialog.setOnDismissListener {
            adminViewModel.allShifts.removeObserver(observer)
        }
        dialog.show()

        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        posBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_primary)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
            setOnClickListener {
                showAddEditShiftDialog(null)
            }
        }

        negBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(16, 0, 16, 16)
            layoutParams = params
            setOnClickListener {
                dialog.dismiss()
            }
        }
    }

    private fun showAddEditShiftDialog(shift: Shift?, onShiftAdded: ((Shift) -> Unit)? = null) {
        val builder = AlertDialog.Builder(requireContext())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_admin_add_shift, null)
        builder.setView(view)

        val headerTitle = view.findViewById<TextView>(R.id.dialog_header_title)
        val headerSubtitle = view.findViewById<TextView>(R.id.dialog_header_subtitle)
        val editName = view.findViewById<EditText>(R.id.dialog_shift_name)
        val textStart = view.findViewById<TextView>(R.id.dialog_shift_start_time)
        val textEnd = view.findViewById<TextView>(R.id.dialog_shift_end_time)

        var selectedStart = ""
        var selectedEnd = ""

        if (shift != null) {
            headerTitle.text = "Edit Shift Details"
            headerSubtitle.text = "Modify work shift name and timings"
            editName.setText(shift.name)
            textStart.text = "Start Time: ${shift.startTime}"
            textEnd.text = "End Time: ${shift.endTime}"
            selectedStart = shift.startTime
            selectedEnd = shift.endTime
        }

        fun showTimePicker(isStartTime: Boolean) {
            val cal = Calendar.getInstance()
            val listener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                val isPM = hourOfDay >= 12
                val hour12 = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                val formatted = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, if (isPM) "PM" else "AM")
                if (isStartTime) {
                    selectedStart = formatted
                    textStart.text = "Start Time: $selectedStart"
                } else {
                    selectedEnd = formatted
                    textEnd.text = "End Time: $selectedEnd"
                }
            }
            TimePickerDialog(requireContext(), listener, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }

        textStart.setOnClickListener { showTimePicker(true) }
        textEnd.setOnClickListener { showTimePicker(false) }

        if (shift == null) {
            builder.setPositiveButton("Create Shift", null)
            builder.setNegativeButton("Cancel", null)
        } else {
            builder.setPositiveButton("Save", null)
            builder.setNegativeButton("Delete", null)
            builder.setNeutralButton("Cancel", null)
        }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_card)
        dialog.show()

        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val neuBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)

        posBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_primary)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(8, 0, 8, 16)
            layoutParams = params
        }
        negBtn?.run {
            setBackgroundResource(if (shift == null) R.drawable.bg_dialog_btn_neutral else R.drawable.bg_dialog_btn_delete)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(8, 0, 8, 16)
            layoutParams = params
        }
        neuBtn?.run {
            setBackgroundResource(R.drawable.bg_dialog_btn_neutral)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            val params = layoutParams as android.widget.LinearLayout.LayoutParams
            params.setMargins(8, 0, 8, 16)
            layoutParams = params
        }

        posBtn?.setOnClickListener {
            val name = editName.text.toString().trim()
            if (name.isBlank() || selectedStart.isBlank() || selectedEnd.isBlank()) {
                Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newOrUpdated = if (shift == null) {
                Shift(name = name, startTime = selectedStart, endTime = selectedEnd)
            } else {
                shift.copy(name = name, startTime = selectedStart, endTime = selectedEnd)
            }

            adminViewModel.addShift(newOrUpdated)
            Toast.makeText(context, if (shift == null) "Shift created successfully!" else "Shift updated successfully!", Toast.LENGTH_SHORT).show()
            onShiftAdded?.invoke(newOrUpdated)
            dialog.dismiss()
        }

        if (shift == null) {
            negBtn?.setOnClickListener { dialog.dismiss() }
        } else {
            negBtn?.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Shift")
                    .setMessage("Are you sure you want to delete shift '${shift.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        adminViewModel.deleteShift(shift.id)
                        Toast.makeText(context, "Shift deleted!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            neuBtn?.setOnClickListener { dialog.dismiss() }
        }
    }

    private inner class ShiftAdapter(
        private var list: List<Shift>,
        private val onEditClick: (Shift) -> Unit
    ) : RecyclerView.Adapter<ShiftAdapter.ShiftViewHolder>() {

        fun submitList(newList: List<Shift>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShiftViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_shift, parent, false)
            return ShiftViewHolder(v)
        }

        override fun onBindViewHolder(holder: ShiftViewHolder, position: Int) {
            val shift = list[position]
            holder.nameText.text = shift.name
            holder.timingsText.text = "${shift.startTime} - ${shift.endTime}"
            holder.editBtn.setOnClickListener { onEditClick(shift) }
        }

        override fun getItemCount(): Int = list.size

        inner class ShiftViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(R.id.text_shift_name)
            val timingsText: TextView = itemView.findViewById(R.id.text_shift_timings)
            val editBtn: View = itemView.findViewById(R.id.btn_edit_shift)
        }
    }
}
