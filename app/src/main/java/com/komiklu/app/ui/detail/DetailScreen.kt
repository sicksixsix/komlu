package com.komiklu.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komiklu.app.data.model.*
import com.komiklu.app.data.repository.ComicRepository
import com.komiklu.app.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CHAPTERS_PER_PAGE = 50

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val comicRepo: ComicRepository,
    private val historyRepo: HistoryRepository
) : ViewModel() {

    val comicTitle: String = savedState["comicTitle"] ?: ""

    private val _comic = MutableStateFlow<UiState<Comic>>(UiState.Loading)
    val comic: StateFlow<UiState<Comic>> = _comic.asStateFlow()

    private val _allChapters = MutableStateFlow<List<Chapter>>(emptyList())

    private val _displayedChapters = MutableStateFlow<List<Chapter>>(emptyList())
    val displayedChapters: StateFlow<List<Chapter>> = _displayedChapters.asStateFlow()

    private val _sortAsc = MutableStateFlow(false) // default DESC (terbaru dulu)
    val sortAsc: StateFlow<Boolean> = _sortAsc.asStateFlow()

    private val _hasMoreChapters = MutableStateFlow(false)
    val hasMoreChapters: StateFlow<Boolean> = _hasMoreChapters.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _lastReadChapter = MutableStateFlow<ReadHistoryEntity?>(null)
    val lastReadChapter: StateFlow<ReadHistoryEntity?> = _lastReadChapter.asStateFlow()

    private var chapterPage = 1

    init {
        loadComic()
        observeFavorite()
        loadLastRead()
    }

    private fun loadComic() {
        viewModelScope.launch {
            _comic.value = UiState.Loading

            comicRepo.getAllComics().fold(
                onSuccess = { comics ->
                    val comic = comics.find { it.title == comicTitle }
                    if (comic != null) {
                        _comic.value = UiState.Success(comic)
                        loadChapters()
                    } else {
                        _comic.value = UiState.Error("Manga tidak ditemukan")
                    }
                },
                onFailure = { _comic.value = UiState.Error(it.message ?: "Error") }
            )
        }
    }

    private fun loadChapters() {
        viewModelScope.launch {
            comicRepo.getChapters(comicTitle).fold(
                onSuccess = { chapters ->
                    _allChapters.value = chapters
                    applySort()
                },
                onFailure = { /* Chapter error — tampilkan empty state */ }
            )
        }
    }

    fun toggleSort() {
        _sortAsc.value = !_sortAsc.value
        chapterPage = 1
        applySort()
    }

    fun loadMoreChapters() {
        chapterPage++
        applySort(append = true)
    }

    private fun applySort(append: Boolean = false) {
        val sorted = if (_sortAsc.value) {
            _allChapters.value.sortedBy { it.chapterNumber }
        } else {
            _allChapters.value.sortedByDescending { it.chapterNumber }
        }

        val end = chapterPage * CHAPTERS_PER_PAGE
        val slice = sorted.take(end)

        _displayedChapters.value = if (append) slice else slice
        _hasMoreChapters.value = sorted.size > end
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            comicRepo.isFavorite(comicTitle).collect { fav ->
                _isFavorite.value = fav
            }
        }
    }

    private fun loadLastRead() {
        viewModelScope.launch {
            _lastReadChapter.value = historyRepo.getLastRead(comicTitle)
        }
    }

    fun toggleFavorite() {
        val comicState = _comic.value
        if (comicState !is UiState.Success) return

        viewModelScope.launch {
            if (_isFavorite.value) {
                comicRepo.removeFavorite(comicTitle)
            } else {
                comicRepo.addFavorite(comicState.data)
            }
        }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────────────────────────

package com.komiklu.app.ui.detail

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.komiklu.app.R
import com.komiklu.app.data.model.UiState
import com.komiklu.app.databinding.FragmentDetailBinding
import com.komiklu.app.ui.adapter.ChapterListAdapter
import com.komiklu.app.ui.adapter.GenreChipAdapter
import com.komiklu.app.ui.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: DetailViewModel by viewModels()
    private val args: DetailFragmentArgs by navArgs()

    private lateinit var chapterAdapter: ChapterListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupChapterList()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupChapterList() {
        chapterAdapter = ChapterListAdapter { chapter ->
            findNavController().navigate(
                DetailFragmentDirections.actionDetailToReader(
                    vm.comicTitle, chapter.chapter, chapter.url
                )
            )
        }
        binding.rvChapters.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chapterAdapter
            isNestedScrollingEnabled = false
        }

        binding.btnSortChapter.setOnClickListener { vm.toggleSort() }
        binding.btnLoadMoreChapters.setOnClickListener { vm.loadMoreChapters() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    vm.comic.collect { state ->
                        when (state) {
                            is UiState.Loading -> binding.shimmer.visible()
                            is UiState.Success -> {
                                binding.shimmer.gone()
                                binding.contentScroll.visible()
                                bindComic(state.data)
                            }
                            is UiState.Error -> {
                                binding.shimmer.gone()
                                showSnackbar(state.message)
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    vm.displayedChapters.collect { chapters ->
                        chapterAdapter.submitList(chapters)
                        binding.tvChapterCount.text = "${vm.comicTitle.let { _ ->
                            chapters.size
                        }} Chapter ditampilkan"
                    }
                }

                launch {
                    vm.sortAsc.collect { asc ->
                        binding.btnSortChapter.text = if (asc) "ASC ↑" else "DESC ↓"
                    }
                }

                launch {
                    vm.hasMoreChapters.collect { hasMore ->
                        binding.btnLoadMoreChapters.visibility =
                            if (hasMore) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    vm.isFavorite.collect { isFav ->
                        binding.btnFavorite.setIconResource(
                            if (isFav) R.drawable.ic_bookmark_filled
                            else R.drawable.ic_bookmark_outline
                        )
                        binding.btnFavorite.text =
                            if (isFav) "Tersimpan" else "Favorit"
                    }
                }

                launch {
                    vm.lastReadChapter.collect { history ->
                        if (history != null) {
                            binding.btnContinueRead.visible()
                            binding.btnContinueRead.text = "Lanjut ${history.chapter}"
                        } else {
                            binding.btnContinueRead.gone()
                        }
                    }
                }
            }
        }
    }

    private fun bindComic(comic: com.komiklu.app.data.model.Comic) {
        binding.ivCoverBlur.load(comic.coverUrl) { crossfade(true) }
        binding.ivCover.load(comic.coverUrl) { crossfade(true) }
        binding.tvTitle.text = comic.title
        binding.tvAuthor.text = comic.author
        binding.tvYear.text = comic.year.toString()
        binding.tvRating.text = "⭐ ${comic.rating}"
        binding.tvStatus.text = comic.status
        binding.tvDesc.text = comic.desc

        // Genre chips
        val chipAdapter = GenreChipAdapter { genre ->
            findNavController().navigate(
                DetailFragmentDirections.actionDetailToAllSeries(filterGenre = genre)
            )
        }
        binding.rvGenres.adapter = chipAdapter
        binding.rvGenres.layoutManager =
            androidx.recyclerview.widget.FlexboxLayoutManager(context)
        chipAdapter.submitList(comic.genres)

        // Actions
        binding.btnRead.setOnClickListener {
            // Mulai dari chapter 1 atau lanjut
        }
        binding.btnFavorite.setOnClickListener { vm.toggleFavorite() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
