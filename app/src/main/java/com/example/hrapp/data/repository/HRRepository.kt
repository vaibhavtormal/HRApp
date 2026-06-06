package com.example.hrapp.data.repository

import com.example.hrapp.data.PreferencesManager
import com.example.hrapp.data.db.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.hrapp.data.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from

class HRRepository(
    private val db: AppDatabase,
    val prefs: PreferencesManager
) {
    // Simulated network delay to let shimmer/progress bars show
    private suspend fun simulateNetwork() {
        delay(400)
    }

    // --- Employee ---
    suspend fun getEmployeeById(id: Long): Employee? = withContext(Dispatchers.IO) {
        try {
            val query = FirebaseFirestore.getInstance().collection("employees")
                .whereEqualTo("id", id).get().await()
            val emp = query.documents.firstOrNull()?.toObject(Employee::class.java)
            if (emp != null) {
                db.employeeDao().insertEmployee(emp)
            }
            emp ?: db.employeeDao().getEmployeeById(id)
        } catch (e: Exception) {
            db.employeeDao().getEmployeeById(id)
        }
    }

    suspend fun getEmployeeByEmail(email: String): Employee? = withContext(Dispatchers.IO) {
        simulateNetwork()
        try {
            val doc = FirebaseFirestore.getInstance().collection("employees").document(email).get().await()
            val emp = doc.toObject(Employee::class.java)
            if (emp != null) {
                db.employeeDao().insertEmployee(emp)
            }
            emp ?: db.employeeDao().getEmployeeByEmail(email)
        } catch (e: Exception) {
            db.employeeDao().getEmployeeByEmail(email)
        }
    }

    suspend fun getEmployeeByMobile(mobile: String): Employee? = withContext(Dispatchers.IO) {
        simulateNetwork()
        val trimmed = mobile.trim()
        val tenDigits = if (trimmed.length >= 10) trimmed.takeLast(10) else trimmed
        val formats = listOf(trimmed, tenDigits, "+91$tenDigits", "91$tenDigits")
        
        var foundEmp: Employee? = null
        
        // 1. Try Firestore with all formats
        try {
            val employeesRef = FirebaseFirestore.getInstance().collection("employees")
            for (fmt in formats) {
                val query = employeesRef.whereEqualTo("mobile", fmt).get().await()
                val emp = query.documents.firstOrNull()?.toObject(Employee::class.java)
                if (emp != null) {
                    db.employeeDao().insertEmployee(emp)
                    foundEmp = emp
                    break
                }
            }
        } catch (e: Exception) {
            // Ignore Firestore errors
        }
        
        // 2. Try Local database fallback with all formats
        if (foundEmp == null) {
            for (fmt in formats) {
                val emp = db.employeeDao().getEmployeeByMobile(fmt)
                if (emp != null) {
                    foundEmp = emp
                    break
                }
            }
        }
        
        foundEmp
    }

    suspend fun getAllEmployees(): List<Employee> = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("employees").get().await()
            val list = snapshot.toObjects(Employee::class.java)
            for (emp in list) {
                db.employeeDao().insertEmployee(emp)
            }
            list
        } catch (e: Exception) {
            db.employeeDao().getAllEmployees()
        }
    }

    suspend fun insertEmployee(employee: Employee): Long = withContext(Dispatchers.IO) {
        val id = employee.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        val finalEmp = employee.copy(id = id)
        try {
            FirebaseFirestore.getInstance().collection("employees").document(employee.email).set(finalEmp).await()
        } catch (e: Exception) {
            // Ignore
        }
        db.employeeDao().insertEmployee(finalEmp)
        id
    }

    suspend fun updateEmployee(employee: Employee) = withContext(Dispatchers.IO) {
        val finalEmp = if (employee.email == "vaibhavtormal82@gmail.com") {
            employee.copy(loginEnabled = true, role = "ADMIN")
        } else {
            employee
        }
        try {
            FirebaseFirestore.getInstance().collection("employees").document(finalEmp.email).set(finalEmp).await()
        } catch (e: Exception) {
            // Ignore
        }
        db.employeeDao().insertEmployee(finalEmp)
    }

    // --- Attendance ---
    suspend fun getAttendanceForEmployee(employeeId: String): List<Attendance> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("attendance").select {
                filter {
                    eq("employeeId", employeeId)
                }
            }.decodeList<Attendance>().sortedByDescending { it.date }
        } catch (e: Exception) {
            e.printStackTrace()
            db.attendanceDao().getAttendanceForEmployee(employeeId)
        }
    }

    suspend fun getTodayAttendance(employeeId: String, dateStr: String): Attendance? = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("attendance").select {
                filter {
                    eq("employeeId", employeeId)
                    eq("date", dateStr)
                }
            }.decodeList<Attendance>().firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            db.attendanceDao().getAttendanceByDate(employeeId, dateStr)
        }
    }

    suspend fun punchIn(attendance: Attendance): Long = withContext(Dispatchers.IO) {
        simulateNetwork()
        val id = System.currentTimeMillis()
        val finalAtt = attendance.copy(id = id)
        try {
            SupabaseProvider.client.from("attendance").insert(finalAtt)
        } catch (e: Exception) {
            e.printStackTrace()
            db.attendanceDao().insertAttendance(finalAtt)
        }
        id
    }

    suspend fun punchOut(attendance: Attendance) = withContext(Dispatchers.IO) {
        simulateNetwork()
        try {
            if (attendance.id > 0) {
                SupabaseProvider.client.from("attendance").update(attendance) {
                    filter {
                        eq("id", attendance.id)
                    }
                }
            } else {
                SupabaseProvider.client.from("attendance").update(attendance) {
                    filter {
                        eq("employeeId", attendance.employeeId)
                        eq("date", attendance.date)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            db.attendanceDao().updateAttendance(attendance)
        }
    }

    suspend fun getAllAttendance(): List<Attendance> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("attendance").select().decodeList<Attendance>()
                .sortedWith(compareByDescending<Attendance> { it.date }.thenByDescending { it.punchInTime })
        } catch (e: Exception) {
            e.printStackTrace()
            db.attendanceDao().getAllAttendance()
        }
    }

    suspend fun getAttendanceForDate(date: String): List<Attendance> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("attendance").select {
                filter {
                    eq("date", date)
                }
            }.decodeList<Attendance>()
        } catch (e: Exception) {
            e.printStackTrace()
            db.attendanceDao().getAttendanceByDateString(date)
        }
    }

    // --- Leaves ---
    suspend fun getLeavesForEmployee(employeeId: String): List<LeaveRequest> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("leave_requests").select {
                filter {
                    eq("employeeId", employeeId)
                }
            }.decodeList<LeaveRequest>().sortedByDescending { it.fromDate }
        } catch (e: Exception) {
            e.printStackTrace()
            db.leaveDao().getLeavesForEmployee(employeeId)
        }
    }

    suspend fun applyLeave(leaveRequest: LeaveRequest): Long = withContext(Dispatchers.IO) {
        simulateNetwork()
        val id = System.currentTimeMillis()
        val finalReq = leaveRequest.copy(id = id)
        try {
            SupabaseProvider.client.from("leave_requests").insert(finalReq)
        } catch (e: Exception) {
            e.printStackTrace()
            db.leaveDao().insertLeave(finalReq)
        }
        id
    }

    suspend fun getAllLeaves(): List<LeaveRequest> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("leave_requests").select().decodeList<LeaveRequest>().sortedByDescending { it.fromDate }
        } catch (e: Exception) {
            e.printStackTrace()
            db.leaveDao().getAllLeaves()
        }
    }

    suspend fun updateLeaveStatus(leaveId: Long, status: String, adminRemark: String?) = withContext(Dispatchers.IO) {
        simulateNetwork()
        try {
            SupabaseProvider.client.from("leave_requests").update(
                {
                    set("status", status)
                    set("adminRemark", adminRemark)
                }
            ) {
                filter {
                    eq("id", leaveId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            db.leaveDao().getLeaveById(leaveId)?.let {
                db.leaveDao().insertLeave(it.copy(status = status, adminRemark = adminRemark))
            }
        }
    }

    suspend fun updateLeave(leaveRequest: LeaveRequest) = withContext(Dispatchers.IO) {
        simulateNetwork()
        try {
            SupabaseProvider.client.from("leave_requests").update(leaveRequest) {
                filter {
                    eq("id", leaveRequest.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            db.leaveDao().updateLeave(leaveRequest)
        }
    }

    // --- Expenses ---
    suspend fun getExpensesForEmployee(employeeId: String): List<Expense> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("expenses").select {
                filter {
                    eq("employeeId", employeeId)
                }
            }.decodeList<Expense>().sortedByDescending { it.expenseDate }
        } catch (e: Exception) {
            e.printStackTrace()
            db.expenseDao().getExpensesForEmployee(employeeId)
        }
    }

    suspend fun addExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        simulateNetwork()
        val id = System.currentTimeMillis()
        val finalExp = expense.copy(id = id)
        try {
            SupabaseProvider.client.from("expenses").insert(finalExp)
        } catch (e: Exception) {
            e.printStackTrace()
            db.expenseDao().insertExpense(finalExp)
        }
        id
    }

    suspend fun getAllExpenses(): List<Expense> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("expenses").select().decodeList<Expense>().sortedByDescending { it.expenseDate }
        } catch (e: Exception) {
            e.printStackTrace()
            db.expenseDao().getAllExpenses()
        }
    }

    suspend fun updateExpenseStatus(expenseId: Long, status: String) = withContext(Dispatchers.IO) {
        simulateNetwork()
        try {
            SupabaseProvider.client.from("expenses").update(
                {
                    set("status", status)
                }
            ) {
                filter {
                    eq("id", expenseId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            db.expenseDao().getExpenseById(expenseId)?.let {
                db.expenseDao().insertExpense(it.copy(status = status))
            }
        }
    }

    // --- Notifications ---
    suspend fun getAllNotifications(): List<Notification> = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("notifications").get().await()
            snapshot.toObjects(Notification::class.java).sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            db.notificationDao().getAllNotifications()
        }
    }

    suspend fun getUnreadNotificationsCount(): Int = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("notifications").get().await()
            val list = snapshot.toObjects(Notification::class.java)
            list.filter { !it.isRead }.size
        } catch (e: Exception) {
            db.notificationDao().getUnreadCount()
        }
    }

    suspend fun markNotificationsAsRead() = withContext(Dispatchers.IO) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("notifications").get().await()
            val list = snapshot.toObjects(Notification::class.java)
            for (notif in list) {
                if (!notif.isRead) {
                    firestore.collection("notifications").document(notif.id.toString()).set(notif.copy(isRead = true)).await()
                }
            }
        } catch (e: Exception) {
            db.notificationDao().markAllAsRead()
        }
    }

    suspend fun clearAllNotifications() = withContext(Dispatchers.IO) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("notifications").get().await()
            for (doc in snapshot.documents) {
                firestore.collection("notifications").document(doc.id).delete().await()
            }
        } catch (e: java.lang.Exception) {
            // Firestore write error
        }
        try {
            db.notificationDao().deleteAllNotifications()
        } catch (e: java.lang.Exception) {
            // Local DB delete error
        }
    }

    suspend fun deleteNotification(id: Long) = withContext(Dispatchers.IO) {
        try {
            FirebaseFirestore.getInstance().collection("notifications").document(id.toString()).delete().await()
        } catch (e: java.lang.Exception) {
            // Remote delete failed
        }
        try {
            db.notificationDao().deleteNotification(id)
        } catch (e: java.lang.Exception) {
            // Local delete failed
        }
    }

    suspend fun postNotification(title: String, description: String, type: String): Long = withContext(Dispatchers.IO) {
        simulateNetwork()
        val id = System.currentTimeMillis()
        val notif = Notification(
            id = id,
            title = title,
            description = description,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            type = type
        )
        try {
            FirebaseFirestore.getInstance().collection("notifications").document(id.toString()).set(notif).await()
        } catch (e: Exception) {
            db.notificationDao().insertNotification(notif)
        }
        id
    }

    // --- Banners ---
    suspend fun getAllBanners(): List<Banner> = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("banners").get().await()
            snapshot.toObjects(Banner::class.java)
        } catch (e: Exception) {
            db.bannerDao().getAllBanners()
        }
    }

    suspend fun addBanner(banner: Banner): Long = withContext(Dispatchers.IO) {
        val id = banner.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        val finalBanner = banner.copy(id = id)
        try {
            FirebaseFirestore.getInstance().collection("banners").document(id.toString()).set(finalBanner).await()
        } catch (e: Exception) {
            db.bannerDao().insertBanner(finalBanner)
        }
        id
    }

    suspend fun deleteBanner(id: Long) = withContext(Dispatchers.IO) {
        try {
            FirebaseFirestore.getInstance().collection("banners").document(id.toString()).delete().await()
        } catch (e: Exception) {
            db.bannerDao().deleteBanner(id)
        }
    }

    // --- Videos ---
    suspend fun getAllVideos(): List<Video> = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("videos").get().await()
            snapshot.toObjects(Video::class.java)
        } catch (e: Exception) {
            db.videoDao().getAllVideos()
        }
    }

    suspend fun searchVideos(query: String): List<Video> = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("videos").get().await()
            val list = snapshot.toObjects(Video::class.java)
            if (query.isBlank()) {
                list
            } else {
                list.filter { it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
            }
        } catch (e: Exception) {
            if (query.isBlank()) {
                db.videoDao().getAllVideos()
            } else {
                db.videoDao().searchVideos(query)
            }
        }
    }

    suspend fun addVideo(video: Video): Long = withContext(Dispatchers.IO) {
        val id = video.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        val finalVideo = video.copy(id = id)
        try {
            FirebaseFirestore.getInstance().collection("videos").document(id.toString()).set(finalVideo).await()
        } catch (e: Exception) {
            db.videoDao().insertVideo(finalVideo)
        }
        id
    }

    suspend fun deleteVideo(id: Long) = withContext(Dispatchers.IO) {
        try {
            FirebaseFirestore.getInstance().collection("videos").document(id.toString()).delete().await()
        } catch (e: Exception) {
            db.videoDao().deleteVideo(id)
        }
    }

    // --- Holidays ---
    suspend fun getAllHolidays(): List<Holiday> = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("holidays").get().await()
            snapshot.toObjects(Holiday::class.java).sortedBy { it.date }
        } catch (e: Exception) {
            db.holidayDao().getAllHolidays()
        }
    }

    suspend fun addHoliday(holiday: Holiday): Long = withContext(Dispatchers.IO) {
        val id = holiday.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        val finalHoliday = holiday.copy(id = id)
        try {
            FirebaseFirestore.getInstance().collection("holidays").document(id.toString()).set(finalHoliday).await()
        } catch (e: Exception) {
            db.holidayDao().insertHoliday(finalHoliday)
        }
        id
    }

    suspend fun deleteHoliday(id: Long) = withContext(Dispatchers.IO) {
        try {
            FirebaseFirestore.getInstance().collection("holidays").document(id.toString()).delete().await()
        } catch (e: Exception) {
            db.holidayDao().deleteHoliday(id)
        }
    }

    // --- Regularizations ---
    suspend fun getRegularizationsForEmployee(employeeId: String): List<RegularizationRequest> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("regularizations").select {
                filter {
                    eq("employeeId", employeeId)
                }
            }.decodeList<RegularizationRequest>().sortedByDescending { it.date }
        } catch (e: Exception) {
            e.printStackTrace()
            db.regularizationDao().getRegularizationsForEmployee(employeeId)
        }
    }

    suspend fun applyRegularization(request: RegularizationRequest): Long = withContext(Dispatchers.IO) {
        simulateNetwork()
        val id = System.currentTimeMillis()
        val finalReq = request.copy(id = id)
        try {
            SupabaseProvider.client.from("regularizations").insert(finalReq)
        } catch (e: Exception) {
            e.printStackTrace()
            db.regularizationDao().insertRegularization(finalReq)
        }
        id
    }

    suspend fun getAllRegularizations(): List<RegularizationRequest> = withContext(Dispatchers.IO) {
        try {
            SupabaseProvider.client.from("regularizations").select().decodeList<RegularizationRequest>().sortedByDescending { it.date }
        } catch (e: Exception) {
            e.printStackTrace()
            db.regularizationDao().getAllRegularizations()
        }
    }

    suspend fun updateRegularizationStatus(reqId: Long, status: String) = withContext(Dispatchers.IO) {
        simulateNetwork()
        try {
            SupabaseProvider.client.from("regularizations").update(
                {
                    set("status", status)
                }
            ) {
                filter {
                    eq("id", reqId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            db.regularizationDao().getRegularizationById(reqId)?.let {
                db.regularizationDao().insertRegularization(it.copy(status = status))
            }
        }
    }

    // --- Company Info ---
    suspend fun getCompanyInfo(): CompanyInfo? = withContext(Dispatchers.IO) {
        try {
            val doc = FirebaseFirestore.getInstance().collection("company_info").document("1").get().await()
            doc.toObject(CompanyInfo::class.java)
        } catch (e: Exception) {
            db.companyInfoDao().getCompanyInfo()
        }
    }

    suspend fun updateCompanyInfo(info: CompanyInfo) = withContext(Dispatchers.IO) {
        simulateNetwork()
        try {
            FirebaseFirestore.getInstance().collection("company_info").document("1").set(info).await()
        } catch (e: Exception) {
            db.companyInfoDao().insertCompanyInfo(info)
        }
    }

    // --- Shifts ---
    suspend fun getAllShifts(): List<Shift> = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("shifts").get().await()
            val list = snapshot.toObjects(Shift::class.java).sortedBy { it.name }
            for (shift in list) {
                db.shiftDao().insertShift(shift)
            }
            list
        } catch (e: Exception) {
            db.shiftDao().getAllShifts()
        }
    }

    suspend fun addShift(shift: Shift): Long = withContext(Dispatchers.IO) {
        val id = shift.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        val finalShift = shift.copy(id = id)
        try {
            FirebaseFirestore.getInstance().collection("shifts").document(id.toString()).set(finalShift).await()
        } catch (e: Exception) {
            // Ignore
        }
        db.shiftDao().insertShift(finalShift)
        id
    }

    suspend fun deleteShift(id: Long) = withContext(Dispatchers.IO) {
        try {
            FirebaseFirestore.getInstance().collection("shifts").document(id.toString()).delete().await()
        } catch (e: Exception) {
            // Ignore
        }
        db.shiftDao().deleteShift(id)
    }
}
