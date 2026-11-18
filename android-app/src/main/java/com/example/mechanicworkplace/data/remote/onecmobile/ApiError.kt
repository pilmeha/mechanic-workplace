package com.example.mechanicworkplace.data.remote.onecmobile

import kotlinx.serialization.Serializable

/**
 * Error payload of the 1C envelope. `details` is reserved by the contract and currently
 * never filled, so it is deliberately not modelled.
 */
@Serializable
data class ApiError(
    val code: String? = null,
    val message: String? = null
)
