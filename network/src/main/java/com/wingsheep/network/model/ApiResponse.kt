package com.wingsheep.network.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * Envelope shared by every server response.
 *
 * [Keep] is the shrinker contract: Gson fills this class through reflection, so R8
 * would otherwise be free to remove or rename members it never sees accessed.
 * [SerializedName] pins the wire names, so renaming a Kotlin property can never
 * change the JSON contract by accident.
 */
@Keep
data class ApiResponse<T>(
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T?
)
