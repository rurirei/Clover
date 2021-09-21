package com.namelessnetx.ang.util

import android.content.Context
import android.content.Intent
import com.namelessnetx.ang.app

object MessageUtil {

    fun sendMsg2Service(ctx: Context, what: Int, content: String) {
        sendMsg(ctx, app.BROADCAST_ACTION_SERVICE, what, content)
    }

    fun sendMsg2UI(ctx: Context, what: Int, content: String) {
        sendMsg(ctx, app.BROADCAST_ACTION_ACTIVITY, what, content)
    }

    private fun sendMsg(ctx: Context, action: String, what: Int, content: String) {
        try {
            val intent = Intent()
            intent.action = action
            intent.`package` = app.PACKAGE
            intent.putExtra("key", what)
            intent.putExtra("content", content)
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}