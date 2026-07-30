package com.gigcreator.network

data class SendContainer<T>(val data: T, val sendType: SendType)
