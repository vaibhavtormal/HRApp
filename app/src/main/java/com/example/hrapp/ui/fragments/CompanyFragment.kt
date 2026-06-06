package com.example.hrapp.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hrapp.MainActivity
import com.example.hrapp.databinding.FragmentCompanyAboutBinding
import com.example.hrapp.ui.viewmodel.DashboardViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory

class CompanyFragment : Fragment() {

    private var _binding: FragmentCompanyAboutBinding? = null
    private val binding get() = _binding!!

    private lateinit var dashboardViewModel: DashboardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanyAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        dashboardViewModel = ViewModelProvider(mainAct, factory)[DashboardViewModel::class.java]

        setupObservers()
        setupListeners()

        dashboardViewModel.loadDashboardData()
    }

    private fun setupObservers() {
        dashboardViewModel.companyInfo.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                binding.textCoAbout.text = info.aboutText
                binding.textCoHistory.text = info.history
                binding.textCoDirector.text = info.directorMessage
                binding.textCoMission.text = info.mission
                binding.textCoVision.text = info.vision
                binding.textCoValues.text = info.values.replace(", ", "\n• ")
                binding.textCoPhone.text = "Phone: ${if (info.contactPhone.contains("555-0199") || info.contactPhone.isEmpty()) "8668842351" else info.contactPhone}"
                binding.textCoEmail.text = "Email: ${if (info.contactEmail.contains("stubbornpatil6") || info.contactEmail.isEmpty()) "vaibhavtormal82@gmail.com" else info.contactEmail}"
                binding.textCoWeb.text = "Website: ${if (info.website.contains("stubborn.com") || info.website.isEmpty()) "Coming Soon #Stubborn" else info.website}"
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val mainAct = activity as MainActivity
            mainAct.setBottomNavigationVisibility(true)
            mainAct.replaceFragment(DashboardFragment(), addToBackStack = false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
