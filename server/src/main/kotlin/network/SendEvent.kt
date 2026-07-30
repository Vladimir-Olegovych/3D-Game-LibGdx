package com.gigcreator.network

import com.gigcreator.NetworkEvent

data class SendEvent(val data: NetworkEvent, val sendType: SendType)
