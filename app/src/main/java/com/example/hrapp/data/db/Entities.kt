package com.example.hrapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Required

@Serializable
@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val employeeId: String = "",
    val designation: String = "",
    val department: String = "",
    val mobile: String = "",
    val email: String = "",
    val reportingManager: String = "",
    val joiningDate: String = "",
    val birthday: String = "",
    val employeeType: String = "",
    val profilePhotoUrl: String = "",
    val role: String = "EMPLOYEE", // "EMPLOYEE" or "ADMIN"
    val loginEnabled: Boolean = true,
    val shiftType: String = ""
)

@Serializable
@Entity(tableName = "attendance")
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: String = "",
    val date: String = "", // YYYY-MM-DD
    val vehicleType: String = "", // "Company Bike", "Company Car", "Personal Bike", "Personal Car", "No Vehicle"
    val vehicleNumber: String = "",
    val punchInTime: String = "",
    val punchOutTime: String? = null,
    val punchInReading: Double = 0.0,
    val punchOutReading: Double? = null,
    val workSummary: String = "",
    val closingWorkSummary: String? = null,
    val vehicleImageUri: String? = null,
    val closingVehicleImageUri: String? = null,
    val status: String? = "" // "Present", "Late", "Absent"
)

@Serializable
@Entity(tableName = "leave_requests")
data class LeaveRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: String = "",
    val leaveType: String = "", // "Casual Leave", "Sick Leave", "Earned Leave"
    val fromDate: String = "", // YYYY-MM-DD
    val toDate: String = "", // YYYY-MM-DD
    val reason: String = "",
    val attachmentUri: String? = null,
    @Required val status: String? = "Pending", // "Pending", "Approved", "Rejected"
    val adminRemark: String? = null
)

@Serializable
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: String = "",
    val expenseDate: String = "", // YYYY-MM-DD
    val amount: Double = 0.0,
    val category: String = "", // "Fuel", "Travel", "Food", "Hotel", "Miscellaneous"
    val description: String = "",
    val billImageUri: String? = null,
    @Required val status: String? = "Pending" // "Pending", "Approved", "Rejected"
)

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val timestamp: Long = 0,
    val isRead: Boolean = false,
    val type: String = "" // "Announcement", "Meeting", "Holiday", "Leave", "Expense"
)

@Entity(tableName = "banners")
data class Banner(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val clickAction: String = ""
)

@Entity(tableName = "videos")
data class Video(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val youtubeUrl: String = "",
    val thumbnailUrl: String = "",
    val category: String = "",
    val isEnabled: Boolean = true
)

@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val date: String = "", // YYYY-MM-DD
    val type: String = "" // "National", "State", "Company"
)

@Serializable
@Entity(tableName = "regularizations")
data class RegularizationRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: String = "",
    val date: String = "", // YYYY-MM-DD
    val reason: String = "",
    val remarks: String = "",
    val imageUri: String? = null,
    @Required val status: String? = "Pending" // "Pending", "Approved", "Rejected"
)

@Entity(tableName = "company_info")
data class CompanyInfo(
    @PrimaryKey(autoGenerate = true) val id: Long = 1,
    val logoUrl: String = "",
    val aboutText: String = "",
    val history: String = "",
    val mission: String = "",
    val vision: String = "",
    val values: String = "",
    val directorMessage: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val website: String = "",
    val socialLinksJson: String = "" // Serialized social links list
)

@Serializable
@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val startTime: String = "",
    val endTime: String = ""
)
