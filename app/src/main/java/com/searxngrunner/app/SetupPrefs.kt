package com.searxngrunner.app

import android.content.Context

/**
 * 아주 단순한 설정 저장소.
 * "설치 안내 화면을 이미 봤는지"만 기억합니다.
 */
class SetupPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("searxng_runner", Context.MODE_PRIVATE)

    var guideDone: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_DONE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GUIDE_DONE, value).apply()
        }

    private companion object {
        const val KEY_GUIDE_DONE = "guide_done"
    }
}
