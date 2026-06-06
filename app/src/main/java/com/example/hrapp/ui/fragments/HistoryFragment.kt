package com.example.hrapp.ui.fragments

import android.content.res.ColorStateList
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Attendance
import com.example.hrapp.databinding.FragmentHistoryBinding
import com.example.hrapp.databinding.ItemAttendanceHistoryBinding
import com.example.hrapp.ui.viewmodel.AttendanceViewModel
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var authViewModel: AuthViewModel
    private lateinit var attendanceViewModel: AttendanceViewModel
    private var allHistoryList: List<Attendance> = emptyList()
    private var filteredList: List<Attendance> = emptyList()

    private var selectedMonthOffset = 0 // 0: June 2026, 1: May 2026, 2: April 2026, etc.

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(true)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)

        authViewModel = ViewModelProvider(mainAct, factory)[AuthViewModel::class.java]
        attendanceViewModel = ViewModelProvider(mainAct, factory)[AttendanceViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        binding.recyclerAttendanceHistory.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                attendanceViewModel.fetchHistory(user.employeeId)
            }
        }

        attendanceViewModel.attendanceHistory.observe(viewLifecycleOwner) { list ->
            allHistoryList = list
            applyFilter()
        }
    }

    private fun setupListeners() {
        binding.btnFilterMonth.setOnClickListener {
            showMonthFilterDialog()
        }

        binding.btnFilterCustom.setOnClickListener {
            showCustomDateRangeDialog()
        }
    }

    private fun applyFilter() {
        if (allHistoryList.isEmpty()) {
            showEmptyState()
            return
        }

        // Apply Month Filter based on offset
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JUNE, 1) // Base lock on June 2026 for demonstration matching local system date
        cal.add(Calendar.MONTH, -selectedMonthOffset)
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)

        val sdfParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calLog = Calendar.getInstance()

        filteredList = allHistoryList.filter { att ->
            try {
                val date = sdfParser.parse(att.date)
                if (date != null) {
                    calLog.time = date
                    calLog.get(Calendar.MONTH) == targetMonth && calLog.get(Calendar.YEAR) == targetYear
                } else false
            } catch (e: Exception) {
                false
            }
        }

        updateListUI()
    }

    private fun updateListUI() {
        if (filteredList.isEmpty()) {
            showEmptyState()
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerAttendanceHistory.visibility = View.VISIBLE
            binding.recyclerAttendanceHistory.adapter = HistoryAdapter(filteredList)
        }
    }

    private fun showEmptyState() {
        binding.recyclerAttendanceHistory.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
    }

    private fun showMonthFilterDialog() {
        val months = arrayOf("June 2026", "May 2026", "April 2026", "March 2026", "February 2026")
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Month Filter")
            .setItems(months) { dialog, which ->
                selectedMonthOffset = which
                binding.textFilterMonth.text = months[which]
                applyFilter()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomDateRangeDialog() {
        val calendar = Calendar.getInstance()
        var startDateStr = ""
        var endDateStr = ""

        val dateSetListenerEnd = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            val endCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(endCal.time)
            
            // Perform date range query
            if (startDateStr.isNotBlank() && endDateStr.isNotBlank()) {
                val sdfParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                filteredList = allHistoryList.filter { att ->
                    try {
                        val logDate = sdfParser.parse(att.date)
                        val start = sdfParser.parse(startDateStr)
                        val end = sdfParser.parse(endDateStr)
                        if (logDate != null && start != null && end != null) {
                            !logDate.before(start) && !logDate.after(end)
                        } else false
                    } catch (e: Exception) {
                        false
                    }
                }
                binding.textFilterMonth.text = "$startDateStr to $endDateStr"
                updateListUI()
            }
        }

        val dateSetListenerStart = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            val startCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            startDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(startCal.time)
            
            DatePickerDialog(requireContext(), dateSetListenerEnd, year, month, dayOfMonth).apply {
                setTitle("Select End Date")
                show()
            }
        }

        DatePickerDialog(
            requireContext(),
            dateSetListenerStart,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle("Select Start Date")
            show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Recycler View Adapter ---
    private class HistoryAdapter(
        private val list: List<Attendance>
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        class ViewHolder(val itemBinding: ItemAttendanceHistoryBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemAttendanceHistoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = list[position]
            val context = holder.itemView.context

            // Parse Date
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val d = parser.parse(log.date)
                if (d != null) {
                    val dayStr = SimpleDateFormat("dd", Locale.getDefault()).format(d)
                    val monthStr = SimpleDateFormat("MMM", Locale.getDefault()).format(d).uppercase()
                    holder.itemBinding.textItemDay.text = dayStr
                    holder.itemBinding.textItemMonth.text = monthStr
                }
            } catch (e: Exception) {
                holder.itemBinding.textItemDay.text = "??"
                holder.itemBinding.textItemMonth.text = "LOG"
            }

            // Punch times
            val outTime = log.punchOutTime ?: "Present"
            holder.itemBinding.textItemTimes.text = "In: ${log.punchInTime} | Out: $outTime"

            // Commute Details
            if (log.vehicleType == "No Vehicle") {
                holder.itemBinding.textItemDetails.text = "Commute: No Vehicle"
            } else {
                val km = if (log.punchOutReading != null) {
                    (log.punchOutReading - log.punchInReading).toString() + " KM"
                } else {
                    "Pending Out"
                }
                holder.itemBinding.textItemDetails.text = "Commute: ${log.vehicleType} | $km"
            }

            // Status Badge
            holder.itemBinding.textItemStatus.text = log.status
            when (log.status) {
                "Present" -> {
                    holder.itemBinding.textItemStatus.text = "On Time"
                    holder.itemBinding.textItemStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textItemStatus.setBackgroundResource(R.drawable.badge_role_admin) // Purple present
                }
                "Late" -> {
                    holder.itemBinding.textItemStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textItemStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.warning_orange))
                    )
                }
                "Absent" -> {
                    holder.itemBinding.textItemStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textItemStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.danger_red))
                    )
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
