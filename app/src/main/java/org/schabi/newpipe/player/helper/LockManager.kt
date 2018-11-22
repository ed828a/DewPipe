package org.schabi.newpipe.player.helper

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

import android.content.Context.POWER_SERVICE
import android.content.Context.WIFI_SERVICE

class LockManager(context: Context) {
    private val TAG = "LockManager@" + hashCode()

    private val powerManager: PowerManager
    private val wifiManager: WifiManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    init {
        powerManager = context.applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    }

    fun acquireWifiAndCpu() {
        Log.d(TAG, "acquireWifiAndCpu() called")
        if (wakeLock != null && wakeLock!!.isHeld && wifiLock != null && wifiLock!!.isHeld) return

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG)

        if (wakeLock != null) wakeLock!!.acquire()
        if (wifiLock != null) wifiLock!!.acquire()
    }

    fun releaseWifiAndCpu() {
        Log.d(TAG, "releaseWifiAndCpu() called")
        if (wakeLock != null && wakeLock!!.isHeld) wakeLock!!.release()
        if (wifiLock != null && wifiLock!!.isHeld) wifiLock!!.release()

        wakeLock = null
        wifiLock = null
    }
}
