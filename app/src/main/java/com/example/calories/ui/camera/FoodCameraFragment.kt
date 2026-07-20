package com.example.calories.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
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

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(::handleGalleryImage)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), R.string.camera_permission_required, Toast.LENGTH_LONG)
                .show()
            parentFragmentManager.popBackStack()
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

        binding.btnCloseCamera.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.fabCapture.setOnClickListener { capturePhoto() }
        binding.fabGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.fabFlipCamera.setOnClickListener { flipCamera() }
        observeViewModel()

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED -> startCamera()
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(requireContext(), event.text, Toast.LENGTH_LONG).show()
                is UiEvent.MessageRes ->
                    Toast.makeText(requireContext(), event.resId, Toast.LENGTH_LONG).show()
            }
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.navEvents) { event ->
            when (event) {
                is FoodCameraNavEvent.AnalysisReady -> showInterstitialThenNavigate(event)
            }
        }
    }

    /**
     * Analysis is complete — load the interstitial first, show it only on [onAdLoaded],
     * and navigate to the food detail screen from [onAdDismissedFullScreenContent].
     */
    private fun showInterstitialThenNavigate(event: FoodCameraNavEvent.AnalysisReady) {
        if (pendingResultNavigation != null) return

        pendingResultNavigation = event.imagePath to event.result
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
                    Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
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
                Toast.makeText(
                    requireContext(),
                    R.string.error_load_gallery_image,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val cacheFile = File(requireContext().cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
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
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.openAnalysis(photoFile.absolutePath)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.onCaptureFailed(
                        exception.message ?: getString(R.string.error_analyze_food),
                    )
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
