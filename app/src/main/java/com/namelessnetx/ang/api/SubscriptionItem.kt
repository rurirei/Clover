package com.namelessnetx.ang.api

data class SubscriptionItem(
        var remarks: String = "",
        var url: String = "",
        var enabled: Boolean = true,
        val addedTime: Long = System.currentTimeMillis()) {
}
