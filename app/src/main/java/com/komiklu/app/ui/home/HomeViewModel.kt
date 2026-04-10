package com.komiklu.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komiklu.app.data.model.*
import com.komiklu.app.data.repository.ComicRepository
import com.komiklu.app.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── CONFIG ───────────────────────────────────────────────────────────────────
private const val NEW_CHAPTER_BADGE_COUNT = 5   // dari config, bisa diubah
private const val POPULAR_LIMIT           = 10
private const val PROJECT_LIMIT           = 6
private const val LATEST_COMICS_LIMIT     = 10

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val comicRepo: ComicRepository,
    private val historyRepo: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Semua comics di-cache di memory setelah fetch pertama
    private var allComics: List<Comic> = emptyList()

    // Search results — real-time dari memory
    val searchResults: StateFlow<List<Comic>> = _searchQuery
        .debounce(300)
        .map { query ->
            if (query.isBlank()) emptyList()
            else allComics.filter { comic ->
                comic.title.contains(query, ignoreCase = true) ||
                comic.author.contains(query, ignoreCase = true) ||
                comic.genres.any { it.contains(query, ignoreCase = true) }
            }.take(20)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            comicRepo.getAllComics().fold(
                onSuccess = { comics ->
                    allComics = comics
                    processHomeData(comics)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    private suspend fun processHomeData(comics: List<Comic>) {
        // Banner: manga rekomendasi atau project teratas
        val bannerComics = comics.filter { it.isRekomen || it.isProject }.take(5)
            .ifEmpty { comics.take(3) }

        // Project Kami: project = "1"
        val projectComics = comics.filter { it.isProject }.take(PROJECT_LIMIT)

        // Terpopuler: sort by viewCount desc
        val popularComics = comics.sortedByDescending { it.viewCount }.take(POPULAR_LIMIT)

        // Komik Terbaru: urutan input terbaru (index terakhir di JSON = terbaru)
        val newestComics = comics.reversed().take(LATEST_COMICS_LIMIT)

        // Chapter terbaru: fetch chapters dari N manga terpopuler
        val latestChapters = buildLatestChapters(comics.take(15))

        _uiState.update {
            it.copy(
                isLoading = false,
                bannerComics = bannerComics,
                projectComics = projectComics,
                popularComics = popularComics,
                latestChapters = latestChapters,
                newestComics = newestComics
            )
        }
    }

    private suspend fun buildLatestChapters(comics: List<Comic>): List<LatestChapter> {
        val result = mutableListOf<LatestChapter>()
        var newCount = 0

        for (comic in comics) {
            comicRepo.getChapters(comic.title).getOrNull()?.let { chapters ->
                val latest = chapters.maxByOrNull { it.chapterNumber } ?: return@let
                result.add(
                    LatestChapter(
                        comicTitle = comic.title,
                        coverUrl = comic.coverUrl,
                        chapter = latest.chapter,
                        chapterUrl = latest.url,
                        isNew = newCount < NEW_CHAPTER_BADGE_COUNT
                    )
                )
                newCount++
            }
            if (result.size >= 10) break
        }

        return result.sortedByDescending { it.updatedAt }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun refresh() = loadHomeData()
}
