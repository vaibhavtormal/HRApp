package com.example.hrapp.data.db

import androidx.room.*

@Dao
interface EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Long): Employee?

    @Query("SELECT * FROM employees WHERE email = :email LIMIT 1")
    suspend fun getEmployeeByEmail(email: String): Employee?

    @Query("SELECT * FROM employees WHERE mobile = :mobile LIMIT 1")
    suspend fun getEmployeeByMobile(mobile: String): Employee?

    @Query("SELECT * FROM employees ORDER BY name ASC")
    suspend fun getAllEmployees(): List<Employee>

    @Query("SELECT COUNT(*) FROM employees")
    suspend fun getEmployeeCount(): Int
}

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance): Long

    @Update
    suspend fun updateAttendance(attendance: Attendance)

    @Query("SELECT * FROM attendance WHERE id = :id")
    suspend fun getAttendanceById(id: Long): Attendance?

    @Query("SELECT * FROM attendance WHERE employeeId = :employeeId AND date = :date LIMIT 1")
    suspend fun getAttendanceByDate(employeeId: String, date: String): Attendance?

    @Query("SELECT * FROM attendance WHERE employeeId = :employeeId ORDER BY date DESC")
    suspend fun getAttendanceForEmployee(employeeId: String): List<Attendance>

    @Query("SELECT * FROM attendance ORDER BY date DESC, punchInTime DESC")
    suspend fun getAllAttendance(): List<Attendance>

    @Query("SELECT * FROM attendance WHERE date = :date")
    suspend fun getAttendanceByDateString(date: String): List<Attendance>
}

@Dao
interface LeaveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeave(leaveRequest: LeaveRequest): Long

    @Update
    suspend fun updateLeave(leaveRequest: LeaveRequest)

    @Query("SELECT * FROM leave_requests WHERE id = :id")
    suspend fun getLeaveById(id: Long): LeaveRequest?

    @Query("SELECT * FROM leave_requests WHERE employeeId = :employeeId ORDER BY fromDate DESC")
    suspend fun getLeavesForEmployee(employeeId: String): List<LeaveRequest>

    @Query("SELECT * FROM leave_requests ORDER BY fromDate DESC")
    suspend fun getAllLeaves(): List<LeaveRequest>
}

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE employeeId = :employeeId ORDER BY expenseDate DESC")
    suspend fun getExpensesForEmployee(employeeId: String): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    suspend fun getAllExpenses(): List<Expense>
}

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification): Long

    @Update
    suspend fun updateNotification(notification: Notification)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAllNotifications(): List<Notification>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    suspend fun getUnreadCount(): Int

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)
}

@Dao
interface BannerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBanner(banner: Banner): Long

    @Query("DELETE FROM banners WHERE id = :id")
    suspend fun deleteBanner(id: Long)

    @Query("SELECT * FROM banners ORDER BY id DESC")
    suspend fun getAllBanners(): List<Banner>
}

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: Video): Long

    @Update
    suspend fun updateVideo(video: Video)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideo(id: Long)

    @Query("SELECT * FROM videos ORDER BY id DESC")
    suspend fun getAllVideos(): List<Video>

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    suspend fun searchVideos(query: String): List<Video>
}

@Dao
interface HolidayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoliday(holiday: Holiday): Long

    @Query("DELETE FROM holidays WHERE id = :id")
    suspend fun deleteHoliday(id: Long)

    @Query("SELECT * FROM holidays ORDER BY date ASC")
    suspend fun getAllHolidays(): List<Holiday>
}

@Dao
interface RegularizationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegularization(request: RegularizationRequest): Long

    @Update
    suspend fun updateRegularization(request: RegularizationRequest)

    @Query("SELECT * FROM regularizations WHERE id = :id")
    suspend fun getRegularizationById(id: Long): RegularizationRequest?

    @Query("SELECT * FROM regularizations WHERE employeeId = :employeeId ORDER BY date DESC")
    suspend fun getRegularizationsForEmployee(employeeId: String): List<RegularizationRequest>

    @Query("SELECT * FROM regularizations ORDER BY date DESC")
    suspend fun getAllRegularizations(): List<RegularizationRequest>
}

@Dao
interface CompanyInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanyInfo(info: CompanyInfo): Long

    @Query("SELECT * FROM company_info WHERE id = 1 LIMIT 1")
    suspend fun getCompanyInfo(): CompanyInfo?
}

@Dao
interface ShiftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: Shift): Long

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun deleteShift(id: Long)

    @Query("SELECT * FROM shifts ORDER BY name ASC")
    suspend fun getAllShifts(): List<Shift>
}
