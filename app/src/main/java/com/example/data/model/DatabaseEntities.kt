package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dob: String,
    val bloodGroup: String,
    val sex: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val contact: String,
    val email: String,
    val passwordHash: String,
    val isVerified: Boolean = false,
    val isDonorActive: Boolean = true,
    val lastDonationTime: Long? = null
)

@Entity(tableName = "blood_requests")
data class BloodRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientName: String,
    val bloodGroup: String,
    val requiredUnits: Int,
    val hospitalName: String,
    val contactNumber: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val status: String, // "ACTIVE", "FULFILLED", "CANCELLED"
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = ""
)

@Entity(tableName = "donations")
data class DonationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val donationDate: Long,
    val facilityName: String,
    val notes: String = "",
    val bloodGroup: String
)

@Entity(tableName = "hospital_notifications")
data class HospitalNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hospitalName: String,
    val bloodGroup: String,
    val urgency: String, // "CRITICAL", "HIGH", "NORMAL"
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
