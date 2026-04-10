package com.komiklu.app.ui.reader

import android.app.Activity
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.ImageRequest
import com.komiklu.app.BuildConfig
import com.komiklu.app.data.model.*
import com.komiklu.app.data.repository.ComicRepository
import com.komiklu.app.data.repository.HistoryRepository
import com.komiklu.app.data.session.SessionManager
import com.komiklu.app.databinding.ActivityReaderBinding
import com.komiklu.app.databinding.ItemMangaPageBinding
import com.komiklu.app.ui.util.gone
import com.komiklu.app.ui.util.visible
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// ─── VIEWMODEL ────────────────────────────────────────────────────────────────

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val comicRepo: ComicRepository,
    private val historyRepo: HistoryRepository
) : ViewModel() {

    val comicTitle: String = savedState["comicTitle"] ?: ""
    val chapterName: String = savedState["chapter"] ?: ""
    val chapterUrl: String = savedState["chapterUrl"] ?: ""

    private val _pages = MutableStateFlow<UiState<List<MangaPage>>>(UiState.Loading)
    val pages: StateFlow<UiState<List<MangaPage>>> = _pages.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _allChapters = MutableStateFlow<List<Chapter>>(emptyList())
    val allChapters: StateFlow<List<Chapter>> = _allChapters.asStateFlow()

    val chapterNumber: Int
        get() = chapterName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    init {
        loadPages()
        loadAllChapters()
    }

    fun loadPages(quality: ImageQuality? = null) {
        viewModelScope.launch {
            _pages.value = UiState.Loading
            val q = quality ?: _settings.value.imageQuality
            comicRepo.getPages(chapterUrl, q).fold(
                onSuccess = { pages ->
                    _pages.value = UiState.Success(pages)
                    // Simpan ke history
                    saveToHistory(pages.size)
                },
                onFailure = { _pages.value = UiState.Error(it.message ?: "Gagal memuat halaman") }
            )
        }
    }

    private fun loadAllChapters() {
        viewModelScope.launch {
            comicRepo.getChapters(comicTitle).getOrNull()?.let { chapters ->
                _allChapters.value = chapters.sortedBy { it.chapterNumber }
            }
        }
    }

    fun onPageChanged(page: Int) {
        _currentPage.value = page
        // Auto-save progress setiap 3 halaman
        if (page % 3 == 0) {
            viewModelScope.launch {
                historyRepo.saveProgress(
                    comicTitle = comicTitle,
                    chapter = chapterName,
                    chapterNumber = chapterNumber,
                    coverUrl = "", // sudah disimpan saat pertama baca
                    page = page,
                    totalPages = (_pages.value as? UiState.Success)?.data?.size ?: 0
                )
            }
        }
    }

    fun updateSettings(settings: ReaderSettings) {
        _settings.value = settings
        if (settings.imageQuality != _settings.value.imageQuality) {
            loadPages(settings.imageQuality)
        }
    }

    fun getPrevChapter(): Chapter? {
        val chapters = _allChapters.value
        return chapters.lastOrNull { it.chapterNumber < chapterNumber }
    }

    fun getNextChapter(): Chapter? {
        val chapters = _allChapters.value
        return chapters.firstOrNull { it.chapterNumber > chapterNumber }
    }

    private suspend fun saveToHistory(totalPages: Int) {
        historyRepo.saveProgress(
            comicTitle = comicTitle,
            chapter = chapterName,
            chapterNumber = chapterNumber,
            coverUrl = "",
            page = _currentPage.value,
            totalPages = totalPages
        )
    }
}

// ─── ACTIVITY ─────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val vm: ReaderViewModel by viewModels()
    private lateinit var pageAdapter: MangaPageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fullscreen immersive
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN

        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPageList()
        observeState()
        setupControls()
    }

    private fun setupPageList() {
        pageAdapter = MangaPageAdapter()
        binding.rvPages.apply {
            adapter = pageAdapter
            setHasFixedSize(true)
        }
        updateLayoutForReadMode(vm.settings.value.readMode)

        // Listen scroll untuk update current page
        binding.rvPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val visible = lm.findFirstCompletelyVisibleItemPosition()
                if (visible >= 0) {
                    vm.onPageChanged(visible + 1)
                    updatePageIndicator(visible + 1)
                }
            }
        })
    }

    private fun updateLayoutForReadMode(mode: ReadMode) {
        when (mode) {
            ReadMode.VERTICAL -> {
                binding.rvPages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
                // Remove horizontal snap if any
            }
            ReadMode.HORIZONTAL -> {
                binding.rvPages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                PagerSnapHelper().attachToRecyclerView(binding.rvPages)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    vm.pages.collect { state ->
                        when (state) {
                            is UiState.Loading -> {
                                binding.progressBar.visible()
                                binding.rvPages.gone()
                            }
                            is UiState.Success -> {
                                binding.progressBar.gone()
                                binding.rvPages.visible()
                                pageAdapter.submitList(state.data)
                                updatePageIndicator(vm.currentPage.value)

                                // Preload gambar berikutnya
                                preloadNextImages(state.data)
                            }
                            is UiState.Error -> {
                                binding.progressBar.gone()
                                binding.tvError.visible()
                                binding.tvError.text = state.message
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    vm.currentPage.collect { page ->
                        updatePageIndicator(page)
                    }
                }
            }
        }
    }

    private fun setupControls() {
        binding.tvTitle.text = vm.comicTitle
        binding.tvChapter.text = vm.chapterName

        binding.btnPrevChapter.setOnClickListener {
            vm.getPrevChapter()?.let { chapter ->
                // Navigasi ke chapter sebelumnya
                restartWithChapter(chapter)
            }
        }

        binding.btnNextChapter.setOnClickListener {
            vm.getNextChapter()?.let { chapter ->
                restartWithChapter(chapter)
            } ?: run {
                // Tidak ada chapter berikutnya
                showToast("Ini chapter terakhir!")
            }
        }

        binding.btnSettings.setOnClickListener {
            showReaderSettings()
        }

        // Tap tengah layar untuk toggle UI
        binding.rvPages.setOnClickListener {
            toggleUiVisibility()
        }
    }

    private var uiVisible = true
    private fun toggleUiVisibility() {
        uiVisible = !uiVisible
        if (uiVisible) {
            binding.topBar.visible()
            binding.bottomBar.visible()
        } else {
            binding.topBar.gone()
            binding.bottomBar.gone()
        }
    }

    private fun updatePageIndicator(page: Int) {
        val total = (vm.pages.value as? UiState.Success)?.data?.size ?: 0
        binding.tvPageInfo.text = "$page / $total"
        if (total > 0) {
            binding.progressReader.progress = (page * 100 / total)
        }
    }

    private fun preloadNextImages(pages: List<MangaPage>) {
        // Preload 3 halaman berikutnya di background
        val current = vm.currentPage.value
        val toPreload = pages.drop(current).take(3)
        toPreload.forEach { page ->
            val request = ImageRequest.Builder(this)
                .data(page.imageUrl)
                .memoryCacheKey(page.imageUrl)
                .build()
            coil.imageLoader.enqueue(request)
        }
    }

    private fun showReaderSettings() {
        ReaderSettingsBottomSheet.newInstance(vm.settings.value) { newSettings ->
            vm.updateSettings(newSettings)
            updateLayoutForReadMode(newSettings.readMode)
        }.show(supportFragmentManager, "reader_settings")
    }

    private fun restartWithChapter(chapter: Chapter) {
        intent.putExtra("chapter", chapter.chapter)
        intent.putExtra("chapterUrl", chapter.url)
        recreate()
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }
}

// ─── PAGE ADAPTER ─────────────────────────────────────────────────────────────

class MangaPageAdapter : androidx.recyclerview.widget.ListAdapter<MangaPage, MangaPageAdapter.PageVH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<MangaPage>() {
        override fun areItemsTheSame(a: MangaPage, b: MangaPage) = a.pageNumber == b.pageNumber
        override fun areContentsTheSame(a: MangaPage, b: MangaPage) = a == b
    }
) {
    inner class PageVH(val binding: ItemMangaPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(page: MangaPage) {
            binding.ivPage.load(page.imageUrl) {
                crossfade(true)
                placeholder(com.komiklu.app.R.drawable.placeholder_manga_page)
                error(com.komiklu.app.R.drawable.error_manga_page)
                // Disable right-click / long press download
                listener(
                    onStart = { binding.pageShimmer.startShimmer() },
                    onSuccess = { _, _ -> binding.pageShimmer.stopShimmer(); binding.pageShimmer.gone() },
                    onError = { _, _ -> binding.pageShimmer.gone() }
                )
            }
            // Anti download: disable long press
            binding.ivPage.isLongClickable = false
            binding.ivPage.setOnLongClickListener { true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val binding = ItemMangaPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageVH(binding)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) = holder.bind(getItem(position))
}
