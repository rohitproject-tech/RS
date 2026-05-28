package com.example.data.repository

import com.example.data.dao.AppDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

class RaktaSewaRepository(private val appDao: AppDao) {

    // Users
    suspend fun getUserByEmail(email: String): UserEntity? = appDao.getUserByEmail(email)
    suspend fun getUserById(userId: Int): UserEntity? = appDao.getUserById(userId)
    suspend fun registerUser(user: UserEntity): Long = appDao.insertUser(user)
    suspend fun updateUserProfile(user: UserEntity) = appDao.updateUser(user)
    fun getActiveDonorsFlow(): Flow<List<UserEntity>> = appDao.getActiveDonorsFlow()
    suspend fun getAllUsers(): List<UserEntity> = appDao.getAllUsers()

    // Blood requests
    fun getAllBloodRequestsFlow(): Flow<List<BloodRequestEntity>> = appDao.getAllBloodRequestsFlow()
    suspend fun createBloodRequest(request: BloodRequestEntity): Long = appDao.insertBloodRequest(request)
    suspend fun updateRequestStatus(requestId: Int, status: String) = appDao.updateRequestStatus(requestId, status)

    // Donations
    fun getDonationsForUserFlow(userId: Int): Flow<List<DonationEntity>> = appDao.getDonationsForUserFlow(userId)
    suspend fun addDonation(donation: DonationEntity) = appDao.insertDonation(donation)

    // Notifications
    fun getAllHospitalNotificationsFlow(): Flow<List<HospitalNotificationEntity>> = appDao.getAllHospitalNotificationsFlow()
    suspend fun sendHospitalNotification(notification: HospitalNotificationEntity) = appDao.insertHospitalNotification(notification)
    suspend fun markNotificationAsRead(id: Int) = appDao.markNotificationAsRead(id)
}
