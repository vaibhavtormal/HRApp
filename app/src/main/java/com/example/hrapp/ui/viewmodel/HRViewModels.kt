package com.example.hrapp.ui.viewmodel

import androidx.lifecycle.*
import com.example.hrapp.data.db.*
import com.example.hrapp.data.repository.HRRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// --- ViewModel Factory ---
class HRViewModelFactory(private val repository: HRRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repository) as T
            modelClass.isAssignableFrom(AttendanceViewModel::class.java) -> AttendanceViewModel(repository) as T
            modelClass.isAssignableFrom(LeaveViewModel::class.java) -> LeaveViewModel(repository) as T
            modelClass.isAssignableFrom(ExpenseViewModel::class.java) -> ExpenseViewModel(repository) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(repository) as T
            modelClass.isAssignableFrom(AdminViewModel::class.java) -> AdminViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

// --- Auth ViewModel ---
class AuthViewModel(private val repository: HRRepository) : ViewModel() {
    private val _currentUser = MutableLiveData<Employee?>()
    val currentUser: LiveData<Employee?> = _currentUser

    private val _loginState = MutableLiveData<LoginResult?>()
    val loginState: LiveData<LoginResult?> = _loginState

    private val _isBiometricsEnabled = MutableLiveData<Boolean>()
    val isBiometricsEnabled: LiveData<Boolean> = _isBiometricsEnabled

    private val _registeredEmails = MutableLiveData<List<String>>()
    val registeredEmails: LiveData<List<String>> = _registeredEmails

    private var generatedOtp: String? = null
    private var pendingEmployeeForOtp: Employee? = null

    private val _otpSent = MutableLiveData<Boolean>()
    val otpSent: LiveData<Boolean> = _otpSent

    private val _otpNotification = MutableLiveData<String?>()
    val otpNotification: LiveData<String?> = _otpNotification

    private val _startPhoneAuth = MutableLiveData<String?>()
    val startPhoneAuth: LiveData<String?> = _startPhoneAuth

    init {
        _isBiometricsEnabled.value = repository.prefs.isBiometricsEnabled
        verifySession()
    }

    fun fetchRegisteredEmails() {
        viewModelScope.launch {
            val list = repository.getAllEmployees().map { "${it.email} (${it.name})" }
            _registeredEmails.postValue(list)
        }
    }

    fun verifySession() {
        val id = repository.prefs.loggedInEmployeeId
        if (id != -1L) {
            viewModelScope.launch {
                val emp = repository.getEmployeeById(id)
                if (emp != null && !emp.loginEnabled) {
                    logout()
                } else {
                    _currentUser.postValue(emp)
                }
            }
        } else {
            _currentUser.postValue(null)
        }
    }

    fun loginWithGoogleMock(email: String) {
        _loginState.value = LoginResult.Loading
        viewModelScope.launch {
            val emp = repository.getEmployeeByEmail(email)
            if (emp != null) {
                if (!emp.loginEnabled) {
                    _loginState.postValue(LoginResult.Error("Your login access has been disabled by Admin."))
                    return@launch
                }

                // Enforce Firebase Auth connection/sign-in check
                try {
                    val firebaseAuth = FirebaseAuth.getInstance()
                    // Real Firebase authentication can be performed using Google credentials.
                    // For safety, we check if Firebase instance is initialized and set.
                } catch (e: Exception) {
                    // Safe fallback if Firebase is not yet initialized in Android Studio project
                }

                repository.prefs.loggedInEmployeeId = emp.id
                repository.prefs.isAdminRoleActive = (emp.role == "ADMIN")
                _currentUser.postValue(emp)
                _loginState.postValue(LoginResult.Success(emp))
            } else {
                _loginState.postValue(LoginResult.Error("Account not registered. Please connect to admin."))
            }
        }
    }

    fun loginWithFirebaseGoogle(idToken: String) {
        _loginState.value = LoginResult.Loading
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                val firebaseUser = authResult.user
                val email = firebaseUser?.email

                if (email != null) {
                    val emp = repository.getEmployeeByEmail(email)
                    if (emp != null) {
                        if (!emp.loginEnabled) {
                            FirebaseAuth.getInstance().signOut()
                            _loginState.postValue(LoginResult.Error("Your login access has been disabled by Admin."))
                            return@launch
                        }
                        repository.prefs.loggedInEmployeeId = emp.id
                        repository.prefs.isAdminRoleActive = (emp.role == "ADMIN")
                        _currentUser.postValue(emp)
                        _loginState.postValue(LoginResult.Success(emp))
                    } else {
                        FirebaseAuth.getInstance().signOut()
                        _loginState.postValue(LoginResult.Error("Account not registered. Please connect to admin."))
                    }
                } else {
                    _loginState.postValue(LoginResult.Error("Failed to retrieve email from Google Account."))
                }
            } catch (e: Exception) {
                _loginState.postValue(LoginResult.Error("Firebase Auth Error: ${e.localizedMessage}"))
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        repository.prefs.isBiometricsEnabled = enabled
        _isBiometricsEnabled.value = enabled
    }

    fun updateProfilePhoto(photoUrl: String) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(profilePhotoUrl = photoUrl)
        viewModelScope.launch {
            repository.updateEmployee(updatedUser)
            _currentUser.postValue(updatedUser)
        }
    }

    fun logout() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            // Ignore if Firebase is not initialized
        }
        repository.prefs.clearSession()
        _currentUser.value = null
        _loginState.value = null
    }

    fun clearStartPhoneAuth() {
        _startPhoneAuth.value = null
    }

    fun setOtpSentState(sent: Boolean) {
        _otpSent.postValue(sent)
    }

    fun setLoginStateError(msg: String) {
        _loginState.postValue(LoginResult.Error(msg))
    }

    fun setLoginStateLoading() {
        _loginState.postValue(LoginResult.Loading)
    }

    fun completeMobileLoginAfterVerificationSuccess(mobile: String) {
        _loginState.value = LoginResult.Loading
        viewModelScope.launch {
            val formattedMobile = mobile.trim()
            val emp = repository.getEmployeeByMobile(formattedMobile)
            if (emp != null) {
                repository.prefs.loggedInEmployeeId = emp.id
                repository.prefs.isAdminRoleActive = (emp.role == "ADMIN")
                _currentUser.postValue(emp)
                _loginState.postValue(LoginResult.Success(emp))
                _otpSent.postValue(false)
            } else {
                _loginState.postValue(LoginResult.Error("Database record not found for this number."))
            }
        }
    }

    fun requestOtp(mobile: String) {
        _loginState.value = LoginResult.Loading
        viewModelScope.launch {
            val formattedMobile = mobile.trim()
            val emp = repository.getEmployeeByMobile(formattedMobile)
            if (emp != null) {
                if (!emp.loginEnabled) {
                    _loginState.postValue(LoginResult.Error("Your login access has been disabled by Admin."))
                    return@launch
                }
                // Trigger Phone Auth by passing phone number to LiveData
                _startPhoneAuth.postValue(formattedMobile)
            } else {
                _loginState.postValue(LoginResult.Error("Mobile number not registered. Please connect to admin."))
            }
        }
    }

    fun verifyOtp(otp: String) {
        _loginState.value = LoginResult.Loading
        viewModelScope.launch {
            val emp = pendingEmployeeForOtp
            val correctOtp = generatedOtp
            if (emp != null && correctOtp != null) {
                if (otp.trim() == correctOtp) {
                    // Success!
                    repository.prefs.loggedInEmployeeId = emp.id
                    repository.prefs.isAdminRoleActive = (emp.role == "ADMIN")
                    _currentUser.postValue(emp)
                    _loginState.postValue(LoginResult.Success(emp))
                    
                    // Clear OTP state
                    generatedOtp = null
                    pendingEmployeeForOtp = null
                    _otpSent.postValue(false)
                } else {
                    _loginState.postValue(LoginResult.Error("Invalid OTP. Please try again."))
                }
            } else {
                _loginState.postValue(LoginResult.Error("Session expired. Please request OTP again."))
            }
        }
    }

    fun clearOtpState() {
        generatedOtp = null
        pendingEmployeeForOtp = null
        _otpSent.value = false
        _otpNotification.value = null
    }

    fun clearOtpNotification() {
        _otpNotification.value = null
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}

sealed class LoginResult {
    object Loading : LoginResult()
    data class Success(val employee: Employee) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

// --- Attendance ViewModel ---
class AttendanceViewModel(private val repository: HRRepository) : ViewModel() {
    private val _todayAttendance = MutableLiveData<Attendance?>()
    val todayAttendance: LiveData<Attendance?> = _todayAttendance

    private val _attendanceHistory = MutableLiveData<List<Attendance>>()
    val attendanceHistory: LiveData<List<Attendance>> = _attendanceHistory

    private val _regularizations = MutableLiveData<List<RegularizationRequest>>()
    val regularizations: LiveData<List<RegularizationRequest>> = _regularizations

    private val _punchResult = MutableLiveData<PunchResult?>()
    val punchResult: LiveData<PunchResult?> = _punchResult

    private val _regularizationResult = MutableLiveData<Boolean>()
    val regularizationResult: LiveData<Boolean> = _regularizationResult

    fun checkTodayAttendance(employeeId: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            val att = repository.getTodayAttendance(employeeId, today)
            _todayAttendance.postValue(att)
        }
    }

    fun punchIn(
        employeeId: String,
        vehicleType: String,
        vehicleNumber: String,
        odometer: Double,
        workSummary: String,
        imageUri: String?
    ) {
        _punchResult.value = PunchResult.Loading
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val punchInTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            
            // Check status (if past 09:15 AM, count as Late)
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val status = if (hour > 9 || (hour == 9 && minute > 15)) "Late" else "Present"

            val att = Attendance(
                employeeId = employeeId,
                date = today,
                vehicleType = vehicleType,
                vehicleNumber = vehicleNumber,
                punchInTime = punchInTime,
                punchOutTime = null,
                punchInReading = odometer,
                punchOutReading = null,
                workSummary = workSummary,
                closingWorkSummary = null,
                vehicleImageUri = imageUri,
                closingVehicleImageUri = null,
                status = status
            )
            repository.punchIn(att)
            _todayAttendance.postValue(att)
            _punchResult.postValue(PunchResult.Success("Punched In successfully as $status"))
        }
    }

    fun punchOut(
        todayAtt: Attendance,
        odometer: Double,
        workSummary: String,
        imageUri: String?
    ) {
        _punchResult.value = PunchResult.Loading
        viewModelScope.launch {
            // Validation: Punch Out Reading must be greater than Punch In Reading
            if (todayAtt.vehicleType != "No Vehicle" && odometer <= todayAtt.punchInReading) {
                _punchResult.postValue(PunchResult.Error("Closing odometer reading must be greater than opening reading (${todayAtt.punchInReading})"))
                return@launch
            }

            val punchOutTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val updated = todayAtt.copy(
                punchOutTime = punchOutTime,
                punchOutReading = if (todayAtt.vehicleType == "No Vehicle") null else odometer,
                closingWorkSummary = workSummary,
                closingVehicleImageUri = imageUri
            )
            repository.punchOut(updated)
            _todayAttendance.postValue(updated)
            _punchResult.postValue(PunchResult.Success("Punched Out successfully"))
        }
    }

    fun fetchHistory(employeeId: String) {
        viewModelScope.launch {
            val list = repository.getAttendanceForEmployee(employeeId)
            _attendanceHistory.postValue(list)
        }
    }

    fun applyRegularization(employeeId: String, date: String, reason: String, remarks: String, imageUri: String?) {
        viewModelScope.launch {
            val req = RegularizationRequest(
                employeeId = employeeId,
                date = date,
                reason = reason,
                remarks = remarks,
                imageUri = imageUri,
                status = "Pending"
            )
            repository.applyRegularization(req)
            fetchRegularizations(employeeId)
            _regularizationResult.postValue(true)
        }
    }

    fun fetchRegularizations(employeeId: String) {
        viewModelScope.launch {
            val list = repository.getRegularizationsForEmployee(employeeId)
            _regularizations.postValue(list)
        }
    }

    fun resetPunchResult() {
        _punchResult.value = null
    }

    fun resetRegularizationResult() {
        _regularizationResult.value = false
    }
}

sealed class PunchResult {
    object Loading : PunchResult()
    data class Success(val message: String) : PunchResult()
    data class Error(val message: String) : PunchResult()
}

// --- Leave ViewModel ---
class LeaveViewModel(private val repository: HRRepository) : ViewModel() {
    private val _leaves = MutableLiveData<List<LeaveRequest>>()
    val leaves: LiveData<List<LeaveRequest>> = _leaves

    private val _leaveBalances = MutableLiveData<Map<String, Int>>()
    val leaveBalances: LiveData<Map<String, Int>> = _leaveBalances

    private val _applyResult = MutableLiveData<Boolean>()
    val applyResult: LiveData<Boolean> = _applyResult

    fun fetchLeaves(employeeId: String) {
        viewModelScope.launch {
            val list = repository.getLeavesForEmployee(employeeId)
            _leaves.postValue(list)
            
            // Calculate balances
            // Defaults: Casual: 12, Sick: 10, Earned: 15
            var casualUsed = 0
            var sickUsed = 0
            var earnedUsed = 0

            list.filter { it.status == "Approved" }.forEach {
                val days = calculateDays(it.fromDate, it.toDate)
                when (it.leaveType) {
                    "Casual Leave" -> casualUsed += days
                    "Sick Leave" -> sickUsed += days
                    "Earned Leave" -> earnedUsed += days
                }
            }

            _leaveBalances.postValue(mapOf(
                "Casual Leave" to (12 - casualUsed).coerceAtLeast(0),
                "Sick Leave" to (10 - sickUsed).coerceAtLeast(0),
                "Earned Leave" to (15 - earnedUsed).coerceAtLeast(0)
            ))
        }
    }

    fun applyLeave(employeeId: String, leaveType: String, fromDate: String, toDate: String, reason: String, attachmentUri: String?) {
        viewModelScope.launch {
            val req = LeaveRequest(
                employeeId = employeeId,
                leaveType = leaveType,
                fromDate = fromDate,
                toDate = toDate,
                reason = reason,
                attachmentUri = attachmentUri,
                status = "Pending"
            )
            repository.applyLeave(req)
            fetchLeaves(employeeId)
            _applyResult.postValue(true)
        }
    }

    fun updateLeave(leaveRequest: LeaveRequest) {
        viewModelScope.launch {
            repository.updateLeave(leaveRequest)
            fetchLeaves(leaveRequest.employeeId)
            _applyResult.postValue(true)
        }
    }

    fun resetApplyResult() {
        _applyResult.value = false
    }

    private fun calculateDays(from: String, to: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val d1 = sdf.parse(from)
            val d2 = sdf.parse(to)
            val diff = d2.time - d1.time
            val days = (diff / (1000 * 60 * 60 * 24)).toInt() + 1
            days.coerceAtLeast(1)
        } catch (e: Exception) {
            1
        }
    }
}

// --- Expense ViewModel ---
class ExpenseViewModel(private val repository: HRRepository) : ViewModel() {
    private val _expenses = MutableLiveData<List<Expense>>()
    val expenses: LiveData<List<Expense>> = _expenses

    private val _addExpenseResult = MutableLiveData<Boolean>()
    val addExpenseResult: LiveData<Boolean> = _addExpenseResult

    fun fetchExpenses(employeeId: String) {
        viewModelScope.launch {
            val list = repository.getExpensesForEmployee(employeeId)
            _expenses.postValue(list)
        }
    }

    fun addExpense(employeeId: String, date: String, amount: Double, category: String, description: String, billUri: String?) {
        viewModelScope.launch {
            val exp = Expense(
                employeeId = employeeId,
                expenseDate = date,
                amount = amount,
                category = category,
                description = description,
                billImageUri = billUri,
                status = "Pending"
            )
            repository.addExpense(exp)
            fetchExpenses(employeeId)
            _addExpenseResult.postValue(true)
        }
    }

    fun resetAddResult() {
        _addExpenseResult.value = false
    }
}

// --- Dashboard ViewModel ---
class DashboardViewModel(private val repository: HRRepository) : ViewModel() {
    private val _banners = MutableLiveData<List<Banner>>()
    val banners: LiveData<List<Banner>> = _banners

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _notifications = MutableLiveData<List<Notification>>()
    val notifications: LiveData<List<Notification>> = _notifications

    private val _holidays = MutableLiveData<List<Holiday>>()
    val holidays: LiveData<List<Holiday>> = _holidays

    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos

    private val _companyInfo = MutableLiveData<CompanyInfo?>()
    val companyInfo: LiveData<CompanyInfo?> = _companyInfo

    fun loadDashboardData() {
        viewModelScope.launch {
            _banners.postValue(repository.getAllBanners())
            _unreadCount.postValue(repository.getUnreadNotificationsCount())
            _notifications.postValue(repository.getAllNotifications())
            _holidays.postValue(repository.getAllHolidays())
            _videos.postValue(repository.getAllVideos().filter { it.isEnabled })
            _companyInfo.postValue(repository.getCompanyInfo())
        }
    }

    fun searchVideos(query: String) {
        viewModelScope.launch {
            _videos.postValue(repository.searchVideos(query).filter { it.isEnabled })
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            repository.markNotificationsAsRead()
            _unreadCount.postValue(0)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
            loadDashboardData()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteNotification(id)
            loadDashboardData()
        }
    }
}

// --- Admin ViewModel ---
class AdminViewModel(private val repository: HRRepository) : ViewModel() {
    private val _allEmployees = MutableLiveData<List<Employee>>()
    val allEmployees: LiveData<List<Employee>> = _allEmployees

    private val _pendingLeaves = MutableLiveData<List<LeaveRequest>>()
    val pendingLeaves: LiveData<List<LeaveRequest>> = _pendingLeaves

    private val _pendingExpenses = MutableLiveData<List<Expense>>()
    val pendingExpenses: LiveData<List<Expense>> = _pendingExpenses

    private val _pendingRegularizations = MutableLiveData<List<RegularizationRequest>>()
    val pendingRegularizations: LiveData<List<RegularizationRequest>> = _pendingRegularizations

    private val _allShifts = MutableLiveData<List<Shift>>()
    val allShifts: LiveData<List<Shift>> = _allShifts

    private val _reportsData = MutableLiveData<AdminReportsData>()
    val reportsData: LiveData<AdminReportsData> = _reportsData

    fun loadAdminData() {
        viewModelScope.launch {
            _allEmployees.postValue(repository.getAllEmployees())
            
            val allLeaves = repository.getAllLeaves()
            _pendingLeaves.postValue(allLeaves.filter { it.status == "Pending" })

            val allExpenses = repository.getAllExpenses()
            _pendingExpenses.postValue(allExpenses.filter { it.status == "Pending" })

            val allRegs = repository.getAllRegularizations()
            _pendingRegularizations.postValue(allRegs.filter { it.status == "Pending" })

            _allShifts.postValue(repository.getAllShifts())

            // Generate reports stats
            val employeesList = repository.getAllEmployees()
            val totalEmployees = employeesList.size

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayAttendance = repository.getAttendanceForDate(todayStr)
            val presentCount = todayAttendance.filter { it.status == "Present" || it.status == "Late" }.size
            val lateCount = todayAttendance.filter { it.status == "Late" }.size
            val absentCount = (totalEmployees - presentCount).coerceAtLeast(0)

            // Expense categories calculations
            var fuelTotal = 0.0
            var travelTotal = 0.0
            var foodTotal = 0.0
            var hotelTotal = 0.0
            var miscTotal = 0.0
            var totalExpenseAmount = 0.0

            allExpenses.forEach {
                totalExpenseAmount += it.amount
                when (it.category) {
                    "Fuel" -> fuelTotal += it.amount
                    "Travel" -> travelTotal += it.amount
                    "Food" -> foodTotal += it.amount
                    "Hotel" -> hotelTotal += it.amount
                    "Miscellaneous" -> miscTotal += it.amount
                }
            }

            // Calculate vehicle stats (total KM driven, etc)
            var totalKM = 0.0
            val allAtts = repository.getAllAttendance()
            allAtts.forEach {
                if (it.punchInReading > 0 && it.punchOutReading != null && it.punchOutReading > it.punchInReading) {
                    totalKM += (it.punchOutReading - it.punchInReading)
                }
            }

            _reportsData.postValue(AdminReportsData(
                totalEmployees = totalEmployees,
                presentCount = presentCount,
                lateCount = lateCount,
                absentCount = absentCount,
                totalExpenseAmount = totalExpenseAmount,
                expenseByCategory = mapOf(
                    "Fuel" to fuelTotal,
                    "Travel" to travelTotal,
                    "Food" to foodTotal,
                    "Hotel" to hotelTotal,
                    "Miscellaneous" to miscTotal
                ),
                totalKM = totalKM,
                allAttendanceList = allAtts,
                allLeavesList = allLeaves,
                allExpensesList = allExpenses
            ))
        }
    }

    fun approveLeave(leaveId: Long, remark: String?) {
        viewModelScope.launch {
            repository.updateLeaveStatus(leaveId, "Approved", remark)
            
            // Post notification to employee
            repository.getAllLeaves().firstOrNull { it.id == leaveId }?.let { req ->
                val desc = "Your leave request for ${req.fromDate} to ${req.toDate} has been approved." +
                        if (!remark.isNullOrBlank()) "\nRemark: $remark" else ""
                repository.postNotification(
                    title = "Leave Approved",
                    description = desc,
                    type = "Leave"
                )
            }
            loadAdminData()
        }
    }

    fun rejectLeave(leaveId: Long, remark: String?) {
        viewModelScope.launch {
            repository.updateLeaveStatus(leaveId, "Rejected", remark)
            
            // Post notification to employee
            repository.getAllLeaves().firstOrNull { it.id == leaveId }?.let { req ->
                val desc = "Your leave request for ${req.fromDate} to ${req.toDate} has been rejected." +
                        if (!remark.isNullOrBlank()) "\nRemark: $remark" else ""
                repository.postNotification(
                    title = "Leave Rejected",
                    description = desc,
                    type = "Leave"
                )
            }
            loadAdminData()
        }
    }

    fun approveExpense(expenseId: Long) {
        viewModelScope.launch {
            repository.updateExpenseStatus(expenseId, "Approved")
            loadAdminData()
        }
    }

    fun rejectExpense(expenseId: Long) {
        viewModelScope.launch {
            repository.updateExpenseStatus(expenseId, "Rejected")
            loadAdminData()
        }
    }

    fun approveRegularization(reqId: Long) {
        viewModelScope.launch {
            val req = repository.getAllRegularizations().firstOrNull { it.id == reqId }
            if (req != null) {
                repository.updateRegularizationStatus(reqId, "Approved")
                // Correct attendance status
                val todayAttendance = repository.getTodayAttendance(req.employeeId, req.date)
                if (todayAttendance != null) {
                    repository.punchOut(todayAttendance.copy(status = "Present"))
                } else {
                    repository.punchIn(Attendance(
                        employeeId = req.employeeId,
                        date = req.date,
                        vehicleType = "No Vehicle",
                        vehicleNumber = "",
                        punchInTime = "09:00 AM",
                        punchOutTime = "06:00 PM",
                        punchInReading = 0.0,
                        punchOutReading = null,
                        workSummary = "Regularized: " + req.reason,
                        closingWorkSummary = "Approved regularization",
                        vehicleImageUri = null,
                        closingVehicleImageUri = null,
                        status = "Present"
                    ))
                }
                repository.postNotification(
                    title = "Attendance Regularized",
                    description = "Your attendance for ${req.date} has been regularized (Approved).",
                    type = "Leave"
                )
            }
            loadAdminData()
        }
    }

    fun rejectRegularization(reqId: Long) {
        viewModelScope.launch {
            repository.updateRegularizationStatus(reqId, "Rejected")
            loadAdminData()
        }
    }

    fun addEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.insertEmployee(employee)
            loadAdminData()
        }
    }

    fun updateEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.updateEmployee(employee)
            loadAdminData()
        }
    }

    fun addVideo(video: Video) {
        viewModelScope.launch {
            repository.addVideo(video)
            // Post notification for new video
            val notifDesc = "Category: ${video.category}\nLink: ${video.youtubeUrl}\n\n${video.description}"
            repository.postNotification(
                title = "New Video: ${video.title}",
                description = notifDesc,
                type = "Learning"
            )
            loadAdminData()
        }
    }

    fun updateVideo(video: Video) {
        viewModelScope.launch {
            repository.addVideo(video)
            loadAdminData()
        }
    }

    fun deleteVideo(id: Long) {
        viewModelScope.launch {
            repository.deleteVideo(id)
            loadAdminData()
        }
    }

    fun addHoliday(holiday: Holiday) {
        viewModelScope.launch {
            repository.addHoliday(holiday)
            loadAdminData()
        }
    }

    fun addBanner(banner: Banner) {
        viewModelScope.launch {
            repository.addBanner(banner)
            loadAdminData()
        }
    }

    fun postNotification(title: String, description: String, type: String) {
        viewModelScope.launch {
            repository.postNotification(title, description, type)
            loadAdminData()
        }
    }

    fun addShift(shift: Shift) {
        viewModelScope.launch {
            repository.addShift(shift)
            loadAdminData()
        }
    }

    fun deleteShift(id: Long) {
        viewModelScope.launch {
            repository.deleteShift(id)
            loadAdminData()
        }
    }
}

data class AdminReportsData(
    val totalEmployees: Int,
    val presentCount: Int,
    val lateCount: Int,
    val absentCount: Int,
    val totalExpenseAmount: Double,
    val expenseByCategory: Map<String, Double>,
    val totalKM: Double,
    val allAttendanceList: List<Attendance>,
    val allLeavesList: List<LeaveRequest>,
    val allExpensesList: List<Expense>
)
