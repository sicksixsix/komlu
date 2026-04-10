package com.komiklu.app

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.komiklu.app.databinding.ActivityMainBinding
import com.komiklu.app.ui.util.gone
import com.komiklu.app.ui.util.visible
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// ─── APPLICATION ──────────────────────────────────────────────────────────────

@HiltAndroidApp
class KomikluApp : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
        // Prune expired Room cache on startup
        // (dilakukan di background thread oleh WorkManager / coroutine)
    }
}

// ─── MAIN ACTIVITY ────────────────────────────────────────────────────────────

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** Fragment yang menyembunyikan bottom navigation */
    private val hideNavDestinations = setOf(
        R.id.detailFragment,
        R.id.loginFragment,
        R.id.registerFragment,
        R.id.historyFragment,
        R.id.favoritesFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen
        val splashScreen = androidx.core.splashscreen.SplashScreen.installSplashScreen(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Bottom nav setup
        binding.bottomNav.setupWithNavController(navController)

        // Sembunyikan bottom nav di halaman tertentu
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in hideNavDestinations) {
                binding.bottomNav.gone()
            } else {
                binding.bottomNav.visible()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
