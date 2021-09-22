package com.namelessnetx.ang.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import android.util.Log
import com.namelessnetx.ang.MainActivity
import com.namelessnetx.ang.R
import com.namelessnetx.ang.addOn.AppConfig
import com.namelessnetx.ang.addOn.AppConfig.ANG_PACKAGE
import com.namelessnetx.ang.addOn.AppConfig.TAG_DIRECT
import com.tencent.mmkv.MMKV
import com.namelessnetx.ang.dto.ServerConfig
import com.namelessnetx.ang.extension.toSpeedString
import com.namelessnetx.ang.extension.toast
import com.namelessnetx.ang.util.*
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import org.greenrobot.eventbus.EventBus
import rx.Observable
import rx.Subscription
import java.lang.ref.SoftReference
import kotlin.math.min

object V2RayServiceManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_ICON_THRESHOLD = 3000

    val v2rayPoint: V2RayPoint = Libv2ray.newV2RayPoint(V2RayCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val context = value?.get()?.getService()?.applicationContext
            context?.let {
                v2rayPoint.packageName = Utils.packagePath(context)
                v2rayPoint.packageCodePath = context.applicationInfo.nativeLibraryDir + "/"
                Seq.setContext(context)
            }
        }
    var currentConfig: ServerConfig? = null

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var mSubscription: Subscription? = null
    private var mNotificationManager: NotificationManager? = null

    private var stats = DataTransferStats
    fun startV2Ray(context: Context) {
        if (settingsStorage?.decodeBool(AppConfig.PREF_PROXY_SHARING) == true) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        } else {
            context.toast(R.string.toast_services_start)
        }
        val intent = if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
            Intent(context.applicationContext, V2RayVpnService::class.java)
        } else {
            Intent(context.applicationContext, V2RayProxyOnlyService::class.java)
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private class V2RayCallback : V2RayVPNServiceSupportsSet {
        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            // called by go
            // shutdown the whole vpn service
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                Log.d(ANG_PACKAGE, e.toString())
                -1
            }
        }

        override fun prepare(): Long {
            return 0
        }

        override fun protect(l: Long): Long {
            val serviceControl = serviceControl?.get() ?: return 0
            return if (serviceControl.vpnProtect(l.toInt())) 0 else 1
        }

        override fun onEmitStatus(l: Long, s: String?): Long {
            //Logger.d(s)
            return 0
        }

        override fun setup(s: String): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            //Logger.d(s)
            return try {
                serviceControl.startService(s)
                lastQueryTime = System.currentTimeMillis()
                startSpeedNotification()
                0
            } catch (e: Exception) {
                Log.d(ANG_PACKAGE, e.toString())
                -1
            }
        }

    }

    fun startV2rayPoint() {
        val service = serviceControl?.get()?.getService() ?: return
        val guid = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER) ?: return
        val config = MmkvManager.decodeServerConfig(guid) ?: return
        if (!v2rayPoint.isRunning) {
            val result = V2rayConfigUtil.getV2rayConfig(service, guid)
            if (!result.status)
                return

            try {
                val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
                mFilter.addAction(Intent.ACTION_SCREEN_ON)
                mFilter.addAction(Intent.ACTION_SCREEN_OFF)
                mFilter.addAction(Intent.ACTION_USER_PRESENT)
                service.registerReceiver(mMsgReceive, mFilter)
            } catch (e: Exception) {
                Log.d(ANG_PACKAGE, e.toString())
            }

            v2rayPoint.configureFileContent = result.content
            v2rayPoint.domainName = config.getV2rayPointDomainAndPort()
            currentConfig = config
            v2rayPoint.enableLocalDNS =
                settingsStorage?.decodeBool(AppConfig.PREF_LOCAL_DNS_ENABLED) ?: false
            v2rayPoint.forwardIpv6 =
                settingsStorage?.decodeBool(AppConfig.PREF_FORWARD_IPV6) ?: false
            v2rayPoint.proxyOnly =
                settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" != "VPN"

            try {
                v2rayPoint.runLoop()
            } catch (e: Exception) {
                Log.d(ANG_PACKAGE, e.toString())
            }

            if (v2rayPoint.isRunning) {
                MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
                showNotification()
            } else {
                MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, "")
                cancelNotification()
            }
        }
    }

    fun stopV2rayPoint() {
        val service = serviceControl?.get()?.getService() ?: return

        if (v2rayPoint.isRunning) {
            GlobalScope.launch(Dispatchers.Default) {
                try {
                    v2rayPoint.stopLoop()
                } catch (e: Exception) {
                    Log.d(ANG_PACKAGE, e.toString())
                }
            }
        }

        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        cancelNotification()
        //sendVpnToHfMsgEvent("stopped service")

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            Log.d(ANG_PACKAGE, e.toString())
        }
    }

    private class ReceiveMessageHandler : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    //Logger.e("ReceiveMessageHandler", intent?.getIntExtra("key", 0).toString())
                    if (v2rayPoint.isRunning) {
                        MessageUtil.sendMsg2UI(
                            serviceControl.getService(),
                            AppConfig.MSG_STATE_RUNNING,
                            ""
                        )
                    } else {
                        MessageUtil.sendMsg2UI(
                            serviceControl.getService(),
                            AppConfig.MSG_STATE_NOT_RUNNING,
                            ""
                        )
                    }
                }
                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }
                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }
                AppConfig.MSG_STATE_STOP -> {
                    serviceControl.stopService()
                    //sendVpnToHfMsgEvent("VPN START FAILED")
                    //Logger.v(, R.string.vpn_start_error)
                }
                AppConfig.MSG_STATE_RESTART -> {
                    startV2rayPoint()
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(ANG_PACKAGE, "SCREEN_OFF, stop querying stats")
                    stopSpeedNotification()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(ANG_PACKAGE, "SCREEN_ON, start querying stats")
                    startSpeedNotification()
                }
            }
        }
    }

    private fun showNotification() {
        val service = serviceControl?.get()?.getService() ?: return
        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            service,
            NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)

        val stopV2RayPendingIntent = PendingIntent.getBroadcast(
            service,
            NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stop)
            .setContentTitle(currentConfig?.remarks)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_stop,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
        //.build()

        //mBuilder?.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)  //取消震动,铃声其他都不好使

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
        playsound(service)
    }

    private fun playsound(context: Context) {
        try {
            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(context, defaultSound)
            r.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "nlx"
        val channelName = "nlx Background Service"
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        chan.lightColor = Color.DKGRAY
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    private fun cancelNotification() {
        val service = serviceControl?.get()?.getService() ?: return
        service.stopForeground(true)
        mBuilder = null
        mSubscription?.unsubscribe()
        mSubscription = null
    }

    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stop)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stop)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stop)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText) // Emui4.1 need content text even if style is set as BigTextStyle
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = serviceControl?.get()?.getService() ?: return null
            mNotificationManager =
                service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    fun startSpeedNotification() {
        if (mSubscription == null &&
            v2rayPoint.isRunning &&
            settingsStorage?.decodeBool(AppConfig.PREF_SPEED_ENABLED) == true
        ) {
            var lastZeroSpeed = false
            val outboundTags = currentConfig?.getAllOutboundTags()
            outboundTags?.remove(TAG_DIRECT)
            stats.startSession()
            mSubscription = Observable.interval(3, java.util.concurrent.TimeUnit.SECONDS)
                .subscribe {
                    val queryTime = System.currentTimeMillis()
                    val sinceLastQueryInSeconds = (queryTime - lastQueryTime) / 1000.0
                    var proxyTotal = 0L
                    val text = StringBuilder()
                    outboundTags?.forEach {
                        val up = v2rayPoint.queryStats(it, "uplink")
                        val down = v2rayPoint.queryStats(it, "downlink")
                        Log.d("status checking....", up.toString())
                        Log.d("status checking2....", down.toString())

                        stats.addUploadBytes(up.toInt())
                        stats.addDownloadBytes(down.toInt())

                        sendStatsEvent(
                            stats.getTotalUploadBuckets(),
                            stats.getTotalDownloadBuckets(),
                            stats.getTotalUploadBytes(),
                            stats.getTotalDownloadBytes()
                        )
                        if (up + down > 0) {
                            appendSpeedString(
                                text,
                                it,
                                up / sinceLastQueryInSeconds,
                                down / sinceLastQueryInSeconds
                            )
                            proxyTotal += up + down
                        }

                    }
                    val directUplink = v2rayPoint.queryStats(TAG_DIRECT, "uplink")
                    val directDownlink = v2rayPoint.queryStats(TAG_DIRECT, "downlink")
                    val zeroSpeed = (proxyTotal == 0L && directUplink == 0L && directDownlink == 0L)
                    if (!zeroSpeed || !lastZeroSpeed) {
                        if (proxyTotal == 0L) {
                            appendSpeedString(text, outboundTags?.firstOrNull(), 0.0, 0.0)
                        }
                        appendSpeedString(
                            text, TAG_DIRECT, directUplink / sinceLastQueryInSeconds,
                            directDownlink / sinceLastQueryInSeconds
                        )
                        updateNotification(
                            text.toString(),
                            proxyTotal,
                            directDownlink + directUplink
                        )
                    }
                    lastZeroSpeed = zeroSpeed
                    lastQueryTime = queryTime
                }
        }
    }

    private fun sendStatsEvent(ups: ArrayList<Int>, downs: ArrayList<Int>, up: Int, down: Int) {
        EventBus.getDefault().post(Events.VpnToSfMsgEvent(ups, downs, up, down))
    }

    /**  private fun sendStatsEvent(
    ups: ArrayList<Int>,
    downs: ArrayList<Int>,
    up: Int,
    down: Int
    ) {
    MessageUtil.sendMsg1(
    serviceControl?.get()?.getService()?.applicationContext!!,
    AppConfig.MSG_GRAPH_UP,
    ups
    )
    MessageUtil.sendMsg2(
    serviceControl?.get()?.getService()?.applicationContext!!,
    AppConfig.MSG_GRAPH_DOWNS,
    downs
    )
    MessageUtil.sendMsg3(
    serviceControl?.get()?.getService()?.applicationContext!!,
    AppConfig.MSG_REALTIME_UPS,
    up
    )
    MessageUtil.sendMsg4(
    serviceControl?.get()?.getService()?.applicationContext!!,
    AppConfig.MSG_REALTIME_DOWNS,
    down
    )
    } **/


    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.substring(0, min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append(" ")
        }
        text.append("•  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }

    fun stopSpeedNotification() {
        if (mSubscription != null) {
            mSubscription?.unsubscribe() //stop queryStats
            mSubscription = null
            stats.stopSession()
            updateNotification(currentConfig?.remarks, 0, 0)
        }
    }
}
