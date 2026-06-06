package com.example.hrapp.ui.fragments

import android.app.DatePickerDialog
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Employee
import com.example.hrapp.databinding.FragmentStaffListBinding
import com.example.hrapp.databinding.ItemStaffBinding
import com.example.hrapp.ui.viewmodel.AdminViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory

class StaffListFragment : Fragment() {

    private var _binding: FragmentStaffListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adminViewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStaffListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        adminViewModel = ViewModelProvider(mainAct, factory)[AdminViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()

        adminViewModel.loadAdminData()
    }

    private fun setupRecyclerView() {
        binding.recyclerStaff.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        adminViewModel.allEmployees.observe(viewLifecycleOwner) { list ->
            val sortedList = list.sortedBy { it.name }
            binding.recyclerStaff.adapter = StaffAdapter(sortedList)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val mainAct = activity as MainActivity
            mainAct.replaceFragment(AdminDashboardFragment(), addToBackStack = false)
        }
    }

    private fun showEditStaffDialog(employee: Employee) {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Edit Staff: ${employee.name}")

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

        editJoiningDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val parts = employee.joiningDate.split("-")
            if (parts.size == 3) {
                parts[0].toIntOrNull()?.let { y ->
                    parts[1].toIntOrNull()?.let { m ->
                        parts[2].toIntOrNull()?.let { d ->
                            cal.set(y, m - 1, d)
                        }
                    }
                }
            }
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosen.time)
                editJoiningDate.setText(dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        editBirthday.setOnClickListener {
            val cal = Calendar.getInstance()
            val parts = employee.birthday.split("-")
            if (parts.size == 3) {
                parts[0].toIntOrNull()?.let { y ->
                    parts[1].toIntOrNull()?.let { m ->
                        parts[2].toIntOrNull()?.let { d ->
                            cal.set(y, m - 1, d)
                        }
                    }
                }
            } else {
                cal.set(1998, 0, 1)
            }
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosen.time)
                editBirthday.setText(dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
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

        // Add current employee values if missing
        if (employee.employeeId.startsWith("EMPA", ignoreCase = true)) {
            if (employee.designation.isNotBlank() && !apprenticeDesignations.contains(employee.designation)) {
                apprenticeDesignations.add(employee.designation)
            }
        } else {
            if (employee.designation.isNotBlank() && !permanentDesignations.contains(employee.designation)) {
                permanentDesignations.add(employee.designation)
            }
        }
        if (employee.department.isNotBlank() && !departmentsList.contains(employee.department)) {
            departmentsList.add(employee.department)
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

        // 4. Setup Reporting Managers list
        val managersList = ArrayList<String>().apply { add("None") }
        existingEmployees.filter { 
            it.designation.contains("Manager", ignoreCase = true) || it.role.equals("ADMIN", ignoreCase = true)
        }.map { 
            "${it.name} (${it.employeeId})"
        }.distinct().let { 
            managersList.addAll(it)
        }
        spinnerReporting.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, managersList)

        // Pre-fill fields
        editName.setText(employee.name)
        val displayEmail = if (employee.email.endsWith("@company.local")) "" else employee.email
        editEmail.setText(displayEmail)
        editEmail.isEnabled = employee.email.endsWith("@company.local") || employee.email.isBlank()
        editEmpId.setText(employee.employeeId)
        editMobile.setText(employee.mobile)
        editJoiningDate.setText(employee.joiningDate)
        editBirthday.setText(employee.birthday)
        spinnerRole.setSelection(roles.indexOf(employee.role).coerceAtLeast(0))

        // Set Employee Type selection
        val initialType = if (employee.employeeId.startsWith("EMPA", ignoreCase = true)) "Apprentice" else "Permanent"
        spinnerType.setSelection(types.indexOf(initialType).coerceAtLeast(0))

        // Swap designations spinner and select correct design
        if (initialType == "Apprentice") {
            spinnerDesign.adapter = designAdapterAppr
            spinnerDesign.setSelection(dispApprenticeDesignations.indexOf(employee.designation).coerceAtLeast(0))
        } else {
            spinnerDesign.adapter = designAdapterPerm
            spinnerDesign.setSelection(dispPermanentDesignations.indexOf(employee.designation).coerceAtLeast(0))
        }
        
        // Select correct department
        spinnerDept.setSelection(dispDepartments.indexOf(employee.department).coerceAtLeast(0))

        // Select correct reporting manager
        val managerDisplay = existingEmployees.firstOrNull { it.name.equals(employee.reportingManager, ignoreCase = true) }?.let {
            "${it.name} (${it.employeeId})"
        } ?: "None"
        spinnerReporting.setSelection(managersList.indexOf(managerDisplay).coerceAtLeast(0))

        // Type selection listener
        spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val type = types[position]
                if (type == "Apprentice") {
                    spinnerDesign.adapter = designAdapterAppr
                    spinnerDesign.setSelection(dispApprenticeDesignations.indexOf(employee.designation).coerceAtLeast(0))
                } else {
                    spinnerDesign.adapter = designAdapterPerm
                    spinnerDesign.setSelection(dispPermanentDesignations.indexOf(employee.designation).coerceAtLeast(0))
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

        builder.setPositiveButton("Save Changes") { dialog, _ ->
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

            if (name.isBlank() || empId.isBlank() || design.isBlank() || dept.isBlank() || joiningDateVal.isBlank() || birthdayVal.isBlank()) {
                Toast.makeText(context, "All required fields are mandatory", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (email.isBlank() && mobile.isBlank()) {
                Toast.makeText(context, "Either Corporate Email or Mobile Number must be provided", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val finalEmail = email.ifBlank { "${empId.lowercase()}@company.local" }

            if (design == "+ Add New Designation..." || dept == "+ Add New Department...") {
                Toast.makeText(context, "Please select or create a valid designation/department", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val repManager = if (repManagerRaw == "None") {
                ""
            } else {
                repManagerRaw.substringBefore(" (").trim()
            }

            val updatedEmployee = employee.copy(
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
                role = role
            )
            adminViewModel.updateEmployee(updatedEmployee)
            Toast.makeText(context, "Staff details updated successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Staff Recycler Adapter ---
    private inner class StaffAdapter(
        private val list: List<Employee>
    ) : RecyclerView.Adapter<StaffAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: ItemStaffBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemStaffBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val emp = list[position]
            val context = holder.itemView.context
            val mainAct = activity as MainActivity
            val currentLoggedInId = mainAct.repository.prefs.loggedInEmployeeId

            holder.itemBinding.textStaffName.text = emp.name
            holder.itemBinding.textStaffIdRole.text = "${emp.employeeId} | Role: ${emp.role}"
            holder.itemBinding.textStaffDeptDesignation.text = "${emp.department} - ${emp.designation}"
            holder.itemBinding.textStaffEmail.text = if (emp.email.endsWith("@company.local")) "No Email (OTP Login Only)" else emp.email

            // Access Switch logic
            holder.itemBinding.switchLoginAccess.setOnCheckedChangeListener(null)
            holder.itemBinding.switchLoginAccess.isChecked = emp.loginEnabled

            if (emp.email == "vaibhavtormal82@gmail.com") {
                holder.itemBinding.switchLoginAccess.isEnabled = false
                holder.itemBinding.textAccessStatus.text = "Owner (Locked)"
                if (currentLoggedInId == 9999L) {
                    holder.itemBinding.btnEditStaff.visibility = View.VISIBLE
                } else {
                    holder.itemBinding.btnEditStaff.visibility = View.GONE
                }
            } else if (emp.id == currentLoggedInId) {
                // Cannot block yourself
                holder.itemBinding.switchLoginAccess.isEnabled = false
                holder.itemBinding.textAccessStatus.text = "Active Admin"
                holder.itemBinding.btnEditStaff.visibility = View.VISIBLE
            } else {
                holder.itemBinding.switchLoginAccess.isEnabled = true
                holder.itemBinding.textAccessStatus.text = if (emp.loginEnabled) "Login Allowed" else "Login Disabled"
                holder.itemBinding.btnEditStaff.visibility = View.VISIBLE
            }

            holder.itemBinding.switchLoginAccess.setOnCheckedChangeListener { _, isChecked ->
                val updatedEmp = emp.copy(loginEnabled = isChecked)
                adminViewModel.updateEmployee(updatedEmp)
                val statusText = if (isChecked) "Login access enabled for ${emp.name}" else "Login access disabled for ${emp.name}"
                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
            }

            holder.itemBinding.btnEditStaff.setOnClickListener {
                showEditStaffDialog(emp)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
