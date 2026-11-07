package com.kyeongrok.youtubemp3

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val mutableHomeOrder = MutableStateFlow(AppSettings.loadHomeOrder(application))
    val homeOrder = mutableHomeOrder.asStateFlow()

    private val mutableColorTheme = MutableStateFlow(AppSettings.loadColor(application))
    val colorTheme = mutableColorTheme.asStateFlow()

    private val mutableSajuProfiles = MutableStateFlow(SajuProfiles.load(application))
    val sajuProfiles = mutableSajuProfiles.asStateFlow()

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

    fun setHomeOrder(order: List<String>) {
        mutableHomeOrder.value = order
        AppSettings.saveHomeOrder(getApplication(), order)
    }

    fun setThemeMode(mode: ThemeMode) {
        mutableThemeMode.value = mode
        AppSettings.saveTheme(getApplication(), mode)
    }

    fun setColorTheme(color: ColorTheme) {
        mutableColorTheme.value = color
        AppSettings.saveColor(getApplication(), color)
    }

    private val mutableCheckingUpdate = MutableStateFlow(false)
    val checkingUpdate = mutableCheckingUpdate.asStateFlow()

    /** 설정·메뉴에서 직접 누른 업데이트 확인. 새 버전이 있으면 설치 안내가 다시 뜨도록 넘겨준다. */
    fun checkForUpdate(onResult: (UpdateCheck) -> Unit) {
        if (mutableCheckingUpdate.value) return
        mutableCheckingUpdate.value = true
        viewModelScope.launch {
            val result = AppUpdate.check()
            if (result is UpdateCheck.Newer) mutableUpdate.value = result.release
            mutableCheckingUpdate.value = false
            onResult(result)
        }
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

    private val mutableTrimming = MutableStateFlow<Track?>(null)
    /** 편집 화면에 열린 곡. null이면 닫혀 있다. */
    val trimming = mutableTrimming.asStateFlow()

    fun openTrim(track: Track) {
        mutableTrimming.value = track
    }

    fun closeTrim() {
        mutableTrimming.value = null
    }

    fun refreshTracks() {
        mutableTracks.value = Library.list(getApplication())
    }

    fun refreshDeviceTracks() {
        mutableDeviceTracks.value = Library.listDevice(getApplication())
    }

    /** 이 곡이 있는 폴더 전체를 녹음(또는 음악)으로 옮겨 보여 준다. */
    fun setFolderKind(track: Track, recording: Boolean) {
        Library.setFolderKind(getApplication(), track.folder, recording)
        refreshDeviceTracks()
    }

    private val mutableScanning = MutableStateFlow(false)
    val scanning = mutableScanning.asStateFlow()

    /** 저장소를 훑어 빠진 파일을 등록하고 목록을 다시 읽는다. 끝나면 새로 찾은 개수를 알려 준다. */
    fun scanDevice(onDone: (Int) -> Unit) {
        if (mutableScanning.value) return
        mutableScanning.value = true
        viewModelScope.launch {
            val found = try {
                Library.scanStorage(getApplication())
            } catch (e: Exception) {
                0
            }
            refreshTracks()
            refreshDeviceTracks()
            mutableScanning.value = false
            onDone(found)
        }
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

    fun renamePlaylist(id: Long, name: String) {
        savePlaylists(mutablePlaylists.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    /** 이미 들어 있는 곡이면 false를 돌려준다. */
    fun addPlaylistTrack(id: Long, trackId: Long): Boolean {
        val playlist = mutablePlaylists.value.find { it.id == id } ?: return false
        if (trackId in playlist.trackIds) return false
        updatePlaylistTracks(id) { it + trackId }
        return true
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

    /**
     * 끌어서 바꾼 순서를 저장한다. [ordered]에는 기기에 있는 곡만 들어 있으니,
     * 지금 기기에서 못 찾은 곡은 뒤에 그대로 남겨 둔다.
     */
    fun reorderPlaylistTracks(id: Long, ordered: List<Long>) {
        updatePlaylistTracks(id) { ids -> ordered.filter { it in ids } + ids.filter { it !in ordered } }
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

    /** 같은 사람이 있으면 고치고, 없으면 끝에 더한다. */
    fun saveSajuProfile(profile: SajuProfile) {
        val profiles = mutableSajuProfiles.value
        mutableSajuProfiles.value = if (profiles.any { it.id == profile.id }) {
            profiles.map { if (it.id == profile.id) profile else it }
        } else {
            profiles + profile
        }
        SajuProfiles.save(getApplication(), mutableSajuProfiles.value)
    }

    fun deleteSajuProfile(id: Long) {
        mutableSajuProfiles.value = mutableSajuProfiles.value.filter { it.id != id }
        SajuProfiles.save(getApplication(), mutableSajuProfiles.value)
    }

    companion object {
        val QUALITIES = listOf("320K", "192K", "128K")
        private const val DEFAULT_QUALITY = "192K"

        private val URL_PATTERN = Regex("""https?://\S+""")

        fun extractUrl(text: String) = URL_PATTERN.find(text)?.value
    }
}
