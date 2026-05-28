package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- User operations ---
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE isDonorActive = 1")
    fun getActiveDonorsFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    // --- Blood Requests operations ---
    @Query("SELECT * FROM blood_requests ORDER BY timestamp DESC")
    fun getAllBloodRequestsFlow(): Flow<List<BloodRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBloodRequest(request: BloodRequestEntity): Long

    @Query("UPDATE blood_requests SET status = :status WHERE id = :requestId")
    suspend fun updateRequestStatus(requestId: Int, status: String)

    // --- Donation History operations ---
    @Query("SELECT * FROM donations WHERE userId = :userId ORDER BY donationDate DESC")
    fun getDonationsForUserFlow(userId: Int): Flow<List<DonationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDonation(donation: DonationEntity)

    // --- Hospital Notifications operations ---
    @Query("SELECT * FROM hospital_notifications ORDER BY timestamp DESC")
    fun getAllHospitalNotificationsFlow(): Flow<List<HospitalNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHospitalNotification(notification: HospitalNotificationEntity)

    @Query("UPDATE hospital_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Int)
}
