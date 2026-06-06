package com.example.hrapp.data.repository

import com.example.hrapp.data.db.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

object FirebaseSeeder {

    suspend fun seedDatabaseIfEmpty(localDb: AppDatabase) {
        // Always seed local Room database to guarantee offline login/validation works
        try {
            val empDao = localDb.employeeDao()
            val emp1 = Employee(
                id = 1,
                name = "Vaibhav Tormal",
                employeeId = "EMP102",
                designation = "Senior Android Engineer",
                department = "Product Engineering",
                mobile = "9812345678",
                email = "employee@company.com",
                reportingManager = "Ramesh Sharma",
                joiningDate = "2024-03-01",
                profilePhotoUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6",
                role = "EMPLOYEE",
                loginEnabled = true
            )
            val emp2 = Employee(
                id = 2,
                name = "Ramesh Sharma",
                employeeId = "EMP001",
                designation = "HR Director",
                department = "Human Resources",
                mobile = "9876543210",
                email = "admin@company.com",
                reportingManager = "Board of Directors",
                joiningDate = "2020-01-15",
                profilePhotoUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d",
                role = "ADMIN",
                loginEnabled = true
            )
            val masterEmp = Employee(
                id = 9999,
                name = "Vaibhav Tormal",
                employeeId = "OWNER01",
                designation = "Founder & Owner",
                department = "Management",
                mobile = "8668842351",
                email = "vaibhavtormal82@gmail.com",
                reportingManager = "None",
                profilePhotoUrl = "",
                role = "ADMIN",
                loginEnabled = true
            )
            empDao.insertEmployee(emp1)
            empDao.insertEmployee(emp2)
            empDao.insertEmployee(masterEmp)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val firestore = FirebaseFirestore.getInstance()
        try {
            // Force overwrite seeder data on startup to reset cached duplicates
            seedData(firestore)

            // Ensure owner account always exists and is not disabled
            val ownerDoc = firestore.collection("employees").document("vaibhavtormal82@gmail.com").get().await()
            if (!ownerDoc.exists()) {
                val ownerEmp = Employee(
                    id = 9999,
                    name = "Vaibhav Tormal",
                    employeeId = "OWNER01",
                    designation = "Founder & Owner",
                    department = "Management",
                    mobile = "8668842351",
                    email = "vaibhavtormal82@gmail.com",
                    reportingManager = "None",
                    profilePhotoUrl = "",
                    role = "ADMIN",
                    loginEnabled = true
                )
                firestore.collection("employees").document(ownerEmp.email).set(ownerEmp).await()
            } else {
                val existingOwner = ownerDoc.toObject(Employee::class.java)
                if (existingOwner != null) {
                    val updatedOwner = existingOwner.copy(loginEnabled = true, role = "ADMIN", mobile = "8668842351")
                    firestore.collection("employees").document(updatedOwner.email).set(updatedOwner).await()
                }
            }
        } catch (e: Exception) {
            // Safe fallback if firestore config/connectivity fails
        }
    }

    private suspend fun seedData(db: FirebaseFirestore) {
        // 1. Seed Employees
        val emp1 = Employee(
            id = 1,
            name = "Vaibhav Tormal",
            employeeId = "EMP102",
            designation = "Senior Android Engineer",
            department = "Product Engineering",
            mobile = "9812345678",
            email = "employee@company.com",
            reportingManager = "Ramesh Sharma",
            joiningDate = "2024-03-01",
            profilePhotoUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6",
            role = "EMPLOYEE",
            loginEnabled = true
        )
        val emp2 = Employee(
            id = 2,
            name = "Ramesh Sharma",
            employeeId = "EMP001",
            designation = "HR Director",
            department = "Human Resources",
            mobile = "9876543210",
            email = "admin@company.com",
            reportingManager = "Board of Directors",
            joiningDate = "2020-01-15",
            profilePhotoUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d",
            role = "ADMIN",
            loginEnabled = true
        )
        val masterEmp = Employee(
            id = 9999,
            name = "Vaibhav Tormal",
            employeeId = "OWNER01",
            designation = "Founder & Owner",
            department = "Management",
            mobile = "8668842351",
            email = "vaibhavtormal82@gmail.com",
            reportingManager = "None",
            profilePhotoUrl = "",
            role = "ADMIN",
            loginEnabled = true
        )
        // Seed Employees only if they do not exist
        if (!db.collection("employees").document(emp1.email).get().await().exists()) {
            db.collection("employees").document(emp1.email).set(emp1).await()
        }
        if (!db.collection("employees").document(emp2.email).get().await().exists()) {
            db.collection("employees").document(emp2.email).set(emp2).await()
        }
        if (!db.collection("employees").document(masterEmp.email).get().await().exists()) {
            db.collection("employees").document(masterEmp.email).set(masterEmp).await()
        }

        // 2. Seed Sliding Banners
        val banners = listOf(
            Banner(
                id = 1,
                title = "Welcome to HR Portal!",
                description = "Manage your attendance, leaves, and expenses seamlessly.",
                imageUrl = "https://images.unsplash.com/photo-1557804506-669a67965ba0",
                clickAction = "about_company"
            ),
            Banner(
                id = 2,
                title = "Annual Hackathon 2026",
                description = "Register by June 15th to showcase your innovations.",
                imageUrl = "https://images.unsplash.com/photo-1515187029135-18ee286d815b",
                clickAction = "videos"
            ),
            Banner(
                id = 3,
                title = "New Health Insurance Policy",
                description = "Download the updated guidelines from settings.",
                imageUrl = "https://images.unsplash.com/photo-1454165804606-c3d57bc86b40",
                clickAction = "settings"
            )
        )
        for (banner in banners) {
            db.collection("banners").document(banner.id.toString()).set(banner).await()
        }

        // 3. Clear/Delete dummy videos from Firestore to keep it clean
        try {
            db.collection("videos").document("1").delete().await()
            db.collection("videos").document("2").delete().await()
            db.collection("videos").document("3").delete().await()
        } catch (e: Exception) {
            // Ignore
        }

        // 4. Seed Holidays for 2026
        val holidays = listOf(
            Holiday(id = 1, name = "New Year's Day", date = "2026-01-01", type = "National"),
            Holiday(id = 2, name = "Republic Day", date = "2026-01-26", type = "National"),
            Holiday(id = 3, name = "Holi Festival", date = "2026-03-04", type = "State"),
            Holiday(id = 4, name = "Good Friday", date = "2026-04-03", type = "Company"),
            Holiday(id = 5, name = "Labor Day", date = "2026-05-01", type = "Company"),
            Holiday(id = 6, name = "Independence Day", date = "2026-08-15", type = "National"),
            Holiday(id = 7, name = "Gandhi Jayanti", date = "2026-10-02", type = "National"),
            Holiday(id = 8, name = "Diwali Festival", date = "2026-11-09", type = "National"),
            Holiday(id = 9, name = "Christmas Day", date = "2026-12-25", type = "National")
        )
        for (holiday in holidays) {
            db.collection("holidays").document(holiday.id.toString()).set(holiday).await()
        }

        // 5. Clear/Delete dummy notifications from Firestore to keep it clean
        try {
            db.collection("notifications").document("1").delete().await()
            db.collection("notifications").document("2").delete().await()
        } catch (e: Exception) {
            // Ignore
        }

        // 6. Seed Company Info
        val companyInfo = CompanyInfo(
            id = 1,
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
        db.collection("company_info").document(companyInfo.id.toString()).set(companyInfo).await()

        // 7. Seed Shifts
        val shifts = listOf(
            Shift(id = 1, name = "General Shift", startTime = "09:00 AM", endTime = "06:00 PM"),
            Shift(id = 2, name = "Morning Shift", startTime = "06:00 AM", endTime = "02:00 PM"),
            Shift(id = 3, name = "Night Shift", startTime = "10:00 PM", endTime = "06:00 AM")
        )
        for (shift in shifts) {
            db.collection("shifts").document(shift.id.toString()).set(shift).await()
        }
    }
}
