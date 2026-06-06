package com.example.hrapp.ui.fragments

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
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
import com.example.hrapp.data.db.Employee
import com.example.hrapp.data.db.Expense
import com.example.hrapp.databinding.FragmentExpenseBinding
import com.example.hrapp.databinding.ItemExpenseBinding
import com.example.hrapp.ui.viewmodel.AuthViewModel
import com.example.hrapp.ui.viewmodel.ExpenseViewModel
import com.example.hrapp.ui.viewmodel.HRViewModelFactory
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ExpenseFragment : Fragment() {

    private var _binding: FragmentExpenseBinding? = null
    private val binding get() = _binding!!

    private lateinit var authViewModel: AuthViewModel
    private lateinit var expenseViewModel: ExpenseViewModel
    
    private var currentUserEmployeeId = ""
    private var currentUser: Employee? = null
    private var allExpensesList: List<Expense> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setBottomNavigationVisibility(true)

        val mainAct = activity as MainActivity
        val factory = HRViewModelFactory(mainAct.repository)

        authViewModel = ViewModelProvider(mainAct, factory)[AuthViewModel::class.java]
        expenseViewModel = ViewModelProvider(mainAct, factory)[ExpenseViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        binding.recyclerExpenses.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                currentUser = user
                currentUserEmployeeId = user.employeeId
                expenseViewModel.fetchExpenses(user.employeeId)
            }
        }

        expenseViewModel.expenses.observe(viewLifecycleOwner) { list ->
            allExpensesList = list
            updateSummaryCards(list)
            
            if (list.isEmpty()) {
                binding.recyclerExpenses.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.VISIBLE
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.recyclerExpenses.visibility = View.VISIBLE
                binding.recyclerExpenses.adapter = ExpenseAdapter(list)
            }
        }

        expenseViewModel.addExpenseResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, "Expense claim logged successfully!", Toast.LENGTH_SHORT).show()
                expenseViewModel.resetAddResult()
            }
        }
    }

    private fun updateSummaryCards(list: List<Expense>) {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        
        val approvedTotal = list.filter { (it.status ?: "Pending") == "Approved" }.sumOf { it.amount }
        val pendingTotal = list.filter { (it.status ?: "Pending") == "Pending" }.sumOf { it.amount }

        binding.textTotalApproved.text = format.format(approvedTotal)
        binding.textTotalPending.text = format.format(pendingTotal)
    }

    private fun setupListeners() {
        binding.btnAddExpense.setOnClickListener {
            showAddExpenseDialog()
        }

        binding.btnDownloadReport.setOnClickListener {
            showDownloadMonthDialog()
        }
    }

    private fun showAddExpenseDialog() {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Log New Expense")

        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_add_expense, null)
        builder.setView(dialogView)

        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.dialog_spinner_expense_category)
        val textDate = dialogView.findViewById<TextView>(R.id.dialog_text_expense_date)
        val editAmount = dialogView.findViewById<EditText>(R.id.dialog_edit_expense_amount)
        val editDesc = dialogView.findViewById<EditText>(R.id.dialog_edit_expense_desc)
        val cardAttachment = dialogView.findViewById<View>(R.id.dialog_card_expense_attachment)
        val textAttachmentLabel = dialogView.findViewById<TextView>(R.id.dialog_text_expense_attachment_label)

        var selectedDateStr = ""
        var receiptAttached = false

        // 1. Setup Categories Spinner
        val categories = arrayOf("Fuel", "Travel", "Food", "Hotel", "Miscellaneous")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        spinnerCategory.adapter = spinnerAdapter

        // 2. Setup date picker
        val calendar = Calendar.getInstance()
        textDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val chosenCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosenCal.time)
                textDate.text = "Date: $selectedDateStr"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // 3. Receipt mock capture
        cardAttachment.setOnClickListener {
            receiptAttached = true
            textAttachmentLabel.text = "📎 Bill Receipt Logged"
            textAttachmentLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
            Toast.makeText(context, "Bill receipt attached successfully.", Toast.LENGTH_SHORT).show()
        }

        builder.setPositiveButton("Submit Claim") { dialog, _ ->
            val desc = editDesc.text.toString().trim()
            val category = spinnerCategory.selectedItem.toString()
            val amountStr = editAmount.text.toString().trim()

            if (selectedDateStr.isBlank()) {
                Toast.makeText(context, "Please select the date", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (amountStr.isBlank()) {
                Toast.makeText(context, "Please enter the amount", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            if (amount <= 0) {
                Toast.makeText(context, "Please enter a valid positive amount", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (desc.isBlank()) {
                Toast.makeText(context, "Please enter a description", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (!receiptAttached) {
                Toast.makeText(context, "Please upload the bill receipt receipt image log", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val mockReceiptUri = "content://mock/bill_receipt.png"
            expenseViewModel.addExpense(currentUserEmployeeId, selectedDateStr, amount, category, desc, mockReceiptUri)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showDownloadMonthDialog() {
        val months = arrayOf("June 2026", "May 2026", "April 2026", "March 2026")
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Generate PDF Report")
            .setItems(months) { dialog, which ->
                generateMonthlyPdfReport(which, months[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateMonthlyPdfReport(monthOffset: Int, monthLabel: String) {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JUNE, 1) // Base lock on June 2026
        cal.add(Calendar.MONTH, -monthOffset)
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)

        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calLog = Calendar.getInstance()

        val monthlyExpenses = allExpensesList.filter { exp ->
            try {
                val d = parser.parse(exp.expenseDate)
                if (d != null) {
                    calLog.time = d
                    calLog.get(Calendar.MONTH) == targetMonth && calLog.get(Calendar.YEAR) == targetYear
                } else false
            } catch (e: Exception) {
                false
            }
        }

        if (monthlyExpenses.isEmpty()) {
            Toast.makeText(context, "No expense claims found for $monthLabel", Toast.LENGTH_LONG).show()
            return
        }

        // Native PDF generation
        try {
            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size standard DPI
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
            canvas.drawText("Monthly Expense Claims Report - $monthLabel", 40f, 90f, paint)

            // Employee info
            paint.textSize = 11f
            paint.color = Color.GRAY
            currentUser?.let {
                canvas.drawText("Employee: ${it.name} (${it.employeeId})", 40f, 120f, paint)
                canvas.drawText("Department: ${it.department} | ${it.designation}", 40f, 138f, paint)
            }

            // Divider Line
            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            canvas.drawLine(40f, 155f, 555f, 155f, paint)

            // Draw Table Headings
            paint.isFakeBoldText = true
            paint.textSize = 10f
            canvas.drawText("DATE", 40f, 180f, paint)
            canvas.drawText("CATEGORY", 130f, 180f, paint)
            canvas.drawText("DESCRIPTION", 220f, 180f, paint)
            canvas.drawText("AMOUNT", 430f, 180f, paint)
            canvas.drawText("STATUS", 500f, 180f, paint)

            // Divider
            paint.strokeWidth = 1f
            canvas.drawLine(40f, 190f, 555f, 190f, paint)

            // Draw Table Rows
            paint.isFakeBoldText = false
            var yOffset = 215f
            val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

            monthlyExpenses.forEach { exp ->
                canvas.drawText(exp.expenseDate, 40f, yOffset, paint)
                canvas.drawText(exp.category, 130f, yOffset, paint)
                
                // Truncate desc if too long for spacing
                val truncatedDesc = if (exp.description.length > 25) exp.description.substring(0, 22) + "..." else exp.description
                canvas.drawText(truncatedDesc, 220f, yOffset, paint)
                
                canvas.drawText(format.format(exp.amount), 430f, yOffset, paint)
                canvas.drawText(exp.status ?: "Pending", 500f, yOffset, paint)

                yOffset += 25f
            }

            // Draw Footer Divider
            canvas.drawLine(40f, yOffset, 555f, yOffset, paint)

            // Draw Totals
            yOffset += 25f
            paint.isFakeBoldText = true
            val totalAmount = monthlyExpenses.sumOf { it.amount }
            val approvedAmount = monthlyExpenses.filter { (it.status ?: "Pending") == "Approved" }.sumOf { it.amount }
            canvas.drawText("Total Claims: ${format.format(totalAmount)}", 40f, yOffset, paint)
            canvas.drawText("Total Approved: ${format.format(approvedAmount)}", 300f, yOffset, paint)

            pdfDoc.finishPage(page)

            // Write out file inside Downloads folder safely
            val downloadDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val fileName = "Expense_Report_${monthLabel.replace(" ", "_")}.pdf"
            val file = File(downloadDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDoc.writeTo(outputStream)
            pdfDoc.close()
            outputStream.close()

            Toast.makeText(context, "PDF Report saved at: ${file.name}\nLocation: App Downloads folder", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write PDF: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Recycler View Adapter ---
    private class ExpenseAdapter(
        private val list: List<Expense>
    ) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {

        class ViewHolder(val itemBinding: ItemExpenseBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val bind = ItemExpenseBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(bind)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val exp = list[position]
            val context = holder.itemView.context
            val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

            holder.itemBinding.textExpenseCategory.text = "${exp.category} Expense"
            holder.itemBinding.textExpenseDesc.text = exp.description
            holder.itemBinding.textExpenseDate.text = exp.expenseDate
            holder.itemBinding.textExpenseAmount.text = format.format(exp.amount)

            // Category Icon Selection
            when (exp.category) {
                "Fuel" -> holder.itemBinding.imgCategoryIcon.setImageResource(R.drawable.ic_bike)
                "Travel" -> holder.itemBinding.imgCategoryIcon.setImageResource(R.drawable.ic_car)
                "Food" -> holder.itemBinding.imgCategoryIcon.setImageResource(R.drawable.ic_check_circle)
                "Hotel" -> holder.itemBinding.imgCategoryIcon.setImageResource(R.drawable.ic_about)
                "Miscellaneous" -> holder.itemBinding.imgCategoryIcon.setImageResource(R.drawable.ic_expense)
            }

            val status = exp.status ?: "Pending"
            holder.itemBinding.textExpenseStatus.text = status
            when (status) {
                "Pending" -> {
                    holder.itemBinding.textExpenseStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textExpenseStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.warning_orange))
                    )
                }
                "Approved" -> {
                    holder.itemBinding.textExpenseStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textExpenseStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.success_green))
                    )
                }
                "Rejected" -> {
                    holder.itemBinding.textExpenseStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                    holder.itemBinding.textExpenseStatus.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.danger_red))
                    )
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
