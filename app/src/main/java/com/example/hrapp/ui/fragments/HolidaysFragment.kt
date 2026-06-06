package com.example.hrapp.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Holiday
import com.example.hrapp.databinding.FragmentHolidaysBinding
import com.example.hrapp.databinding.ItemHolidayBinding
import com.example.hrapp.ui.viewmodel.DashboardViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class HolidaysFragment : Fragment() {

    private var _binding: FragmentHolidaysBinding? = null
    private val binding get() = _binding!!

    private lateinit var dashboardViewModel: DashboardViewModel
    private var holidaysList: List<Holiday> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHolidaysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        dashboardViewModel = ViewModelProvider(mainAct, factory)[DashboardViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()

        dashboardViewModel.loadDashboardData()
    }

    private fun setupRecyclerView() {
        binding.recyclerHolidaysList.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        dashboardViewModel.holidays.observe(viewLifecycleOwner) { list ->
            holidaysList = list
            binding.recyclerHolidaysList.adapter = HolidayListAdapter(list)
            
            // Check today's date initially in Calendar View
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            checkHolidayForDate(todayStr)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val mainAct = activity as MainActivity
            mainAct.setBottomNavigationVisibility(true)
            mainAct.replaceFragment(DashboardFragment(), addToBackStack = false)
        }

        binding.btnToggleCalendar.setOnClickListener {
            showCalendarContainer()
        }

        binding.btnToggleList.setOnClickListener {
            showListContainer()
        }

        binding.calendarView.setOnDateChangeListener { _: CalendarView, year: Int, month: Int, dayOfMonth: Int ->
            val cal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            val chosenDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            checkHolidayForDate(chosenDateStr)
        }
    }

    private fun showCalendarContainer() {
        binding.btnToggleCalendar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
        binding.btnToggleCalendar.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        
        binding.btnToggleList.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.transparent))
        binding.btnToggleList.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        // Outlined border check
        
        binding.recyclerHolidaysList.visibility = View.GONE
        binding.containerCalendar.visibility = View.VISIBLE
    }

    private fun showListContainer() {
        binding.btnToggleList.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
        binding.btnToggleList.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        
        binding.btnToggleCalendar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.transparent))
        binding.btnToggleCalendar.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))

        binding.containerCalendar.visibility = View.GONE
        binding.recyclerHolidaysList.visibility = View.VISIBLE
    }

    private fun checkHolidayForDate(dateStr: String) {
        val holiday = holidaysList.firstOrNull { it.date == dateStr }
        binding.textSelectedDateLabel.text = "DATE LOG DETAILS: $dateStr"
        if (holiday != null) {
            binding.textCalendarHolidayDetails.text = "🎉 ${holiday.name} (${holiday.type} Holiday)"
            binding.textCalendarHolidayDetails.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_light))
        } else {
            binding.textCalendarHolidayDetails.text = "No company holidays on this date."
            binding.textCalendarHolidayDetails.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Recycler View Adapter ---
    private class HolidayListAdapter(
        private val list: List<Holiday>
    ) : RecyclerView.Adapter<HolidayListAdapter.ViewHolder>() {

        class ViewHolder(val itemBinding: ItemHolidayBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemHolidayBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val holiday = list[position]
            val context = holder.itemView.context

            holder.itemBinding.textHolidayName.text = holiday.name
            
            // Format date presentation
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val d = parser.parse(holiday.date)
                if (d != null) {
                    val formatted = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(d)
                    holder.itemBinding.textHolidayDate.text = formatted
                }
            } catch (e: Exception) {
                holder.itemBinding.textHolidayDate.text = holiday.date
            }

            holder.itemBinding.textHolidayType.text = holiday.type
            when (holiday.type) {
                "National" -> {
                    holder.itemBinding.textHolidayType.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textHolidayType.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.danger_red))
                    )
                }
                "State" -> {
                    holder.itemBinding.textHolidayType.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textHolidayType.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.warning_orange))
                    )
                }
                "Company" -> {
                    holder.itemBinding.textHolidayType.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textHolidayType.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.info_blue))
                    )
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
