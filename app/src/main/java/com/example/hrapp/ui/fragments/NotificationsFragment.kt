package com.example.hrapp.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Notification
import com.example.hrapp.databinding.FragmentNotificationsBinding
import com.example.hrapp.databinding.ItemNotificationBinding
import com.example.hrapp.ui.viewmodel.DashboardViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var dashboardViewModel: DashboardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
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
        dashboardViewModel.markNotificationsRead() // Mark read on open
    }

    private fun setupRecyclerView() {
        binding.recyclerNotifications.layoutManager = LinearLayoutManager(context)

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val adapter = binding.recyclerNotifications.adapter as? NotificationAdapter
                if (adapter != null) {
                    val notif = adapter.getNotificationAt(position)
                    dashboardViewModel.deleteNotification(notif.id)
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerNotifications)
    }

    private fun setupObservers() {
        dashboardViewModel.notifications.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                binding.recyclerNotifications.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.recyclerNotifications.visibility = View.VISIBLE
                binding.recyclerNotifications.adapter = NotificationAdapter(list)
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val mainAct = activity as MainActivity
            mainAct.setBottomNavigationVisibility(true)
            mainAct.replaceFragment(DashboardFragment(), addToBackStack = false)
        }
        binding.btnClearAll.setOnClickListener {
            dashboardViewModel.clearAllNotifications()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Recycler View Adapter ---
    private class NotificationAdapter(
        private val list: List<Notification>
    ) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        fun getNotificationAt(position: Int): Notification = list[position]

        class ViewHolder(val itemBinding: ItemNotificationBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val notif = list[position]
            val context = holder.itemView.context

            holder.itemBinding.textNotifTitle.text = notif.title
            holder.itemBinding.textNotifDesc.text = notif.description
            holder.itemBinding.textNotifTime.text = getRelativeTime(notif.timestamp)

             // Category specific colors and icons
            when (notif.type) {
                "Announcement" -> {
                    holder.itemBinding.imgNotifIcon.setImageResource(R.drawable.ic_notification)
                    holder.itemBinding.cardNotifIconBg.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.primary_light) and 0x00FFFFFF or 0x1A000000
                    )
                    holder.itemBinding.imgNotifIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.primary_light)
                }
                "Meeting" -> {
                    holder.itemBinding.imgNotifIcon.setImageResource(R.drawable.ic_history)
                    holder.itemBinding.cardNotifIconBg.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.warning_orange) and 0x00FFFFFF or 0x1A000000
                    )
                    holder.itemBinding.imgNotifIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.warning_orange)
                }
                "Holiday" -> {
                    holder.itemBinding.imgNotifIcon.setImageResource(R.drawable.ic_leave)
                    holder.itemBinding.cardNotifIconBg.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.accent_gold) and 0x00FFFFFF or 0x1A000000
                    )
                    holder.itemBinding.imgNotifIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.accent_gold)
                }
                "Learning" -> {
                    holder.itemBinding.imgNotifIcon.setImageResource(R.drawable.ic_play)
                    holder.itemBinding.cardNotifIconBg.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.primary) and 0x00FFFFFF or 0x1A000000
                    )
                    holder.itemBinding.imgNotifIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.primary)
                }
                else -> {
                    holder.itemBinding.imgNotifIcon.setImageResource(R.drawable.ic_check_circle)
                    holder.itemBinding.cardNotifIconBg.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.success_green) and 0x00FFFFFF or 0x1A000000
                    )
                    holder.itemBinding.imgNotifIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.success_green)
                }
            }

            // Redirect logic when notification is clicked
            holder.itemView.setOnClickListener {
                val mainAct = context as? MainActivity
                when (notif.type) {
                    "Learning" -> {
                        val desc = notif.description
                        val marker = "Link: "
                        if (desc.contains(marker)) {
                            val linkPart = desc.substringAfter(marker).substringBefore("\n").trim()
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(linkPart)
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                mainAct?.replaceFragment(LearningFragment())
                            }
                        } else {
                            mainAct?.replaceFragment(LearningFragment())
                        }
                    }
                    "Leave" -> {
                        mainAct?.setBottomNavigationVisibility(true)
                        mainAct?.selectTab(R.id.tab_leaves)
                        mainAct?.replaceFragment(LeaveFragment(), addToBackStack = false)
                    }
                    "Expense" -> {
                        mainAct?.setBottomNavigationVisibility(true)
                        mainAct?.selectTab(R.id.tab_expenses)
                        mainAct?.replaceFragment(ExpenseFragment(), addToBackStack = false)
                    }
                    "Holiday" -> {
                        mainAct?.replaceFragment(HolidaysFragment())
                    }
                    "Announcement", "Meeting" -> {
                        // Standard announcement - go to dashboard
                        mainAct?.setBottomNavigationVisibility(true)
                        mainAct?.selectTab(R.id.tab_dashboard)
                        mainAct?.replaceFragment(DashboardFragment(), addToBackStack = false)
                    }
                }
            }
        }

        private fun getRelativeTime(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            val minutes = diff / (1000 * 60)
            val hours = minutes / 60
            val days = hours / 24

            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "$minutes mins ago"
                hours < 24 -> "$hours hours ago"
                else -> "$days days ago"
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
