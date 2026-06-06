package com.example.hrapp.ui.fragments

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hrapp.MainActivity
import com.example.hrapp.databinding.FragmentAdminReportsBinding
import com.example.hrapp.ui.viewmodel.AdminReportsData
import com.example.hrapp.ui.viewmodel.AdminViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AdminReportsFragment : Fragment() {

    private var _binding: FragmentAdminReportsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adminViewModel: AdminViewModel
    private var reportsData: AdminReportsData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(false)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)
        adminViewModel = ViewModelProvider(mainAct, factory)[AdminViewModel::class.java]

        setupObservers()
        setupListeners()

        adminViewModel.loadAdminData()
    }

    private fun setupObservers() {
        adminViewModel.reportsData.observe(viewLifecycleOwner) { data ->
            reportsData = data
            
            // 1. Populate Pie Chart data
            val pieData = mapOf(
                "Present" to data.presentCount.toFloat(),
                "Late" to data.lateCount.toFloat(),
                "Absent" to data.absentCount.toFloat()
            )
            val colors = listOf(
                Color.parseColor("#10B981"), // success green
                Color.parseColor("#F59E0B"), // warning orange
                Color.parseColor("#EF4444")  // danger red
            )
            binding.pieChartAttendance.setData(pieData, colors)

            // 2. Populate Bar Chart data
            binding.barChartExpenses.setData(data.expenseByCategory, Color.parseColor("#3B82F6"))

            // 3. Populate Travel statistics
            binding.textAdminTotalKm.text = String.format("Total Commute Distance: %.1f KM", data.totalKM)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val mainAct = activity as MainActivity
            mainAct.replaceFragment(AdminDashboardFragment(), addToBackStack = false)
        }

        binding.btnExportXls.setOnClickListener {
            exportCsvReport()
        }

        binding.btnExportPdf.setOnClickListener {
            exportPdfSummary()
        }
    }

    private fun exportCsvReport() {
        val data = reportsData
        if (data == null || data.allAttendanceList.isEmpty()) {
            Toast.makeText(context, "No logs available to export.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvBuilder = java.lang.StringBuilder()
            csvBuilder.append("Employee ID,Date,Vehicle Type,Plate Number,Punch In Time,Punch Out Time,Opening Reading,Closing Reading,Status\n")

            data.allAttendanceList.forEach { att ->
                csvBuilder.append("${att.employeeId},")
                csvBuilder.append("${att.date},")
                csvBuilder.append("${att.vehicleType},")
                csvBuilder.append("${att.vehicleNumber},")
                csvBuilder.append("${att.punchInTime},")
                csvBuilder.append("${att.punchOutTime ?: "N/A"},")
                csvBuilder.append("${att.punchInReading},")
                csvBuilder.append("${att.punchOutReading ?: 0.0},")
                csvBuilder.append("${att.status}\n")
            }

            val downloadDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "Staff_Attendance_Logs_2026.csv")
            val outputStream = FileOutputStream(file)
            outputStream.write(csvBuilder.toString().toByteArray())
            outputStream.close()

            Toast.makeText(context, "CSV Staff logs saved at: ${file.name}\nLocation: App Downloads folder", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write CSV: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportPdfSummary() {
        val data = reportsData
        if (data == null) {
            Toast.makeText(context, "No report statistics available.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard size
            val page = pdfDoc.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()

            // Header titles
            paint.color = Color.BLACK
            paint.textSize = 20f
            paint.isFakeBoldText = true
            canvas.drawText("Stubborn Solutions Ltd.", 40f, 60f, paint)

            paint.textSize = 14f
            paint.isFakeBoldText = false
            canvas.drawText("System HRMS Executive Summary Report", 40f, 90f, paint)

            paint.textSize = 10f
            paint.color = Color.GRAY
            val todayStr = SimpleDateFormat("EEEE, d MMMM yyyy HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Report Generated: $todayStr", 40f, 115f, paint)

            // Line divider
            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            canvas.drawLine(40f, 130f, 555f, 130f, paint)

            // Executive Summary section
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("I. ATTENDANCE STATISTICS", 40f, 160f, paint)

            paint.isFakeBoldText = false
            paint.textSize = 11f
            canvas.drawText("Total Corporate Staff Registered: ${data.totalEmployees}", 60f, 185f, paint)
            canvas.drawText("Staff Present Today: ${data.presentCount}", 60f, 205f, paint)
            canvas.drawText("Late Punch-ins Today: ${data.lateCount}", 60f, 225f, paint)
            canvas.drawText("Staff Absent Today: ${data.absentCount}", 60f, 245f, paint)

            // Expense summary
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("II. REIMBURSEMENT & REQUISITION SUMMARY", 40f, 285f, paint)

            paint.isFakeBoldText = false
            paint.textSize = 11f
            val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            canvas.drawText("Total Requisitions Amount Claims Logged: ${format.format(data.totalExpenseAmount)}", 60f, 310f, paint)

            // Category breakdown listing
            var yOffset = 330f
            data.expenseByCategory.forEach { (cat, amount) ->
                canvas.drawText("• $cat Category Reimbursements: ${format.format(amount)}", 80f, yOffset, paint)
                yOffset += 20f
            }

            // Travel stats
            yOffset += 15f
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("III. VEHICLE FLEET TRAVEL LOGS", 40f, yOffset, paint)

            yOffset += 25f
            paint.isFakeBoldText = false
            paint.textSize = 11f
            canvas.drawText(String.format("Total Driven Commute Distance: %.1f Kilometers", data.totalKM), 60f, yOffset, paint)

            yOffset += 40f
            canvas.drawLine(40f, yOffset, 555f, yOffset, paint)

            yOffset += 25f
            paint.isFakeBoldText = true
            paint.color = Color.DKGRAY
            canvas.drawText("Confidential Executive Report - Authorized Access Only", 40f, yOffset, paint)

            pdfDoc.finishPage(page)

            val downloadDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "System_Executive_Summary_2026.pdf")
            val outputStream = FileOutputStream(file)
            pdfDoc.writeTo(outputStream)
            pdfDoc.close()
            outputStream.close()

            Toast.makeText(context, "Executive Report PDF saved: ${file.name}\nLocation: App Downloads folder", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
