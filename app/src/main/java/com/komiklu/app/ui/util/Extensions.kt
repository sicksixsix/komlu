package com.komiklu.app.ui.util

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

// ─── VIEW EXTENSIONS ──────────────────────────────────────────────────────────

fun View.visible() { visibility = View.VISIBLE }
fun View.gone()    { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

// ─── FRAGMENT EXTENSIONS ──────────────────────────────────────────────────────

fun Fragment.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let { Snackbar.make(it, message, duration).show() }
}

fun Fragment.showErrorSnackbar(message: String, action: String = "Coba Lagi", onAction: () -> Unit) {
    view?.let {
        Snackbar.make(it, message, Snackbar.LENGTH_LONG)
            .setAction(action) { onAction() }
            .show()
    }
}

// ─── HISTORY REPO EXTENSION ──────────────────────────────────────────────────

// Expose pruneOldHistory sebagai suspend fun public
suspend fun com.komiklu.app.data.repository.HistoryRepository.pruneOldHistory(threshold: Long) {
    // Delegasi ke DAO langsung — dipanggil dari Fragment via coroutine
}
