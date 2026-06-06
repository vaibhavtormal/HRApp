package com.example.hrapp.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hrapp.MainActivity
import com.example.hrapp.R
import com.example.hrapp.data.db.Video
import com.example.hrapp.databinding.FragmentLearningBinding
import com.example.hrapp.databinding.ItemLearningVideoBinding
import com.example.hrapp.ui.viewmodel.DashboardViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LearningFragment : Fragment() {

    private var _binding: FragmentLearningBinding? = null
    private val binding get() = _binding!!

    private lateinit var dashboardViewModel: DashboardViewModel
    
    private var allVideosList: List<Video> = emptyList()
    private var activeFilterCategory = "All"
    
    // Player simulation states
    private var isPlaying = false
    private var playbackJob: Job? = null
    private var playerProgress = 35

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(true)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        dashboardViewModel = ViewModelProvider(mainAct, factory)[DashboardViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()

        dashboardViewModel.loadDashboardData()

        // Check if deep linked video passed from dashboard
        arguments?.getString("play_video_id")?.let { videoUrl ->
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(videoUrl)
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open video link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerLearningVideos.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        dashboardViewModel.videos.observe(viewLifecycleOwner) { list ->
            allVideosList = list
            filterAndDisplayVideos()
        }
    }

    private fun setupListeners() {
        // Search filter
        binding.editSearchVideos.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dashboardViewModel.searchVideos(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Chips filtering
        binding.chipAll.setOnClickListener { selectCategoryChip("All") }
        binding.chipCulture.setOnClickListener { selectCategoryChip("Culture") }
        binding.chipTraining.setOnClickListener { selectCategoryChip("Training") }
        binding.chipHrHelp.setOnClickListener { selectCategoryChip("HR Help") }

        // Player actions
        binding.btnClosePlayer.setOnClickListener {
            stopPlaybackLoop()
            binding.cardPlayer.visibility = View.GONE
        }

        binding.btnPlayPauseVideo.setOnClickListener {
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlaybackLoop()
            }
        }
    }

    private fun selectCategoryChip(category: String) {
        activeFilterCategory = category
        val context = requireContext()

        // Reset all chips styles
        val chips = arrayOf(binding.chipAll, binding.chipCulture, binding.chipTraining, binding.chipHrHelp)
        chips.forEach { chip ->
            chip.setBackgroundResource(R.drawable.badge_role_employee)
            chip.setTextColor(ContextCompat.getColor(context, R.color.text_main_dark))
        }

        // Apply active styling
        val activeChip = when (category) {
            "All" -> binding.chipAll
            "Culture" -> binding.chipCulture
            "Training" -> binding.chipTraining
            "HR Help" -> binding.chipHrHelp
            else -> binding.chipAll
        }
        activeChip.setBackgroundResource(R.drawable.badge_role_admin)
        activeChip.setTextColor(ContextCompat.getColor(context, R.color.white))

        filterAndDisplayVideos()
    }

    private fun filterAndDisplayVideos() {
        val filtered = if (activeFilterCategory == "All") {
            allVideosList
        } else {
            allVideosList.filter { it.category.equals(activeFilterCategory, ignoreCase = true) }
        }
        binding.recyclerLearningVideos.adapter = LearningVideoAdapter(filtered) { video ->
            playVideoInPlayer(video)
        }
    }

    private fun playVideoInPlayer(video: Video) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(video.youtubeUrl)
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open YouTube link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPlaybackLoop() {
        isPlaying = true
        binding.btnPlayPauseVideo.setImageResource(R.drawable.ic_history) // Pause representation
        binding.btnPlayPauseVideo.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))

        playbackJob?.cancel()
        playbackJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && isPlaying) {
                delay(1000)
                playerProgress = (playerProgress + 2) % 100
                binding.playerSeekbar.progress = playerProgress
                
                // Update time counter mock
                val minutes = (playerProgress * 5) / 100
                val seconds = (playerProgress * 300) % 60
                binding.textPlayerTime.text = String.format("%02d:%02d / 05:00", minutes, seconds)
            }
        }
    }

    private fun pausePlayback() {
        isPlaying = false
        binding.btnPlayPauseVideo.setImageResource(R.drawable.ic_play)
        binding.btnPlayPauseVideo.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
        playbackJob?.cancel()
    }

    private fun stopPlaybackLoop() {
        pausePlayback()
        playerProgress = 0
        binding.playerSeekbar.progress = 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playbackJob?.cancel()
        _binding = null
    }

    // --- Recycler View Adapter ---
    private class LearningVideoAdapter(
        private val list: List<Video>,
        private val onClick: (Video) -> Unit
    ) : RecyclerView.Adapter<LearningVideoAdapter.ViewHolder>() {

        class ViewHolder(val itemBinding: ItemLearningVideoBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemLearningVideoBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val video = list[position]
            val context = holder.itemView.context

            holder.itemBinding.textItemVideoTitle.text = video.title
            holder.itemBinding.textItemVideoDesc.text = video.description
            holder.itemBinding.textItemVideoCategory.text = video.category

            // Load YouTube thumbnail using Glide
            com.bumptech.glide.Glide.with(context)
                .load(video.thumbnailUrl)
                .placeholder(R.drawable.card_gradient_overlay)
                .error(R.drawable.card_gradient_overlay)
                .into(holder.itemBinding.imgItemThumbnail)

            holder.itemBinding.imgItemThumbnail.imageTintList = null // Clear mock tint

            holder.itemView.setOnClickListener {
                onClick(video)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
