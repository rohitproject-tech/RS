package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.viewmodel.RaktaSewaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random

sealed class Screen {
    object Welcome : Screen()
    object Login : Screen()
    object Register : Screen()
    object Dashboard : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaktaSewaApp(viewModel: RaktaSewaViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Welcome) }
    
    // Track selected tab inside Dashboard
    var activeTab by remember { mutableStateOf(0) }
    
    // Fetch state from viewmodel
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val activeDonors by viewModel.activeDonors.collectAsStateWithLifecycle()
    val bloodRequests by viewModel.bloodRequests.collectAsStateWithLifecycle()
    val notifications by viewModel.hospitalNotifications.collectAsStateWithLifecycle()
    val userDonations by viewModel.userDonations.collectAsStateWithLifecycle()
    val selectedRequest by viewModel.selectedRequest.collectAsStateWithLifecycle()

    // Dialog flags
    var showPostRequestDialog by remember { mutableStateOf(false) }
    var showAddDonationDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var showRequestDetailDialog by remember { mutableStateOf(false) }

    // Snackbar Host
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle back presses elegantly
    BackHandler(enabled = currentScreen != Screen.Welcome) {
        when (currentScreen) {
            Screen.Login, Screen.Register -> currentScreen = Screen.Welcome
            Screen.Dashboard -> {
                if (showRequestDetailDialog) {
                    showRequestDetailDialog = false
                    viewModel.selectRequest(null)
                } else if (activeTab > 0) {
                    activeTab = 0
                } else {
                    viewModel.logout()
                    currentScreen = Screen.Welcome
                }
            }
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        is Screen.Welcome -> {
                            WelcomeScreen(
                                onNavigateToLogin = { currentScreen = Screen.Login },
                                onNavigateToRegister = { currentScreen = Screen.Register }
                            )
                        }
                        is Screen.Login -> {
                            LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = { msg ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                    currentScreen = Screen.Dashboard
                                },
                                onCancel = { currentScreen = Screen.Welcome }
                            )
                        }
                        is Screen.Register -> {
                            RegisterScreen(
                                viewModel = viewModel,
                                onRegisterTriggerOtp = { code ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("OTP Sent to Contact Number for verification.")
                                    }
                                    showOtpDialog = true
                                },
                                onCancel = { currentScreen = Screen.Welcome }
                            )
                        }
                        is Screen.Dashboard -> {
                            DashboardScreen(
                                activeTab = activeTab,
                                onTabChanged = { activeTab = it },
                                currentUser = currentUser,
                                activeDonors = activeDonors,
                                bloodRequests = bloodRequests,
                                notifications = notifications,
                                userDonations = userDonations,
                                selectedRequest = selectedRequest,
                                viewModel = viewModel,
                                onLogout = {
                                    viewModel.logout()
                                    activeTab = 0
                                    currentScreen = Screen.Welcome
                                },
                                onRequestClicked = { req ->
                                    viewModel.selectRequest(req)
                                    showRequestDetailDialog = true
                                },
                                onAddRequestClicked = { showPostRequestDialog = true },
                                onAddDonationClicked = { showAddDonationDialog = true }
                            )
                        }
                    }
                }

                // --- Dialog Models ---

                // OTP verification Dialog
                if (showOtpDialog) {
                    OtpVerificationDialog(
                        otpCode = viewModel.pendingOtpCode,
                        onVerify = { enteredOtp ->
                            viewModel.completeRegistration(enteredOtp) { success, msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                                if (success) {
                                    showOtpDialog = false
                                    currentScreen = Screen.Dashboard
                                }
                            }
                        },
                        onDismiss = {
                            showOtpDialog = false
                        }
                    )
                }

                // Add Donation Record Dialog
                if (showAddDonationDialog) {
                    AddDonationRecordDialog(
                        onDismiss = { showAddDonationDialog = false },
                        onSave = { facility, timestamp, notes ->
                            viewModel.addDonationHistory(facility, timestamp, notes) { success, msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                                if (success) {
                                    showAddDonationDialog = false
                                }
                            }
                        }
                    )
                }

                // Create Blood Request Dialog
                if (showPostRequestDialog) {
                    PostRequestDialog(
                        onDismiss = { showPostRequestDialog = false },
                        onPost = { name, group, units, hosp, contact, loc, lat, lon, desc ->
                            viewModel.postBloodRequest(name, group, units, hosp, contact, loc, lat, lon, desc) { success, msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                                if (success) {
                                    showPostRequestDialog = false
                                }
                            }
                        }
                    )
                }

                // Emergency Request Detail Dialog
                if (showRequestDetailDialog && selectedRequest != null) {
                    RequestDetailDialog(
                        request = selectedRequest!!,
                        currentUser = currentUser,
                        viewModel = viewModel,
                        onDismiss = {
                            showRequestDetailDialog = false
                            viewModel.selectRequest(null)
                        }
                    )
                }
            }
        }
    }
}

// ==================== SCREEN COMPONENTS ====================

@Composable
fun WelcomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Aesthetic Top Decorative Header with Blood droplet gradient logo
        Box(
            modifier = Modifier
                .size(140.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(BloodTertiary.copy(alpha = 0.2f), Color.Transparent)
                        ),
                        radius = size.width
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        Brush.linearGradient(listOf(BloodPrimary, BloodSecondary)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Bloodtype,
                    contentDescription = "Rakta Sewa Droplet",
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "RS (Rakta Sewa)",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BloodPrimary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Connects blood donors with patients in emergency situations in real-time.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Onboarding Buttons
        Button(
            onClick = onNavigateToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("welcome_login_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = "Lock Icon")
            Spacer(modifier = Modifier.width(12.dp))
            Text("Login to Dashboard", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateToRegister,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("welcome_register_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BloodPrimary),
            border = BorderStroke(2.dp, BloodPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Person Icon")
            Spacer(modifier = Modifier.width(12.dp))
            Text("Register as Volunteer/Donor", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Privacy Footer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = "Shield Privacy Secure icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Secure local encryption protects your contact and location details. Data privacy is strictly preserved.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: RaktaSewaViewModel,
    onLoginSuccess: (String) -> Unit,
    onCancel: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inProgress by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BloodPrimary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome Back",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = BloodPrimary
        )

        Text(
            text = "Sign in to coordinates or view real-time blood requests",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMsg != null) {
            Text(
                text = errorMsg!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMsg = null
            },
            label = { Text("Email Address") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = "Email Icon", tint = BloodPrimary) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_email_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMsg = null
            },
            label = { Text("Secure Password") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password Icon", tint = BloodPrimary) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_password_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                scope.launch {
                    inProgress = true
                    delay(300) // fast stylish transition
                    viewModel.login(email, password) { success, msg ->
                        inProgress = false
                        if (success) {
                            onLoginSuccess(msg)
                        } else {
                            errorMsg = msg
                        }
                    }
                }
            },
            enabled = !inProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("login_submit_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (inProgress) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Login Now", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Tip: Try logging in with: anil@gmail.com / password123 (Sample Donor) or register a new one to unlock features.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(
    viewModel: RaktaSewaViewModel,
    onRegisterTriggerOtp: (String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("1998-05-10") }
    var bloodGroup by remember { mutableStateOf("O+") }
    var sex by remember { mutableStateOf("Male") }
    var contact by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Simulated Maps Picker State
    var address by remember { mutableStateOf("New Baneshwor, Kathmandu") }
    var selectedLat by remember { mutableStateOf(27.6913) }
    var selectedLon by remember { mutableStateOf(85.3331) }
    var showAddressMapPicker by remember { mutableStateOf(false) }

    var errorMsg by remember { mutableStateOf<String?>(null) }
    var inProgress by remember { mutableStateOf(false) }

    val bloodGroupsList = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
    val sexList = listOf("Male", "Female", "Other")

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BloodPrimary)
        }

        Text(
            text = "Create Volunteer Profile",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = BloodPrimary
        )

        Text(
            text = "Verify eligibility and details to enroll as an emergency lifesaver.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMsg != null) {
            Text(
                text = errorMsg!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Form Fields
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorMsg = null },
            label = { Text("Full Name") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "User icon", tint = BloodPrimary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = dob,
                onValueChange = { dob = it; errorMsg = null },
                label = { Text("DOB (YYYY-MM-DD)") },
                leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Cal icon", tint = BloodPrimary) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Blood Group Combo Selector
        Text("Blood Group", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BloodPrimary)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4
        ) {
            bloodGroupsList.forEach { grp ->
                val isSelected = bloodGroup == grp
                Box(
                    modifier = Modifier
                        .clickable { bloodGroup = grp }
                        .background(
                            color = if (isSelected) BloodPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = grp,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Sex Selector
        Text("Sex", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BloodPrimary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sexList.forEach { item ->
                val isSelected = sex == item
                Row(
                    modifier = Modifier
                        .clickable { sex = item }
                        .background(
                            color = if (isSelected) BloodPrimary.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) BloodPrimary else Color.Gray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { sex = item },
                        colors = RadioButtonDefaults.colors(selectedColor = BloodPrimary)
                    )
                    Text(item, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Address Field with map selection option
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; errorMsg = null },
            label = { Text("Resident Address") },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = "Map loc Icon", tint = BloodPrimary) },
            trailingIcon = {
                TextButton(onClick = { showAddressMapPicker = true }) {
                    Text("Choose Map", color = BloodPrimary, fontWeight = FontWeight.Bold)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Text(
            text = "GPS Coordinates: ($selectedLat, $selectedLon)",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        if (showAddressMapPicker) {
            SimulatedMapPickerDialog(
                currentLat = selectedLat,
                currentLon = selectedLon,
                onLocationSelected = { addr, lat, lon ->
                    address = addr
                    selectedLat = lat
                    selectedLon = lon
                    showAddressMapPicker = false
                },
                onDismiss = { showAddressMapPicker = false }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = contact,
            onValueChange = { contact = it; errorMsg = null },
            label = { Text("Contact Number (for SMS & OTP)") },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = "Phone contact Icon", tint = BloodPrimary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMsg = null },
            label = { Text("Email Address") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = "Mail address key", tint = BloodPrimary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMsg = null },
            label = { Text("Choose Secure Password") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Secure password lock", tint = BloodPrimary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.prepareRegistration(
                    name = name,
                    dob = dob,
                    bloodGroup = bloodGroup,
                    sex = sex,
                    address = address,
                    latitude = selectedLat,
                    longitude = selectedLon,
                    contact = contact,
                    email = email,
                    passwordText = password,
                    onOtpSent = { code ->
                        onRegisterTriggerOtp(code)
                    },
                    onError = { err ->
                        errorMsg = err
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("register_submit_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Verify Phone & Submit", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==================== DASHBOARD & SUB-TABS ====================

@Composable
fun DashboardScreen(
    activeTab: Int,
    onTabChanged: (Int) -> Unit,
    currentUser: UserEntity?,
    activeDonors: List<UserEntity>,
    bloodRequests: List<BloodRequestEntity>,
    notifications: List<HospitalNotificationEntity>,
    userDonations: List<DonationEntity>,
    selectedRequest: BloodRequestEntity?,
    viewModel: RaktaSewaViewModel,
    onLogout: () -> Unit,
    onRequestClicked: (BloodRequestEntity) -> Unit,
    onAddRequestClicked: () -> Unit,
    onAddDonationClicked: () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFF3F3FA), // MATCHING Polish Web template #F3F3FA
                modifier = Modifier.navigationBarsPadding()
            ) {
                val navBarColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1B1B1F),
                    selectedTextColor = Color(0xFF1B1B1F),
                    unselectedIconColor = Color(0xFF44474E),
                    unselectedTextColor = Color(0xFF44474E),
                    indicatorColor = Color(0xFFDDE2F1) // Soft periwinkle active nav pill
                )
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { onTabChanged(0) },
                    icon = { Icon(Icons.Outlined.Bloodtype, contentDescription = "Requests") },
                    label = { Text("Emergency", fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp) },
                    colors = navBarColors
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { onTabChanged(1) },
                    icon = { Icon(Icons.Outlined.PinDrop, contentDescription = "Map donor list icon") },
                    label = { Text("Donor Map", fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp) },
                    colors = navBarColors
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { onTabChanged(2) },
                    icon = {
                        val unreadCount = notifications.count { !it.isRead }
                        BadgedBox(badge = {
                            if (unreadCount > 0) {
                                Badge(containerColor = BloodPrimary) { Text(unreadCount.toString(), color = Color.White) }
                            }
                        }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Alerts notifications")
                        }
                    },
                    label = { Text("Hospitals", fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp) },
                    colors = navBarColors
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { onTabChanged(3) },
                    icon = { Icon(Icons.Outlined.Person, contentDescription = "Donor account configuration icon") },
                    label = { Text("Profile", fontWeight = if (activeTab == 3) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp) },
                    colors = navBarColors
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PolishBackground) // Professional Polish Background
                .padding(innerPadding)
        ) {
            // Elegant MD3 Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(BloodPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bloodtype,
                            contentDescription = "Rakta Sewa logo",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Rakta Sewa",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PolishOnBackground
                        )
                        Text(
                            text = "EMERGENCY NETWORK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BloodPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // SECURE E2E indicator chip
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFD1FAE5), CircleShape)
                            .border(1.dp, Color(0xFFA7F3D0), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Secure lock icon",
                                tint = Color(0xFF065F46),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "SECURE E2E",
                                color = Color(0xFF065F46),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Initials profile avatar bubble. Clicking triggers modern logout dialog or immediate safe exit.
                    val userInitials = currentUser?.name?.split(" ")?.mapNotNull { it.firstOrNull() }?.take(2)?.joinToString("")?.uppercase() ?: "RK"
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .border(2.dp, PolishSecondaryContainer, CircleShape)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PolishSecondaryContainer, CircleShape)
                                .clickable { onLogout() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userInitials,
                                color = Color(0xFF44474E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Statistics & Status Grid (MD3 columns from the Professional Polish theme)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Your Status card (Verified Donor)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE1E2EC)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "Verified Donor mark",
                            tint = Color(0xFF44474E),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Your Status",
                                color = Color(0xFF44474E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (currentUser?.isDonorActive == true) "Active Lifesaver" else "Verified Volunteer",
                                color = Color(0xFF1B1B1F),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Card 2: Next Eligibility card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD7E3FF)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EventAvailable,
                            contentDescription = "Eligible Donor details calendar icon",
                            tint = Color(0xFF001B3E),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Eligibility",
                                color = Color(0xFF001B3E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (currentUser?.isDonorActive == true) "Eligible Now" else "Pending Review",
                                color = Color(0xFF001B3E),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Active Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    0 -> EmergencyRequestsTab(
                        bloodRequests = bloodRequests,
                        onRequestClicked = onRequestClicked,
                        onAddClicked = onAddRequestClicked
                    )
                    1 -> DonorMapTab(
                        activeDonors = activeDonors,
                        bloodRequests = bloodRequests,
                        currentUser = currentUser,
                        viewModel = viewModel
                    )
                    2 -> HospitalAlertsTab(
                        notifications = notifications,
                        viewModel = viewModel
                    )
                    3 -> ProfileTab(
                        currentUser = currentUser,
                        userDonations = userDonations,
                        viewModel = viewModel,
                        onAddDonationClicked = onAddDonationClicked
                    )
                }
            }
        }
    }
}

// -------------------- TAB 1: EMERGENCY REQUESTS --------------------

@Composable
fun EmergencyRequestsTab(
    bloodRequests: List<BloodRequestEntity>,
    onRequestClicked: (BloodRequestEntity) -> Unit,
    onAddClicked: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (bloodRequests.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.HeartBroken,
                    contentDescription = "No requests active",
                    tint = Color.Gray,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Active Emergency Requests",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Text(
                    text = "Everyone is currently safe! Share request of patients in emergency to notify nearby hospitals.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Active Emergencies Nearby",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = BloodPrimary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                items(bloodRequests) { req ->
                    val isCritical = req.requiredUnits >= 3 && req.status == "ACTIVE"
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestClicked(req) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (req.status == "ACTIVE") {
                                if (isCritical) Color(0xFFFFDAD6) // #FFDAD6 Urgent alert bg
                                else Color.White
                            } else {
                                Color(0xFFF2F0F4).copy(alpha = 0.7f) // Fulfilled grey bg
                            }
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (req.status == "ACTIVE") {
                                if (isCritical) Color(0xFFFFB4AB) // #FFB4AB Urgent alert border
                                else Color(0xFFDEDEE7)
                            } else {
                                Color(0xFFDDE2F1)
                            }
                        ),
                        shape = RoundedCornerShape(28.dp) // Professional rounded edge
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            // Urgent alert banner with flashing red indicator
                            if (isCritical) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFFBA1A1A).copy(alpha = pulseAlpha), CircleShape)
                                    )
                                    Text(
                                        text = "URGENT REQUEST",
                                        color = Color(0xFF410002),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(
                                                if (req.status == "ACTIVE") Color(0xFFBA1A1A) else Color.Gray,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = req.bloodGroup,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Patient: ${req.patientName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isCritical) Color(0xFF410002) else PolishOnBackground
                                        )
                                        Text(
                                            text = req.hospitalName,
                                            fontSize = 13.sp,
                                            color = if (isCritical) Color(0xFF410002).copy(alpha = 0.7f) else Color.Gray
                                        )
                                    }
                                }

                                // Required units badge
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isCritical) Color(0xFFBA1A1A) else Color.Gray.copy(alpha = 0.15f),
                                            CircleShape
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${req.requiredUnits} Units",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isCritical) Color.White else PolishOnBackground
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = req.description,
                                fontSize = 13.sp,
                                maxLines = 2,
                                color = if (isCritical) Color(0xFF410002).copy(alpha = 0.85f) else PolishOnBackground.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = "location",
                                        tint = Color(0xFFBA1A1A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = req.locationName,
                                        fontSize = 12.sp,
                                        color = if (isCritical) Color(0xFF410002).copy(alpha = 0.8f) else Color.Gray
                                    )
                                }

                                Text(
                                    text = if (req.status == "ACTIVE") "🚨 Emergency Alert" else "✓ Fulfilled",
                                    color = if (req.status == "ACTIVE") Color(0xFFBA1A1A) else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Dynamic action buttons inside the card for high-level Polish parity
                            if (isCritical) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onRequestClicked(req) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A)),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Respond Now checkmark",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Respond Now", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { /* Share simulated action */ },
                                        modifier = Modifier.size(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.4f), contentColor = Color(0xFF410002)),
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = "Share alert logo"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // Add Emergency Request Floating Action Button
        FloatingActionButton(
            onClick = onAddClicked,
            containerColor = BloodPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_request_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Emergency Blood request button")
        }
    }
}

// -------------------- TAB 2: DONOR INTERACTIVE MAP --------------------

@Composable
fun DonorMapTab(
    activeDonors: List<UserEntity>,
    bloodRequests: List<BloodRequestEntity>,
    currentUser: UserEntity?,
    viewModel: RaktaSewaViewModel
) {
    var selectedEntityName by remember { mutableStateOf<String?>(null) }
    var selectedEntityType by remember { mutableStateOf<String?>(null) }
    var selectedEntityDetails by remember { mutableStateOf<String?>(null) }
    var selectedEntityContact by remember { mutableStateOf<String?>(null) }

    // Geolocation alerts toggle
    var enableRealtimeAlerts by remember { mutableStateOf(true) }

    // Compass animation rotation simulation
    val infiniteTransition = rememberInfiniteTransition()
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Real-time Donor Geolocation Map",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BloodPrimary
                )
                Text(
                    text = "Track active matching lifesavers within 10 km",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            // Realtime Alert toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(SoftGray, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Alerts", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (enableRealtimeAlerts) BloodPrimary else Color.Gray)
                Spacer(modifier = Modifier.width(2.dp))
                Switch(
                    checked = enableRealtimeAlerts,
                    onCheckedChange = { enableRealtimeAlerts = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = BloodPrimary, checkedTrackColor = AccentRed.copy(0.4f)),
                    modifier = Modifier.scale(0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Custom Staggered Canvas Map representing matching blood grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF222225), RoundedCornerShape(16.dp))
                .border(2.dp, BloodPrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Simulated pinpoint mapping bounds collision
                        // Since positions are static on our visual map canvas representation,
                        // we'll randomly toggle coordinate landmarks based on tapping zones
                        val picked = when {
                            offset.y < 200 -> "Bir Hospital Pinpoint"
                            offset.y > 600 -> "Anil Shrestha [O+ Donor]"
                            offset.x > 400 -> "Pooja Sharma [A- Donor]"
                            else -> "T.U. Teaching Hospital (TUTH)"
                        }

                        if (picked.contains("Anil")) {
                            selectedEntityName = "Anil Shrestha"
                            selectedEntityType = "Emergency Donor (O+)"
                            selectedEntityDetails = "Located in New Baneshwor, Kathmandu. Eligible & Active to donate. Registered lifesaver."
                            selectedEntityContact = "9841234567"
                        } else if (picked.contains("Pooja")) {
                            selectedEntityName = "Pooja Sharma"
                            selectedEntityType = "Emergency Donor (A-)"
                            selectedEntityDetails = "Located in Lakeside, Pokhara. Registered lifesaver. Verified."
                            selectedEntityContact = "9856012345"
                        } else if (picked.contains("Teaching")) {
                            selectedEntityName = "T.U. Teaching Hospital (TUTH)"
                            selectedEntityType = "Emergency Center"
                            selectedEntityDetails = "Maharajgunj Road. Real-time notifications active at this center."
                            selectedEntityContact = "01-441234"
                        } else {
                            selectedEntityName = "Bir Hospital"
                            selectedEntityType = "Government Emergency Hub"
                            selectedEntityDetails = "Kanti Path, Kathmandu. Primary local emergency blood bank."
                            selectedEntityContact = "01-422600"
                        }
                    }
                }
        ) {
            // Draw visual high-density stylized radar map
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw central geo alert radar concentric circles
                if (enableRealtimeAlerts) {
                    drawCircle(
                        color = BloodPrimary,
                        radius = 120f * radarScale,
                        center = Offset(canvasWidth / 2, canvasHeight / 2),
                        style = Stroke(width = 3f),
                        alpha = 1.0f - radarScale
                    )
                    drawCircle(
                        color = BloodPrimary,
                        radius = 240f * radarScale,
                        center = Offset(canvasWidth / 2, canvasHeight / 2),
                        style = Stroke(width = 2f),
                        alpha = (1.0f - radarScale) * 0.5f
                    )
                }

                // Draw coordinates gird lines
                val strokeColor = Color.White.copy(alpha = 0.05f)
                val gridLines = 8
                for (i in 1..gridLines) {
                    val x = canvasWidth * i / gridLines
                    val y = canvasHeight * i / gridLines
                    drawLine(strokeColor, Offset(x, 0f), Offset(x, canvasHeight))
                    drawLine(strokeColor, Offset(0f, y), Offset(canvasWidth, y))
                }

                // Draw simulated arterial grid lines (linking Kathmandu, Lalitpur, and Pokhara networks)
                val lineBrush = Brush.linearGradient(listOf(BloodPrimary.copy(alpha = 0.3f), Color.Blue.copy(alpha = 0.1f)))
                drawLine(
                    brush = lineBrush,
                    start = Offset(100f, 150f),
                    end = Offset(canvasWidth - 100f, canvasHeight - 200f),
                    strokeWidth = 4f
                )
                drawLine(
                    brush = lineBrush,
                    start = Offset(canvasWidth - 150f, 100f),
                    end = Offset(100f, canvasHeight - 150f),
                    strokeWidth = 3f
                )

                // Render active epicenter (Kathmandu City Hub)
                drawCircle(
                    color = BloodPrimary,
                    radius = 14f,
                    center = Offset(canvasWidth / 2, canvasHeight / 2)
                )

                // Render Sample Hospital Pin (TUTH Maharajgunj)
                drawCircle(
                    color = Color.Cyan,
                    radius = 10f,
                    center = Offset(canvasWidth * 0.35f, canvasHeight * 0.25f)
                )

                // Render Sample Hospital Pin (Bir Hospital)
                drawCircle(
                    color = Color.Cyan,
                    radius = 10f,
                    center = Offset(canvasWidth * 0.45f, canvasHeight * 0.40f)
                )

                // Render Active Donors Pins (O+ and A-)
                drawCircle(
                    color = Color(0xFF4CAF50), // Green for available donors
                    radius = 9f,
                    center = Offset(canvasWidth * 0.70f, canvasHeight * 0.65f) // Pooja (A-)
                )
                drawCircle(
                    color = Color(0xFF4CAF50), // Green
                    radius = 9f,
                    center = Offset(canvasWidth * 0.52f, canvasHeight * 0.55f) // Anil (O+)
                )
            }

            // Real-time Map Instructions overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Epicenter Target (Patient)", color = Color.White, fontSize = 9.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Active Lifesavers (Donors)", color = Color.White, fontSize = 9.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color.Cyan, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simulated Emergency Centers", color = Color.White, fontSize = 9.sp)
                }
            }

            // Callout tap instruction overlay
            Text(
                text = "Tap any map landmark or donor region to inspect coordinates",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Pin Detail Display Panel
        AnimatedVisibility(
            visible = selectedEntityName != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BloodPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedEntityName ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = BloodPrimary
                            )
                            Text(
                                text = selectedEntityType ?: "",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = { selectedEntityName = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close pin detail label")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = selectedEntityDetails ?: "",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedEntityContact != null) {
                        Button(
                            onClick = { /* simulated dial action */ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                        ) {
                            Icon(Icons.Filled.Phone, contentDescription = "call pin")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Contact Now (${selectedEntityContact})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -------------------- TAB 3: HOSPITAL ALERTS --------------------

@Composable
fun HospitalAlertsTab(
    notifications: List<HospitalNotificationEntity>,
    viewModel: RaktaSewaViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalHospital,
                    contentDescription = "Hospitals safe",
                    tint = Color.Gray,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Urgent Hospital Requests",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Text(
                    text = "Nearby hospital blood banks are currently stable. Automated notification system is monitoring matching groups.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Automated Hospital Broadcast Network",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = BloodPrimary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                items(notifications) { alert ->
                    val isCritical = alert.urgency == "CRITICAL"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (alert.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
                            else if (isCritical) MaterialTheme.colorScheme.errorContainer.copy(0.15f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (!alert.isRead) {
                                if (isCritical) MaterialTheme.colorScheme.error else BloodPrimary.copy(alpha = 0.4f)
                            } else {
                                Color.Gray.copy(alpha = 0.3f)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalHospital,
                                        contentDescription = "Hospital vector",
                                        tint = if (isCritical) Color.Red else BloodPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = alert.hospitalName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (!alert.isRead) {
                                    TextButton(onClick = { viewModel.markNotificationAsRead(alert.id) }) {
                                        Text("Mark Read", color = BloodPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = alert.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isCritical) Color.Red else Color.Gray,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = alert.urgency,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(BloodPrimary.copy(0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Blood group: ${alert.bloodGroup}",
                                            color = BloodPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = viewModel.formatTimestamp(alert.timestamp),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

// -------------------- TAB 4: PROFILE & ELIGIBILITY --------------------

@Composable
fun ProfileTab(
    currentUser: UserEntity?,
    userDonations: List<DonationEntity>,
    viewModel: RaktaSewaViewModel,
    onAddDonationClicked: () -> Unit
) {
    if (currentUser == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.AccountCircle, "Profile Empty key", modifier = Modifier.size(64.dp), tint = Color.Gray)
            Text("Please Login first to view Profile.", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        }
        return
    }

    val (isEligible, daysRemaining) = viewModel.checkDonationEligibility(currentUser)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Core Eligibility Track Status Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isEligible) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isEligible) Color(0xFF4CAF50) else Color(0xFFFF9800))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isEligible) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEligible) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = "status",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (isEligible) "Eligible to Donate Blood" else "Ineligible / Waiting Period",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isEligible) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                    Text(
                        text = if (isEligible) "You are verified, healthy, and ready to save lives! Turn on status to receive alerts."
                        else "Standard 90-day rest interval required. Next donation eligible in $daysRemaining days.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Profile Details Info
        Text("Volunteer Personal Record", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BloodPrimary)
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileRowItem(label = "Full Name", value = currentUser.name)
                ProfileRowItem(label = "Date of Birth", value = currentUser.dob)
                ProfileRowItem(label = "Blood Group", value = currentUser.bloodGroup)
                ProfileRowItem(label = "Sex", value = currentUser.sex)
                ProfileRowItem(label = "Physical Residence", value = currentUser.address)
                ProfileRowItem(label = "Contact Link", value = currentUser.contact)
                ProfileRowItem(label = "Email Username", value = currentUser.email)

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Active Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Willingness to Donate (Donation Active)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Toggle if you are currently available to receive instant emergency location alerts.", fontSize = 11.sp, color = Color.Gray)
                    }

                    Switch(
                        checked = currentUser.isDonorActive,
                        onCheckedChange = { viewModel.toggleDonorStatus(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = BloodPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Donation Track list history
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Verified My Donation History", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BloodPrimary)
            Button(
                onClick = onAddDonationClicked,
                colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add donation history row icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Log", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (userDonations.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.History, contentDescription = "no history", tint = Color.Gray, modifier = Modifier.size(36.dp))
                    Text(
                        text = "No Recorded Donation History yet",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Log your previous donation dates to automatically calculate accurate eligibility track logs.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                userDonations.forEach { donation ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(0.5.dp, Color.LightGray)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(BloodPrimary.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Bloodtype, contentDescription = "Droplet icon", tint = BloodPrimary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(donation.facilityName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Notes: ${donation.notes.ifBlank { "Regular donor" }}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Blood: ${donation.bloodGroup}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BloodPrimary
                                )
                                Text(
                                    text = viewModel.formatTimestamp(donation.donationDate).substringBefore(" -"),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun ProfileRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 13.sp)
        Text(text = value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// ==================== INTERACTIVE DIALOGS SIMULATION ====================

data class MapLocation(val name: String, val lat: Double, val lon: Double)

@Composable
fun SimulatedMapPickerDialog(
    currentLat: Double,
    currentLon: Double,
    onLocationSelected: (String, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    
    val locations = listOf(
        MapLocation("New Baneshwor, Kathmandu", 27.6913, 85.3331),
        MapLocation("Maitighar Circle, Kathmandu", 27.6938, 85.3219),
        MapLocation("Patan Durbar Square, Lalitpur", 27.6727, 85.3253),
        MapLocation("Lakeside Ward 6, Pokhara", 28.2096, 83.9584),
        MapLocation("Chabahil Chowk, Kathmandu", 27.7172, 85.3468),
        MapLocation("Maharajgunj Circle, Kathmandu", 27.7360, 85.3300),
        MapLocation("Jawalakhel Mandir, Lalitpur", 27.6713, 85.3121)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Choose Address from Map", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BloodPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Dismiss map selection dialog")
                    }
                }

                Text(
                    text = "Standard Google Maps coordinate API picker is pre-simulated for convenience. Choose a region landmark to populate real GPS coordinates.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search location landmark...") },
                    leadingIcon = { Icon(Icons.Filled.Search, "search location icon") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Map landmarks scroll list
                Text("Select Regional Landmark PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BloodPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(SoftGray, RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        locations.filter { it.name.contains(query, ignoreCase = true) }.forEach { loc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLocationSelected(loc.name, loc.lat, loc.lon)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.LocationOn, contentDescription = "coordinate flag", tint = BloodPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(loc.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("GPS Coordinates: (${loc.lat}, ${loc.lon})", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tip: High-contrast map overlays will center at Maharajgunj, Patan, Pokhara, or Chabahil regions dynamically when chosen.",
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun OtpVerificationDialog(
    otpCode: String,
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Sms,
                    contentDescription = "sms icon verified",
                    tint = BloodPrimary,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Phone Contact OTP Verification", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BloodPrimary)
                
                Text(
                    text = "To ensure secure donor verification and prevent fraudulent claims, we have sent a simulated 6-digit SMS OTP code to your registered contact.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )

                // Simulated code preview
                Box(
                    modifier = Modifier
                        .background(BloodPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Simulated SMS Received: $otpCode",
                        color = BloodPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Enter 6-digit OTP code") },
                    singleLine = true,
                    placeholder = { Text("e.g. 123456") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onVerify(code) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                ) {
                    Text("Verify & Complete Registration", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddDonationRecordDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, String) -> Unit
) {
    var facility by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Donation Log", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BloodPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Dismiss log dialog")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = facility,
                    onValueChange = { facility = it },
                    label = { Text("Donation Hospital / Facility Center") },
                    placeholder = { Text("e.g. Nepal Red Cross Society") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Optional Notes / Verified Cert Code") },
                    placeholder = { Text("e.g. Regular whole blood donation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSave(facility, System.currentTimeMillis(), notes)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                ) {
                    Text("Save Donated History", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostRequestDialog(
    onDismiss: () -> Unit,
    onPost: (String, String, Int, String, String, String, Double, Double, String) -> Unit
) {
    var patientName by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("O+") }
    var units by remember { mutableStateOf("1") }
    var hospitalName by remember { mutableStateOf("") }
    var contactContact by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    // Coordinates variables
    var locationName by remember { mutableStateOf("Kathmandu Model Hospital") }
    var lat by remember { mutableStateOf(27.7028) }
    var lon by remember { mutableStateOf(85.3186) }
    var showMapPicker by remember { mutableStateOf(false) }

    val bloodGroups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Request Blood (Emergency)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BloodPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Dismiss emergency dialog")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Patient Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Blood Group Needed", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BloodPrimary)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bloodGroups.forEach { grp ->
                        val selected = bloodGroup == grp
                        Box(
                            modifier = Modifier
                                .clickable { bloodGroup = grp }
                                .background(
                                    color = if (selected) BloodPrimary else SoftGray,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = grp,
                                color = if (selected) Color.White else Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = units,
                    onValueChange = { units = it },
                    label = { Text("Required Blood Units") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = hospitalName,
                    onValueChange = { hospitalName = it },
                    label = { Text("Hospital Name & Ward") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = contactContact,
                    onValueChange = { contactContact = it },
                    label = { Text("Patient Contact Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Emergency Coordinates Region") },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, "coordinate", tint = BloodPrimary) },
                    trailingIcon = {
                        TextButton(onClick = { showMapPicker = true }) {
                            Text("Map Pin", color = BloodPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showMapPicker) {
                    SimulatedMapPickerDialog(
                        currentLat = lat,
                        currentLon = lon,
                        onLocationSelected = { addr, latitude, longitude ->
                            locationName = addr
                            lat = latitude
                            lon = longitude
                            showMapPicker = false
                        },
                        onDismiss = { showMapPicker = false }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Case Description / Instructions") },
                    placeholder = { Text("e.g. Patient undergoes trauma surgery at Bir Hospital.") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val unitsInt = units.toIntOrNull() ?: 1
                        onPost(patientName, bloodGroup, unitsInt, hospitalName, contactContact, locationName, lat, lon, desc)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                ) {
                    Text("Broadcast & Alert Networks", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RequestDetailDialog(
    request: BloodRequestEntity,
    currentUser: UserEntity?,
    viewModel: RaktaSewaViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Emergency Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BloodPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Dismiss details overlay button")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Patient Details block
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(BloodPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(request.bloodGroup, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(request.patientName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Requested: ${viewModel.formatTimestamp(request.timestamp)}", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ProfileRowItem(label = "Blood Group Needed", value = request.bloodGroup)
                ProfileRowItem(label = "Required Quantity", value = "${request.requiredUnits} Units")
                ProfileRowItem(label = "Hospital Facility", value = request.hospitalName)
                ProfileRowItem(label = "Emergency Point", value = request.locationName)
                ProfileRowItem(label = "Contact Hotline", value = request.contactNumber)
                ProfileRowItem(label = "Current Request Status", value = request.status)

                Spacer(modifier = Modifier.height(12.dp))

                Text("Description & Case details", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BloodPrimary)
                Text(
                    text = request.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .background(SoftGray, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action controls depending on role or status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Call hotline
                    Button(
                        onClick = { /* Simulated dialer action */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BloodPrimary)
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = "call button")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call Helpline", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Toggling request status (fulfilled by current matching user or centers)
                    if (request.status == "ACTIVE" && currentUser != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updateRequestStatus(request.id, "FULFILLED")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CAF50)),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "complete")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fulfilled", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
