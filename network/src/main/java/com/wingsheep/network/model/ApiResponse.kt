package com.wingsheep.network.model

data class ApiResponse<T>(
    val message: String,
    val data: T?
)
