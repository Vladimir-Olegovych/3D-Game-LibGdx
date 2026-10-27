package com.gigcreator.network

internal data class SendContainer<T>(val data: T, val sendType: SendType)
