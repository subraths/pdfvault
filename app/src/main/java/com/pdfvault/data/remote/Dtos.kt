package com.pdfvault.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val email: String, val password: String)

@Serializable
data class UserDto(val id: String, val email: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val active: Boolean,
    val updatedAt: Long = 0,
)

@Serializable
data class AccountsResponse(val accounts: List<AccountDto> = emptyList())

@Serializable
data class CreateAccountRequest(
    val name: String,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val active: Boolean = false,
)

@Serializable
data class UpdateAccountRequest(val name: String? = null, val active: Boolean? = null)

@Serializable
data class RecentDto(
    val docId: String,
    val name: String,
    val openedAt: Long,
    val totalPages: Int = 0,
    val lastPage: Int = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class RecentsResponse(val recents: List<RecentDto> = emptyList())

@Serializable
data class SyncRequest(val items: List<RecentDto>)
