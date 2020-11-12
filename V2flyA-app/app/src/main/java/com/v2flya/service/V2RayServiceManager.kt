package com.v2flya.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.v2flya.AppConfig
import com.v2flya.extension.defaultDPreference
import com.v2flya.ui.SettingsActivity
import com.v2flya.util.MessageUtil
import com.v2flya.util.Utils
import go.Seq
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import java.lang.ref.SoftReference

object V2RayServiceManager {
    val v2rayPoint: V2RayPoint = Libv2ray.newV2RayPoint(V2RayCallback())
    private val mMsgReceive = ReceiveMessageHandler()

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

    private var lastQueryTime = 0L

    fun startV2Ray(context: Context, mode: String) {
        val intent = if (mode == "VPN") {
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
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                Log.d(serviceControl.getService().packageName, e.toString())
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
                0
            } catch (e: Exception) {
                Log.d(serviceControl.getService().packageName, e.toString())
                -1
            }
        }

    }

    fun startV2rayPoint() {
        val service = serviceControl?.get()?.getService() ?: return
        if (!v2rayPoint.isRunning) {

            try {
                val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
                mFilter.addAction(Intent.ACTION_SCREEN_ON)
                mFilter.addAction(Intent.ACTION_SCREEN_OFF)
                mFilter.addAction(Intent.ACTION_USER_PRESENT)
                service.registerReceiver(mMsgReceive, mFilter)
            } catch (e: Exception) {
                Log.d(service.packageName, e.toString())
            }

            v2rayPoint.configureFileContent = service.defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG, "")
            v2rayPoint.enableLocalDNS = service.defaultDPreference.getPrefBoolean(SettingsActivity.PREF_LOCAL_DNS_ENABLED, false)
            v2rayPoint.forwardIpv6 = service.defaultDPreference.getPrefBoolean(SettingsActivity.PREF_FORWARD_IPV6, false)
            v2rayPoint.domainName = service.defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_DOMAIN, "")
            v2rayPoint.proxyOnly = service.defaultDPreference.getPrefString(AppConfig.PREF_MODE, "VPN") != "VPN"

            try {
                v2rayPoint.runLoop()
            } catch (e: Exception) {
                Log.d(service.packageName, e.toString())
            }

            if (v2rayPoint.isRunning) {
                MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
            } else {
                MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, "")
            }
        }
    }

    fun stopV2rayPoint() {
        val service = serviceControl?.get()?.getService() ?: return

        if (v2rayPoint.isRunning) {
            try {
                v2rayPoint.stopLoop()
            } catch (e: Exception) {
                Log.d(service.packageName, e.toString())
            }
        }
        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            Log.d(service.packageName, e.toString())
        }
    }

    private class ReceiveMessageHandler : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (v2rayPoint.isRunning) {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
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
                }
                AppConfig.MSG_STATE_RESTART -> {
                    startV2rayPoint()
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(AppConfig.ANG_PACKAGE, "SCREEN_OFF, stop querying stats")
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(AppConfig.ANG_PACKAGE, "SCREEN_ON, start querying stats")
                }
            }
        }
    }


}
