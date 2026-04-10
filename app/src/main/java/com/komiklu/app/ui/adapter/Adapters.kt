package com.komiklu.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.komiklu.app.data.model.Chapter
import com.komiklu.app.data.model.Comic
import com.komiklu.app.data.model.LatestChapter
import com.komiklu.app.databinding.*
import android.text.format.DateUtils

// ─── MANGA HORIZONTAL ADAPTER (Home cards) ───────────────────────────────────

class MangaHorizontalAdapter(
    private val onClick: (Comic) -> Unit
) : ListAdapter<Comic, MangaHorizontalAdapter.VH>(ComicDiff()) {

    inner class VH(val b: ItemMangaCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(comic: Comic) {
            b.ivCover.load(comic.coverUrl) {
                crossfade(true)
                placeholder(com.komiklu.app.R.drawable.placeholder_cover)
            }
            b.tvTitle.text = comic.title
            b.tvChapter.text = "⭐ ${comic.rating}"
            b.badgeNew.visibility = if (comic.isProject)
                android.view.View.VISIBLE else android.view.View.GONE
            b.root.setOnClickListener { onClick(comic) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VH(ItemMangaCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── LATEST CHAPTER ADAPTER (Home list) ──────────────────────────────────────

class LatestChapterAdapter(
    private val onComicClick: (LatestChapter) -> Unit,
    private val onChapterClick: (LatestChapter) -> Unit
) : ListAdapter<LatestChapter, LatestChapterAdapter.VH>(LatestChapterDiff()) {

    inner class VH(val b: ItemLatestChapterBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: LatestChapter) {
            b.ivCover.load(item.coverUrl) { crossfade(true) }
            b.tvTitle.text = item.comicTitle
            b.tvChapter.text = item.chapter
            val timeAgo = DateUtils.getRelativeTimeSpanString(
                item.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            b.tvTime.text = timeAgo.toString()
            b.badgeNew.visibility = if (item.isNew)
                android.view.View.VISIBLE else android.view.View.GONE
            b.ivCover.setOnClickListener { onComicClick(item) }
            b.tvChapter.setOnClickListener { onChapterClick(item) }
            b.root.setOnClickListener { onChapterClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VH(ItemLatestChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── SEARCH RESULT ADAPTER ────────────────────────────────────────────────────

class SearchResultAdapter(
    private val onClick: (Comic) -> Unit
) : ListAdapter<Comic, SearchResultAdapter.VH>(ComicDiff()) {

    inner class VH(val b: ItemSearchResultBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(comic: Comic) {
            b.ivCover.load(comic.coverUrl) { crossfade(true) }
            b.tvTitle.text = comic.title
            b.tvAuthor.text = comic.author
            b.tvGenres.text = comic.genres.take(2).joinToString(", ")
            b.tvRating.text = "⭐ ${comic.rating}"
            b.root.setOnClickListener { onClick(comic) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VH(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── CHAPTER LIST ADAPTER (Detail page) ──────────────────────────────────────

class ChapterListAdapter(
    private val onClick: (Chapter) -> Unit
) : ListAdapter<Chapter, ChapterListAdapter.VH>(ChapterDiff()) {

    inner class VH(val b: ItemChapterRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(ch: Chapter) {
            b.tvChapterName.text = ch.chapter
            b.root.setOnClickListener { onClick(ch) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VH(ItemChapterRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── SERIES LIST ADAPTER (All Series / Project) ───────────────────────────────

class SeriesListAdapter(
    private val onClick: (Comic) -> Unit
) : ListAdapter<Comic, SeriesListAdapter.VH>(ComicDiff()) {

    inner class VH(val b: ItemSeriesBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(comic: Comic) {
            b.ivCover.load(comic.coverUrl) { crossfade(true) }
            b.tvTitle.text = comic.title
            b.tvAuthor.text = comic.author
            b.tvRating.text = "⭐ ${comic.rating}"
            b.tvStatus.text = comic.status
            b.tvViews.text = formatViews(comic.viewCount)
            b.tvGenres.text = comic.genres.take(3).joinToString(" · ")
            b.root.setOnClickListener { onClick(comic) }
        }

        private fun formatViews(count: Int) = when {
            count >= 1_000_000 -> "${count / 1_000_000}M views"
            count >= 1_000     -> "${count / 1_000}K views"
            else               -> "$count views"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VH(ItemSeriesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── BANNER PAGER ADAPTER ────────────────────────────────────────────────────

class BannerPagerAdapter(
    private val onClick: (Comic) -> Unit
) : ListAdapter<Comic, BannerPagerAdapter.VH>(ComicDiff()) {

    inner class VH(val b: ItemBannerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(comic: Comic) {
            b.ivBannerBg.load(comic.coverUrl) { crossfade(true) }
            b.tvBannerTitle.text = comic.title
            b.tvBannerAuthor.text = comic.author
            b.tvBannerRating.text = "⭐ ${comic.rating}"
            if (comic.isProject) b.badgeProject.visibility = android.view.View.VISIBLE
            b.root.setOnClickListener { onClick(comic) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VH(ItemBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── GENRE CHIP ADAPTER ───────────────────────────────────────────────────────

class GenreChipAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<String, GenreChipAdapter.VH>(
    object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(a: String, b: String) = a == b
        override fun areContentsTheSame(a: String, b: String) = a == b
    }
) {
    inner class VH(val b: ItemGenreChipBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(genre: String) {
            b.tvGenre.text = genre
            b.root.setOnClickListener { onClick(genre) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VH(ItemGenreChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── DIFF CALLBACKS ───────────────────────────────────────────────────────────

class ComicDiff : DiffUtil.ItemCallback<Comic>() {
    override fun areItemsTheSame(a: Comic, b: Comic) = a.title == b.title
    override fun areContentsTheSame(a: Comic, b: Comic) = a == b
}

class ChapterDiff : DiffUtil.ItemCallback<Chapter>() {
    override fun areItemsTheSame(a: Chapter, b: Chapter) = a.chapter == b.chapter
    override fun areContentsTheSame(a: Chapter, b: Chapter) = a == b
}

class LatestChapterDiff : DiffUtil.ItemCallback<LatestChapter>() {
    override fun areItemsTheSame(a: LatestChapter, b: LatestChapter) =
        a.comicTitle == b.comicTitle && a.chapter == b.chapter
    override fun areContentsTheSame(a: LatestChapter, b: LatestChapter) = a == b
}
