package com.example.hrapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Employee::class,
        Attendance::class,
        LeaveRequest::class,
        Expense::class,
        Notification::class,
        Banner::class,
        Video::class,
        Holiday::class,
        RegularizationRequest::class,
        CompanyInfo::class,
        Shift::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun employeeDao(): EmployeeDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun leaveDao(): LeaveDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun notificationDao(): NotificationDao
    abstract fun bannerDao(): BannerDao
    abstract fun videoDao(): VideoDao
    abstract fun holidayDao(): HolidayDao
    abstract fun regularizationDao(): RegularizationDao
    abstract fun companyInfoDao(): CompanyInfoDao
    abstract fun shiftDao(): ShiftDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hrapp_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database)
                }
            }
        }

        suspend fun populateDatabase(db: AppDatabase) {
            // 2. Insert sliding banners
            db.bannerDao().insertBanner(
                Banner(
                    title = "Welcome to HR Portal!",
                    description = "Manage your attendance, leaves, and expenses seamlessly.",
                    imageUrl = "https://images.unsplash.com/photo-1557804506-669a67965ba0",
                    clickAction = "about_company"
                )
            )
            db.bannerDao().insertBanner(
                Banner(
                    title = "Annual Hackathon 2026",
                    description = "Register by June 15th to showcase your innovations.",
                    imageUrl = "https://images.unsplash.com/photo-1515187029135-18ee286d815b",
                    clickAction = "videos"
                )
            )
            db.bannerDao().insertBanner(
                Banner(
                    title = "New Health Insurance Policy",
                    description = "Download the updated guidelines from settings.",
                    imageUrl = "https://images.unsplash.com/photo-1454165804606-c3d57bc86b40",
                    clickAction = "settings"
                )
            )


            // 4. Insert default holidays for 2026
            db.holidayDao().insertHoliday(Holiday(name = "New Year's Day", date = "2026-01-01", type = "National"))
            db.holidayDao().insertHoliday(Holiday(name = "Republic Day", date = "2026-01-26", type = "National"))
            db.holidayDao().insertHoliday(Holiday(name = "Holi Festival", date = "2026-03-04", type = "State"))
            db.holidayDao().insertHoliday(Holiday(name = "Good Friday", date = "2026-04-03", type = "Company"))
            db.holidayDao().insertHoliday(Holiday(name = "Labor Day", date = "2026-05-01", type = "Company"))
            db.holidayDao().insertHoliday(Holiday(name = "Independence Day", date = "2026-08-15", type = "National"))
            db.holidayDao().insertHoliday(Holiday(name = "Gandhi Jayanti", date = "2026-10-02", type = "National"))
            db.holidayDao().insertHoliday(Holiday(name = "Diwali Festival", date = "2026-11-09", type = "National"))
            db.holidayDao().insertHoliday(Holiday(name = "Christmas Day", date = "2026-12-25", type = "National"))

            // 6. Insert Company Info
            db.companyInfoDao().insertCompanyInfo(
                CompanyInfo(
                    logoUrl = "https://images.unsplash.com/photo-1516321318423-f06f85e504b3",
                    aboutText = "Stubborn Solutions is a globally recognized software product organization delivering state of the art HR systems, cloud integrations, and enterprise mobile solutions.",
                    history = "Founded in 2018 with a small team of 5 developers, Stubborn has expanded to a workforce of over 500 professionals worldwide, pioneering workplace productivity suites.",
                    mission = "To empower enterprises with intelligent, mobile-first tools that simplify work and drive efficiency.",
                    vision = "To be the leading platform for corporate workspace enablement and employee wellness.",
                    values = "Integrity, Innovation, Inclusion, Excellence, Employee First",
                    directorMessage = "Welcome to the team! Our employees are our greatest asset. This app was built to make your work life smoother and help you connect with all corporate support features. Let's achieve great milestones together.",
                    contactPhone = "8668842351",
                    contactEmail = "vaibhavtormal82@gmail.com",
                    website = "Coming Soon #Stubborn",
                    socialLinksJson = "[{\"name\":\"LinkedIn\",\"url\":\"https://linkedin.com\"},{\"name\":\"Twitter\",\"url\":\"https://twitter.com\"},{\"name\":\"Facebook\",\"url\":\"https://facebook.com\"}]"
                )
            )

            // 7. Insert default shifts
            db.shiftDao().insertShift(Shift(name = "General Shift", startTime = "09:00 AM", endTime = "06:00 PM"))
            db.shiftDao().insertShift(Shift(name = "Morning Shift", startTime = "06:00 AM", endTime = "02:00 PM"))
            db.shiftDao().insertShift(Shift(name = "Night Shift", startTime = "10:00 PM", endTime = "06:00 AM"))
        }
    }
}
