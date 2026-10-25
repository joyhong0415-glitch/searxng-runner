package com.searxngrunner.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.app.Activity

/**
 * 1회 설치 안내 화면.
 * 1단계 Termux 설치 → 2단계 외부 앱 제어 허용 → 3단계 권한 허용
 */
class GuideActivity : Activity() {

    companion object {
        // Termux 공식 F-Droid 패키지 주소
        private const val FDROID_TERMUX_URL = "https://f-droid.org/packages/com.termux/"

        // Termux 안에 붙여넣을 명령 1줄
        const val ALLOW_EXTERNAL_APPS_COMMAND =
            "echo \"allow-external-apps=true\" >> ~/.termux/termux.properties"
    }

    private lateinit var prefs: SetupPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)
        prefs = SetupPrefs(this)

        title = getString(R.string.guide_title)

        findViewById<TextView>(R.id.allowCommandText).text = ALLOW_EXTERNAL_APPS_COMMAND

        findViewById<Button>(R.id.btnOpenFdroid).setOnClickListener {
            openUrl(FDROID_TERMUX_URL)
        }

        findViewById<Button>(R.id.btnOpenTermux).setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(TermuxCommand.TERMUX_PACKAGE)
            if (intent == null) {
                toast("Termux 가 설치되어 있지 않습니다. 1단계부터 진행하세요.")
            } else {
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btnCopyCommand).setOnClickListener {
            copyToClipboard(ALLOW_EXTERNAL_APPS_COMMAND)
        }

        findViewById<Button>(R.id.btnOpenAppSettings).setOnClickListener {
            openAppSettings()
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            prefs.guideDone = true
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val installed = TermuxCommand.isTermuxInstalled(this)
        findViewById<TextView>(R.id.termuxStateText).text =
            if (installed) "Termux 가 설치되어 있습니다." else "Termux 가 아직 설치되지 않았습니다."
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("브라우저를 열 수 없습니다. 주소를 직접 입력하세요: $url")
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("설정 화면을 열 수 없습니다. 설정 → 앱 → SearXNG Runner 에서 직접 권한을 허용하세요.")
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("allow-external-apps", text))
        toast("복사했습니다. Termux 창을 길게 눌러 붙여넣으세요.")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
