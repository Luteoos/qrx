package io.github.luteoos.qrx

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toRectF
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.luteoos.qrx.utils.BarcodeWrapper
import io.github.luteoos.qrx.utils.IconAnchor
import io.github.luteoos.qrx.utils.repeatOnLifecycle
import io.github.luteoos.qrx.utils.useWithTryCatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.concurrent.Executors

/**
 * Layout with integrated CameraX and MLKit capabilities
 *
 * @see onPermission
 * @see initialize
 *
 * @author [Luteoos](https://github.com/Luteoos)
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class QrXScanner : FrameLayout {
    /**
     * Change how icon is attached to detected code
     *
     * [IconAnchor.PIN] anchors to bottom / horizontal center
     *
     * [IconAnchor.ICON] anchors to center
     */
    var iconAnchor: IconAnchor = IconAnchor.ICON

    /**
     * [Bitmap] to be drawn over detected code,
     * no resizing will be done to this bitmap
     */
    var iconBitmap: Bitmap? =
        ResourcesCompat.getDrawable(resources, R.drawable.ic_outline_touch, context.theme)
            ?.toBitmap(config = Bitmap.Config.ARGB_8888)
            ?.scale(52, 52)

    /**
     * [Paint] used to draw contour around detected code
     */
    var contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            resources.getColor(R.color.colorAccentLight, context.theme)
        else
            resources.getColor(R.color.colorAccentLight)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
        alpha = 255
        pathEffect = DashPathEffect(floatArrayOf(30f, 10f), 0f)
        setShadowLayer(7f, 3f, 3f, Color.BLACK)
    }

    /**
     * [Button] displayed when permission is denied
     *
     * accessible after view is inflated
     */
    lateinit var permissionDeniedButton: Button
    /**
     * [TextView] displayed when permission is denied
     *
     * accessible after view is inflated
     */
    lateinit var permissionDeniedText: TextView

    /**
     * when not null it gets invoked with every [CodeAnalyzer] image analysis
     *
     */
    var onBarcodeScannedListener: ((List<Barcode>) -> Unit)? = null
    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraOverlay: ImageView
    private lateinit var cameraTorchBtn: ToggleButton
    private lateinit var permissionDeniedLayout: View
    private lateinit var lifecycleOwner: LifecycleOwner
    private var camera: Camera? = null
    private var onBarcodeClickListener: ((Barcode) -> Unit)? = null
    private val paintDrawable = Paint().apply {
        alpha = 255
        isAntiAlias = false
    }
    private var barcodes = LimitedLinkedList<BarcodeWrapper>(resources.getInteger(R.integer.qrx_analysis_list_buffer))
    private val uiRefreshFlow = flow {
        while (true) {
            delay(resources.getInteger(R.integer.qrx_analysis_ui_refresh_delay).toLong())
            emit(Unit)
        }
    }
    constructor(context: Context) : super(context)
    @SuppressLint("Recycle")
    constructor(
        context: Context,
        attrs: AttributeSet
    ) : super(context, attrs) {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.QrXScanner
        ).useWithTryCatch {
        }
    }
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        inflate(context)
    }

    /**
     * use this after onPermissionResult
     *
     * `isGranted == true` starts Camera
     *
     * `isGranted == false` displays required permission overlay
     */
    fun onPermission(isGranted: Boolean) {
        when (isGranted && checkCameraPermission()) {
            true -> {
                permissionDeniedLayout.visibility = View.GONE
                cameraTorchBtn.visibility = View.VISIBLE
                startCamera()
            }
            false -> {
                permissionDeniedLayout.visibility = View.VISIBLE
                cameraTorchBtn.visibility = View.GONE
            }
        }
    }

    private fun checkCameraPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this.context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * Call it in `onViewCreated`
     *
     * [lifecycleOwner] is used for CameraX lifecycle bind
     *
     * [onBarcodeClickListener] is invoked when user click on code barcode/QR
     *
     * [onPermissionRetryButton] is invoked when user click on *Turn On* button on permission denied screen
     */
    @SuppressLint("ClickableViewAccessibility")
    fun initialize(
        lifecycleOwner: LifecycleOwner,
        onBarcodeClickListener: (Barcode) -> Unit,
        onPermissionRetryButton: () -> Unit
    ) {
        this.onBarcodeClickListener = onBarcodeClickListener
        this.lifecycleOwner = lifecycleOwner
        permissionDeniedButton.setOnClickListener { onPermissionRetryButton.invoke() }
        lifecycleOwner.lifecycle.coroutineScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    uiRefreshFlow.collect {
                        barcodes.getMaxOrNull()?.let {
                            refreshOverlay(it)
                        }
                    }
                }
            }
        }
        cameraOverlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                barcodes.getMaxOrNull()?.let { barcodes ->
                    barcodes.barcodeList.forEach {
                        if (it.boundingBox?.contains(
                                (event.x / (width / barcodes.analyzerDimension.width.toFloat())).toInt(),
                                (event.y.toInt() / (height / barcodes.analyzerDimension.height.toFloat())).toInt()
                            ) == true
                        )
                            onBarcodeClickListener.invoke(it)
                    }
                }
            }
            return@setOnTouchListener false
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (changed) {
            cameraPreview.layout(l, t, r, b)
            cameraOverlay.layout(l, t, r, b)
        }
        super.onLayout(changed, l, t, r, b)
    }

    private fun inflate(context: Context) {
        View.inflate(context, R.layout.view_camera_barcode, this)
        cameraPreview = findViewWithTag(CAMERA_PREVIEW)
        cameraOverlay = findViewWithTag(CAMERA_OVERLAY)
        cameraTorchBtn = findViewWithTag(CAMERA_TORCH_BUTTON)
        permissionDeniedLayout = findViewWithTag(PERMISSION_DENIED_LAYOUT)
        permissionDeniedText = findViewWithTag(PERMISSION_DENIED_TEXT)
        permissionDeniedButton = findViewWithTag(PERMISSION_DENIED_BUTTON)
    }

    private fun refreshOverlay(data: BarcodeWrapper) {
        cameraOverlay.setImageBitmap(
            Bitmap.createBitmap(data.analyzerDimension.width, data.analyzerDimension.height, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).apply {
                    drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    data.barcodeList.forEach { barcode ->
                        barcode.boundingBox?.let {
                            drawRoundRect(it.toRectF(), 5f, 5f, contourPaint)
                            iconBitmap?.let { bitmap ->
                                getRectOffsetPosition(iconAnchor, it, bitmap.width, bitmap.height).let { position ->
                                    drawBitmap(bitmap, position.width, position.height, paintDrawable)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    private fun getRectOffsetPosition(
        type: IconAnchor,
        rect: Rect,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): SizeF =
        SizeF(
            rect.centerX() - bitmapWidth / 2f,
            when (type) {
                IconAnchor.PIN -> rect.centerY().toFloat() - bitmapHeight
                IconAnchor.ICON -> rect.centerY() - bitmapHeight / 2f
            }
        )

    /**
     * Called after obtaining permission
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview
                    .Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(cameraPreview.surfaceProvider)
                    }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            Executors.newSingleThreadExecutor(),
                            CodeAnalyzer { wrapper ->
                                onBarcodeScannedListener?.invoke(wrapper.barcodeList)
                                this.barcodes.add(wrapper)
                            }
                        )
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis).also {
                    setTorchHandling(it)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun setTorchHandling(camera: Camera) {
        if (!camera.cameraInfo.hasFlashUnit())
            cameraTorchBtn.visibility = View.GONE
        else {
            cameraTorchBtn.visibility = View.VISIBLE
            cameraTorchBtn.isChecked = camera.cameraInfo.torchState.value == TorchState.ON
            cameraTorchBtn.setOnCheckedChangeListener { _, isChecked ->
                camera.cameraControl.enableTorch(isChecked)
            }
        }
    }

    /**
     * Internal class implementing [ImageAnalysis.Analyzer] used to analyze and process [ImageProxy] delivered by CameraX
     */
    internal class CodeAnalyzer(private val barcodeListener: (barcode: BarcodeWrapper) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner by lazy {
            BarcodeScanning.getClient(
                BarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
            )
        }

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(proxyImage: ImageProxy) {
            val mediaImage = proxyImage.image
            mediaImage?.let {
                val image = InputImage.fromMediaImage(mediaImage, proxyImage.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener {
                        Size(image.width, image.height)
                        barcodeListener.invoke(
                            BarcodeWrapper(
                                it,
                                when (image.rotationDegrees) {
                                    90, 270 -> Size(image.height, image.width)
                                    else -> Size(image.width, image.height)
                                }
                            )
                        )
                    }
                    .addOnFailureListener {
                        Log.d("${this.javaClass}", null, it)
                    }
                    .addOnCompleteListener {
                        proxyImage.close()
                    }
            }
        }
    }

    /**
     * Implementation of [LinkedList] allowing for **very** basic image analysis output smoothing
     */
    internal class LimitedLinkedList<T : BarcodeWrapper>(private val itemLimit: Int) : LinkedList<T>() {
        private var onNewItem: ((list: LimitedLinkedList<T>) -> Unit)? = null

        override fun add(element: T): Boolean {
            if (size >= itemLimit)
                remove()
            return super.add(element).apply {
                onNewItem?.invoke(this@LimitedLinkedList)
            }
        }

        fun getMaxOrNull(): T? {
            return this.maxByOrNull {
                it.barcodeList.size
            }
        }

        fun getMax(): T {
            return getMaxOrNull()!!
        }

        fun setNewItemListener(listener: ((list: LimitedLinkedList<T>) -> Unit)?) {
            onNewItem = listener
        }
    }

    companion object {
        const val CAMERA_PREVIEW = "camera_preview"
        const val CAMERA_OVERLAY = "camera_overlay"
        const val CAMERA_TORCH_BUTTON = "camera_torch_button"
        const val PERMISSION_DENIED_LAYOUT = "permission_denied_layout"
        const val PERMISSION_DENIED_TEXT = "permission_denied_text"
        const val PERMISSION_DENIED_BUTTON = "permission_denied_button"
    }
}
