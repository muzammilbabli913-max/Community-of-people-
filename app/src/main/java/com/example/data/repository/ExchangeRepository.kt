package com.example.data.repository

import com.example.data.dao.UserDao
import com.example.data.dao.ListingDao
import com.example.data.dao.MessageDao
import com.example.data.model.User
import com.example.data.model.Listing
import com.example.data.model.Message
import kotlinx.coroutines.flow.Flow
import kotlin.math.*

class ExchangeRepository(
    private val userDao: UserDao,
    private val listingDao: ListingDao,
    private val messageDao: MessageDao
) {
    val registeredUser: Flow<User?> = userDao.getRegisteredUser()
    val allListings: Flow<List<Listing>> = listingDao.getAllListings()

    fun getListingsByCategory(category: String): Flow<List<Listing>> {
        return listingDao.getListingsByCategory(category)
    }

    fun getMessagesBetweenUsers(user1Id: Long, user2Id: Long): Flow<List<Message>> {
        return messageDao.getMessagesBetweenUsers(user1Id, user2Id)
    }

    fun getAllMessagesForUser(userId: Long): Flow<List<Message>> {
        return messageDao.getAllMessagesForUser(userId)
    }

    suspend fun registerUser(user: User): Long {
        return userDao.insertUser(user)
    }

    suspend fun clearAllUsers() {
        userDao.clearAllUsers()
    }

    suspend fun insertListing(listing: Listing): Long {
        return listingDao.insertListing(listing)
    }

    suspend fun updateListing(listing: Listing) {
        listingDao.updateListing(listing)
    }

    suspend fun deleteListing(listing: Listing) {
        listingDao.deleteListing(listing)
    }

    suspend fun insertMessage(message: Message): Long {
        return messageDao.insertMessage(message)
    }

    suspend fun clearAllData() {
        userDao.clearAllUsers()
        listingDao.clearAllListings()
        messageDao.clearAllMessages()
    }

    /**
     * Seed the database with high-quality nearby sample neighborhood listings 
     * when a user registers. This ensures the prototype is interactive.
     */
    suspend fun prepopulateSampleData(userLat: Double, userLon: Double) {
        // First clear existing listings/messages to avoid duplicates, but keep general
        listingDao.clearAllListings()
        messageDao.clearAllMessages()

        // Generate coordinates close to user
        // 1 degree latitude ~ 111 km, 1 degree longitude ~ 111 km * cos(lat)
        val latCos = cos(Math.toRadians(userLat))
        
        val samples = listOf(
            Listing(
                ownerId = 101,
                ownerName = "Oliver Green",
                title = "Dewalt Cordless Lawn Mower",
                description = "Happy to lend my battery-operated mower for the weekend to anyone in the block! Please return clean and brush off the grass clippings.",
                category = "Tools & Gardening",
                exchangeType = "Borrow",
                priceOrDetails = "Free to Borrow (Weekend)",
                latitude = userLat + 0.0035, // ~0.4km North
                longitude = userLon + 0.0012 * latCos
            ),
            Listing(
                ownerId = 102,
                ownerName = "Sophia Miller",
                title = "Fresh Homemade Sourdough",
                description = "I bake every Tuesday and Saturday! Looking to trade a fresh sourdough loaf for any garden fruits (lemons, tomatoes) or coffee beans.",
                category = "Home & Kitchen",
                exchangeType = "Trade",
                priceOrDetails = "Trade for Fruits/Coffee",
                latitude = userLat - 0.0075, // ~0.8km South
                longitude = userLon - 0.0045 * latCos
            ),
            Listing(
                ownerId = 103,
                ownerName = "Carlos Vance",
                title = "Heavy-Duty Hammer Drill Set",
                description = "Need to drill into concrete or brick walls? Don't buy a drill you will use once. Come borrow mine! Includes masonry drill bits.",
                category = "Lending & Share",
                exchangeType = "Free",
                priceOrDetails = "Free to Borrow",
                latitude = userLat + 0.0015, // ~0.2km North-East
                longitude = userLon + 0.0055 * latCos
            ),
            Listing(
                ownerId = 104,
                ownerName = "Emma Kincaid",
                title = "Pet Care & Dog Walking Services",
                description = "Experienced veterinary assistant offering premium overnight pet sitting or daily dog walks. Standard rate is $15/walk, but negotiable for direct neighbors.",
                category = "Pet Sitting & Care",
                exchangeType = "Sale",
                priceOrDetails = "$15/walk (Negotiable)",
                latitude = userLat - 0.0125, // ~1.4km South-West
                longitude = userLon + 0.0115 * latCos
            ),
            Listing(
                ownerId = 105,
                ownerName = "Liam Torres",
                title = "24-inch Dell HD Monitor",
                description = "Upgraded my home office setup. Selling this Dell 1080p monitor. Works flawlessly, comes with power cable and an HDMI cable. Pickup only.",
                category = "Electronics",
                exchangeType = "Sale",
                priceOrDetails = "$45",
                latitude = userLat + 0.0210, // ~2.3km North-East
                longitude = userLon - 0.0150 * latCos
            ),
            Listing(
                ownerId = 106,
                ownerName = "Aria Rhodes",
                title = "French Conversation Tutoring",
                description = "Native speaker offering casual conversation tutoring or basic instruction in French. Willing to exchange for yoga instruction or piano accompaniment.",
                category = "Skills & Tutoring",
                exchangeType = "Trade",
                priceOrDetails = "Exchange for skills",
                latitude = userLat - 0.0045, // ~0.5km South
                longitude = userLon + 0.0110 * latCos
            ),
            Listing(
                ownerId = 107,
                ownerName = "Jackson Parker",
                title = "Graco Wooden High Chair",
                description = "Our kids are grown now, so we no longer need this sturdy wooden high chair. It is in good condition, thoroughly sanitized. Yours for free if you pick it up!",
                category = "Miscellaneous",
                exchangeType = "Free",
                priceOrDetails = "Free for Pickup",
                latitude = userLat - 0.0160, // ~1.8km South-West
                longitude = userLon - 0.0210 * latCos
            )
        )

        for (sample in samples) {
            listingDao.insertListing(sample)
        }

        // Add 1 default starter welcome message from Oliver Green
        messageDao.insertMessage(
            Message(
                senderId = 101,
                senderName = "Oliver Green",
                receiverId = 1, // User's ID is usually 1 since it's the first auto-generated key
                receiverName = "You",
                content = "Welcome to the block! I saw you just registered. If you ever need helper tools, feel free to borrow my cordless lawn mower or drill. Glad to have you here in our neighborhood community exchange!",
                timestamp = System.currentTimeMillis() - 300000, // 5 mins ago
                listingId = null
            )
        )
    }

    /**
     * Compute geographical distance using the Haversine equation
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
