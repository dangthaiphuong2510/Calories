package com.example.calories.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.calories.ui.common.BaseActivity
import com.example.calories.ui.common.EdgeToEdgeHost
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.calories.R
import com.example.calories.data.preferences.NotificationPreferences
import com.example.calories.databinding.ActivityMainBinding
import com.example.calories.ui.auth.LoginActivity
import com.example.calories.ui.camera.FoodCameraFragment
import com.example.calories.ui.explore.ExploreFragment
import com.example.calories.ui.home.HomeFragment
import com.example.calories.ui.profile.ProfileFragment
import com.example.calories.ui.weight.WeightTrackingFragment
import com.google.android.material.navigation.NavigationBarView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts bottom-nav tabs with show/hide (not replace) so each tab's ViewModel stays
 * alive and keeps collecting [com.example.calories.data.preferences.AppPreferences]
 * StateFlows — unit/language changes on Profile re-emit Home/Analytics uiState immediately.
 */
@AndroidEntryPoint
class MainActivity : BaseActivity(), EdgeToEdgeHost {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var notificationPreferences: NotificationPreferences

    private var activeTabTag: String = TAG_HOME

    private var suppressBottomNavSelection = false

    /** Cached on first inset pass so post-ad inset recalculations cannot inflate padding. */
    private var cachedStatusBarTopInset: Int? = null
    private var cachedBottomNavPadding: Int? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* User can grant later from notification settings */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        lifecycleScope.launch {
            if (!viewModel.shouldAllowAccess()) {
                startActivity(
                    Intent(this@MainActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    },
                )
                finish()
                return@launch
            }
            initializeMainUi(savedInstanceState)
        }
    }

    private fun initializeMainUi(savedInstanceState: Bundle?) {
        val surfaceColor = ContextCompat.getColor(this, R.color.surface)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT, Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(surfaceColor, surfaceColor),
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val topInset = cacheStatusBarTopInset(statusBars.top)
            binding.navHostFragment.setPadding(0, topInset, 0, 0)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomPadding = cacheBottomNavPadding(systemBars.bottom)
            view.setPadding(0, 0, 0, bottomPadding)
            insets
        }

        activeTabTag = savedInstanceState?.getString(STATE_ACTIVE_TAB) ?: TAG_HOME

        setupBottomNavigation()
        alignCenterCameraTab()
        requestIntakeWarningPermissionIfNeeded()

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                // Camera popped — reveal the active tab again.
                showTab(tag = activeTabTag, updateBottomNav = false)
            }
        }

        if (savedInstanceState == null) {
            showTab(TAG_HOME, updateBottomNav = false)
            selectBottomNavItem(TAG_HOME)
        } else {
            showTab(activeTabTag, updateBottomNav = false)
            selectBottomNavItem(activeTabTag)
        }

        handleWidgetIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                com.example.calories.widget.CaloriesHomeWidgetProvider.EXTRA_OPEN_PROGRESS,
                false,
            ) == true
        ) {
            intent.removeExtra(com.example.calories.widget.CaloriesHomeWidgetProvider.EXTRA_OPEN_PROGRESS)
            openProgressTab()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ACTIVE_TAB, activeTabTag)
    }

    private fun setupBottomNavigation() {
        binding.fabCameraAi.setOnClickListener { openCameraAi() }

        binding.bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (suppressBottomNavSelection) return@setOnItemSelectedListener true

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
                    val tag = tagFor(item.itemId)
                    // User tap already updated selection; skip if we're already on this tab.
                    if (tag == activeTabTag && isTabVisible(tag)) {
                        return@setOnItemSelectedListener true
                    }
                    supportFragmentManager.popBackStack(
                        CAMERA_BACK_STACK,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE,
                    )
                    // Do not update bottom nav here — the listener fired because the user selected it.
                    showTab(tag, updateBottomNav = false)
                    true
                }
                else -> false
            }
        }
    }

    fun openProgressTab() {
        supportFragmentManager.popBackStack(
            CAMERA_BACK_STACK,
            FragmentManager.POP_BACK_STACK_INCLUSIVE,
        )
        showTab(TAG_PROGRESS, updateBottomNav = true)
    }

    private fun showTab(tag: String, updateBottomNav: Boolean = true) {
        if (tag == activeTabTag && isTabVisible(tag)) {
            if (updateBottomNav) selectBottomNavItem(tag)
            return
        }

        activeTabTag = tag
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()

        TAB_TAGS.forEach { tabTag ->
            fm.findFragmentByTag(tabTag)?.let { fragment ->
                if (tabTag == tag) {
                    transaction.show(fragment)
                    transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
                } else {
                    transaction.hide(fragment)
                    transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                }
            }
        }

        // Hide camera overlay if it is somehow still present without a back stack.
        fm.findFragmentByTag(TAG_CAMERA)?.let { transaction.hide(it) }

        if (fm.findFragmentByTag(tag) == null) {
            transaction.add(R.id.navHostFragment, createTabFragment(tag), tag)
        }

        transaction.commit()

        if (updateBottomNav) {
            selectBottomNavItem(tag)
        }
    }

    private fun selectBottomNavItem(tag: String) {
        val menuId = menuIdForTag(tag)
        if (binding.bottomNav.selectedItemId == menuId) return
        suppressBottomNavSelection = true
        try {
            binding.bottomNav.selectedItemId = menuId
        } finally {
            suppressBottomNavSelection = false
        }
    }

    private fun isTabVisible(tag: String): Boolean =
        supportFragmentManager.findFragmentByTag(tag)?.isVisible == true

    private fun createTabFragment(tag: String): Fragment = when (tag) {
        TAG_EXPLORE -> ExploreFragment()
        TAG_PROGRESS -> WeightTrackingFragment()
        TAG_PROFILE -> ProfileFragment()
        else -> HomeFragment()
    }

    private fun tagFor(menuItemId: Int): String = when (menuItemId) {
        R.id.navigation_explore -> TAG_EXPLORE
        R.id.nav_progress -> TAG_PROGRESS
        R.id.nav_profile -> TAG_PROFILE
        else -> TAG_HOME
    }

    private fun menuIdForTag(tag: String): Int = when (tag) {
        TAG_EXPLORE -> R.id.navigation_explore
        TAG_PROGRESS -> R.id.nav_progress
        TAG_PROFILE -> R.id.nav_profile
        else -> R.id.nav_home
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

    private fun openCameraAi() {
        val fm = supportFragmentManager
        if (fm.findFragmentByTag(TAG_CAMERA) != null && fm.backStackEntryCount > 0) return

        val transaction = fm.beginTransaction()
        TAB_TAGS.forEach { tabTag ->
            fm.findFragmentByTag(tabTag)?.let { fragment ->
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        transaction
            .add(R.id.navHostFragment, FoodCameraFragment(), TAG_CAMERA)
            .addToBackStack(CAMERA_BACK_STACK)
            .commit()
    }

    fun navigateToHome() {
        supportFragmentManager.popBackStack(
            CAMERA_BACK_STACK,
            FragmentManager.POP_BACK_STACK_INCLUSIVE,
        )
        supportFragmentManager.executePendingTransactions()
        showTab(TAG_HOME)
    }

    private fun requestIntakeWarningPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!notificationPreferences.intakeWarningsEnabled.value) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun restoreEdgeToEdgeAfterFullscreenAd() {
        if (!::binding.isInitialized) return
        val surfaceColor = ContextCompat.getColor(this, R.color.surface)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(surfaceColor, surfaceColor),
        )
        applyCachedWindowInsets()
    }

    private fun cacheStatusBarTopInset(currentTop: Int): Int {
        cachedStatusBarTopInset?.let { return it }
        if (currentTop > 0) {
            cachedStatusBarTopInset = currentTop
        }
        return cachedStatusBarTopInset ?: currentTop
    }

    private fun cacheBottomNavPadding(currentBottom: Int): Int {
        cachedBottomNavPadding?.let { return it }
        val padding = (currentBottom * 0.6f).toInt()
        cachedBottomNavPadding = padding
        return padding
    }

    private fun applyCachedWindowInsets() {
        cachedStatusBarTopInset?.let { top ->
            binding.navHostFragment.setPadding(0, top, 0, 0)
        }
        cachedBottomNavPadding?.let { bottom ->
            binding.bottomNav.setPadding(0, 0, 0, bottom)
        }
    }

    private companion object {
        const val CAMERA_BACK_STACK = "camera_ai"
        const val STATE_ACTIVE_TAB = "state_active_tab"
        const val TAG_HOME = "tab_home"
        const val TAG_EXPLORE = "tab_explore"
        const val TAG_PROGRESS = "tab_progress"
        const val TAG_PROFILE = "tab_profile"
        const val TAG_CAMERA = "tab_camera"
        val TAB_TAGS = listOf(TAG_HOME, TAG_EXPLORE, TAG_PROGRESS, TAG_PROFILE)
    }
}