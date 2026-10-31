package com.kyeongrok.youtubemp3

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val quality = MutableStateFlow(QUALITIES.first())

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

    private val mutableSudoku = MutableStateFlow<SudokuGame?>(null)
    val sudoku = mutableSudoku.asStateFlow()

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
        refreshTracks()
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
            ids.filter { it !in visible || it in selected } + (selected - ids.toSet())
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
        }
    }

    fun enterSudoku(index: Int, digit: Int) {
        mutableSudoku.value = mutableSudoku.value?.enter(index, digit)
    }

    companion object {
        val QUALITIES = listOf("320K", "192K", "128K")

        private val URL_PATTERN = Regex("""https?://\S+""")

        fun extractUrl(text: String) = URL_PATTERN.find(text)?.value
    }
}
