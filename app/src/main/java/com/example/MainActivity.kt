package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ExchangeDatabase
import com.example.data.model.Listing
import com.example.data.model.User
import com.example.data.repository.ExchangeRepository
import com.example.notification.AppNotification
import com.example.notification.NotificationHelper
import com.example.notification.NotificationType
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.ExchangeViewModel
import com.example.ui.viewmodel.ExchangeViewModelFactory
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val msg = if (isGranted) "Notifications Enabled!" else "Notifications Permission Denied"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create the notification channels for messages and listings
        NotificationHelper.createNotificationChannels(applicationContext)

        // Request POST_NOTIFICATIONS runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize Room DB and architecture layers
        val database = ExchangeDatabase.getDatabase(applicationContext)
        val repository = ExchangeRepository(
            database.userDao(),
            database.listingDao(),
            database.messageDao()
        )
        val viewModelFactory = ExchangeViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[ExchangeViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainContainer(this, viewModel) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Toast.makeText(this, "Permissions handled automatically by system.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
fun MainContainer(
    context: Context,
    viewModel: ExchangeViewModel,
    onRequestPermissions: () -> Unit
) {
    val currentUser by viewModel.currentUserState.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val notificationLog by viewModel.notificationsLog.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (currentUser != null) {
                BentoBottomBar(
                    currentScreen = currentScreen,
                    notificationCount = notificationLog.count { !it.isRead },
                    onNavigate = { screen -> viewModel.currentScreen.value = screen }
                )
            }
        },
        containerColor = Color(0xFFFEF7FF), // Bento violet-cream template bg
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(185))
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    AppScreen.REGISTRATION -> {
                        RegistrationScreen(
                            onRegister = { name, email, neighborhood, lat, lon ->
                                viewModel.register(name, email, neighborhood, lat, lon)
                            }
                        )
                    }
                    AppScreen.MAP_AND_BROWSE -> {
                        DashboardBentoScreen(
                            context = context,
                            viewModel = viewModel,
                            currentUser = currentUser
                        )
                    }
                    AppScreen.CREATE_LISTING -> {
                        CreateListingScreen(
                            onPost = { title, desc, cat, exType, details ->
                                viewModel.postListing(title, desc, cat, exType, details)
                            },
                            onCancel = { viewModel.currentScreen.value = AppScreen.MAP_AND_BROWSE }
                        )
                    }
                    AppScreen.MESSAGES -> {
                        ConversationsScreen(viewModel = viewModel)
                    }
                    AppScreen.PROFILE -> {
                        ProfileScreen(
                            currentUser = currentUser,
                            viewModel = viewModel,
                            onReset = { viewModel.unregisterAndReset() }
                        )
                    }
                    AppScreen.NOTIFICATIONS_PREFS -> {
                        NotificationsPrefsScreen(
                            context = context,
                            viewModel = viewModel,
                            onRequestPermission = onRequestPermissions
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// BOTTOM NAVIGATION WITH BENTO DESIGN FLAIR
// --------------------------------------------------------------------------
@Composable
fun BentoBottomBar(
    currentScreen: AppScreen,
    notificationCount: Int,
    onNavigate: (AppScreen) -> Unit
) {
    // Beautiful lavender material footer
    Surface(
        color = Color(0xFFF3EDF7),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Color(0xFFCAC4D0).copy(alpha = 0.25f),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.GridView,
                label = "Bento",
                isSelected = currentScreen == AppScreen.MAP_AND_BROWSE,
                onClick = { onNavigate(AppScreen.MAP_AND_BROWSE) },
                testTag = "tab_dashboard"
            )
            BottomNavItem(
                icon = Icons.Default.AddCircle,
                textColor = Color(0xFF6750A4),
                label = "Offer",
                isSelected = currentScreen == AppScreen.CREATE_LISTING,
                onClick = { onNavigate(AppScreen.CREATE_LISTING) },
                testTag = "tab_create_listing"
            )
            BottomNavItem(
                icon = Icons.Default.Chat,
                label = "Chats",
                isSelected = currentScreen == AppScreen.MESSAGES,
                onClick = { onNavigate(AppScreen.MESSAGES) },
                testTag = "tab_messages"
            )
            BottomNavItem(
                icon = Icons.Default.Notifications,
                label = "Alerts",
                badgeCount = notificationCount,
                isSelected = currentScreen == AppScreen.NOTIFICATIONS_PREFS,
                onClick = { onNavigate(AppScreen.NOTIFICATIONS_PREFS) },
                testTag = "tab_notifications"
            )
            BottomNavItem(
                icon = Icons.Default.Person,
                label = "Profile",
                isSelected = currentScreen == AppScreen.PROFILE,
                onClick = { onNavigate(AppScreen.PROFILE) },
                testTag = "tab_profile"
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badgeCount: Int = 0,
    textColor: Color = Color(0xFF49454F)
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFE8DEF8) else Color.Transparent,
        animationSpec = tween(200),
        label = "BgAnim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag(testTag)
            .width(62.dp)
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(animatedBg)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF21005D) else textColor,
                modifier = Modifier.size(22.dp)
            )

            if (badgeCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .background(Color(0xFFB3261E), CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color(0xFF1D192B) else textColor.copy(alpha = 0.8f)
        )
    }
}


// --------------------------------------------------------------------------
// REGISTRATION (SECURE NEIGHBOR PROFILE SETUP)
// --------------------------------------------------------------------------
@Composable
fun RegistrationScreen(
    onRegister: (name: String, email: String, neighborhood: String, lat: Double, lon: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var neighborhood by remember { mutableStateOf("") }

    // Set interactive coordinates to simulate Greenwich, NYC, or suburbs
    var selectedLocationName by remember { mutableStateOf("Greenwood Central Block") }
    var lat by remember { mutableStateOf(40.7128) }
    var lon by remember { mutableStateOf(-74.0060) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Aesthetic Top Logo
        Surface(
            color = Color(0xFFEADDFF),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "🏡",
                    fontSize = 40.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Community Exchange",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFF21005D),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Secure local registration & neighborhood coordinate setup",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF49454F),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "SECURE PROFILE DETAILS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    placeholder = { Text("e.g. John Doe") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reg_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("e.g. john@house.net") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reg_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = neighborhood,
                    onValueChange = { neighborhood = it },
                    label = { Text("Neighborhood Name") },
                    placeholder = { Text("e.g. Greenwich, Brooklyn North") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reg_neighborhood_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // GPS Coordinate simulator card
                Text(
                    text = "CHOOSE PROXIMITY SEED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val locations = listOf(
                        Triple("Park Slope", 40.6698, -73.9804),
                        Triple("Greenwich NY", 40.7128, -74.0060),
                        Triple("Palo Alto", 37.4419, -122.1430)
                    )

                    locations.forEach { (locName, locLat, locLon) ->
                        val isSelected = selectedLocationName == locName
                        val btnBg = if (isSelected) Color(0xFF6750A4) else Color(0xFFEADDFF).copy(alpha = 0.5f)
                        val btnText = if (isSelected) Color.White else Color(0xFF21005D)

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(btnBg)
                                .clickable {
                                    selectedLocationName = locName
                                    lat = locLat
                                    lon = locLon
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = locName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = btnText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "📍", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "Assigned Location: $selectedLocationName", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Lat: $lat, Lon: $lon", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                if (name.isNotBlank() && email.isNotBlank() && neighborhood.isNotBlank()) {
                    onRegister(name, email, neighborhood, lat, lon)
                }
            },
            enabled = name.isNotBlank() && email.isNotBlank() && neighborhood.isNotBlank(),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6750A4),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_registration")
        ) {
            Text(
                text = "Secure Register & Seed Neighborhood",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// --------------------------------------------------------------------------
// BENTO STYLE DASHBOARD (MAP, RADIAL FILTER, SEARCH, HIGHLIGHTS CARD)
// --------------------------------------------------------------------------
@Composable
fun DashboardBentoScreen(
    context: Context,
    viewModel: ExchangeViewModel,
    currentUser: User?
) {
    val listings by viewModel.filteredListings.collectAsStateWithLifecycle()
    val searchVal by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeRadius by viewModel.radiusKm.collectAsStateWithLifecycle()
    val activeCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val notificationLog by viewModel.notificationsLog.collectAsStateWithLifecycle()

    var showPinPopup by remember { mutableStateOf<Listing?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // App top header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome to ${currentUser?.neighborhood ?: "The Block"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Text(
                    text = "Hi, ${currentUser?.name ?: "Neighbor"}!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF21005D)
                )
            }
            // Quick Profile icon representation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(currentUser?.avatarColor ?: 0xFF6750A4.toInt()))
            ) {
                Text(
                    text = (currentUser?.name ?: "U").take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bento Top Search Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3EDF7), RoundedCornerShape(28.dp))
                .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF49454F))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = searchVal,
                onValueChange = { viewModel.searchQuery.value = it },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                placeholder = { Text("Search neighborly goods...", color = Color(0xFF49454F).copy(alpha = 0.6f)) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_search_input")
            )
            if (searchVal.isNotEmpty()) {
                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear text")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Large Bento block: Custom Map Canvas with Proximity Range highlighted
        Text(
            text = "PROXIMITY MAP (RADIAL INTEGRATION)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF6750A4),
            letterSpacing = 1.1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = Color(0xFFE8DEF8),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Interactive Custom drawing canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            // Touch anywhere on map background info
                            Toast
                                .makeText(
                                    context,
                                    "Tap individual pinned item nodes to see detailed exchange offers!",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)

                    // Draw neighborhood background grids patterns
                    // Draw dynamic concentric range filters circles
                    val scales = listOf(0.3f, 0.65f, 0.9f)
                    scales.forEachIndexed { i, factor ->
                        drawCircle(
                            color = Color(0xFF6750A4).copy(alpha = 0.08f),
                            radius = (w / 2.1f) * factor,
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFF6750A4).copy(alpha = 0.22f),
                            radius = (w / 2.1f) * factor,
                            center = center,
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }

                    // Represent user's circular range outline matching slider selection
                    val currentRadiusScale = (activeRadius / 15.0).coerceIn(0.15, 0.95).toFloat()
                    drawCircle(
                        color = Color(0xFF6750A4).copy(alpha = 0.07f),
                        radius = (w / 2.1f) * currentRadiusScale,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF6750A4).copy(alpha = 0.55f),
                        radius = (w / 2.1f) * currentRadiusScale,
                        center = center,
                        style = Stroke(width = 3f)
                    )

                    // Draw primary central User Home Home Station node
                    drawCircle(
                        color = Color.White,
                        radius = 16f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF6750A4),
                        radius = 11f,
                        center = center
                    )
                }

                // Superimpose local Listing Node Pins as rich responsive buttons
                currentUser?.let { user ->
                    listings.forEach { item ->
                        val distance = viewModel.distanceToUser(item)
                        // Normalize map plotting within maximum 15km representation space
                        val norm = distance / 15.0
                        if (norm <= 1.0) {
                            // Convert distance to directional coordinates around center
                            val angleRad = (item.id * 37.1 % 360) * (Math.PI / 180.0)
                            val rPixels = (210f * 2.2f / 2f) * norm.coerceIn(0.1, 0.9)
                            
                            val xOffset = (rPixels * cos(angleRad)).toFloat()
                            val yOffset = (rPixels * sin(angleRad)).toFloat()

                            // Select icon for categories
                            val pinEmoji = when (item.category) {
                                "Tools & Gardening" -> "🛠️"
                                "Home & Kitchen" -> "🍎"
                                "Pet Sitting & Care" -> "🐕"
                                "Electronics" -> "💻"
                                "Skills & Tutoring" -> "📚"
                                "Lending & Share" -> "🤝"
                                else -> "📦"
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = xOffset.dp, y = yOffset.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (item == showPinPopup) Color(0xFF6750A4) else Color.White
                                    )
                                    .border(
                                        1.5.dp, 
                                        Color(0xFF6750A4).copy(alpha = 0.8f), 
                                        CircleShape
                                    )
                                    .clickable {
                                        showPinPopup = item
                                    }
                            ) {
                                Text(text = pinEmoji, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Overlay interactive filter settings bottom board
                Surface(
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "LIVING COMPASS RADIUS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6750A4)
                            )
                            Text(
                                text = "${currentUser?.neighborhood ?: "Greenwood"} Circle (${"%.1f".format(activeRadius)} km limit)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(1.0, 5.0, 10.0, 15.0).forEach { kmVal ->
                                val isSelected = activeRadius == kmVal
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) Color(0xFF6750A4) else Color(0xFFEADDFF).copy(0.4f)
                                        )
                                        .clickable {
                                            viewModel.radiusKm.value = kmVal
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${kmVal.toInt()}k",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF21005D)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Pin Details expansion overlay if touched
        showPinPopup?.let { pin ->
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD8E4)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📍", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pin.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF31111D)
                            )
                        }
                        IconButton(
                            onClick = { showPinPopup = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close detail", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Category: ${pin.category} • Offered by ${pin.ownerName} (~${"%.1f".format(viewModel.distanceToUser(pin))} km away)",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pin.description,
                        fontSize = 12.sp,
                        color = Color(0xFF1D1B20)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFC05621),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${pin.exchangeType}: ${pin.priceOrDetails}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.activeChatPartnerId.value = pin.ownerId
                                viewModel.activeChatPartnerName.value = pin.ownerName
                                viewModel.currentScreen.value = AppScreen.MESSAGES
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(text = "Connect & Message", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Recent In-App Notification Notification ticker bento piece
        val unreadNotifications = notificationLog.filter { !it.isRead }
        if (unreadNotifications.isNotEmpty()) {
            val topUnread = unreadNotifications.first()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Mark as read and jump to settings log
                        viewModel.markNotificationAsRead(topUnread.id)
                        viewModel.currentScreen.value = AppScreen.NOTIFICATIONS_PREFS
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFF6750A4), CircleShape)
                    ) {
                        Text(
                            text = if (topUnread.type == NotificationType.DIRECT_MESSAGE) "💬" else "🔔",
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = topUnread.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF21005D)
                            )
                            Text(
                                text = "Recent",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = topUnread.message,
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Horizontal Category Select Bento Row
        Text(
            text = "DEPARTMENTS & DISCOVERIES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF6750A4),
            letterSpacing = 1.1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        val allCategoriesWithIcons = listOf(
            "All" to "🌐",
            "Tools & Gardening" to "🛠️",
            "Home & Kitchen" to "🍎",
            "Pet Sitting & Care" to "🐕",
            "Electronics" to "💻",
            "Skills & Tutoring" to "📚",
            "Lending & Share" to "🤝",
            "Miscellaneous" to "📦"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(allCategoriesWithIcons) { (cat, emoji) ->
                val isSelected = activeCategory == cat
                Surface(
                    color = if (isSelected) Color(0xFF6750A4) else Color(0xFFF7F2FA),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.3f)),
                    modifier = Modifier.clickable {
                        viewModel.selectedCategory.value = cat
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = emoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF49454F)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Nearby postings feed list bento
        Text(
            text = "NEIGHBORS POSTED FEED (${listings.size} matching items)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF6750A4),
            letterSpacing = 1.1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (listings.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔎", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No active listings within ${activeRadius.toInt()} km.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Try increasing the Proximity search radius or clear filters.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Standard high-fidelity Grid card bento arrangement
            listings.forEach { l ->
                BentoListingFeedCard(
                    listing = l,
                    distanceKm = viewModel.distanceToUser(l),
                    onConnectChat = {
                        viewModel.activeChatPartnerId.value = l.ownerId
                        viewModel.activeChatPartnerName.value = l.ownerName
                        viewModel.currentScreen.value = AppScreen.MESSAGES
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun BentoListingFeedCard(
    listing: Listing,
    distanceKm: Double,
    onConnectChat: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Tag
                Surface(
                    color = Color(0xFFE8DEF8),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = listing.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Proximity distance badge
                Text(
                    text = "📍 ~${"%.2f".format(distanceKm)} km away",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = listing.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D1B20)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = listing.description,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF49454F)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = Color(0xFFCAC4D0).copy(0.24f))

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OWNER: ${listing.ownerName}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "${listing.exchangeType} • ${listing.priceOrDetails}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC05621)
                    )
                }

                Button(
                    onClick = onConnectChat,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "Message", modifier = Modifier.size(14.dp))
                        Text(text = "Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// --------------------------------------------------------------------------
// POST NEW COMMUNITY EXCHANGE CARD LISTING SCREEN
// --------------------------------------------------------------------------
@Composable
fun CreateListingScreen(
    onPost: (title: String, desc: String, category: String, exchangeType: String, priceDetail: String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tools & Gardening") }
    var selectedExchangeType by remember { mutableStateOf("Borrow") }
    var priceDetail by remember { mutableStateOf("") }

    val categories = listOf(
        "Tools & Gardening",
        "Home & Kitchen",
        "Skills & Tutoring",
        "Pet Sitting & Care",
        "Electronics",
        "Lending & Share",
        "Miscellaneous"
    )

    val exchangeTypes = listOf("Borrow", "Trade", "Free", "Sale")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Offer Item or Service",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFF21005D)
        )
        Text(
            text = "Post an availability card inside the neighborhood. Your local neighbors will be notified automatically based on their distance and category filters!",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What are you sharing/requesting?") },
            placeholder = { Text("e.g. Aluminum step ladder, Sourdough starters") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6750A4)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("listing_title_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Exchanges / Care guidelines / Description") },
            placeholder = { Text("Describe details, pick-up times, and expectations here...") },
            minLines = 3,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6750A4)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("listing_desc_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Categories selection group
        Text(
            text = "Select Category",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6750A4)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    color = if (isSelected) Color(0xFF6750A4) else Color(0xFFF7F2FA),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.3f)),
                    modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                    Text(
                        text = cat,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFF49454F),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Exchange model selection group
        Text(
            text = "Exchange Model Type",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6750A4)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            exchangeTypes.forEach { type ->
                val isSelected = selectedExchangeType == type
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFFC05621) else Color(0xFFF7F2FA))
                        .border(1.dp, Color(0xFFCAC4D0).copy(0.3f), RoundedCornerShape(12.dp))
                        .clickable { selectedExchangeType = type }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = type,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFF49454F)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = priceDetail,
            onValueChange = { priceDetail = it },
            label = { Text("Terms (e.g. 'Complimentary', 'Trade for fruit', '$15')") },
            placeholder = { Text("Keep it friendly and clear") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6750A4)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("listing_terms_input")
        )

        Spacer(modifier = Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.DarkGray),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(text = "Cancel")
            }

            Button(
                onClick = {
                    if (title.isNotBlank() && description.isNotBlank()) {
                        onPost(title, description, selectedCategory, selectedExchangeType, priceDetail.ifBlank { "Free" })
                    }
                },
                enabled = title.isNotBlank() && description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("submit_create_listing")
            ) {
                Text(text = "Post Proximity Ad", fontWeight = FontWeight.Bold)
            }
        }
    }
}


// --------------------------------------------------------------------------
// DIRECT MESSAGES (CHAT THREAD HISTORY AND REAL CHAT SIMULATION PANEL)
// --------------------------------------------------------------------------
@Composable
fun ConversationsScreen(viewModel: ExchangeViewModel) {
    val headers by viewModel.conversationHeaders.collectAsStateWithLifecycle()
    val activePartnerId by viewModel.activeChatPartnerId.collectAsStateWithLifecycle()
    val activePartnerName by viewModel.activeChatPartnerName.collectAsStateWithLifecycle()
    val messages by viewModel.activeChatMessages.collectAsStateWithLifecycle()

    var chatText by remember { mutableStateOf("") }

    if (activePartnerId != null) {
        // Render Active Deep Chat bubble screen representation
        Column(modifier = Modifier.fillMaxSize()) {
            // Screen Header Back Bar
            Surface(
                color = Color(0xFFF3EDF7),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.activeChatPartnerId.value = null
                            viewModel.activeChatPartnerName.value = null
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF6750A4), CircleShape)
                    ) {
                        Text(
                            text = (activePartnerName ?: "N").take(2).uppercase(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = activePartnerName ?: "Neighbor",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20)
                        )
                        Text(text = "Active conversation Thread", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            // Message list Scroll logic
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isSelf = msg.senderId == 1L // primary local user
                    
                    val alignment = if (isSelf) Alignment.End else Alignment.Start
                    val containerColor = if (isSelf) Color(0xFF6750A4) else Color(0xFFF7F2FA)
                    val textColor = if (isSelf) Color.White else Color(0xFF1D1B20)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = alignment
                    ) {
                        Surface(
                            color = containerColor,
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isSelf) 16.dp else 2.dp,
                                bottomEnd = if (isSelf) 2.dp else 16.dp
                            ),
                            border = if (!isSelf) BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.4f)) else null,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.content,
                                    color = textColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isSelf) "Sent • Realtime" else "${msg.senderName} • Neighbor",
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Bottom Chat Input and Mock Automated responses option helper for prototype testing
            Surface(
                color = Color(0xFFF3EDF7),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Quick reply assistant pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Yes, still available!",
                            "Awesome, where can I pick it up?",
                            "Thank you neighbor!",
                            "Sounds great, let me inspect it"
                        ).forEach { pillText ->
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable {
                                    chatText = pillText
                                }
                            ) {
                                Text(
                                    text = pillText,
                                    color = Color(0xFF6750A4),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatText,
                            onValueChange = { chatText = it },
                            placeholder = { Text("Write a secure message...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6750A4)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_text")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (chatText.isNotBlank()) {
                                    viewModel.sendMessage(
                                        receiverId = activePartnerId!!,
                                        receiverName = activePartnerName ?: "Neighbor",
                                        content = chatText
                                    )
                                    chatText = ""
                                }
                            },
                            modifier = Modifier
                                .background(Color(0xFF6750A4), CircleShape)
                                .size(48.dp)
                                .testTag("send_chat_msg_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    } else {
        // Conversations list screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Neighborhood Chats",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF21005D)
            )
            Text(
                text = "Secure end-to-end direct transactions regarding borrow requests & garden swaps.",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (headers.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "💬", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No private conversations yet.",
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        Text(
                            text = "Open listings near you and press 'Connect' to negotiate an item trade!",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(headers) { head ->
                        val initialRepresentation = head.partnerName.take(2).uppercase()
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (head.isUnread) Color(0xFFFFD8E4) else Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.25f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.activeChatPartnerId.value = head.partnerId
                                    viewModel.activeChatPartnerName.value = head.partnerName
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFF6750A4), CircleShape)
                                ) {
                                    Text(
                                        text = initialRepresentation,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = head.partnerName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1D1B20)
                                        )
                                        Text(
                                            text = "Active",
                                            fontSize = 10.sp,
                                            color = Color(0xFF6750A4),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = head.lastMessage,
                                        fontSize = 12.sp,
                                        color = Color.DarkGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (head.isUnread) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFFB3261E), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// --------------------------------------------------------------------------
// PROXIMITY NOTIFICATION PREFERENCES & SIMULATOR LOG FEED
// --------------------------------------------------------------------------
@Composable
fun NotificationsPrefsScreen(
    context: Context,
    viewModel: ExchangeViewModel,
    onRequestPermission: () -> Unit
) {
    val masterEnabled by viewModel.masterNotificationsEnabled.collectAsStateWithLifecycle()
    val dmEnabled by viewModel.dmNotificationsEnabled.collectAsStateWithLifecycle()
    val listingEnabled by viewModel.nearbyListingsNotificationsEnabled.collectAsStateWithLifecycle()
    val alertRadius by viewModel.alertMaxRadiusKm.collectAsStateWithLifecycle()
    val rawSubscribedCategories by viewModel.subscribedCategories.collectAsStateWithLifecycle()
    val simLogs by viewModel.simulationLogs.collectAsStateWithLifecycle()
    val notificationsLogList by viewModel.notificationsLog.collectAsStateWithLifecycle()

    val availableCategories = listOf(
        "Tools & Gardening",
        "Home & Kitchen",
        "Skills & Tutoring",
        "Pet Sitting & Care",
        "Electronics",
        "Lending & Share",
        "Miscellaneous"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notification Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF21005D)
                )
                Text(
                    text = "Customize and manage how and what proximity exchange notifications trigger alerts on your device.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            // Quick Button to request dynamic device push permission
            IconButton(
                onClick = onRequestPermission,
                modifier = Modifier
                    .background(Color(0xFFEADDFF), CircleShape)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Refresh System Permissions", tint = Color(0xFF21005D), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bento 1: Master Toggle Switch
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (masterEnabled) Color(0xFFEADDFF) else Color(0xFFF7F2FA)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Global Push Notifications",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF21005D)
                    )
                    Text(
                        text = "Allow community transaction and neighbor listing alerts on this device.",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F)
                    )
                }

                Switch(
                    checked = masterEnabled,
                    onCheckedChange = { viewModel.toggleMasterNotifications() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6750A4)
                    ),
                    modifier = Modifier.testTag("toggle_master_notifications")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bento 2: Channels Section (Direct Messages vs Neaby items)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ACTIVE ALERT CHANNELS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                // DM Alert Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Direct Messages", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "Alert when any neighbor messages you regarding coordinates or listings", fontSize = 11.sp, color = Color.Gray)
                    }
                    Checkbox(
                        checked = dmEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = { viewModel.toggleDmNotifications() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4)),
                        modifier = Modifier.testTag("toggle_dm_notifications")
                    )
                }

                Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 6.dp))

                // New Listings Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Nearby Listing Postings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "Alert when new resources are posted matching parameters below", fontSize = 11.sp, color = Color.Gray)
                    }
                    Checkbox(
                        checked = listingEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = { viewModel.toggleNearbyListingsNotifications() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4)),
                        modifier = Modifier.testTag("toggle_listing_notifications")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bento 3: Radius Proximity Alert Slider Settings
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ALERT RADIUS RADIUS THRESHOLD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${alertRadius.toInt()} km",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFC05621)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Do not distract me with listings posted outside of this maximum distance limit.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = alertRadius.toFloat(),
                    onValueChange = { viewModel.updateAlertMaxRadius(it.toDouble()) },
                    valueRange = 1f..15f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6750A4),
                        activeTrackColor = Color(0xFF6750A4)
                    ),
                    modifier = Modifier.testTag("notification_radius_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "1 km", fontSize = 10.sp, color = Color.Gray)
                    Text(text = "5.0 km", fontSize = 10.sp, color = Color.Gray)
                    Text(text = "10.0 km", fontSize = 10.sp, color = Color.Gray)
                    Text(text = "15 km", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bento 4: Subscribed Categories for notification alert
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SUBSCRIBED CATEGORIES ALERTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Only push notifications for items belonging to checked departments.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableCategories) { cat ->
                        val isSubscribed = rawSubscribedCategories.contains(cat)
                        Surface(
                            color = if (isSubscribed) Color(0xFFE6F3EE) else Color(0xFFF7F2FA),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSubscribed) Color(0xFF009688) else Color(0xFFCAC4D0).copy(0.2f)
                            ),
                            modifier = Modifier.clickable {
                                viewModel.toggleCategorySubscription(cat)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSubscribed) Icons.Default.CheckCircle else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (isSubscribed) Color(0xFF009688) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = cat,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSubscribed) Color(0xFF004D3F) else Color(0xFF49454F),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bento 5: INTERACTIVE REALTIME NOTIFICATION DIAGNOSTIC SIMULATOR TESTER
        Text(
            text = "PROXIMITY ALERTS DIAGNOSTIC ENGINE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6750A4),
            letterSpacing = 1.1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)), // Soft pink alerts canvas
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFFFDA4AF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🚨 Neighbor Simulation Test-bed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF9F1239)
                )
                Text(
                    text = "Simulate mock neighbor actions to evaluate if your filters (proximity distance limits, subscribed categories, master switches) pass matching criteria successfully!",
                    fontSize = 11.sp,
                    color = Color(0xFFBE123C)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // The Output logs representing physical evaluation state
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = simLogs,
                        color = Color(0xFF34D399), // Neon green console styling
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Button 1: Send simulated text DM
                    Button(
                        onClick = { viewModel.simulateIncomingDirectMessage(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_simulate_msg")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Simulate Key", fontSize = 9.sp, color = Color.White.copy(0.7f))
                            Text(text = "📩 Message DM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Button 2: Post simulated block listing
                    Button(
                        onClick = { viewModel.simulateNeighborPostingListing(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC05621)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("btn_simulate_listing")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Simulate Random", fontSize = 9.sp, color = Color.White.copy(0.7f))
                            Text(text = "🏷️ Neighbor Ad", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bento 6: HISTORY LOG OF DELIVERED ALERTS FEED
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ALERTS RECEIVED LOG (${notificationsLogList.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6750A4),
                letterSpacing = 1.1.sp
            )
            Text(
                text = "Clear History",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.clearNotifications() }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (notificationsLogList.isEmpty()) {
            Surface(
                color = Color(0xFFF7F2FA),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(text = "No alerts fired yet. Run simulation tests above!", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            notificationsLogList.forEach { log ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (log.isRead) Color.White else Color(0xFFEADDFF).copy(0.3f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            viewModel.markNotificationAsRead(log.id)
                            if (log.type == NotificationType.DIRECT_MESSAGE && log.relatedId != null) {
                                viewModel.activeChatPartnerId.value = log.relatedId
                                viewModel.activeChatPartnerName.value = "Neighbor"
                                viewModel.currentScreen.value = AppScreen.MESSAGES
                            } else {
                                viewModel.currentScreen.value = AppScreen.MAP_AND_BROWSE
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (log.type) {
                                NotificationType.DIRECT_MESSAGE -> "📩"
                                NotificationType.NEW_LISTING -> "🏷️"
                                else -> "⚙️"
                            },
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = log.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF21005D)
                            )
                            Text(
                                text = log.message,
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}


// --------------------------------------------------------------------------
// PROFILE (RESET, STATS, INFORMATION PANEL)
// --------------------------------------------------------------------------
@Composable
fun ProfileScreen(
    currentUser: User?,
    viewModel: ExchangeViewModel,
    onReset: () -> Unit
) {
    val listings by viewModel.filteredListings.collectAsStateWithLifecycle()
    val rawSubscribedCategories by viewModel.subscribedCategories.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aesthetic Top Logo Representation
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(currentUser?.avatarColor ?: 0xFF6750A4.toInt()))
        ) {
            Text(
                text = (currentUser?.name ?: "User").take(2).uppercase(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = currentUser?.name ?: "Registered Neighbor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFF21005D)
        )

        Text(
            text = "Block Status: Active Resident • Secure ID #${currentUser?.id ?: 1}",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Dashboard Stats Grid
        Text(
            text = "NEIGHBORHOOD PERFORMANCE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6750A4),
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🏡", fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Neighborhood", fontSize = 11.sp, color = Color.Gray)
                    Text(text = currentUser?.neighborhood ?: "Greenwood", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📦", fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Total Active Ads", fontSize = 11.sp, color = Color.Gray)
                    Text(text = "${listings.size} Items", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bento layout showing profile summary of options
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PROFILE SETTINGS SUMMARY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Email Address:", fontSize = 12.sp, color = Color.Gray)
                    Text(text = currentUser?.email ?: "Not set", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Divider(color = Color(0xFFCAC4D0).copy(0.2f), modifier = Modifier.padding(vertical = 6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Subscribed Departments:", fontSize = 12.sp, color = Color.Gray)
                    Text(text = "${rawSubscribedCategories.size} Categories", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF009688))
                }

                Divider(color = Color(0xFFCAC4D0).copy(0.2f), modifier = Modifier.padding(vertical = 6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "GPS Coordinate Proximity:", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = "Lat: ${"%.4f".format(currentUser?.latitude ?: 0.0)}, Lon: ${"%.4f".format(currentUser?.longitude ?: 0.0)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Reset system button
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
            shape = RoundedCornerShape(50.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("reset_profile_btn")
        ) {
            Text(
                text = "Clear Database & Reset Native Prototype",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}
