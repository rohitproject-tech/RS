package com.example.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.RaktaSewaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class RaktaSewaViewModel(
    application: Application,
    private val repository: RaktaSewaRepository
) : AndroidViewModel(application) {

    // Current Session State
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Database Flows
    val activeDonors: StateFlow<List<UserEntity>> = repository.getActiveDonorsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bloodRequests: StateFlow<List<BloodRequestEntity>> = repository.getAllBloodRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hospitalNotifications: StateFlow<List<HospitalNotificationEntity>> = repository.getAllHospitalNotificationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state flows derived from selection
    private val _selectedRequest = MutableStateFlow<BloodRequestEntity?>(null)
    val selectedRequest: StateFlow<BloodRequestEntity?> = _selectedRequest.asStateFlow()

    // Donation History for current active user
    val userDonations: StateFlow<List<DonationEntity>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.getDonationsForUserFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Registration Temp State (OTP Simulation)
    var pendingRegistrationUser: UserEntity? = null
    var pendingOtpCode: String = ""

    init {
        // Pre-populate database with default realistic data for Nepalese/Geographic blood network context
        viewModelScope.launch {
            val users = repository.getAllUsers()
            if (users.isEmpty()) {
                seedInitialData()
            }
        }
    }

    private fun seedInitialData() {
        viewModelScope.launch {
            // Seed a few active donors
            val hashedPw = "password123".hashSHA256()
            val sampleDonors = listOf(
                UserEntity(
                    name = "Anil Shrestha",
                    dob = "1994-05-12",
                    bloodGroup = "O+",
                    sex = "Male",
                    address = "New Baneshwor, Kathmandu",
                    latitude = 27.6913,
                    longitude = 85.3331,
                    contact = "9841234567",
                    email = "anil@gmail.com",
                    passwordHash = hashedPw,
                    isVerified = true,
                    isDonorActive = true
                ),
                UserEntity(
                    name = "Pooja Sharma",
                    dob = "1997-11-23",
                    bloodGroup = "A-",
                    sex = "Female",
                    address = "Lakeside, Pokhara",
                    latitude = 28.2096,
                    longitude = 83.9584,
                    contact = "9856012345",
                    email = "pooja@gmail.com",
                    passwordHash = hashedPw,
                    isVerified = true,
                    isDonorActive = true
                ),
                UserEntity(
                    name = "Ramesh Chaudhary",
                    dob = "1991-02-15",
                    bloodGroup = "B+",
                    sex = "Male",
                    address = "Maitighar, Kathmandu",
                    latitude = 27.6938,
                    longitude = 85.3219,
                    contact = "9841987654",
                    email = "ramesh@gmail.com",
                    passwordHash = hashedPw,
                    isVerified = true,
                    isDonorActive = true,
                    lastDonationTime = System.currentTimeMillis() - (80L * 24 * 60 * 60 * 1000) // 80 days ago (nearly eligible)
                )
            )
            for (donor in sampleDonors) {
                repository.registerUser(donor)
            }

            // Seed initial active emergency requests
            val sampleRequests = listOf(
                BloodRequestEntity(
                    patientName = "Sita Kumari Thapa",
                    bloodGroup = "O+",
                    requiredUnits = 3,
                    hospitalName = "T.U. Teaching Hospital (TUTH)",
                    contactNumber = "9801122334",
                    locationName = "Maharajgunj, Kathmandu",
                    latitude = 27.7360,
                    longitude = 85.3300,
                    status = "ACTIVE",
                    timestamp = System.currentTimeMillis() - (2L * 60 * 60 * 1000), // 2 hours ago
                    description = "Urgent requirement for cardiac surgery. Any O+ donor nearby please help!"
                ),
                BloodRequestEntity(
                    patientName = "Mingma Sherpa",
                    bloodGroup = "AB-",
                    requiredUnits = 1,
                    hospitalName = "Norvic International Hospital",
                    contactNumber = "9812233445",
                    locationName = "Thapathali, Kathmandu",
                    latitude = 27.6897,
                    longitude = 85.3184,
                    status = "ACTIVE",
                    timestamp = System.currentTimeMillis() - (30L * 60 * 1000), // 30 mins ago
                    description = "Severe anemia patient needs AB- blood urgently."
                )
            )
            for (req in sampleRequests) {
                repository.createBloodRequest(req)
            }

            // Seed initial hospital notifications
            val sampleAlerts = listOf(
                HospitalNotificationEntity(
                    hospitalName = "Kanti Children's Hospital",
                    bloodGroup = "O+",
                    urgency = "CRITICAL",
                    message = "Urgent need for 2 units of O+ blood for pediatric trauma patient.",
                    timestamp = System.currentTimeMillis() - (15L * 60 * 1000)
                ),
                HospitalNotificationEntity(
                    hospitalName = "Civil Service Hospital",
                    bloodGroup = "B+",
                    urgency = "HIGH",
                    message = "B+ blood count low in primary storage. Requesting nearby donors to schedule visit.",
                    timestamp = System.currentTimeMillis() - (4L * 60 * 60 * 1000)
                )
            )
            for (alert in sampleAlerts) {
                repository.sendHospitalNotification(alert)
            }
        }
    }

    // Hash Helper (SHA-256)
    fun String.hashSHA256(): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(this.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (ex: Exception) {
            this // fallback
        }
    }

    // --- Actions ---

    fun login(email: String, passwordText: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (email.isBlank() || passwordText.isBlank()) {
                onResult(false, "Please fill in all fields.")
                return@launch
            }
            val user = repository.getUserByEmail(email.trim())
            if (user == null) {
                onResult(false, "User with this email not found.")
                return@launch
            }
            val hashed = passwordText.hashSHA256()
            if (user.passwordHash == hashed) {
                _currentUser.value = user
                onResult(true, "Successfully logged in as ${user.name}!")
            } else {
                onResult(false, "Incorrect password. Please try again.")
            }
        }
    }

    fun prepareRegistration(
        name: String,
        dob: String,
        bloodGroup: String,
        sex: String,
        address: String,
        latitude: Double,
        longitude: Double,
        contact: String,
        email: String,
        passwordText: String,
        onOtpSent: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (name.isBlank() || dob.isBlank() || bloodGroup.isBlank() ||
                sex.isBlank() || address.isBlank() || contact.isBlank() ||
                email.isBlank() || passwordText.isBlank()
            ) {
                onError("Please fill out all registration fields.")
                return@launch
            }

            // Check if user already exists
            val existing = repository.getUserByEmail(email.trim())
            if (existing != null) {
                onError("An account with this email already exists.")
                return@launch
            }

            // Generate 6 digit OTP Code
            val code = (100000 + Random().nextInt(900000)).toString()
            pendingOtpCode = code
            pendingRegistrationUser = UserEntity(
                name = name.trim(),
                dob = dob.trim(),
                bloodGroup = bloodGroup.trim(),
                sex = sex.trim(),
                address = address.trim(),
                latitude = latitude,
                longitude = longitude,
                contact = contact.trim(),
                email = email.trim().lowercase(),
                passwordHash = passwordText.hashSHA256(),
                isVerified = true, // marks as contact verified
                isDonorActive = true
            )

            // Callback to trigger OTP Modal
            onOtpSent(code)
        }
    }

    fun completeRegistration(enteredOtp: String, onCompleted: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = pendingRegistrationUser
            if (user == null) {
                onCompleted(false, "No registration session in progress.")
                return@launch
            }
            if (enteredOtp == pendingOtpCode || enteredOtp == "123456") { // 123456 as bypass dummy
                val newId = repository.registerUser(user)
                val finalUser = user.copy(id = newId.toInt())
                _currentUser.value = finalUser
                pendingRegistrationUser = null
                onCompleted(true, "Welcome ${finalUser.name}! Registration complete.")
            } else {
                onCompleted(false, "Invalid OTP code. Please try again.")
            }
        }
    }

    fun logout() {
        _currentUser.value = null
    }

    fun toggleDonorStatus(isActive: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(isDonorActive = isActive)
            repository.updateUserProfile(updated)
            _currentUser.value = updated
        }
    }

    fun postBloodRequest(
        patientName: String,
        bloodGroup: String,
        requiredUnits: Int,
        hospitalName: String,
        contactNumber: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        description: String,
        onCompleted: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (patientName.isBlank() || requiredUnits <= 0 || hospitalName.isBlank() ||
                contactNumber.isBlank() || locationName.isBlank()
            ) {
                onCompleted(false, "Please fill out all emergency details correctly.")
                return@launch
            }

            val newRequest = BloodRequestEntity(
                patientName = patientName.trim(),
                bloodGroup = bloodGroup,
                requiredUnits = requiredUnits,
                hospitalName = hospitalName.trim(),
                contactNumber = contactNumber.trim(),
                locationName = locationName.trim(),
                latitude = latitude,
                longitude = longitude,
                status = "ACTIVE",
                description = description.trim()
            )

            val reqId = repository.createBloodRequest(newRequest)

            // Calculate & Automatically Notify Nearby Hospitals using coordinate distance (within 10 km)
            val hospitals = listOf(
                HospitalLocation("Bir Hospital", 27.7051, 85.3134),
                HospitalLocation("Kathmandu Model Hospital", 27.7028, 85.3186),
                HospitalLocation("Norvic International Hospital", 27.6897, 85.3184),
                HospitalLocation("T.U. Teaching Hospital (TUTH)", 27.7360, 85.3300),
                HospitalLocation("Civil Service Hospital", 27.6881, 85.3375),
                HospitalLocation("Patan Hospital", 27.6678, 85.3216)
            )

            var notifiedCount = 0
            for (hosp in hospitals) {
                val distance = calculateDistanceInKm(latitude, longitude, hosp.lat, hosp.lon)
                if (distance <= 10.0) { // Notify hospitals within 10km radius
                    val notification = HospitalNotificationEntity(
                        hospitalName = hosp.name,
                        bloodGroup = bloodGroup,
                        urgency = "CRITICAL",
                        message = "EMERGENCY: Patient ${patientName.trim()} requires $requiredUnits units of $bloodGroup blood at ${hospitalName.trim()} (approx. ${"%.1f".format(distance)} km away).",
                        timestamp = System.currentTimeMillis()
                    )
                    repository.sendHospitalNotification(notification)
                    notifiedCount++
                }
            }

            onCompleted(true, "Request posted! Notified $notifiedCount hospitals within 10km radius.")
        }
    }

    fun addDonationHistory(
        facilityName: String,
        timestamp: Long,
        notes: String,
        onCompleted: (Boolean, String) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            if (facilityName.isBlank()) {
                onCompleted(false, "Please specify the donation facility.")
                return@launch
            }

            // Create donation record
            val donation = DonationEntity(
                userId = user.id,
                donationDate = timestamp,
                facilityName = facilityName.trim(),
                notes = notes.trim(),
                bloodGroup = user.bloodGroup
            )
            repository.addDonation(donation)

            // Update user last donation date to track eligibility correctly
            val updatedUser = user.copy(lastDonationTime = timestamp)
            repository.updateUserProfile(updatedUser)
            _currentUser.value = updatedUser

            onCompleted(true, "Donation history successfully updated!")
        }
    }

    // Eligibility Checker Helper
    // Returns: Pair(IsEligible, DaysRemaining)
    fun checkDonationEligibility(user: UserEntity?): Pair<Boolean, Int> {
        if (user == null) return Pair(false, 0)
        val lastDonation = user.lastDonationTime ?: return Pair(true, 0)

        // 90 days required minimum wait interval for eligibility
        val ninetyDaysMillis = 90L * 24 * 60 * 60 * 1000
        val diff = System.currentTimeMillis() - lastDonation
        return if (diff >= ninetyDaysMillis) {
            Pair(true, 0)
        } else {
            val remainingMillis = ninetyDaysMillis - diff
            val remainingDays = (remainingMillis / (24 * 60 * 60 * 1000) + 1).toInt()
            Pair(false, remainingDays)
        }
    }

    fun selectRequest(request: BloodRequestEntity?) {
        _selectedRequest.value = request
    }

    fun updateRequestStatus(requestId: Int, newStatus: String) {
        viewModelScope.launch {
            repository.updateRequestStatus(requestId, newStatus)
            // Update selectedRequest state if it is the one being modified
            val currentSel = _selectedRequest.value
            if (currentSel != null && currentSel.id == requestId) {
                _selectedRequest.value = currentSel.copy(status = newStatus)
            }
        }
    }

    // Distance computation (Haversine formula in Km)
    private fun calculateDistanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius of earth
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    // Human readable timestamps
    fun formatTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun markNotificationAsRead(id: Int) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    data class HospitalLocation(val name: String, val lat: Double, val lon: Double)
}

class RaktaSewaViewModelFactory(
    private val application: Application,
    private val repository: RaktaSewaRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RaktaSewaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RaktaSewaViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
