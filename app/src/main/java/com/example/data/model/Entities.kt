package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val neighborhood: String,
    val latitude: Double,
    val longitude: Double,
    val avatarColor: Int, // Hex value of representation
    val isRegistered: Boolean = true,
    val registeredAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "listings")
data class Listing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: Long,
    val ownerName: String,
    val title: String,
    val description: String,
    val category: String, // e.g., "Tools", "Kitchen", "Skills", "Care", "Electronics", "Other"
    val exchangeType: String, // e.g., "Free", "Borrow", "Trade", "Sale"
    val priceOrDetails: String, // e.g., "Complimentary", "Weekend return", "Exchange for bread", "$15"
    val latitude: Double,
    val longitude: Double,
    val isAvailable: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: Long,
    val senderName: String,
    val receiverId: Long,
    val receiverName: String,
    val listingId: Long?, // Optional, link to listing being discussed
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) : Serializable
