package com.wingsheep.network.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Parses an `ApiResponse<T>` envelope.
 *
 * The anonymous [TypeToken] subclass is how Gson learns `T`. Gson ships consumer
 * rules (`META-INF/proguard/gson.pro`) that retain those subclasses and the generic
 * signature attribute, so this needs no project keep rule.
 */
inline fun <reified T> Gson.apiResponseFrom(json: String): ApiResponse<T> {
    val type = object : TypeToken<ApiResponse<T>>() {}.type
    return fromJson(json, type)
}
