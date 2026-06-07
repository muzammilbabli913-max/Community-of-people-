package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.model.Listing
import com.example.data.model.Message
import com.example.data.repository.ExchangeRepository
import com.example.notification.AppNotification
import com.example.notification.NotificationHelper
import com.example.notification.NotificationType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen {
    REGISTRATION,
    MAP_AND_BROWSE,
    CREATE_LISTING,
    MESSAGES,
    PROFILE,
    NOTIFICATIONS_PREFS
}

data class ConversationHeader(
    val partnerId: Long,
    val partnerName: String,
    val lastMessage: String,
    val timestamp: Long,
    val isUnread: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeViewModel(private val repository: ExchangeRepository) : ViewModel() {

    // Current logged-in user state
    val currentUserState: StateFlow<User?> = repository.registeredUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val currentScreen = MutableStateFlow(AppScreen.REGISTRATION)

    // Browsing filters (for Map & Main List)
    val selectedCategory = MutableStateFlow<String>("All")
    val radiusKm = MutableStateFlow<Double>(5.0)
    val searchQuery = MutableStateFlow<String>("")

    // ----------------------------------------------------
    // NOTIFICATION PREFERENCES STATES
    // ----------------------------------------------------
    val masterNotificationsEnabled = MutableStateFlow(true)
    val dmNotificationsEnabled = MutableStateFlow(true)
    val nearbyListingsNotificationsEnabled = MutableStateFlow(true)
    val alertMaxRadiusKm = MutableStateFlow(5.0)
    
    // Subscribed categories for notification trigger - starts with all
    val subscribedCategories = MutableStateFlow<Set<String>>(
        setOf("Tools & Gardening", "Home & Kitchen", "Skills & Tutoring", "Pet Sitting & Care", "Electronics", "Lending & Share", "Miscellaneous")
    )

    // Notification Log History (In-App notifications)
    private val _notificationsLog = MutableStateFlow<List<AppNotification>>(
        listOf(
            AppNotification(
                id = "init-sys alert",
                title = "Welcome Alert",
                message = "Your Neighborhood Exchange is now operational! Manage your custom filters anytime in settings.",
                type = NotificationType.SYSTEM
            )
        )
    )
    val notificationsLog: StateFlow<List<AppNotification>> = _notificationsLog.asStateFlow()

    // Status message shown in the notification simulator debug card
    val simulationLogs = MutableStateFlow<String>("Simulator ready. Click below to test your notification settings.")

    // All database listings
    private val _dbListings = repository.allListings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Deriving filtered listings based on category, radius and search terms
    val filteredListings: StateFlow<List<Listing>> = combine(
        _dbListings,
        currentUserState,
        selectedCategory,
        radiusKm,
        searchQuery
    ) { listings, user, category, radius, query ->
        if (user == null) return@combine emptyList<Listing>()

        listings.filter { listing ->
            // Category match
            val categoryMatch = category == "All" || listing.category.equals(category, ignoreCase = true)

            // Radius match
            val distance = repository.calculateDistance(
                user.latitude, user.longitude,
                listing.latitude, listing.longitude
            )
            val radiusMatch = distance <= radius

            // Search query match
            val searchMatch = query.isEmpty() || 
                    listing.title.contains(query, ignoreCase = true) ||
                    listing.description.contains(query, ignoreCase = true)

            categoryMatch && radiusMatch && searchMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat controls
    val activeChatPartnerId = MutableStateFlow<Long?>(null)
    val activeChatPartnerName = MutableStateFlow<String?>(null)

    // Current live chat message history
    val activeChatMessages: StateFlow<List<Message>> = combine(
        currentUserState,
        activeChatPartnerId
    ) { user, partnerId ->
        if (user == null || partnerId == null) {
            flowOf(emptyList())
        } else {
            repository.getMessagesBetweenUsers(user.id, partnerId)
        }
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Grouping all messages of active user into conversation threads
    val conversationHeaders: StateFlow<List<ConversationHeader>> = currentUserState.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            repository.getAllMessagesForUser(user.id).map { messages ->
                val grouped = messages.groupBy { msg ->
                    if (msg.senderId == user.id) {
                        msg.receiverId to msg.receiverName
                    } else {
                        msg.senderId to msg.senderName
                    }
                }
                grouped.map { (partner, msgs) ->
                    val lastMsg = msgs.first()
                    ConversationHeader(
                        partnerId = partner.first,
                        partnerName = partner.second,
                        lastMessage = lastMsg.content,
                        timestamp = lastMsg.timestamp,
                        isUnread = !lastMsg.isRead && lastMsg.receiverId == user.id
                    )
                }.sortedByDescending { it.timestamp }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Monitor registration state to route screen
        viewModelScope.launch {
            currentUserState.collect { user ->
                if (user != null) {
                    currentScreen.value = AppScreen.MAP_AND_BROWSE
                } else {
                    currentScreen.value = AppScreen.REGISTRATION
                }
            }
        }
    }

    /**
     * Submit profile and pre-populate local listings to explore
     */
    fun register(name: String, email: String, neighborhood: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            val colors = listOf(
                0xFF3F51B5.toInt(),
                0xFF009688.toInt(),
                0xFFE91E63.toInt(),
                0xFF4CAF50.toInt(),
                0xFFFF9800.toInt(),
                0xFF9C27B0.toInt()
            )
            val randomColor = colors.random()

            val newUser = User(
                id = 1,
                name = name,
                email = email,
                neighborhood = neighborhood,
                latitude = lat,
                longitude = lon,
                avatarColor = randomColor
            )
            
            repository.registerUser(newUser)
            repository.prepopulateSampleData(lat, lon)
        }
    }

    /**
     * Delete user profile to reset prototype
     */
    fun unregisterAndReset() {
        viewModelScope.launch {
            repository.clearAllData()
            _notificationsLog.value = listOf(
                AppNotification(
                    id = "reset-sys alert",
                    title = "Database Cleaned",
                    message = "Ready to register a new user profile.",
                    type = NotificationType.SYSTEM
                )
            )
            currentScreen.value = AppScreen.REGISTRATION
        }
    }

    /**
     * Post a new community offer/ask
     */
    fun postListing(title: String, description: String, category: String, exchangeType: String, priceOrDetails: String) {
        viewModelScope.launch {
            val user = currentUserState.value ?: return@launch
            
            // Subtle layout offset so it appears as a local neighboring residence on map
            val latOffset = (Math.random() - 0.5) * 0.006
            val lonOffset = (Math.random() - 0.5) * 0.006
            
            val newListing = Listing(
                ownerId = user.id,
                ownerName = user.name,
                title = title,
                description = description,
                category = category,
                exchangeType = exchangeType,
                priceOrDetails = priceOrDetails,
                latitude = user.latitude + latOffset,
                longitude = user.longitude + lonOffset
            )
            repository.insertListing(newListing)
            currentScreen.value = AppScreen.MAP_AND_BROWSE
        }
    }

    /**
     * Send direct chat message to neighbor
     */
    fun sendMessage(receiverId: Long, receiverName: String, content: String, listingId: Long? = null) {
        if (content.trim().isEmpty()) return
        
        viewModelScope.launch {
            val user = currentUserState.value ?: return@launch
            val message = Message(
                senderId = user.id,
                senderName = user.name,
                receiverId = receiverId,
                receiverName = receiverName,
                listingId = listingId,
                content = content
            )
            repository.insertMessage(message)
        }
    }

    /**
     * Calculate direct distance list-wise
     */
    fun distanceToUser(listing: Listing): Double {
        val user = currentUserState.value ?: return 0.0
        return repository.calculateDistance(
            user.latitude, user.longitude,
            listing.latitude, listing.longitude
        )
    }

    // -------------------------------------------------------------------------
    // NOTIFICATION TOGGLES AND SETTINGS EVENT HANDLERS
    // -------------------------------------------------------------------------
    fun toggleMasterNotifications() {
        masterNotificationsEnabled.value = !masterNotificationsEnabled.value
    }

    fun toggleDmNotifications() {
        dmNotificationsEnabled.value = !dmNotificationsEnabled.value
    }

    fun toggleNearbyListingsNotifications() {
        nearbyListingsNotificationsEnabled.value = !nearbyListingsNotificationsEnabled.value
    }

    fun updateAlertMaxRadius(radius: Double) {
        alertMaxRadiusKm.value = radius
    }

    fun toggleCategorySubscription(category: String) {
        val currentSet = subscribedCategories.value.toMutableSet()
        if (currentSet.contains(category)) {
            currentSet.remove(category)
        } else {
            currentSet.add(category)
        }
        subscribedCategories.value = currentSet
    }

    fun clearNotifications() {
        _notificationsLog.value = emptyList()
    }

    fun markNotificationAsRead(id: String) {
        _notificationsLog.value = _notificationsLog.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    // -------------------------------------------------------------------------
    // SYSTEM AND INTERACTIVE SIMULATION ENGINE FOR TESTING PREFERENCES
    // -------------------------------------------------------------------------
    
    /**
     * Triggers a push notification if matching user preferences
     */
    private fun triggerPushNotification(
        context: Context,
        title: String,
        message: String,
        type: NotificationType,
        relatedId: Long? = null
    ) {
        // Add to view log always for high-fidelity inspectability
        val newAlert = AppNotification(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            type = type,
            relatedId = relatedId
        )
        
        // Prepend to show most recent at the top
        _notificationsLog.value = listOf(newAlert) + _notificationsLog.value

        // Fire physical device system bar notification
        NotificationHelper.showSystemNotification(context, title, message, type, relatedId)
    }

    /**
     * Simulations: Represents a neighbor sending a DM regarding tool sharing.
     */
    fun simulateIncomingDirectMessage(context: Context) {
        val user = currentUserState.value
        if (user == null) {
            simulationLogs.value = "Cannot simulate: Register a user first!"
            return
        }

        viewModelScope.launch {
            val neighborId = 101L
            val neighborName = "Oliver Green"
            val textOptions = listOf(
                "Hey! I noticed you wanted to borrow my lawn mower. It is ready in my driveway, battery is fully charged!",
                "Hello neighbor, is the hammer drill back in stock yet? No rush!",
                "Sure, you can borrow my folding ladder tomorrow. Let me know what time works best for you."
            )
            val selectedMsg = textOptions.random()

            // 1. Insert message to DB to populate chat history
            repository.insertMessage(
                Message(
                    senderId = neighborId,
                    senderName = neighborName,
                    receiverId = user.id,
                    receiverName = user.name,
                    content = selectedMsg,
                    listingId = null
                )
            )

            // 2. Evaluate Preferences for PUSH NOTIFICATION
            if (!masterNotificationsEnabled.value) {
                simulationLogs.value = "📩 Message Inserted: '${selectedMsg}'. Push Notification blocked by Master Switch."
                return@launch
            }

            if (!dmNotificationsEnabled.value) {
                simulationLogs.value = "📩 Message Inserted: '${selectedMsg}'. Push Notification blocked by Messages Preference."
                return@launch
            }

            // Fire Push Notification successfully!
            triggerPushNotification(
                context = context,
                title = "Message from $neighborName",
                message = selectedMsg,
                type = NotificationType.DIRECT_MESSAGE,
                relatedId = neighborId
            )
            simulationLogs.value = "🎯 Success! Push Notification fired for message: '$selectedMsg'"
        }
    }

    /**
     * Simulations: Generates a listing posted randomly in the block.
     * Evaluates geographical distance and category subscription filters.
     */
    fun simulateNeighborPostingListing(context: Context) {
        val user = currentUserState.value
        if (user == null) {
            simulationLogs.value = "Cannot simulate: Register a user first!"
            return
        }

        viewModelScope.launch {
            // Pick a randomized neighbor & item to offer
            val neighborOptions = listOf(
                Triple("Sophia Miller", "Home & Kitchen", "Homemade Raspberry Jam & Tartlets"),
                Triple("Carlos Vance", "Lending & Share", "Metal Extension Ladder (15 ft)"),
                Triple("Emma Kincaid", "Pet Sitting & Care", "Free Cat Food & Toys (Unused Pack)"),
                Triple("Liam Torres", "Electronics", "Logitech Keyboard & Mouse Set"),
                Triple("Aria Rhodes", "Skills & Tutoring", "Intro to Piano Sheet Music & Practice"),
                Triple("Jackson Parker", "Miscellaneous", "Spare Camping Chair & Umbrella"),
                Triple("Oliver Green", "Tools & Gardening", "Corded Hedge Trimmers")
            )

            val selection = neighborOptions.random()
            val sellerName = selection.first
            val category = selection.second
            val title = selection.third

            // Pick a distance scale (from 0.5km up to 12.0km)
            val testDistancesOptions = listOf(0.4, 1.2, 2.5, 4.2, 7.5, 11.5)
            val targetDistance = testDistancesOptions.random()

            // Math to translate distance in km to lat/lon offsets
            val latDegreePerKm = 1.0 / 111.0
            val lonDegreePerKm = 1.0 / (111.0 * Math.cos(Math.toRadians(user.latitude)))
            
            // Random angle
            val angle = Math.random() * 2 * Math.PI
            val latOffset = targetDistance * latDegreePerKm * Math.sin(angle)
            val lonOffset = targetDistance * lonDegreePerKm * Math.cos(angle)

            val itemLat = user.latitude + latOffset
            val itemLon = user.longitude + lonOffset

            val mockDetail = "Quick listing update. Located ~${"%.2f".format(targetDistance)} km away from you in the local block."

            val simulatedListing = Listing(
                ownerId = (100..400).random().toLong(),
                ownerName = sellerName,
                title = title,
                description = "Simulated listing update for design test. Ready for trade or share. $mockDetail",
                category = category,
                exchangeType = listOf("Free", "Borrow", "Trade", "Sale").random(),
                priceOrDetails = listOf("Complimentary", "Trade for coffee", "$10", "Free").random(),
                latitude = itemLat,
                longitude = itemLon
            )

            // 1. Insert listing to DB to populate local Feed list
            repository.insertListing(simulatedListing)

            // 2. Evaluate Push Preferences
            if (!masterNotificationsEnabled.value) {
                simulationLogs.value = "🏷️ Listing added: '$title' (~${"%.1f".format(targetDistance)}km). Push Notifications disabled globally."
                return@launch
            }

            if (!nearbyListingsNotificationsEnabled.value) {
                simulationLogs.value = "🏷️ Listing added: '$title'. Push Notifications for listings disabled."
                return@launch
            }

            // Evaluated Filters criteria: Category Subscription
            val isSubscribedToCategory = subscribedCategories.value.contains(category)
            if (!isSubscribedToCategory) {
                simulationLogs.value = "❌ Ignored: New listing '$title' matches category ($category) to which your notifications are not subscribed."
                return@launch
            }

            // Evaluated Filters criteria: Distance check matching set limit
            val allowedMaxRadius = alertMaxRadiusKm.value
            if (targetDistance > allowedMaxRadius) {
                simulationLogs.value = "❌ Ignored: New listing '$title' is too far away (~${"%.1f".format(targetDistance)}km), exceeding your set limit of ${allowedMaxRadius}km."
                return@launch
            }

            // Perfect metadata eligibility matching! Send physical + app push notification!
            val notificationMsg = "New in $category: $title is available ~${"%.1f".format(targetDistance)} km away!"
            triggerPushNotification(
                context = context,
                title = "Neighbor Alert: $sellerName",
                message = notificationMsg,
                type = NotificationType.NEW_LISTING,
                relatedId = simulatedListing.id
            )
            simulationLogs.value = "🎯 Success! Fired push notification for: $title inside radius (${"%.1f".format(targetDistance)} km <= ${allowedMaxRadius} km)."
        }
    }
}

class ExchangeViewModelFactory(private val repository: ExchangeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExchangeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExchangeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
