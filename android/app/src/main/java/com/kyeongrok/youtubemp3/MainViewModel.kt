package com.kyeongrok.youtubemp3

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val mutableNewerVersion = MutableStateFlow<String?>(null)
    val newerVersion = mutableNewerVersion.asStateFlow()

    init {
        refreshTracks()
        viewModelScope.launch {
            Downloads.items.collect { items ->
                if (items.any { it.status == DownloadStatus.Done }) refreshTracks()
            }
        }
        viewModelScope.launch { mutableNewerVersion.value = AppUpdate.findNewerVersion() }
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

    fun delete(track: Track) {
        Player.stopIfPlaying(track)
        Library.delete(getApplication(), track)
        refreshTracks()
    }

    companion object {
        val QUALITIES = listOf("320K", "192K", "128K")

        private val URL_PATTERN = Regex("""https?://\S+""")

        fun extractUrl(text: String) = URL_PATTERN.find(text)?.value
    }
}
