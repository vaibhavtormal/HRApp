package com.example.hrapp.ui.fragments

import android.content.res.ColorStateList
import android.app.DatePickerDialog
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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.RegularizationRequest
import com.example.hrapp.databinding.FragmentRegularizationBinding
import com.example.hrapp.databinding.ItemRegularizationBinding
import com.example.hrapp.ui.viewmodel.AttendanceViewModel
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class RegularizationFragment : Fragment() {

    private var _binding: FragmentRegularizationBinding? = null
    private val binding get() = _binding!!

    private lateinit var authViewModel: AuthViewModel
    private lateinit var attendanceViewModel: AttendanceViewModel
    private var currentUserEmployeeId = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegularizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)

        authViewModel = ViewModelProvider(mainAct, factory)[AuthViewModel::class.java]
        attendanceViewModel = ViewModelProvider(mainAct, factory)[AttendanceViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        binding.recyclerRegularizations.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                currentUserEmployeeId = user.employeeId
                attendanceViewModel.fetchRegularizations(user.employeeId)
            }
        }

        attendanceViewModel.regularizations.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                binding.recyclerRegularizations.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.recyclerRegularizations.visibility = View.VISIBLE
                binding.recyclerRegularizations.adapter = RegularizationAdapter(list)
            }
        }

        attendanceViewModel.regularizationResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, "Correction request submitted successfully!", Toast.LENGTH_SHORT).show()
                attendanceViewModel.resetRegularizationResult()
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val mainAct = activity as MainActivity
            mainAct.setBottomNavigationVisibility(true)
            mainAct.replaceFragment(DashboardFragment(), addToBackStack = false)
        }

        binding.btnRaiseRegularization.setOnClickListener {
            showNewRegularizationDialog()
        }
    }

    private fun showNewRegularizationDialog() {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Raise Correction Request")

        // Create Dialog input view dynamically
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_new_regularization, null)
        builder.setView(dialogView)

        val textDate = dialogView.findViewById<TextView>(R.id.dialog_text_date)
        val spinnerReason = dialogView.findViewById<Spinner>(R.id.dialog_spinner_reason)
        val editRemarks = dialogView.findViewById<EditText>(R.id.dialog_edit_remarks)
        val cardAttachment = dialogView.findViewById<View>(R.id.dialog_card_attachment)
        val textAttachmentLabel = dialogView.findViewById<TextView>(R.id.dialog_text_attachment_label)

        var selectedDateStr = ""
        var attachmentLogged = false

        // 1. Date Picker
        textDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosenCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosenCal.time)
                textDate.text = "Selected Date: $selectedDateStr"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // 2. Reason Spinner Setup
        val reasons = arrayOf("Forgot to Punch In", "On-site Client Visit", "Device Malfunction", "Internet Connectivity Outage")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, reasons)
        spinnerReason.adapter = spinnerAdapter

        // 3. Document attachment mock
        cardAttachment.setOnClickListener {
            attachmentLogged = true
            textAttachmentLabel.text = "📎 Document Logged Successfully"
            textAttachmentLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
            Toast.makeText(context, "Document file attached.", Toast.LENGTH_SHORT).show()
        }

        builder.setPositiveButton("Submit") { dialog, _ ->
            val remarks = editRemarks.text.toString().trim()
            val reason = spinnerReason.selectedItem.toString()

            if (selectedDateStr.isBlank()) {
                Toast.makeText(context, "Please select the date", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (remarks.isBlank()) {
                Toast.makeText(context, "Please enter remarks", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val mockImageUri = if (attachmentLogged) "content://mock/supporting_doc.png" else null
            attendanceViewModel.applyRegularization(currentUserEmployeeId, selectedDateStr, reason, remarks, mockImageUri)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Recycler View Adapter ---
    private class RegularizationAdapter(
        private val list: List<RegularizationRequest>
    ) : RecyclerView.Adapter<RegularizationAdapter.ViewHolder>() {

        class ViewHolder(val itemBinding: ItemRegularizationBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemRegularizationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val req = list[position]
            val context = holder.itemView.context

            holder.itemBinding.textRegDate.text = "Date: ${req.date}"
            holder.itemBinding.textRegReason.text = "Reason: ${req.reason}"
            holder.itemBinding.textRegRemarks.text = "Remarks: ${req.remarks}"

            val status = req.status ?: "Pending"
            holder.itemBinding.textRegStatus.text = status
            when (status) {
                "Pending" -> {
                    holder.itemBinding.textRegStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textRegStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.warning_orange))
                    )
                }
                "Approved" -> {
                    holder.itemBinding.textRegStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textRegStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.success_green))
                    )
                }
                "Rejected" -> {
                    holder.itemBinding.textRegStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textRegStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.danger_red))
                    )
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
