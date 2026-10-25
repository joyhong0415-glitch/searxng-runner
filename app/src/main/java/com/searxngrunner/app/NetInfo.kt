package com.searxngrunner.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 폰의 현재 와이파이 IPv4 주소를 읽습니다.
 * 주소를 못 찾으면 null 을 돌려주고, 화면에는 안내 문구를 표시합니다.
 */
object NetInfo {

    fun wifiIpAddress(context: Context): String? {
        // 1순위: Wi-Fi 관리자에서 직접 읽기 (가장 흔히 동작)
        try {
            @Suppress("DEPRECATION")
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wifi?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                // 리틀엔디언 정수라서 바이트 순서를 뒤집어야 합니다.
                val octets = byteArrayOf(
                    (ip and 0xff).toByte(),
                    ((ip shr 8) and 0xff).toByte(),
                    ((ip shr 16) and 0xff).toByte(),
                    ((ip shr 24) and 0xff).toByte()
                )
                val text = octets.joinToString(".") { (it.toInt() and 0xff).toString() }
                if (text != "0.0.0.0") return text
            }
        } catch (e: Exception) {
            // 권한이 없거나 와이파이가 꺼진 경우 아래 2순위로 넘어갑니다.
        }

        // 2순위: 네트워크 인터페이스 목록에서 와이파이 주소 찾기
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nic in interfaces) {
                if (!nic.isUp || nic.isLoopback) continue
                val name = nic.name ?: continue
                if (!name.startsWith("wlan")) continue
                for (addr in nic.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }

        return null
    }

    /** 와이파이에 연결되어 있는지 확인합니다. */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val network = cm.activeNetworkInfo
            network != null && network.isConnected && network.type == ConnectivityManager.TYPE_WIFI
        } catch (e: Exception) {
            false
        }
    }
}
