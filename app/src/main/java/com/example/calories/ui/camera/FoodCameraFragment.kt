package com.example.calories.ui.camera

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.calories.R
import com.example.calories.ads.ScanInterstitialAdHelper
import com.example.calories.databinding.FragmentFoodCameraBinding
import com.example.calories.model.FoodAnalysisResult
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.util.MediaPermissions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class FoodCameraFragment : BaseFragment<FragmentFoodCameraBinding>() {

    private val viewModel: FoodCameraViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var cameraExecutor: ExecutorService
    private var scanInterstitialAdHelper: ScanInterstitialAdHelper? = null
    private var pendingResultNavigation: Pair<String, FoodAnalysisResult>? = null
    private var cameraPermissionDeniedOnce = false
    private var galleryPermissionDeniedOnce = false

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(::handleGalleryImage)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            handleCameraPermissionDenied()
        }
    }

    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchGalleryPicker()
        } else {
            handleGalleryPermissionDenied()
        }
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentFoodCameraBinding = FragmentFoodCameraBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        scanInterstitialAdHelper = ScanInterstitialAdHelper(requireActivity())

        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { headerView, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            headerView.setPadding(0, statusBarHeight, 0, 0)
            insets
        }
        binding.headerContainer.requestApplyInsets()

        binding.btnCloseCamera.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.fabCapture.setOnClickListener { capturePhoto() }
        binding.fabGallery.setOnClickListener { requestGalleryAccess() }
        binding.fabFlipCamera.setOnClickListener { flipCamera() }
        observeViewModel()
        ensureCameraPermission()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            val busy = state.isAnalyzing
            binding.progressAnalyzing.isVisible = busy
            binding.fabCapture.isEnabled = !busy
            binding.fabGallery.isEnabled = !busy
            binding.fabFlipCamera.isEnabled = !busy
            binding.tvCameraHint.text = if (busy) {
                getString(R.string.analyzing_food)
            } else {
                getString(R.string.camera_hint)
            }
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.events) { event ->
            showUiEvent(event)
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.navEvents) { event ->
            when (event) {
                is FoodCameraNavEvent.AnalysisReady -> showInterstitialThenNavigate(event)
            }
        }
    }

    private fun showUiEvent(event: UiEvent) {
        val message = when (event) {
            is UiEvent.Message -> event.text
            is UiEvent.MessageRes -> getString(event.resId)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showInterstitialThenNavigate(event: FoodCameraNavEvent.AnalysisReady) {
        if (pendingResultNavigation != null) return

        pendingResultNavigation = event.imagePath to event.result

        // Ẩn UI Header đi để tránh bị soi qua AdMob Activity
        binding.headerContainer.isVisible = false

        val helper = scanInterstitialAdHelper ?: return navigateToFoodDetail()

        helper.loadAndShow(
            onAdDismissed = ::navigateToFoodDetail,
            onAdUnavailable = ::navigateToFoodDetail,
        )
    }

    private fun navigateToFoodDetail() {
        val pending = pendingResultNavigation ?: return
        pendingResultNavigation = null
        viewModel.onAnalysisFlowComplete()

        binding.headerContainer.isVisible = true

        parentFragmentManager.beginTransaction()
            .replace(
                R.id.navHostFragment,
                FoodAnalysisResultFragment.newInstance(
                    imagePath = pending.first,
                    analysisResult = pending.second,
                ),
            )
            .addToBackStack("food_analysis")
            .commit()
    }

    private fun ensureCameraPermission() {
        when {
            hasCameraPermission() -> startCamera()
            shouldShowCameraRationale() -> showCameraRationaleDialog()
            else -> cameraPermissionLauncher.launch(MediaPermissions.CAMERA)
        }
    }

    private fun handleCameraPermissionDenied() {
        when {
            shouldShowCameraRationale() -> showCameraRationaleDialog()
            cameraPermissionDeniedOnce -> showCameraSettingsDialog()
            else -> {
                cameraPermissionDeniedOnce = true
                showCameraRationaleDialog()
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            MediaPermissions.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowCameraRationale(): Boolean {
        return shouldShowRequestPermissionRationale(MediaPermissions.CAMERA)
    }

    private fun showCameraRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_rationale)
            .setPositiveButton(R.string.allow) { _, _ ->
                cameraPermissionLauncher.launch(MediaPermissions.CAMERA)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                parentFragmentManager.popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    private fun showCameraSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_settings_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                parentFragmentManager.popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestGalleryAccess() {
        val permission = MediaPermissions.galleryPermission
        if (permission == null || hasGalleryPermission()) {
            launchGalleryPicker()
            return
        }

        when {
            shouldShowGalleryRationale(permission) -> showGalleryRationaleDialog(permission)
            galleryPermissionDeniedOnce -> showGallerySettingsDialog()
            else -> galleryPermissionLauncher.launch(permission)
        }
    }

    private fun handleGalleryPermissionDenied() {
        val permission = MediaPermissions.galleryPermission ?: return
        when {
            shouldShowGalleryRationale(permission) -> showGalleryRationaleDialog(permission)
            galleryPermissionDeniedOnce -> showGallerySettingsDialog()
            else -> {
                galleryPermissionDeniedOnce = true
                showGalleryRationaleDialog(permission)
            }
        }
    }

    private fun hasGalleryPermission(): Boolean {
        val permission = MediaPermissions.galleryPermission ?: return true
        return ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowGalleryRationale(permission: String): Boolean {
        return shouldShowRequestPermissionRationale(permission)
    }

    private fun showGalleryRationaleDialog(permission: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.gallery_permission_title)
            .setMessage(R.string.gallery_permission_rationale)
            .setPositiveButton(R.string.allow) { _, _ ->
                galleryPermissionLauncher.launch(permission)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showGallerySettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.gallery_permission_title)
            .setMessage(R.string.gallery_permission_settings_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null),
        )
        startActivity(intent)
    }

    private fun launchGalleryPicker() {
        galleryLauncher.launch("image/*")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = binding.previewView.surfaceProvider
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                    )
                } catch (e: Exception) {
                    Snackbar.make(
                        binding.root,
                        e.message ?: getString(R.string.error_analyze_food),
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            },
            ContextCompat.getMainExecutor(requireContext()),
        )
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun handleGalleryImage(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val imageFile = withContext(Dispatchers.IO) {
                copyUriToCache(uri)
            }
            if (imageFile != null) {
                viewModel.openAnalysis(imageFile.absolutePath)
            } else {
                Snackbar.make(
                    binding.root,
                    R.string.error_load_gallery_image,
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val cacheFile =
                File(requireContext().cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            cacheFile
        } catch (_: Exception) {
            null
        }
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        val photoFile = File(requireContext().cacheDir, "meal_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        viewModel.openAnalysis(photoFile.absolutePath)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        viewModel.onCaptureFailed(
                            exception.message ?: getString(R.string.error_analyze_food),
                        )
                    }
                }
            },
        )
    }

    override fun onDestroyView() {
        scanInterstitialAdHelper?.destroy()
        scanInterstitialAdHelper = null
        pendingResultNavigation = null
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        super.onDestroyView()
    }
}