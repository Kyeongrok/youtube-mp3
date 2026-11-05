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

/** 루미큐브 판 타일의 크기. 배율은 화면 폭에 맞춘 기본 크기에 곱한다. */
enum class RummikubTableSize(val label: String, val scale: Float) {
    Small("작게", 0.8f),
    Medium("보통", 1f),
    Large("크게", 1.3f),
    Largest("최대", 1.6f),
}

/** 루미큐브에서 뽑은 타일이 내 차례가 시작된 뒤 깜빡이는 시간. null이면 차례를 마칠 때까지 깜빡인다. */
enum class RummikubBlinkDuration(val label: String, val seconds: Int?) {
    Three("3초", 3),
    Five("5초", 5),
    Ten("10초", 10),
    Thirty("30초", 30),
    Always("계속", null),
}

object AppSettings {
    private const val FILE_NAME = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_COLOR = "color"
    private const val KEY_RUMMIKUB_TUTORIAL = "rummikubTutorialSeen"
    private const val KEY_RUMMIKUB_TABLE_SIZE = "rummikubTableSize"
    private const val KEY_RUMMIKUB_BLINK = "rummikubBlinkDuration"
    private const val KEY_LAST_SCREEN = "lastScreen"
    private const val KEY_HOME_ORDER = "homeOrder"
    private const val KEY_MUSIC_TAB = "musicTab"

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

    fun loadRummikubTableSize(context: Context): RummikubTableSize {
        val name = preferences(context).getString(KEY_RUMMIKUB_TABLE_SIZE, null)
        return RummikubTableSize.entries.find { it.name == name } ?: RummikubTableSize.Medium
    }

    fun saveRummikubTableSize(context: Context, size: RummikubTableSize) {
        preferences(context).edit().putString(KEY_RUMMIKUB_TABLE_SIZE, size.name).apply()
    }

    fun loadRummikubBlinkDuration(context: Context): RummikubBlinkDuration {
        val name = preferences(context).getString(KEY_RUMMIKUB_BLINK, null)
        return RummikubBlinkDuration.entries.find { it.name == name } ?: RummikubBlinkDuration.Ten
    }

    fun saveRummikubBlinkDuration(context: Context, duration: RummikubBlinkDuration) {
        preferences(context).edit().putString(KEY_RUMMIKUB_BLINK, duration.name).apply()
    }

    /** 마지막으로 연 앱 화면의 이름이다. 처음 켜면 null이다. */
    fun loadLastScreen(context: Context): String? = preferences(context).getString(KEY_LAST_SCREEN, null)

    fun saveLastScreen(context: Context, name: String) {
        preferences(context).edit().putString(KEY_LAST_SCREEN, name).apply()
    }

    /** 음악 화면에서 마지막으로 보던 탭의 번호다. */
    fun loadMusicTab(context: Context) = preferences(context).getInt(KEY_MUSIC_TAB, 0)

    fun saveMusicTab(context: Context, tab: Int) {
        preferences(context).edit().putInt(KEY_MUSIC_TAB, tab).apply()
    }

    /** 처음 화면에서 사용자가 옮겨 놓은 타일 순서다. 화면 이름을 쉼표로 이어 저장한다. */
    fun loadHomeOrder(context: Context): List<String> =
        preferences(context).getString(KEY_HOME_ORDER, null)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

    fun saveHomeOrder(context: Context, order: List<String>) {
        preferences(context).edit().putString(KEY_HOME_ORDER, order.joinToString(",")).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
