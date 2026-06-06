package com.example.hrapp.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Banner
import com.example.hrapp.data.db.Employee
import com.example.hrapp.data.db.Video
import com.example.hrapp.databinding.FragmentDashboardBinding
import com.example.hrapp.databinding.ItemVideoCardBinding
import com.example.hrapp.ui.viewmodel.AttendanceViewModel
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.DashboardViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var authViewModel: AuthViewModel
    private lateinit var attendanceViewModel: AttendanceViewModel
    private lateinit var dashboardViewModel: DashboardViewModel

    private var bannerJob: Job? = null
    private var bannersList: List<Banner> = emptyList()
    private var currentBannerIndex = 0

    private var currentUser: Employee? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure bottom navigation is visible
        (activity as? MainActivity)?.setBottomNavigationVisibility(true)
        (activity as? MainActivity)?.selectTab(R.id.tab_dashboard)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)

        authViewModel = ViewModelProvider(mainAct, factory)[AuthViewModel::class.java]
        attendanceViewModel = ViewModelProvider(mainAct, factory)[AttendanceViewModel::class.java]
        dashboardViewModel = ViewModelProvider(mainAct, factory)[DashboardViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()

        dashboardViewModel.loadDashboardData()
    }

    private fun setupRecyclerView() {
        binding.recyclerVideosDashboard.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupObservers() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                currentUser = user
                binding.textUserName.text = user.name
                
                // Set Profile photo mock or load (use fallback placeholder icon if url empty)
                if (user.profilePhotoUrl.isNotBlank()) {
                    com.bumptech.glide.Glide.with(this)
                        .load(user.profilePhotoUrl)
                        .placeholder(R.drawable.ic_admin)
                        .error(R.drawable.ic_admin)
                        .into(binding.imgProfilePhoto)
                } else {
                    binding.imgProfilePhoto.setImageResource(R.drawable.ic_admin)
                }
                
                // Check Role active mode
                val mainAct = activity as MainActivity
                if (mainAct.repository.prefs.isAdminRoleActive) {
                    binding.btnRoleToggle.text = "Admin Mode"
                    binding.btnRoleToggle.setBackgroundResource(R.drawable.badge_role_admin)
                } else {
                    binding.btnRoleToggle.text = "Employee Mode"
                    binding.btnRoleToggle.setBackgroundResource(R.drawable.badge_role_employee)
                }

                // Check attendance details
                attendanceViewModel.checkTodayAttendance(user.employeeId)
                attendanceViewModel.fetchHistory(user.employeeId)
            }
        }

        attendanceViewModel.todayAttendance.observe(viewLifecycleOwner) { att ->
            val context = requireContext()
            if (att == null) {
                binding.textAttendanceStatus.text = "Not Punched In"
                binding.textAttendanceStatus.setTextColor(ContextCompat.getColor(context, R.color.warning_orange))
                binding.textPunchTimes.text = "Punch in to start your work day."
                binding.textPunchAction.text = "PUNCH IN"
                binding.imgPunchIcon.setImageResource(R.drawable.ic_check_circle)
                binding.btnPunchAction.setCardBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                binding.btnPunchAction.isClickable = true
            } else if (att.punchOutTime == null) {
                binding.textAttendanceStatus.text = "Punched In (${att.status})"
                binding.textAttendanceStatus.setTextColor(ContextCompat.getColor(context, R.color.success_green))
                binding.textPunchTimes.text = "In: ${att.punchInTime} | Vehicle: ${att.vehicleType}"
                binding.textPunchAction.text = "PUNCH OUT"
                binding.imgPunchIcon.setImageResource(R.drawable.ic_history)
                binding.btnPunchAction.setCardBackgroundColor(ContextCompat.getColor(context, R.color.success_green))
                binding.btnPunchAction.isClickable = true
            } else {
                binding.textAttendanceStatus.text = "Work Completed"
                binding.textAttendanceStatus.setTextColor(ContextCompat.getColor(context, R.color.info_blue))
                binding.textPunchTimes.text = "In: ${att.punchInTime} | Out: ${att.punchOutTime}"
                binding.textPunchAction.text = "COMPLETED"
                binding.imgPunchIcon.setImageResource(R.drawable.ic_check_circle)
                binding.btnPunchAction.setCardBackgroundColor(ContextCompat.getColor(context, R.color.border_dark))
                binding.btnPunchAction.isClickable = false
            }
        }

        attendanceViewModel.attendanceHistory.observe(viewLifecycleOwner) { history ->
            val presentCount = history.filter { it.status == "Present" || it.status == "Late" }.size
            val lateCount = history.filter { it.status == "Late" }.size
            binding.textStatPresent.text = presentCount.toString()
            binding.textStatLate.text = lateCount.toString()
        }

        dashboardViewModel.banners.observe(viewLifecycleOwner) { banners ->
            if (banners.isNotEmpty()) {
                bannersList = banners
                startBannerSlider()
            }
        }

        dashboardViewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                binding.textNotificationBadge.visibility = View.VISIBLE
                binding.textNotificationBadge.text = count.toString()
            } else {
                binding.textNotificationBadge.visibility = View.GONE
            }
        }

        dashboardViewModel.videos.observe(viewLifecycleOwner) { videos ->
            binding.recyclerVideosDashboard.adapter = DashboardVideoAdapter(videos) { video ->
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(video.youtubeUrl)
                )
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open video link", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dashboardViewModel.companyInfo.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                binding.textCompanyAbout.text = info.aboutText
            }
        }
    }

    private fun setupListeners() {
        val mainAct = activity as MainActivity

        binding.btnPunchAction.setOnClickListener {
            currentUser?.let {
                val punchFrag = PunchFragment()
                mainAct.replaceFragment(punchFrag)
            }
        }

        binding.btnRoleToggle.setOnClickListener {
            // Role switcher logic
            currentUser?.let { user ->
                if (user.role == "ADMIN") {
                    val currentMode = mainAct.repository.prefs.isAdminRoleActive
                    mainAct.repository.prefs.isAdminRoleActive = !currentMode
                    val newModeText = if (!currentMode) "Admin Mode" else "Employee Mode"
                    Toast.makeText(context, "Switched to $newModeText", Toast.LENGTH_SHORT).show()
                    authViewModel.verifySession() // Trigger refresh
                } else {
                    // Standard employee trying to test admin features - let's offer to log in as Admin Account
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Switch to Admin Dashboard")
                        .setMessage("You are currently logged in as a standard Employee. Would you like to log in as the default Administrator to test manager approvals, reports, and content configurations?")
                        .setPositiveButton("Switch to Admin") { _, _ ->
                            authViewModel.loginWithGoogleMock("admin@company.com")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        binding.layoutNotification.setOnClickListener {
            mainAct.replaceFragment(NotificationsFragment())
        }

        binding.hubLeave.setOnClickListener {
            mainAct.selectTab(R.id.tab_leaves)
            mainAct.replaceFragment(LeaveFragment(), addToBackStack = false)
        }

        binding.hubExpense.setOnClickListener {
            mainAct.selectTab(R.id.tab_expenses)
            mainAct.replaceFragment(ExpenseFragment(), addToBackStack = false)
        }

        binding.hubRegularize.setOnClickListener {
            mainAct.replaceFragment(RegularizationFragment())
        }

        binding.hubHolidays.setOnClickListener {
            mainAct.replaceFragment(HolidaysFragment())
        }

        binding.btnAllVideos.setOnClickListener {
            mainAct.replaceFragment(LearningFragment())
        }

        binding.cardAboutCompany.setOnClickListener {
            mainAct.replaceFragment(CompanyFragment())
        }
    }

    private fun startBannerSlider() {
        bannerJob?.cancel()
        bannerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                if (bannersList.isNotEmpty()) {
                    showBannerAtIndex(currentBannerIndex)
                    setupBannerDots(bannersList.size, currentBannerIndex)
                    currentBannerIndex = (currentBannerIndex + 1) % bannersList.size
                }
                delay(4000)
            }
        }
    }

    private fun showBannerAtIndex(index: Int) {
        if (!isAdded || bannersList.isEmpty()) return
        val banner = bannersList[index]
        
        // Premium fade transition animation
        val fadeOut = AlphaAnimation(1f, 0.4f).apply { duration = 300 }
        val fadeIn = AlphaAnimation(0.4f, 1f).apply { duration = 300 }

        binding.imgBanner.startAnimation(fadeOut)
        binding.textBannerTitle.startAnimation(fadeOut)
        binding.textBannerDesc.startAnimation(fadeOut)

        lifecycleScope.launch {
            delay(300)
            if (!isAdded) return@launch
            binding.textBannerTitle.text = banner.title
            binding.textBannerDesc.text = banner.description
            
            // Mocking dynamic image backgrounds using placeholder graphics or local resource tints
            val tints = arrayOf(R.color.grad_purple_start, R.color.grad_cyan_start, R.color.grad_gold_start)
            val selectedTint = tints[index % tints.size]
            binding.imgBanner.setImageResource(R.drawable.ic_launcher_background)
            binding.imgBanner.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), selectedTint))

            binding.imgBanner.startAnimation(fadeIn)
            binding.textBannerTitle.startAnimation(fadeIn)
            binding.textBannerDesc.startAnimation(fadeIn)
        }

        binding.cardBannerSlider.setOnClickListener {
            val mainAct = activity as MainActivity
            when (banner.clickAction) {
                "settings" -> {
                    mainAct.selectTab(R.id.tab_settings)
                    mainAct.replaceFragment(SettingsFragment(), addToBackStack = false)
                }
                "videos" -> mainAct.replaceFragment(LearningFragment())
                "about_company" -> mainAct.replaceFragment(CompanyFragment())
                else -> Toast.makeText(context, "Action: ${banner.clickAction}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBannerDots(size: Int, activeIndex: Int) {
        if (!isAdded) return
        val container = binding.indicatorContainer
        container.removeAllViews()
        for (i in 0 until size) {
            val dot = ImageView(requireContext()).apply {
                val params = LinearLayout.LayoutParams(16, 16).apply {
                    setMargins(8, 0, 8, 0)
                }
                layoutParams = params
                setImageResource(R.drawable.badge_notification_red)
                imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (i == activeIndex) R.color.white else R.color.text_sub_dark
                    )
                )
            }
            container.addView(dot)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bannerJob?.cancel()
        _binding = null
    }

    // --- Horizontal Recycler Video Adapter ---
    private class DashboardVideoAdapter(
        private val list: List<Video>,
        private val onClick: (Video) -> Unit
    ) : RecyclerView.Adapter<DashboardVideoAdapter.VideoViewHolder>() {

        class VideoViewHolder(val itemBinding: ItemVideoCardBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            val viewBind = ItemVideoCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VideoViewHolder(viewBind)
        }

        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            val video = list[position]
            holder.itemBinding.textVideoTitle.text = video.title
            
            // Load real video thumbnail using Glide
            com.bumptech.glide.Glide.with(holder.itemView.context)
                .load(video.thumbnailUrl)
                .placeholder(R.drawable.card_gradient_overlay)
                .error(R.drawable.card_gradient_overlay)
                .into(holder.itemBinding.imgVideoThumbnail)
            
            holder.itemBinding.imgVideoThumbnail.imageTintList = null // Clear mock tint

            holder.itemView.setOnClickListener {
                onClick(video)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
