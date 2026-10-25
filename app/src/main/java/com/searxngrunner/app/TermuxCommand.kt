package com.searxngrunner.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Termux 의 RUN_COMMAND 인텐트를 만들어 실행합니다.
 *
 * 동작 조건 (Termux 공식 위키 기준)
 * 1. Termux 는 F-Droid/GitHub 배포판이어야 함 (Play 스토어 판은 불가)
 * 2. ~/.termux/termux.properties 에 allow-external-apps=true 가 있어야 함
 * 3. 이 앱에 com.termux.permission.RUN_COMMAND 권한이 허용되어 있어야 함
 * 4. targetSdk 30 이상이면 매니페스트 <queries> 에 com.termux 가 있어야 함 (선언 완료)
 */
object TermuxCommand {

    const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"

    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"

    /** Termux 가 설치되어 있는지 확인합니다. */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 스크립트 문자열을 Termux 의 bash 로 넘겨 실행합니다.
     * 스크립트는 앱 안에 있으므로 별도 파일 저장 없이 그대로 인자로 전달합니다.
     *
     * @return 전달에 성공하면 null, 실패하면 사용자에게 보여줄 오류 메시지
     */
    fun runScript(
        context: Context,
        scriptBody: String,
        label: String,
        background: Boolean
    ): String? {
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", scriptBody))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, background)
            // 최신 Termux 는 RUNNER 를 우선 사용합니다. 둘 다 넣어 구버전과도 호환되게 합니다.
            // app-shell = 화면(터미널 세션) 없이 백그라운드 실행, terminal-session = 터미널에서 실행
            putExtra(
                EXTRA_RUNNER,
                if (background) "app-shell" else "terminal-session"
            )
            putExtra(EXTRA_SESSION_ACTION, "0")
            putExtra(EXTRA_COMMAND_LABEL, label)
            putExtra(EXTRA_COMMAND_DESCRIPTION, "SearXNG Runner 가 보낸 명령입니다.")
        }

        return try {
            context.startService(intent)
            null
        } catch (e: SecurityException) {
            "Termux 권한이 없습니다. 설치 안내 3단계에서 'Termux에서 명령 실행' 권한을 허용하세요."
        } catch (e: Exception) {
            "Termux 에 명령을 전달하지 못했습니다: ${e.message}\n" +
                "Termux 에서 allow-external-apps=true 설정을 했는지 확인하세요."
        }
    }
}
