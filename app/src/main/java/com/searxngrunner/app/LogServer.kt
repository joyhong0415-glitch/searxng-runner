package com.searxngrunner.app

import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 설치 스크립트가 보내는 진행 로그를 받는 아주 작은 HTTP 수신기입니다.
 * 127.0.0.1:8081 에서만 열리므로 외부에서는 접근할 수 없습니다.
 *
 * 스크립트 쪽 사용법 (Termux bashi 안에서):
 *   printf 'POST /log HTTP/1.1\r\nHost: x\r\nContent-Length: %d\r\nConnection: close\r\n\r\n%s' \
 *     "${#msg}" "$msg" > /dev/tcp/127.0.0.1/8081
 */
class LogServer(private val onLine: (String) -> Unit) {

    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            try {
                val socket = ServerSocket(PORT, 10, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket
                while (running) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        break
                    }
                    handle(client)
                }
            } catch (e: Exception) {
                // 포트가 이미 사용 중이거나 소켓을 열 수 없는 경우: 조용히 포기합니다.
                // (로그 표시만 안 될 뿐, 설치는 Termux 안에서 계속 진행됩니다)
                running = false
            }
        }.apply { isDaemon = true }
        thread?.start()
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // 무시
        }
        serverSocket = null
        thread = null
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 5000
            val input = client.getInputStream().buffered()

            // 요청 첫 줄 읽기
            val requestLine = readLine(input) ?: return

            // 헤더 읽기 (Content-Length 만 사용)
            var contentLength = 0
            while (true) {
                val header = readLine(input) ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val message = when {
                contentLength > 0 -> {
                    // 본문은 UTF-8 바이트 기준이므로 바이트로 정확히 읽은 뒤 문자열로 변환합니다.
                    val bytes = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(bytes, read, contentLength - read)
                        if (n <= 0) break
                        read += n
                    }
                    String(bytes, 0, read, Charsets.UTF_8)
                }
                requestLine.startsWith("GET /log?msg=") -> {
                    requestLine.removePrefix("GET /log?msg=").substringBefore(" ").trim()
                }
                else -> ""
            }

            reply(client.getOutputStream())

            if (message.isNotBlank()) {
                val line = message.trim()
                mainHandler.post { onLine(line) }
            }
        } catch (e: Exception) {
            // 한 줄 실패는 무시하고 다음 연결을 받습니다.
        } finally {
            try {
                client.close()
            } catch (e: Exception) {
                // 무시
            }
        }
    }

    /** CRLF 또는 LF 로 끝나는 한 줄을 읽습니다. (ASCII 헤더용) */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(b.toChar())
        }
    }

    private fun reply(out: OutputStream) {
        try {
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())
            out.flush()
        } catch (e: Exception) {
            // 무시
        }
    }

    companion object {
        const val PORT = 8081
    }
}
