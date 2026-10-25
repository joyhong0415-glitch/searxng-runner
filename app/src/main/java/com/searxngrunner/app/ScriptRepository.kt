package com.searxngrunner.app

import android.content.Context
import java.io.IOException

/**
 * assets 폴더에 넣어 둔 셸 스크립트를 문자열로 읽습니다.
 * 스크립트를 앱에 넣어 두면 나중에 설치 명령이 바뀌어도 이 파일만 고치면 됩니다.
 */
object ScriptRepository {

    const val INSTALL_SCRIPT = "searxng-install.sh"
    const val CONTROL_SCRIPT = "searxng-control.sh"

    fun read(context: Context, assetName: String): String {
        return try {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IOException("스크립트를 읽지 못했습니다: $assetName", e)
        }
    }
}
