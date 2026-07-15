package com.example.calories.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.calories.R
import com.example.calories.databinding.ActivityMainBinding
import com.example.calories.ui.auth.LoginActivity
import com.example.calories.ui.camera.FoodCameraFragment
import com.example.calories.ui.explore.ExploreFragment
import com.example.calories.ui.home.HomeFragment
import com.example.calories.ui.profile.ProfileFragment
import com.example.calories.ui.weight.WeightTrackingFragment
import com.google.android.material.navigation.NavigationBarView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!viewModel.isLoggedIn()) {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupBottomNavigation()
        alignCenterCameraTab()

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun setupBottomNavigation() {
        binding.fabCameraAi.setOnClickListener { openCameraAi() }

        binding.bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_camera -> {
                    openCameraAi()
                    false
                }
                R.id.nav_home,
                R.id.navigation_explore,
                R.id.nav_progress,
                R.id.nav_profile,
                    -> {
                    supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    openFragment(fragmentFor(item.itemId))
                    true
                }
                else -> false
            }
        }
    }

    private fun alignCenterCameraTab() {
        binding.bottomNav.post {
            val menuView = binding.bottomNav.getChildAt(0) as? ViewGroup
            menuView?.let { group ->
                val cameraTabView = group.getChildAt(2) as? ViewGroup
                cameraTabView?.let { tab ->
                    tab.translationY = 0f
                    tab.setPadding(0, 0, 0, 0)

                    for (i in 0 until tab.childCount) {
                        val child = tab.getChildAt(i)
                        child.translationY = 0f

                        val params = child.layoutParams as? ViewGroup.MarginLayoutParams
                        params?.let { marginParams ->
                            marginParams.topMargin = 0
                            marginParams.bottomMargin = 0
                            child.layoutParams = marginParams
                        }
                    }
                }
            }
        }
    }

    private fun fragmentFor(itemId: Int): Fragment = when (itemId) {
        R.id.navigation_explore -> ExploreFragment()
        R.id.nav_progress -> WeightTrackingFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> HomeFragment()
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.navHostFragment, fragment)
            .commit()
    }

    private fun openCameraAi() {
        val existing = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        if (existing is FoodCameraFragment) return

        supportFragmentManager.beginTransaction()
            .replace(R.id.navHostFragment, FoodCameraFragment())
            .addToBackStack(CAMERA_BACK_STACK)
            .commit()
    }

    private companion object {
        const val CAMERA_BACK_STACK = "camera_ai"
    }
}