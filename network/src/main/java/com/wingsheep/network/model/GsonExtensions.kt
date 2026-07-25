package com.wingsheep.network.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

inline fun <reified T> Gson.apiResponseFrom(json: String): ApiResponse<T> {
    val type = object : TypeToken<ApiResponse<T>>() {}.type
    return fromJson(json, type)
}
