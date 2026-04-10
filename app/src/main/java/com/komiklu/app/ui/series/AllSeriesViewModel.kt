package com.komiklu.app.ui.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komiklu.app.data.model.Comic
import com.komiklu.app.data.repository.ComicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 20

@HiltViewModel
class AllSeriesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val comicRepo: ComicRepository
) : ViewModel() {

    // Bisa datang dari navigasi genre chip di detail
    private val initialGenreFilter: String? = savedState["filterGenre"]

    private val _allComics = MutableStateFlow<List<Comic>>(emptyList())

    // Filters
    private val _genreFilter = MutableStateFlow(initialGenreFilter)
    private val _statusFilter = MutableStateFlow<String?>(null)
    private val _sortBy = MutableStateFlow(SortBy.LATEST)
    private val _searchQuery = MutableStateFlow("")

    val activeGenreFilter: StateFlow<String?> = _genreFilter.asStateFlow()
    val activeSortBy: StateFlow<SortBy> = _sortBy.asStateFlow()

    // Pagination
    private val _displayedCount = MutableStateFlow(PAGE_SIZE)
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Filtered + paginated hasil
    val displayedComics: StateFlow<List<Comic>> = combine(
        _allComics, _genreFilter, _statusFilter, _sortBy, _searchQuery, _displayedCount
    ) { comics, genre, status, sort, query, count ->
        var filtered = comics

        if (!query.isNullOrBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, true) || it.author.contains(query, true)
            }
        }
        if (!genre.isNullOrBlank()) {
            filtered = filtered.filter { it.genres.contains(genre) }
        }
        if (!status.isNullOrBlank()) {
            filtered = filtered.filter { it.status.equals(status, true) }
        }
        filtered = when (sort) {
            SortBy.LATEST   -> filtered.reversed()
            SortBy.POPULAR  -> filtered.sortedByDescending { it.viewCount }
            SortBy.RATING   -> filtered.sortedByDescending { it.rating }
            SortBy.TITLE_AZ -> filtered.sortedBy { it.title }
        }

        _hasMore.value = filtered.size > count
        filtered.take(count)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Semua genre unik untuk filter chips
    val allGenres: StateFlow<List<String>> = _allComics.map { comics ->
        comics.flatMap { it.genres }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init { loadAll() }

    private fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            comicRepo.getAllComics().fold(
                onSuccess = { _allComics.value = it },
                onFailure = { /* handle error */ }
            )
            _isLoading.value = false
        }
    }

    fun loadMore() { _displayedCount.value += PAGE_SIZE }
    fun setGenreFilter(genre: String?) { _genreFilter.value = genre; _displayedCount.value = PAGE_SIZE }
    fun setStatusFilter(status: String?) { _statusFilter.value = status; _displayedCount.value = PAGE_SIZE }
    fun setSortBy(sort: SortBy) { _sortBy.value = sort; _displayedCount.value = PAGE_SIZE }
    fun setSearch(query: String) { _searchQuery.value = query; _displayedCount.value = PAGE_SIZE }
    fun refresh() = loadAll()
}

enum class SortBy { LATEST, POPULAR, RATING, TITLE_AZ }
