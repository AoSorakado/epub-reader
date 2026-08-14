package com.example.epubreader.data.model.network

data class WebDavResource(
    val name: String,
    val isDirectory: Boolean,
    val path: String,
    val size: Long = 0
)
