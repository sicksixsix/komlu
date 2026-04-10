package com.komiklu.app.ui.profile

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.komiklu.app.R
import com.komiklu.app.data.model.FavoriteEntity
import com.komiklu.app.data.model.ReadHistoryEntity
import com.komiklu.app.data.repository.AuthRepository
import com.komiklu.app.data.repository.ComicRepository
import com.komiklu.app.data.repository.HistoryRepository
import com.komiklu.app.databinding.*
import com.komiklu.app.ui.auth.AuthViewModel
import com.komiklu.app.ui.util.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── PROFILE VIEWMODEL ────────────────────────────────────────────────────────

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val comicRepo: ComicRepository,
    private val historyRepo: HistoryRepository
) : ViewModel() {

    val isLoggedIn = authRepo.isLoggedIn()
    val currentUser = authRepo.getCurrentUser()
    val favoriteCount = comicRepo.getFavoriteCount()
    val historyCount = historyRepo.getHistoryCount()

    fun logout() = authRepo.logout()
}

// ─── PROFILE FRAGMENT ─────────────────────────────────────────────────────────

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!
    private val vm: ProfileViewModel by viewModels()
    private val authVm: AuthViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentProfileBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!vm.isLoggedIn) {
            // Tampilkan prompt login
            b.groupLoggedIn.gone()
            b.groupGuest.visible()
            b.btnLoginGuest.setOnClickListener {
                findNavController().navigate(R.id.action_profile_to_login)
            }
            return
        }

        b.groupLoggedIn.visible()
        b.groupGuest.gone()

        val user = vm.currentUser
        b.tvUsername.text = user?.username ?: "Komiklu User"
        b.tvEmail.text = user?.email ?: ""
        user?.avatarUrl?.let { url ->
            b.ivAvatar.load(url) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_avatar_default)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.favoriteCount.collect { b.tvFavCount.text = it.toString() } }
                launch { vm.historyCount.collect { b.tvHistoryCount.text = it.toString() } }
            }
        }

        b.menuFavorites.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_favorites)
        }
        b.menuHistory.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_history)
        }
        b.menuReadMode.setOnClickListener {
            // Buka bottom sheet reader settings
            com.komiklu.app.ui.reader.ReaderSettingsBottomSheet
                .newInstance(com.komiklu.app.data.model.ReaderSettings()) {}
                .show(parentFragmentManager, "settings")
        }
        b.menuLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showLogoutDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Yakin ingin keluar dari akun Komiklu?")
            .setPositiveButton("Logout") { _, _ ->
                authVm.logout()
                findNavController().navigate(R.id.action_profile_to_login)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ─── FAVORITES FRAGMENT ───────────────────────────────────────────────────────

@AndroidEntryPoint
class FavoritesFragment : Fragment() {

    private var _b: FragmentFavoritesBinding? = null
    private val b get() = _b!!

    @Inject lateinit var comicRepo: ComicRepository

    private val adapter by lazy {
        FavoriteAdapter(
            onItemClick = { fav ->
                findNavController().navigate(
                    FavoritesFragmentDirections.actionFavoritesToDetail(fav.comicTitle)
                )
            },
            onRemove = { fav ->
                viewLifecycleOwner.lifecycleScope.launch {
                    comicRepo.removeFavorite(fav.comicTitle)
                }
            }
        )
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentFavoritesBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        b.rvFavorites.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FavoritesFragment.adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                comicRepo.getAllFavorites().collect { favs ->
                    adapter.submitList(favs)
                    if (favs.isEmpty()) {
                        b.emptyState.visible()
                        b.rvFavorites.gone()
                    } else {
                        b.emptyState.gone()
                        b.rvFavorites.visible()
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ─── READ HISTORY FRAGMENT ────────────────────────────────────────────────────

@AndroidEntryPoint
class ReadHistoryFragment : Fragment() {

    private var _b: FragmentHistoryBinding? = null
    private val b get() = _b!!

    @Inject lateinit var historyRepo: HistoryRepository

    private val adapter by lazy {
        HistoryAdapter { history ->
            findNavController().navigate(
                ReadHistoryFragmentDirections.actionHistoryToReader(
                    history.comicTitle,
                    history.chapter,
                    "" // chapterUrl perlu di-resolve ulang dari chapters JSON
                )
            )
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHistoryBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        b.rvHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ReadHistoryFragment.adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyRepo.getRecentHistory(50).collect { history ->
                    adapter.submitList(history)
                    if (history.isEmpty()) {
                        b.emptyState.visible()
                        b.rvHistory.gone()
                    } else {
                        b.emptyState.gone()
                        b.rvHistory.visible()
                    }
                }
            }
        }

        b.btnClearHistory.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Hapus Riwayat")
                .setMessage("Hapus semua riwayat baca?")
                .setPositiveButton("Hapus") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        historyRepo.pruneOldHistory(Long.MAX_VALUE)
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ─── FAVORITE ADAPTER ─────────────────────────────────────────────────────────

class FavoriteAdapter(
    private val onItemClick: (FavoriteEntity) -> Unit,
    private val onRemove: (FavoriteEntity) -> Unit
) : androidx.recyclerview.widget.ListAdapter<FavoriteEntity, FavoriteAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<FavoriteEntity>() {
        override fun areItemsTheSame(a: FavoriteEntity, b: FavoriteEntity) = a.comicTitle == b.comicTitle
        override fun areContentsTheSame(a: FavoriteEntity, b: FavoriteEntity) = a == b
    }
) {
    inner class VH(val b: ItemFavoriteBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
        fun bind(fav: FavoriteEntity) {
            b.ivCover.load(fav.coverUrl) { crossfade(true) }
            b.tvTitle.text = fav.comicTitle
            b.tvAuthor.text = fav.author
            b.tvRating.text = "⭐ ${fav.rating}"
            b.tvStatus.text = fav.status
            b.root.setOnClickListener { onItemClick(fav) }
            b.btnRemove.setOnClickListener { onRemove(fav) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}

// ─── HISTORY ADAPTER ──────────────────────────────────────────────────────────

class HistoryAdapter(
    private val onClick: (ReadHistoryEntity) -> Unit
) : androidx.recyclerview.widget.ListAdapter<ReadHistoryEntity, HistoryAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ReadHistoryEntity>() {
        override fun areItemsTheSame(a: ReadHistoryEntity, b: ReadHistoryEntity) = a.id == b.id
        override fun areContentsTheSame(a: ReadHistoryEntity, b: ReadHistoryEntity) = a == b
    }
) {
    inner class VH(val b: ItemHistoryBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
        fun bind(h: ReadHistoryEntity) {
            b.ivCover.load(h.coverUrl) { crossfade(true) }
            b.tvTitle.text = h.comicTitle
            b.tvChapter.text = h.chapter
            b.tvProgress.text = if (h.totalPages > 0) "Hal ${h.lastPage}/${h.totalPages}" else ""
            b.progressBar.apply {
                max = h.totalPages.coerceAtLeast(1)
                progress = h.lastPage
            }
            val timeAgo = android.text.format.DateUtils.getRelativeTimeSpanString(
                h.readAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
            )
            b.tvTime.text = timeAgo
            b.root.setOnClickListener { onClick(h) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
