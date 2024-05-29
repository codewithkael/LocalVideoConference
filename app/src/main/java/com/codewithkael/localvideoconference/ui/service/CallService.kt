package com.codewithkael.localvideoconference.ui.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.codewithkael.localvideoconference.R
import com.codewithkael.localvideoconference.ui.MainActivity
import com.codewithkael.localvideoconference.utils.getWifiIPAddress
import com.codewithkael.localvideoconference.webrtc.ManagerEventListener
import com.codewithkael.localvideoconference.webrtc.SessionManager
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@AndroidEntryPoint
class CallService : Service(), ManagerEventListener {


    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var sessionManager: SessionManager

    private var surfaceView: SurfaceViewRenderer? = null

    //service section
    private lateinit var mainNotification: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager

    //scopes
    private lateinit var windowManager: WindowManager

    //variables

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                CallServiceActions.START.name -> handleStartService()
                CallServiceActions.STOP.name -> handleStopService()
                else -> Unit
            }
        }
        return START_STICKY
    }

    private fun handleStartService() {
        if (!isServiceRunning) {
            isServiceRunning = true
            //start service here
            startServiceWithNotification()
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            surfaceView = SurfaceViewRenderer(this)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // This type is suitable for overlays
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            windowManager.addView(surfaceView, params)
            sessionManager.setListener(this)
            sessionManager.start(surfaceView!!)
        }
    }

    private fun handleStopService() {
        if (isServiceRunning) {
            isServiceRunning = false
        }
        sessionManager.onDestroy()
        windowManager.removeViewImmediate(surfaceView)
        surfaceView?.release()
        surfaceView = null
        listener = null
        stopForeground(STOP_FOREGROUND_REMOVE)

    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
        createNotifications()


    }

    private fun startServiceWithNotification() {
        startForeground(MAIN_NOTIFICATION_ID, mainNotification.build())
    }

    @SuppressLint("NewApi")
    private fun createNotifications() {
        val callChannel = NotificationChannel(
            CALL_NOTIFICATION_CHANNEL_ID,
            CALL_NOTIFICATION_CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(callChannel)
        val contentIntent = Intent(
            this, MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), contentIntent, PendingIntent.FLAG_IMMUTABLE
        )


        val notificationChannel = NotificationChannel(
            "chanel_terminal_bluetooth",
            "chanel_terminal_bluetooth",
            NotificationManager.IMPORTANCE_HIGH
        )


        val intent = Intent(this, CallBroadcastReceiver::class.java).apply {
            action = "ACTION_EXIT"
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        notificationManager.createNotificationChannel(notificationChannel)
        mainNotification = NotificationCompat.Builder(
            this, "chanel_terminal_bluetooth"
        ).setSmallIcon(
            R.mipmap.ic_launcher
        ).setOngoing(true).setPriority(NotificationCompat.PRIORITY_HIGH).setOnlyAlertOnce(false)
            .addAction(R.mipmap.ic_launcher, "Exit", pendingIntent)
            .setContentIntent(contentPendingIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    companion object {
        var isServiceRunning = false
        const val CALL_NOTIFICATION_CHANNEL_ID = "CALL_CHANNEL"
        const val MAIN_NOTIFICATION_ID = 2323
        var url: String = ""
        var listener: ServiceEventListener? = null
        fun startService(context: Context) {
            Thread {
                startIntent(context, Intent(context, CallService::class.java).apply {
                    action = CallServiceActions.START.name
                })
            }.start()
        }

        fun stopService(context: Context) {
            startIntent(context, Intent(context, CallService::class.java).apply {
                action = CallServiceActions.STOP.name
            })
        }

        @SuppressLint("NewApi")
        private fun startIntent(context: Context, intent: Intent) {
            context.startForegroundService(intent)
        }
    }

    override fun onSocketPortConnected(socketPort: Int, httpPort: Int) {
        url = "http://${getWifiIPAddress(this)}:$httpPort/$socketPort"
        listener?.onUrlReady(url)
    }


}