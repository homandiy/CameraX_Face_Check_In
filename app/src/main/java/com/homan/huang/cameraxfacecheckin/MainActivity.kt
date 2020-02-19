package com.homan.huang.cameraxfacecheckin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Matrix
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.camera.core.AspectRatio

import androidx.camera.core.AspectRatio.*
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.core.CameraSelector.LENS_FACING_FRONT

import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 111

// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE)

class MainActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        val mOrientationEventListener =  object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                // Monitors orientation values to determine the target rotation value
                val rotation = if (orientation >= 45 && orientation < 135) {
                    Surface.ROTATION_270
                } else if (orientation >= 135 && orientation < 225) {
                    Surface.ROTATION_180
                } else if (orientation >= 225 && orientation < 315) {
                    Surface.ROTATION_90
                } else {
                    Surface.ROTATION_0
                }

                //imageCapture?.setTargetRotation(rotation)
            }
        }
        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        } else {
            mOrientationEventListener.disable();
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

    }

    // Add this after onCreate
    private val executor = Executors.newSingleThreadExecutor()

    private val viewFinder by lazy { findViewById<PreviewView>(R.id.view_finder) }
    private val captureButton by lazy { findViewById<Button>(R.id.capture_Button) }

    private var camera: Camera? = null
    private var rotation: Int = 0

    // Camera Hardware and Use Cases
    private fun startCamera() {
        //setScreenRatio()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(LENS_FACING_BACK).build()

        // Bind the CameraProvider to the LifeCycleOwner
        cameraProviderFuture.addListener(Runnable {
            // CameraProvider
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = setPreview()

            // Must unbind the use-cases before rebinding them.
            cameraProvider.unbindAll()

            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                camera = cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview)
            } catch(exc: Exception) {
                lge("Use case binding failed: $exc")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun setPreview(): Preview {

        val previewWidth = viewFinder.getWidth()
        val previewHeight =  viewFinder.getHeight()
        rotation = viewFinder.display.rotation

        //size of the screen
        val screen = Size(previewWidth, previewHeight)

        // Create configuration object for the previewView use case
        // Remove builder from PreviewConfig
        val preview: Preview = Preview.Builder().apply {
            setTargetResolution(screen)
            setTargetName("Preview")
            setTargetRotation(rotation)
        }.build()

        // Every time the previewView is updated, recompute layout
        preview.setSurfaceProvider(viewFinder.previewSurfaceProvider)

        return preview
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCameraUi()
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private var displayId: Int = -1

    private fun updateCameraUi() {
        // To update the SurfaceTexture, we have to remove it and re-add it
        val parent = viewFinder.parent as ViewGroup
        parent.removeView(viewFinder)
        parent.addView(viewFinder, 0)

    }

    private var screenAspectRatio: Int = 0
    fun setScreenRatio() {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        lgd("Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return RATIO_4_3
        }
        return RATIO_16_9
    }



    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                shortMsg(this, "Permissions not granted by the user.")
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {

        private val TAG = "MYLOG " + MainActivity::class.java.simpleName
        fun lgd(s: String) {
            Log.d(TAG, s)
        }
        fun lge(s: String) {
            Log.e(TAG, s)
        }
        fun lgi(s: String) {
            Log.i(TAG, s)
        }

        fun shortMsg(context: Context, s: String) {
            Toast.makeText(context, s, Toast.LENGTH_SHORT).show()
        }

        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                .format(System.currentTimeMillis()) + extension)

        /** Use external media if it is available, our app's file directory otherwise */
        fun getOutputDirectory(context: Context, gallery: String): File {
            val appContext = context.applicationContext

            // If gallery does not exist, create a new one.
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, gallery).apply { mkdirs() } }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }


    }
}
