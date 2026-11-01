package com.kyeongrok.youtubemp3

import android.content.Context

enum class ThemeMode(val label: String) {
    System("시스템 설정 따르기"),
    Light("라이트"),
    Dark("다크"),
}

/** 라이트 모드의 색상. 색 값은 ARGB이고 보라는 Material 기본 색을 그대로 쓴다. */
enum class ColorTheme(
    val label: String,
    val primary: Long,
    val container: Long = 0,
    val onContainer: Long = 0,
    val secondaryContainer: Long = 0,
    val background: Long = 0,
    val surfaceVariant: Long = 0,
) {
    Purple("보라", 0xFF6750A4),
    Blue("파랑", 0xFF1565C0, 0xFFD3E4FF, 0xFF001C38, 0xFFD7E3F7, 0xFFFDFCFF, 0xFFDFE2EB),
    Green("초록", 0xFF2E7D32, 0xFFB7F0B1, 0xFF002204, 0xFFD8E8D3, 0xFFFCFDF6, 0xFFDEE5D9),
    Orange("주황", 0xFFC25700, 0xFFFFDCC7, 0xFF311300, 0xFFF8DDCC, 0xFFFFFBFF, 0xFFF3DED3),
    Pink("분홍", 0xFFB0276D, 0xFFFFD9E3, 0xFF3E001E, 0xFFF9DBE3, 0xFFFFFBFF, 0xFFF3DDE1),
}

object AppSettings {
    private const val FILE_NAME = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_COLOR = "color"
    private const val KEY_RUMMIKUB_TUTORIAL = "rummikubTutorialSeen"

    fun loadTheme(context: Context): ThemeMode {
        val name = preferences(context).getString(KEY_THEME, null)
        return ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
    }

    fun saveTheme(context: Context, mode: ThemeMode) {
        preferences(context).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun loadColor(context: Context): ColorTheme {
        val name = preferences(context).getString(KEY_COLOR, null)
        return ColorTheme.entries.find { it.name == name } ?: ColorTheme.Purple
    }

    fun saveColor(context: Context, color: ColorTheme) {
        preferences(context).edit().putString(KEY_COLOR, color.name).apply()
    }

    fun loadRummikubTutorialSeen(context: Context) = preferences(context).getBoolean(KEY_RUMMIKUB_TUTORIAL, false)

    fun saveRummikubTutorialSeen(context: Context) {
        preferences(context).edit().putBoolean(KEY_RUMMIKUB_TUTORIAL, true).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
