package com.example.hrapp.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Employee
import com.example.hrapp.data.db.Expense
import com.example.hrapp.data.db.LeaveRequest
import com.example.hrapp.data.db.RegularizationRequest
import com.example.hrapp.databinding.FragmentAdminApprovalsBinding
import com.example.hrapp.databinding.ItemAdminApprovalCardBinding
import com.example.hrapp.ui.viewmodel.AdminViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import java.text.NumberFormat
import java.util.*

class AdminApprovalsFragment : Fragment() {

    private var _binding: FragmentAdminApprovalsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adminViewModel: AdminViewModel
    
    private var activeTab = 0 // 0: Leaves, 1: Expenses, 2: Corrections
    private var employeesMap = mapOf<String, Employee>()

    private var leavesList = emptyList<LeaveRequest>()
    private var expensesList = emptyList<Expense>()
    private var regularizationsList = emptyList<RegularizationRequest>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminApprovalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        adminViewModel = ViewModelProvider(mainAct, factory)[AdminViewModel::class.java]

        activeTab = arguments?.getInt("start_tab_index") ?: 0

        setupRecyclerView()
        setupObservers()
        setupListeners()

        adminViewModel.loadAdminData()
    }

    private fun setupRecyclerView() {
        binding.recyclerApprovals.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        adminViewModel.allEmployees.observe(viewLifecycleOwner) { employees ->
            employeesMap = employees.associateBy { it.employeeId }
            renderActiveList()
        }

        adminViewModel.pendingLeaves.observe(viewLifecycleOwner) { list ->
            leavesList = list
            if (activeTab == 0) renderActiveList()
        }

        adminViewModel.pendingExpenses.observe(viewLifecycleOwner) { list ->
            expensesList = list
            if (activeTab == 1) renderActiveList()
        }

        adminViewModel.pendingRegularizations.observe(viewLifecycleOwner) { list ->
            regularizationsList = list
            if (activeTab == 2) renderActiveList()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val mainAct = activity as MainActivity
            mainAct.replaceFragment(AdminDashboardFragment(), addToBackStack = false)
        }

        binding.tabPendingLeaves.setOnClickListener { selectTab(0) }
        binding.tabPendingExpenses.setOnClickListener { selectTab(1) }
        binding.tabPendingRegs.setOnClickListener { selectTab(2) }
    }

    private fun selectTab(tabIndex: Int) {
        activeTab = tabIndex
        val context = requireContext()
        val activeColor = ContextCompat.getColor(context, R.color.white)
        val inactiveColor = ContextCompat.getColor(context, R.color.text_sub_dark)

        // Reset text styles
        binding.tabPendingLeaves.setTextColor(inactiveColor)
        binding.tabPendingExpenses.setTextColor(inactiveColor)
        binding.tabPendingRegs.setTextColor(inactiveColor)

        binding.tabPendingLeaves.background = null
        binding.tabPendingExpenses.background = null
        binding.tabPendingRegs.background = null

        val activeTabBackground = ContextCompat.getDrawable(context, R.drawable.badge_role_admin)

        when (tabIndex) {
            0 -> {
                binding.tabPendingLeaves.setTextColor(activeColor)
                binding.tabPendingLeaves.background = activeTabBackground
            }
            1 -> {
                binding.tabPendingExpenses.setTextColor(activeColor)
                binding.tabPendingExpenses.background = activeTabBackground
            }
            2 -> {
                binding.tabPendingRegs.setTextColor(activeColor)
                binding.tabPendingRegs.background = activeTabBackground
            }
        }

        renderActiveList()
    }

    private fun showRemarkDialog(title: String, onSubmit: (String) -> Unit) {
        val context = context ?: return
        val builder = androidx.appcompat.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle(title)

        val input = android.widget.EditText(context).apply {
            hint = "Enter remark/comments (optional)..."
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_sub_dark))
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.border_dark)
            setPadding(32, 32, 32, 32)
        }

        val container = android.widget.FrameLayout(context).apply {
            setPadding(40, 20, 40, 20)
            addView(input)
        }

        builder.setView(container)
        builder.setPositiveButton("Submit") { _, _ ->
            onSubmit(input.text.toString().trim())
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showAttachmentDialog(uriStr: String) {
        val context = context ?: return
        val builder = androidx.appcompat.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
            .load(android.net.Uri.parse(uriStr))
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(imageView)

        container.addView(imageView)
        builder.setView(container)
        builder.setPositiveButton("Close", null)
        builder.setNeutralButton("Open External") { _, _ ->
            try {
                val uri = android.net.Uri.parse(uriStr)
                val viewUri = if (uri.scheme == "file") {
                    val file = java.io.File(uri.path ?: "")
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

    private fun renderActiveList() {
        when (activeTab) {
            0 -> {
                if (leavesList.isEmpty()) {
                    showEmptyState()
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.recyclerApprovals.visibility = View.VISIBLE
                    binding.recyclerApprovals.adapter = ApprovalsAdapter(
                        type = 0,
                        leaves = leavesList,
                        expenses = emptyList(),
                        regs = emptyList(),
                        empMap = employeesMap,
                        onApprove = { id -> 
                            showRemarkDialog("Approve Leave") { remark ->
                                adminViewModel.approveLeave(id, remark)
                                Toast.makeText(context, "Leave Approved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onReject = { id -> 
                            showRemarkDialog("Reject Leave") { remark ->
                                adminViewModel.rejectLeave(id, remark)
                                Toast.makeText(context, "Leave Rejected", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onViewAttachmentClick = { uri ->
                            showAttachmentDialog(uri)
                        }
                    )
                }
            }
            1 -> {
                if (expensesList.isEmpty()) {
                    showEmptyState()
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.recyclerApprovals.visibility = View.VISIBLE
                    binding.recyclerApprovals.adapter = ApprovalsAdapter(
                        type = 1,
                        leaves = emptyList(),
                        expenses = expensesList,
                        regs = emptyList(),
                        empMap = employeesMap,
                        onApprove = { id -> 
                            adminViewModel.approveExpense(id)
                            Toast.makeText(context, "Expense Approved", Toast.LENGTH_SHORT).show()
                        },
                        onReject = { id -> 
                            adminViewModel.rejectExpense(id)
                            Toast.makeText(context, "Expense Rejected", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            2 -> {
                if (regularizationsList.isEmpty()) {
                    showEmptyState()
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.recyclerApprovals.visibility = View.VISIBLE
                    binding.recyclerApprovals.adapter = ApprovalsAdapter(
                        type = 2,
                        leaves = emptyList(),
                        expenses = emptyList(),
                        regs = regularizationsList,
                        empMap = employeesMap,
                        onApprove = { id -> 
                            adminViewModel.approveRegularization(id)
                            Toast.makeText(context, "Attendance Correction Approved", Toast.LENGTH_SHORT).show()
                        },
                        onReject = { id -> 
                            adminViewModel.rejectRegularization(id)
                            Toast.makeText(context, "Attendance Correction Rejected", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    private fun showEmptyState() {
        binding.recyclerApprovals.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Approvals Recycler Adapter ---
    private class ApprovalsAdapter(
        private val type: Int, // 0: Leaves, 1: Expenses, 2: Corrections
        private val leaves: List<LeaveRequest>,
        private val expenses: List<Expense>,
        private val regs: List<RegularizationRequest>,
        private val empMap: Map<String, Employee>,
        private val onApprove: (Long) -> Unit,
        private val onReject: (Long) -> Unit,
        private val onViewAttachmentClick: ((String) -> Unit)? = null
    ) : RecyclerView.Adapter<ApprovalsAdapter.ViewHolder>() {

        class ViewHolder(val itemBinding: ItemAdminApprovalCardBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemAdminApprovalCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // Hide attachment viewing button by default
            holder.itemBinding.btnApprViewAttachment.visibility = View.GONE

            when (type) {
                0 -> {
                    val leave = leaves[position]
                    val empName = empMap[leave.employeeId]?.name ?: "Employee"
                    val empId = empMap[leave.employeeId]?.employeeId ?: "EMP"
                    holder.itemBinding.textApprEmpInfo.text = "$empName (ID: $empId)"
                    holder.itemBinding.textApprTitle.text = leave.leaveType
                    holder.itemBinding.textApprSubtitle.text = "Dates: ${leave.fromDate} to ${leave.toDate}"
                    holder.itemBinding.textApprDetails.text = "Reason: ${leave.reason}"
                    
                    holder.itemBinding.btnApprApprove.setOnClickListener { onApprove(leave.id) }
                    holder.itemBinding.btnApprReject.setOnClickListener { onReject(leave.id) }

                    if (!leave.attachmentUri.isNullOrBlank()) {
                        holder.itemBinding.btnApprViewAttachment.visibility = View.VISIBLE
                        holder.itemBinding.btnApprViewAttachment.setOnClickListener {
                            onViewAttachmentClick?.invoke(leave.attachmentUri)
                        }
                    }
                }
                1 -> {
                    val exp = expenses[position]
                    val empName = empMap[exp.employeeId]?.name ?: "Employee"
                    val empId = empMap[exp.employeeId]?.employeeId ?: "EMP"
                    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
                    
                    holder.itemBinding.textApprEmpInfo.text = "$empName (ID: $empId)"
                    holder.itemBinding.textApprTitle.text = "${exp.category} Expense Claim"
                    holder.itemBinding.textApprSubtitle.text = "Claim Amount: ${format.format(exp.amount)} | Date: ${exp.expenseDate}"
                    holder.itemBinding.textApprDetails.text = "Description: ${exp.description}"
                    
                    holder.itemBinding.btnApprApprove.setOnClickListener { onApprove(exp.id) }
                    holder.itemBinding.btnApprReject.setOnClickListener { onReject(exp.id) }
                }
                2 -> {
                    val reg = regs[position]
                    val empName = empMap[reg.employeeId]?.name ?: "Employee"
                    val empId = empMap[reg.employeeId]?.employeeId ?: "EMP"
                    
                    holder.itemBinding.textApprEmpInfo.text = "$empName (ID: $empId)"
                    holder.itemBinding.textApprTitle.text = "Attendance Regularization"
                    holder.itemBinding.textApprSubtitle.text = "Request Date: ${reg.date} | Reason: ${reg.reason}"
                    holder.itemBinding.textApprDetails.text = "Explanation Remarks: ${reg.remarks}"
                    
                    holder.itemBinding.btnApprApprove.setOnClickListener { onApprove(reg.id) }
                    holder.itemBinding.btnApprReject.setOnClickListener { onReject(reg.id) }
                }
            }
        }

        override fun getItemCount(): Int {
            return when (type) {
                0 -> leaves.size
                1 -> expenses.size
                2 -> regs.size
                else -> 0
            }
        }
    }
}
