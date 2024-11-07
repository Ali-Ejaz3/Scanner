package com.example.movieapp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import android.Manifest
import android.content.Intent
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.example.movieapp.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    private lateinit var cameraExecutor :ExecutorService
    private lateinit var barCodeScanner:BarcodeScanner
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        barCodeScanner = BarcodeScanning.getClient()



        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                binding.resultText.text = "Camera Permission is Required"
            }
        }

// Launch the permission request
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)

    }

    private fun startCamera() {
        val preview = binding.preView
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val screenSize = Size(1280,720)
        val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(screenSize,ResolutionStrategy.FALLBACK_RULE_NONE)
        ).build()
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setResolutionSelector(resolutionSelector)
                .build()
                .also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }
            val imageanalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor,{imageProxy->
                        processImageProxy(imageProxy)

                    })
                }
            val cameraSelector =CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.bindToLifecycle(this,cameraSelector,preview,imageanalyzer)
        },ContextCompat.getMainExecutor(this))

    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            barCodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        handleBarCode(barcode)
                        // Handle each barcode here (e.g., display barcode value)
                        binding.resultText.text = barcode.rawValue
                    }
                }
                .addOnFailureListener {
                    binding.resultText.text = "Failed to scan QR code"
                }
                .addOnCompleteListener {
                    imageProxy.close() // Ensure imageProxy is closed only once
                }
        } else {
            imageProxy.close()
        }
    }
    private fun handleBarCode(barcode:Barcode){
        val url = barcode.url?.url ?:barcode.displayValue
        if (url != null){
            binding.resultText.text = url
            binding.resultText.setOnClickListener {
                val intent = Intent(this,WebViewActivity::class.java)
                intent.putExtra("url",url)
                startActivity(intent)
            }
        }else{
            binding.resultText.text = "No QR code Detected"
        }

    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

}