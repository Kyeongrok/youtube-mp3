package com.kyeongrok.youtubemp3

import android.app.Application
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val quality = MutableStateFlow(DEFAULT_QUALITY)

    private val mutableResults = MutableStateFlow<List<VideoSearchResult>>(emptyList())
    val results = mutableResults.asStateFlow()

    private val mutableSearching = MutableStateFlow(false)
    val searching = mutableSearching.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    private val mutableTracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = mutableTracks.asStateFlow()

    private val mutableUpdate = MutableStateFlow<AppRelease?>(null)
    val update = mutableUpdate.asStateFlow()

    private val mutableUpdateProgress = MutableStateFlow<Float?>(null)
    val updateProgress = mutableUpdateProgress.asStateFlow()

    private val mutableThemeMode = MutableStateFlow(AppSettings.loadTheme(application))
    val themeMode = mutableThemeMode.asStateFlow()

    private val mutableColorTheme = MutableStateFlow(AppSettings.loadColor(application))
    val colorTheme = mutableColorTheme.asStateFlow()

    private val mutableSudoku = MutableStateFlow(SavedGames.loadSudoku(application))
    val sudoku = mutableSudoku.asStateFlow()

    private val mutableSudokuRecords = MutableStateFlow(SudokuRecords.load(application))
    val sudokuRecords = mutableSudokuRecords.asStateFlow()

    // 판이 매초 다시 그려지지 않도록 게임 상태와 따로 세고, 저장하거나 끝날 때 게임에 옮긴다.
    private var sudokuSeconds = mutableSudoku.value?.seconds ?: 0

    private val mutableShanghai = MutableStateFlow(SavedGames.loadShanghai(application))
    val shanghai = mutableShanghai.asStateFlow()

    private val mutableShanghaiRecords = MutableStateFlow(ShanghaiRecords.load(application))
    val shanghaiRecords = mutableShanghaiRecords.asStateFlow()

    private var shanghaiSeconds = mutableShanghai.value?.seconds ?: 0

    private val mutableFreeCell = MutableStateFlow(SavedGames.loadFreeCell(application))
    val freeCell = mutableFreeCell.asStateFlow()

    private var lastCardTap = 0L

    private val mutableDeviceTracks = MutableStateFlow<List<Track>>(emptyList())
    val deviceTracks = mutableDeviceTracks.asStateFlow()

    private val mutablePlaylists = MutableStateFlow(Playlists.load(application))
    val playlists = mutablePlaylists.asStateFlow()

    init {
        refreshTracks()
        viewModelScope.launch {
            Downloads.items.collect { items ->
                if (items.any { it.status == DownloadStatus.Done }) refreshTracks()
            }
        }
        viewModelScope.launch { mutableUpdate.value = AppUpdate.findNewerRelease() }
    }

    fun setThemeMode(mode: ThemeMode) {
        mutableThemeMode.value = mode
        AppSettings.saveTheme(getApplication(), mode)
    }

    fun setColorTheme(color: ColorTheme) {
        mutableColorTheme.value = color
        AppSettings.saveColor(getApplication(), color)
    }

    fun installUpdate() {
        val release = mutableUpdate.value ?: return
        if (mutableUpdateProgress.value != null) return
        viewModelScope.launch {
            mutableUpdateProgress.value = 0f
            try {
                AppUpdate.install(getApplication(), release) { mutableUpdateProgress.value = it }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "새 버전을 받지 못했습니다: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                mutableUpdateProgress.value = null
            }
        }
    }

    /** 입력이 주소면 바로 받고, 아니면 검색한다. */
    fun submit(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = extractUrl(text)
        if (url != null) download(url, url) else search(text)
    }

    private fun search(query: String) {
        viewModelScope.launch {
            mutableSearching.value = true
            mutableError.value = null
            try {
                mutableResults.value = Engine.search(query)
            } catch (e: Exception) {
                mutableError.value = e.message
            } finally {
                mutableSearching.value = false
            }
        }
    }

    fun download(title: String, url: String) {
        Downloads.enqueue(getApplication(), title, url, quality.value)
    }

    fun refreshTracks() {
        mutableTracks.value = Library.list(getApplication())
    }

    fun refreshDeviceTracks() {
        mutableDeviceTracks.value = Library.listDevice(getApplication())
    }

    fun delete(track: Track) {
        Player.stopIfPlaying(track)
        Library.delete(getApplication(), track)
        forgetTrack(track)
    }

    /** 파일이 이미 지워진 곡을 플레이어와 목록에서 뺀다. */
    fun forgetTrack(track: Track) {
        Player.stopIfPlaying(track)
        refreshTracks()
        refreshDeviceTracks()
        savePlaylists(mutablePlaylists.value.map { it.copy(trackIds = it.trackIds - track.id) })
    }

    fun createPlaylist(name: String): Long {
        val id = System.currentTimeMillis()
        savePlaylists(mutablePlaylists.value + Playlist(id, name, emptyList()))
        return id
    }

    fun deletePlaylist(id: Long) {
        savePlaylists(mutablePlaylists.value.filter { it.id != id })
    }

    /** 기기에 보이는 곡만 선택대로 바꾸고, 지금 읽을 수 없는 곡은 목록에 그대로 둔다. */
    fun choosePlaylistTracks(id: Long, selected: Set<Long>) {
        val visible = mutableDeviceTracks.value.map { it.id }.toSet()
        updatePlaylistTracks(id) { ids ->
            ids.filter { it !in visible || it in selected } + (selected.filter(visible::contains) - ids.toSet())
        }
    }

    fun removePlaylistTrack(id: Long, trackId: Long) {
        updatePlaylistTracks(id) { it - trackId }
    }

    fun swapPlaylistTracks(id: Long, first: Long, second: Long) {
        updatePlaylistTracks(id) { ids ->
            ids.map {
                when (it) {
                    first -> second
                    second -> first
                    else -> it
                }
            }
        }
    }

    private fun updatePlaylistTracks(id: Long, change: (List<Long>) -> List<Long>) {
        savePlaylists(
            mutablePlaylists.value.map {
                if (it.id == id) it.copy(trackIds = change(it.trackIds)) else it
            },
        )
    }

    private fun savePlaylists(playlists: List<Playlist>) {
        mutablePlaylists.value = playlists
        Playlists.save(getApplication(), playlists)
    }

    fun newSudoku(level: SudokuLevel) {
        viewModelScope.launch {
            mutableSudoku.value = withContext(Dispatchers.Default) { Sudoku.generate(level) }
            sudokuSeconds = 0
            saveSudoku()
        }
    }

    fun tickSudoku() {
        if (mutableSudoku.value?.isComplete == false) sudokuSeconds++
    }

    fun saveSudoku() {
        mutableSudoku.value?.let { SavedGames.saveSudoku(getApplication(), it.copy(seconds = sudokuSeconds)) }
    }

    fun enterSudoku(index: Int, digit: Int) {
        val game = mutableSudoku.value ?: return
        val next = game.enter(index, digit)
        if (next.isComplete && !game.isComplete) {
            mutableSudoku.value = next.copy(seconds = sudokuSeconds)
            val record = SudokuRecord(System.currentTimeMillis(), next.level, sudokuSeconds, next.mistakes)
            mutableSudokuRecords.value = listOf(record) + mutableSudokuRecords.value
            SudokuRecords.save(getApplication(), mutableSudokuRecords.value)
        } else {
            mutableSudoku.value = next
        }
        saveSudoku()
    }

    fun toggleSudokuNote(index: Int, digit: Int) {
        mutableSudoku.value = mutableSudoku.value?.toggleNote(index, digit)
        saveSudoku()
    }

    fun newFreeCell() {
        val game = FreeCellGame.deal()
        mutableFreeCell.value = game
        SavedGames.saveFreeCell(getApplication(), game)
    }

    fun undoFreeCell() {
        updateFreeCell(mutableFreeCell.value?.undo())
    }

    /** 카드를 고르기만 한 탭은 저장하지 않는다. */
    private fun updateFreeCell(next: FreeCellGame?) {
        val moved = next?.previous !== mutableFreeCell.value?.previous
        mutableFreeCell.value = next
        if (next != null && moved) SavedGames.saveFreeCell(getApplication(), next)
    }

    /** 첫 탭은 바로 카드를 고르고, 이어서 같은 끝 카드를 누르면 빈 프리셀로 옮긴다. */
    fun tapFreeCellCascade(column: Int, row: Int?) {
        val game = mutableFreeCell.value ?: return
        val now = SystemClock.uptimeMillis()
        val doubleTap = row != null && row == game.cascades[column].lastIndex &&
            game.picked == FreeCellPick.Cascade(column, row) && now - lastCardTap < DOUBLE_TAP_MILLIS
        val emptyCell = game.cells.indexOf(null)
        lastCardTap = now
        updateFreeCell(if (doubleTap && emptyCell >= 0) game.tapCell(emptyCell) else game.tapCascade(column, row))
    }

    fun tapFreeCellSpot(index: Int) {
        updateFreeCell(mutableFreeCell.value?.tapCell(index))
    }

    fun tapFreeCellFoundation() {
        updateFreeCell(mutableFreeCell.value?.tapFoundation())
    }

    fun newShanghai() {
        viewModelScope.launch {
            mutableShanghai.value = withContext(Dispatchers.Default) { Shanghai.generate() }
            shanghaiSeconds = 0
            saveShanghai()
        }
    }

    fun tickShanghai() {
        if (mutableShanghai.value?.isComplete == false) shanghaiSeconds++
    }

    fun saveShanghai() {
        mutableShanghai.value?.let { SavedGames.saveShanghai(getApplication(), it.copy(seconds = shanghaiSeconds)) }
    }

    fun pickShanghai(index: Int) {
        val game = mutableShanghai.value ?: return
        val next = game.pick(index)
        if (next.isComplete && !game.isComplete) {
            mutableShanghai.value = next.copy(seconds = shanghaiSeconds)
            val record = ShanghaiRecord(System.currentTimeMillis(), shanghaiSeconds, next.shuffles)
            mutableShanghaiRecords.value = listOf(record) + mutableShanghaiRecords.value
            ShanghaiRecords.save(getApplication(), mutableShanghaiRecords.value)
        } else {
            mutableShanghai.value = next
        }
        if (next.faces !== game.faces) saveShanghai()
    }

    fun showShanghaiHint() {
        mutableShanghai.value = mutableShanghai.value?.showHint()
    }

    fun shuffleShanghai() {
        val game = mutableShanghai.value ?: return
        viewModelScope.launch {
            val shuffled = withContext(Dispatchers.Default) { game.shuffled() }
            if (shuffled != null) {
                mutableShanghai.value = shuffled
                saveShanghai()
            } else {
                Toast.makeText(getApplication(), "남은 패로는 풀 수 없습니다. 새 게임을 시작해 주세요.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        val QUALITIES = listOf("320K", "192K", "128K")
        private const val DEFAULT_QUALITY = "192K"
        private const val DOUBLE_TAP_MILLIS = 400L

        private val URL_PATTERN = Regex("""https?://\S+""")

        fun extractUrl(text: String) = URL_PATTERN.find(text)?.value
    }
}
