package com.searxngrunner.app

import java.net.HttpURLConnection
import java.net.URL

/**
 * SearXNG 가 실제로 응답하는지 확인합니다.
 * 검색 응답에 format=json 이 필요하므로, 이 테스트가 성공하면 앱·브라우저 양쪽에서 쓸 수 있는 상태입니다.
 */
object SearxHealth {

    private const val TEST_URL = "http://localhost:8080/search?q=test&format=json"

    data class Result(val ok: Boolean, val message: String)

    /** 네트워크를 쓰므로 반드시 백그라운드 스레드에서 호출해야 합니다. */
    fun testJson(): Result {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(TEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.contains("\"results\"")) {
                    Result(true, "성공: JSON 응답을 받았습니다.")
                } else {
                    Result(false, "응답은 왔지만 JSON 형식이 아닙니다. settings.yml 의 search.formats 를 확인하세요.")
                }
            } else {
                Result(false, "실패: HTTP $code (검색엔진이 오류를 반환했습니다)")
            }
        } catch (e: Exception) {
            Result(false, "실패: ${e.javaClass.simpleName} - ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    /** 상태 표시용: 실행 중 여부만 빠르게 확인합니다. */
    fun isRunning(): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("http://localhost:8080/").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 3000
            }
            conn.responseCode in 200..399
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
