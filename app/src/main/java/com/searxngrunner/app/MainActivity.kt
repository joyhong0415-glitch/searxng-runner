package com.searxngrunner.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * 메인 화면.
 * 앱은 SearXNG 를 직접 실행하지 않고, Termux 의 RUN_COMMAND 인텐트로 명령을 전달합니다.
 */
class MainActivity : Activity() {

    private lateinit var prefs: SetupPrefs
    private lateinit var statusText: TextView
    private lateinit var addressText: TextView
    private lateinit var logText: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private var logServer: LogServer? = null
    private val logBuffer = StringBuilder()

    private var currentAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = SetupPrefs(this)

        statusText = findViewById(R.id.statusText)
        addressText = findViewById(R.id.addressText)
        logText = findViewById(R.id.logText)

        startLogServer()

        findViewById<Button>(R.id.btnInstall).setOnClickListener { runInstall() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { runControl("start") }
        findViewById<Button>(R.id.btnStop).setOnClickListener { runControl("stop") }
        findViewById<Button>(R.id.btnRestart).setOnClickListener { runControl("restart") }
        findViewById<Button>(R.id.btnTest).setOnClickListener { runJsonTest() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.btnCopyAddress).setOnClickListener { copyAddress() }
        findViewById<Button>(R.id.btnGuide).setOnClickListener {
            startActivity(Intent(this, GuideActivity::class.java))
        }

        // 처음 실행이면 안내 화면을 먼저 보여 줍니다.
        if (!prefs.guideDone) {
            startActivity(Intent(this, GuideActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAddress()
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        logServer?.stop()
        logServer = null
        executor.shutdownNow()
    }

    // ---------- 초기화 ----------

    private fun startLogServer() {
        if (logServer != null) return
        logServer = LogServer { line -> appendLog(line) }.also { it.start() }
        appendLog("[앱] 준비되었습니다.")
    }

    /**
     * 설치/제어 스크립트 앞에 로그 전송 함수를 붙여서 Termux 로 보냅니다.
     * curl 을 쓰는 이유: bash 의 /dev/tcp 는 Termux 빌드에서 보장되지 않습니다.
     * 로그 전송이 실패해도 설치 자체는 멈추지 않도록 || true 를 붙입니다.
     */
    private fun wrapWithLogFunction(script: String, label: String): String {
        val header = """
            |rdlog() {
            |  curl -s -m 2 --data-binary "${'$'}1" "http://127.0.0.1:8081/log" >/dev/null 2>&1 || true
            |  echo "${'$'}1"
            |}
            |echo "[$label] 시작"
            |
        """.trimMargin()

        return header + script
    }

    // ---------- 명령 실행 ----------

    private fun runInstall() {
        if (!checkTermuxReady()) return

        val script = try {
            ScriptRepository.read(this, ScriptRepository.INSTALL_SCRIPT)
        } catch (e: Exception) {
            showError("설치 스크립트를 읽지 못했습니다: ${e.message}")
            return
        }

        appendLog("")
        appendLog("=== 자동 설치를 시작합니다 (시간이 걸립니다) ===")
        val error = TermuxCommand.runScript(
            context = this,
            scriptBody = wrapWithLogFunction(script, "install"),
            label = "SearXNG install",
            background = true
        )
        if (error != null) {
            showError(error)
        }
    }

    private fun runControl(action: String) {
        if (!checkTermuxReady()) return

        val script = try {
            ScriptRepository.read(this, ScriptRepository.CONTROL_SCRIPT)
        } catch (e: Exception) {
            showError("제어 스크립트를 읽지 못했습니다: ${e.message}")
            return
        }

        val full = wrapWithLogFunction(script, action) + "\nsearxng_$action\n"
        appendLog("")
        appendLog("=== $action 명령을 보냈습니다 ===")

        val error = TermuxCommand.runScript(
            context = this,
            scriptBody = full,
            label = "SearXNG $action",
            background = true
        )
        if (error != null) {
            showError(error)
            return
        }

        // curl 이 없으면 로그가 오지 않습니다. 설치와 함께 미리 넣어 두었으므로 보통은 있습니다.
        statusText.postDelayed({ refreshStatus() }, 6000)
    }

    private fun checkTermuxReady(): Boolean {
        if (!TermuxCommand.isTermuxInstalled(this)) {
            showError("Termux 가 설치되어 있지 않습니다. [설치 안내 다시 보기]를 눌러 1단계부터 진행하세요.")
            return false
        }
        return true
    }

    // ---------- 상태/주소 ----------

    private fun refreshStatus() {
        statusText.text = getString(R.string.status_unknown)
        statusText.setTextColor(Color.parseColor("#666666"))

        executor.execute {
            val running = SearxHealth.isRunning()
            runOnUiThread {
                if (running) {
                    statusText.text = getString(R.string.status_running)
                    statusText.setTextColor(Color.parseColor("#2E7D32"))
                } else {
                    statusText.text = getString(R.string.status_stopped)
                    statusText.setTextColor(Color.parseColor("#C62828"))
                }
            }
        }
    }

    private fun refreshAddress() {
        executor.execute {
            val ip = NetInfo.wifiIpAddress(this)
            val wifiOn = NetInfo.isWifiConnected(this)
            runOnUiThread {
                currentAddress = if (ip != null) "http://$ip:8080" else ""
                addressText.text = when {
                    ip != null -> "http://$ip:8080"
                    !wifiOn -> "와이파이에 연결하면 주소가 표시됩니다"
                    else -> "IP 주소를 읽지 못했습니다"
                }
            }
        }
    }

    private fun copyAddress() {
        if (currentAddress.isBlank()) {
            toast("복사할 주소가 없습니다. 와이파이 연결을 확인하세요.")
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SearXNG 주소", currentAddress))
        toast("주소를 복사했습니다: $currentAddress")
    }

    // ---------- JSON 테스트 ----------

    private fun runJsonTest() {
        appendLog("[테스트] JSON 응답을 확인합니다…")
        statusText.text = getString(R.string.status_unknown)

        executor.execute {
            val result = SearxHealth.testJson()
            runOnUiThread {
                appendLog("[테스트] ${result.message}")
                toast(result.message)
                refreshStatus()
            }
        }
    }

    // ---------- 배터리 최적화 ----------

    private fun openBatterySettings() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("이미 배터리 최적화에서 제외되어 있습니다.")
            return
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                toast("설정 화면을 열 수 없습니다. 설정 → 앱 → 배터리 → 제한 없음 으로 직접 설정하세요.")
            }
        }
    }

    // ---------- 로그 ----------

    private fun appendLog(line: String) {
        logBuffer.append(line).append('\n')
        // 로그가 너무 길어지면 앞부분을 잘라 앱이 느려지지 않게 합니다.
        if (logBuffer.length > 20000) {
            logBuffer.delete(0, logBuffer.length - 15000)
        }
        logText.text = logBuffer.toString()
    }

    // ---------- 유틸 ----------

    private fun showError(message: String) {
        appendLog("[오류] $message")
        toast(message)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
