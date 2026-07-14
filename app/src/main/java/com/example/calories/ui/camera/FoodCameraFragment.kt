package com.example.calories.ui.camera

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.calories.R
import com.example.calories.databinding.FragmentFoodCameraBinding
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
    private lateinit var cameraExecutor: ExecutorService

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

        binding.btnCloseCamera.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.fabCapture.setOnClickListener { capturePhoto() }
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
        viewLifecycleOwner.collectLatestStarted(viewModel.isAnalyzing) { analyzing ->
            binding.progressAnalyzing.visibility = if (analyzing) View.VISIBLE else View.GONE
            binding.fabCapture.isEnabled = !analyzing
            binding.tvCameraHint.text = getString(
                if (analyzing) R.string.analyzing_food else R.string.camera_hint,
            )
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
                is FoodCameraNavEvent.ToResult -> {
                    parentFragmentManager.beginTransaction()
                        .replace(
                            R.id.navHostFragment,
                            FoodAnalysisResultFragment.newInstance(event.result),
                        )
                        .addToBackStack("food_analysis")
                        .commit()
                }
            }
        }
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
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
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
                        val bytes = withContext(Dispatchers.IO) { photoFile.readBytes() }
                        viewModel.analyzeImage(bytes)
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
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        super.onDestroyView()
    }
}
