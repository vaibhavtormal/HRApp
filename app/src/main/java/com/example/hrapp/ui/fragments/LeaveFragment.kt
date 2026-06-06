package com.example.hrapp.ui.fragments

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.LeaveRequest
import com.example.hrapp.databinding.FragmentLeaveBinding
import com.example.hrapp.databinding.ItemLeaveRequestBinding
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import com.example.hrapp.ui.viewmodel.LeaveViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class LeaveFragment : Fragment() {

    private var _binding: FragmentLeaveBinding? = null
    private val binding get() = _binding!!

    private lateinit var authViewModel: AuthViewModel
    private lateinit var leaveViewModel: LeaveViewModel
    private var currentUserEmployeeId = ""

    private var activeDialogView: View? = null
    private var attachedFileUri: String? = null
    private var cameraImageUri: Uri? = null
    private var cameraTempFile: File? = null

    private var currentStatusFilter = "All"
    private var filterStartDate: String? = null
    private var filterEndDate: String? = null
    private var fullLeavesList = emptyList<LeaveRequest>()

    private val pickGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleSelectedUri(it) }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleSelectedUri(it) }
    }

    private val captureImageLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraTempFile?.let { file ->
                val savedPathUri = Uri.fromFile(file).toString()
                attachedFileUri = savedPathUri
                activeDialogView?.findViewById<TextView>(R.id.dialog_text_leave_attachment_label)?.let { label ->
                    label.text = "📎 leave_camera_photo.jpg"
                    label.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
                }
                Toast.makeText(context, "Photo captured successfully!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to capture photos.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        val permission = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCameraPermissionLauncher.launch(permission)
        }
    }

    private fun launchCamera() {
        try {
            val tempFile = File(requireContext().cacheDir, "leave_temp_${System.currentTimeMillis()}.jpg")
            cameraTempFile = tempFile
            val providerUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "com.example.hrapp.fileprovider",
                tempFile
            )
            cameraImageUri = providerUri
            captureImageLauncher.launch(providerUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to launch camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSelectedUri(uri: Uri) {
        val savedPathUri = copyUriToInternalStorage(uri)
        if (savedPathUri != null) {
            attachedFileUri = savedPathUri
            val fileName = getFileNameFromUri(uri)
            activeDialogView?.findViewById<TextView>(R.id.dialog_text_leave_attachment_label)?.let { label ->
                label.text = "📎 $fileName"
                label.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
            }
            Toast.makeText(context, "Document attached successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to read document", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToInternalStorage(uri: Uri): String? {
        return try {
            val context = context ?: return null
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val ext = when (context.contentResolver.getType(uri)) {
                "application/pdf" -> "pdf"
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                else -> "bin"
            }
            val file = File(context.cacheDir, "leave_${System.currentTimeMillis()}.$ext")
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

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "document.bin"
        val cursor = context?.contentResolver?.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(true)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)

        authViewModel = ViewModelProvider(mainAct, factory)[AuthViewModel::class.java]
        leaveViewModel = ViewModelProvider(mainAct, factory)[LeaveViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        binding.recyclerLeaves.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                currentUserEmployeeId = user.employeeId
                leaveViewModel.fetchLeaves(user.employeeId)
            }
        }

        leaveViewModel.leaveBalances.observe(viewLifecycleOwner) { balances ->
            binding.textBalCasual.text = (balances["Casual Leave"] ?: 12).toString()
            binding.textBalSick.text = (balances["Sick Leave"] ?: 10).toString()
            binding.textBalEarned.text = (balances["Earned Leave"] ?: 15).toString()
        }

        leaveViewModel.leaves.observe(viewLifecycleOwner) { list ->
            fullLeavesList = list
            applyFilterAndRender()
        }

        leaveViewModel.applyResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, "Leave request submitted successfully!", Toast.LENGTH_SHORT).show()
                leaveViewModel.resetApplyResult()
            }
        }
    }

    private fun applyFilterAndRender() {
        var filteredList = fullLeavesList

        // 1. Status Filter
        if (currentStatusFilter != "All") {
            filteredList = filteredList.filter {
                (it.status ?: "Pending").equals(currentStatusFilter, ignoreCase = true)
            }
        }

        // 2. Date Range Filter
        val start = filterStartDate
        val end = filterEndDate
        if (start != null && end != null) {
            filteredList = filteredList.filter {
                it.fromDate >= start && it.fromDate <= end
            }
        }

        // 3. Sorting (New leaves at top by ID descending)
        filteredList = filteredList.sortedByDescending { it.id }

        if (filteredList.isEmpty()) {
            binding.recyclerLeaves.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerLeaves.visibility = View.VISIBLE
            binding.recyclerLeaves.adapter = LeaveAdapter(
                filteredList,
                onEditClick = { req -> showApplyLeaveDialog(req) },
                onViewAttachmentClick = { req -> showAttachmentDialog(req) },
                onCancelClick = { req -> confirmCancelLeave(req) }
            )
        }
    }

    private fun setupListeners() {
        binding.btnApplyLeave.setOnClickListener {
            showApplyLeaveDialog(null)
        }

        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            currentStatusFilter = when (checkedIds.firstOrNull()) {
                R.id.chip_filter_pending -> "Pending"
                R.id.chip_filter_approved -> "Approved"
                R.id.chip_filter_rejected -> "Rejected"
                else -> "All"
            }
            applyFilterAndRender()
        }

        binding.textFilterStartDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosenCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                filterStartDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosenCal.time)
                binding.textFilterStartDate.text = "Start: $filterStartDate"
                binding.btnClearFilters.visibility = View.VISIBLE
                applyFilterAndRender()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.textFilterEndDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosenCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                filterEndDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosenCal.time)
                binding.textFilterEndDate.text = "End: $filterEndDate"
                binding.btnClearFilters.visibility = View.VISIBLE
                applyFilterAndRender()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.btnClearFilters.setOnClickListener {
            filterStartDate = null
            filterEndDate = null
            binding.textFilterStartDate.text = "Start Date 📅"
            binding.textFilterEndDate.text = "End Date 📅"
            binding.btnClearFilters.visibility = View.GONE
            applyFilterAndRender()
        }
    }

    private fun showApplyLeaveDialog(existingRequest: LeaveRequest? = null) {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle(if (existingRequest == null) "Apply For Leave" else "Edit Leave Request")

        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_apply_leave, null)
        builder.setView(dialogView)

        activeDialogView = dialogView
        attachedFileUri = existingRequest?.attachmentUri

        val spinnerType = dialogView.findViewById<Spinner>(R.id.dialog_spinner_leave_type)
        val textFromDate = dialogView.findViewById<TextView>(R.id.dialog_text_from_date)
        val textToDate = dialogView.findViewById<TextView>(R.id.dialog_text_to_date)
        val editReason = dialogView.findViewById<EditText>(R.id.dialog_edit_leave_reason)
        val cardAttachment = dialogView.findViewById<View>(R.id.dialog_card_leave_attachment)
        val textAttachmentLabel = dialogView.findViewById<TextView>(R.id.dialog_text_leave_attachment_label)

        var fromDateStr = existingRequest?.fromDate ?: ""
        var toDateStr = existingRequest?.toDate ?: ""

        // 1. Setup Leave Categories Spinner
        val leaveTypes = arrayOf("Casual Leave", "Sick Leave", "Earned Leave")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, leaveTypes)
        spinnerType.adapter = spinnerAdapter

        existingRequest?.let { req ->
            val index = leaveTypes.indexOf(req.leaveType)
            if (index >= 0) spinnerType.setSelection(index)
            textFromDate.text = "From: ${req.fromDate}"
            textToDate.text = "To: ${req.toDate}"
            editReason.setText(req.reason)
            req.attachmentUri?.let { uri ->
                val fileName = try {
                    getFileNameFromUri(Uri.parse(uri))
                } catch (e: Exception) {
                    "Attached Document"
                }
                textAttachmentLabel.text = "📎 $fileName"
                textAttachmentLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
            }
        }

        // 2. Setup date pickers
        val calendar = Calendar.getInstance()
        textFromDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosenCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                fromDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosenCal.time)
                textFromDate.text = "From: $fromDateStr"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        textToDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosenCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                toDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosenCal.time)
                textToDate.text = "To: $toDateStr"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // 3. Setup Attachment click to choose source
        cardAttachment.setOnClickListener {
            val options = arrayOf("Take Photo (Camera)", "Choose Image (Gallery)", "Choose Document (Files)")
            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Document Source")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            checkCameraPermissionAndLaunch()
                        }
                        1 -> pickGalleryLauncher.launch("image/*")
                        2 -> pickFileLauncher.launch("*/*")
                    }
                }
                .show()
        }

        builder.setPositiveButton(if (existingRequest == null) "Submit" else "Update") { dialog, _ ->
            val reason = editReason.text.toString().trim()
            val leaveType = spinnerType.selectedItem.toString()

            if (fromDateStr.isBlank() || toDateStr.isBlank()) {
                Toast.makeText(context, "Please select complete date range", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (reason.isBlank()) {
                Toast.makeText(context, "Please enter the reason for leave", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (existingRequest == null) {
                leaveViewModel.applyLeave(currentUserEmployeeId, leaveType, fromDateStr, toDateStr, reason, attachedFileUri)
            } else {
                val updated = existingRequest.copy(
                    leaveType = leaveType,
                    fromDate = fromDateStr,
                    toDate = toDateStr,
                    reason = reason,
                    attachmentUri = attachedFileUri
                )
                leaveViewModel.updateLeave(updated)
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel", null)
        builder.setOnDismissListener {
            activeDialogView = null
        }
        builder.show()
    }

    private fun showAttachmentDialog(req: LeaveRequest) {
        val uriStr = req.attachmentUri ?: return
        val context = context ?: return

        val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Leave Attachment")

        val container = android.widget.FrameLayout(context)
        val imageView = android.widget.ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                800
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(16, 16, 16, 16)
        }

        com.bumptech.glide.Glide.with(context)
            .load(Uri.parse(uriStr))
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(imageView)

        container.addView(imageView)
        builder.setView(container)
        builder.setPositiveButton("Close", null)
        builder.setNeutralButton("Open External") { _, _ ->
            try {
                val uri = Uri.parse(uriStr)
                val viewUri = if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "com.example.hrapp.fileprovider",
                        file
                    )
                } else {
                    uri
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(viewUri, context.contentResolver.getType(viewUri) ?: "*/*")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Cannot open: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    private fun confirmCancelLeave(req: LeaveRequest) {
        val context = context ?: return
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Cancel Leave Request")
            .setMessage("Are you sure you want to cancel this leave request?")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                val cancelled = req.copy(status = "Cancelled")
                leaveViewModel.updateLeave(cancelled)
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Recycler View Adapter ---
    private class LeaveAdapter(
        private val list: List<LeaveRequest>,
        private val onEditClick: (LeaveRequest) -> Unit,
        private val onViewAttachmentClick: (LeaveRequest) -> Unit,
        private val onCancelClick: (LeaveRequest) -> Unit
    ) : RecyclerView.Adapter<LeaveAdapter.ViewHolder>() {

        class ViewHolder(val itemBinding: ItemLeaveRequestBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemLeaveRequestBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val req = list[position]
            val context = holder.itemView.context

            holder.itemBinding.textLeaveType.text = req.leaveType
            holder.itemBinding.textLeaveDates.text = "${req.fromDate} to ${req.toDate}"
            holder.itemBinding.textLeaveReason.text = "Reason: ${req.reason}"

            val status = req.status ?: "Pending"
            holder.itemBinding.textLeaveStatus.text = status
            when (status) {
                "Pending" -> {
                    holder.itemBinding.textLeaveStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textLeaveStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.warning_orange))
                    )
                    holder.itemBinding.btnEditLeave.visibility = View.VISIBLE
                    holder.itemBinding.btnEditLeave.setOnClickListener { onEditClick(req) }
                    holder.itemBinding.btnCancelLeave.visibility = View.VISIBLE
                    holder.itemBinding.btnCancelLeave.setOnClickListener { onCancelClick(req) }
                }
                "Approved" -> {
                    holder.itemBinding.textLeaveStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textLeaveStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.success_green))
                    )
                    holder.itemBinding.btnEditLeave.visibility = View.GONE
                    holder.itemBinding.btnCancelLeave.visibility = View.GONE
                }
                "Rejected" -> {
                    holder.itemBinding.textLeaveStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textLeaveStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.danger_red))
                    )
                    holder.itemBinding.btnEditLeave.visibility = View.GONE
                    holder.itemBinding.btnCancelLeave.visibility = View.GONE
                }
                else -> { // Cancelled
                    holder.itemBinding.textLeaveStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textLeaveStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_sub_dark))
                    )
                    holder.itemBinding.btnEditLeave.visibility = View.GONE
                    holder.itemBinding.btnCancelLeave.visibility = View.GONE
                }
            }

            // Document Attachment Button
            if (!req.attachmentUri.isNullOrBlank()) {
                holder.itemBinding.btnViewAttachment.visibility = View.VISIBLE
                holder.itemBinding.btnViewAttachment.setOnClickListener { onViewAttachmentClick(req) }
            } else {
                holder.itemBinding.btnViewAttachment.visibility = View.GONE
            }

            // Admin Remark
            if (!req.adminRemark.isNullOrBlank()) {
                holder.itemBinding.textLeaveAdminRemark.visibility = View.VISIBLE
                holder.itemBinding.textLeaveAdminRemark.text = "Remark: ${req.adminRemark}"
            } else {
                holder.itemBinding.textLeaveAdminRemark.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
