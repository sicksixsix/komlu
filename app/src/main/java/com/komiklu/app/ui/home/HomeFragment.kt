package com.komiklu.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.komiklu.app.R
import com.komiklu.app.databinding.FragmentHomeBinding
import com.komiklu.app.ui.adapter.*
import com.komiklu.app.ui.util.gone
import com.komiklu.app.ui.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by viewModels()

    private lateinit var bannerAdapter: BannerPagerAdapter
    private lateinit var projectAdapter: MangaHorizontalAdapter
    private lateinit var popularAdapter: MangaHorizontalAdapter
    private lateinit var latestChapterAdapter: LatestChapterAdapter
    private lateinit var searchResultAdapter: SearchResultAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupSearch()
        observeState()

        binding.swipeRefresh.setOnRefreshListener { vm.refresh() }
    }

    private fun setupAdapters() {
        // Banner ViewPager2
        bannerAdapter = BannerPagerAdapter { comic ->
            navigateToDetail(comic.title)
        }
        binding.viewPagerBanner.adapter = bannerAdapter
        binding.dotsIndicator.attachTo(binding.viewPagerBanner)

        // Project Kami — horizontal scroll
        projectAdapter = MangaHorizontalAdapter { comic ->
            navigateToDetail(comic.title)
        }
        binding.rvProject.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = projectAdapter
            setHasFixedSize(true)
        }

        // Terpopuler
        popularAdapter = MangaHorizontalAdapter { comic ->
            navigateToDetail(comic.title)
        }
        binding.rvPopular.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = popularAdapter
            setHasFixedSize(true)
        }

        // Chapter Terbaru
        latestChapterAdapter = LatestChapterAdapter(
            onComicClick = { navigateToDetail(it.comicTitle) },
            onChapterClick = { navigateToReader(it.comicTitle, it.chapter, it.chapterUrl) }
        )
        binding.rvLatestChapter.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = latestChapterAdapter
            isNestedScrollingEnabled = false
        }

        // Search Results overlay
        searchResultAdapter = SearchResultAdapter { comic ->
            hideSearch()
            navigateToDetail(comic.title)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchResultAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            vm.onSearchQueryChanged(text.toString())
            if (text.isNullOrBlank()) {
                hideSearch()
            } else {
                showSearchOverlay()
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            hideSearch()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Home state
                launch {
                    vm.uiState.collect { state ->
                        binding.swipeRefresh.isRefreshing = false

                        if (state.isLoading) {
                            binding.shimmerLayout.startShimmer()
                            binding.shimmerLayout.visible()
                            binding.contentGroup.gone()
                        } else {
                            binding.shimmerLayout.stopShimmer()
                            binding.shimmerLayout.gone()
                            binding.contentGroup.visible()
                        }

                        state.error?.let { showError(it) }

                        bannerAdapter.submitList(state.bannerComics)
                        projectAdapter.submitList(state.projectComics)
                        popularAdapter.submitList(state.popularComics)
                        latestChapterAdapter.submitList(state.latestChapters)
                    }
                }

                // Search results
                launch {
                    vm.searchResults.collect { results ->
                        searchResultAdapter.submitList(results)
                        if (results.isEmpty() && binding.etSearch.text?.isNotBlank() == true) {
                            binding.tvNoResult.visible()
                        } else {
                            binding.tvNoResult.gone()
                        }
                    }
                }
            }
        }
    }

    private fun showSearchOverlay() {
        binding.searchResultContainer.visible()
        binding.contentGroup.gone()
        binding.btnClearSearch.visible()
    }

    private fun hideSearch() {
        binding.searchResultContainer.gone()
        binding.contentGroup.visible()
        binding.btnClearSearch.gone()
    }

    private fun navigateToDetail(title: String) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeToDetail(title)
        )
    }

    private fun navigateToReader(title: String, chapter: String, chapterUrl: String) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeToReader(title, chapter, chapterUrl)
        )
    }

    private fun showError(msg: String) {
        // Tampilkan Snackbar error
        com.google.android.material.snackbar.Snackbar.make(
            binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).setAction("Coba Lagi") { vm.refresh() }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
