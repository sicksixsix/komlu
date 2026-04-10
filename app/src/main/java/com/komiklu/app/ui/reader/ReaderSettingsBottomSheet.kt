package com.komiklu.app.ui.reader

import android.os.Bundle
import android.view.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.komiklu.app.data.model.ImageQuality
import com.komiklu.app.data.model.ReadMode
import com.komiklu.app.data.model.ReaderSettings
import com.komiklu.app.databinding.BottomSheetReaderSettingsBinding

class ReaderSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _b: BottomSheetReaderSettingsBinding? = null
    private val b get() = _b!!
    private var onSettingsChanged: ((ReaderSettings) -> Unit)? = null
    private var currentSettings = ReaderSettings()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = BottomSheetReaderSettingsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindCurrentSettings()
        setupListeners()
    }

    private fun bindCurrentSettings() {
        // Mode Baca
        when (currentSettings.readMode) {
            ReadMode.VERTICAL   -> b.rgReadMode.check(b.rbVertical.id)
            ReadMode.HORIZONTAL -> b.rgReadMode.check(b.rbHorizontal.id)
        }

        // Kualitas gambar
        when (currentSettings.imageQuality) {
            ImageQuality.LOW    -> b.rgQuality.check(b.rbLow.id)
            ImageQuality.MEDIUM -> b.rgQuality.check(b.rbMedium.id)
            ImageQuality.HIGH   -> b.rgQuality.check(b.rbHigh.id)
        }

        // Auto next chapter
        b.switchAutoNext.isChecked = currentSettings.autoNextChapter

        // Keep screen on
        b.switchKeepScreen.isChecked = currentSettings.keepScreenOn
    }

    private fun setupListeners() {
        b.rgReadMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                b.rbVertical.id   -> ReadMode.VERTICAL
                b.rbHorizontal.id -> ReadMode.HORIZONTAL
                else              -> ReadMode.VERTICAL
            }
            currentSettings = currentSettings.copy(readMode = mode)
            emitChange()
        }

        b.rgQuality.setOnCheckedChangeListener { _, checkedId ->
            val quality = when (checkedId) {
                b.rbLow.id    -> ImageQuality.LOW
                b.rbMedium.id -> ImageQuality.MEDIUM
                b.rbHigh.id   -> ImageQuality.HIGH
                else          -> ImageQuality.HIGH
            }
            currentSettings = currentSettings.copy(imageQuality = quality)
            emitChange()
        }

        b.switchAutoNext.setOnCheckedChangeListener { _, checked ->
            currentSettings = currentSettings.copy(autoNextChapter = checked)
            emitChange()
        }

        b.switchKeepScreen.setOnCheckedChangeListener { _, checked ->
            currentSettings = currentSettings.copy(keepScreenOn = checked)
            emitChange()
        }

        b.btnClose.setOnClickListener { dismiss() }
    }

    private fun emitChange() = onSettingsChanged?.invoke(currentSettings)

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    companion object {
        fun newInstance(
            settings: ReaderSettings,
            onChanged: (ReaderSettings) -> Unit
        ) = ReaderSettingsBottomSheet().apply {
            currentSettings = settings
            onSettingsChanged = onChanged
        }
    }
}
