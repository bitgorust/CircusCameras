package sh.diqi.circuscameras

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usbcameracommon.UVCCameraHandlerMultiSurface
import com.serenegiant.widget.UVCCameraTextureView
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    /**
     * set 0 if you want to record movie using MediaSurfaceEncoder
     * (writing frame data into Surface camera from MediaCodec
     * by almost same way as USBCameratest2)
     * set 1 if you want to record movie using MediaVideoEncoder
     */
    private val encoderType = 1

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * [UVCCamera.setPreviewSize] throw exception
     */
    private val previewWidth = 640
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * [UVCCamera.setPreviewSize] throw exception
     */
    private val previewHeight = 480
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * [UVCCamera.setPreviewSize] throw exception
     * 0:YUYV, other:MJPEG
     */
    private val previewMode = 1

    private val previewCount = 4

    private lateinit var usbMonitor: USBMonitor
    private lateinit var cameraPreviews: List<UVCCameraTextureView>

    private var connectedDevices = ArrayList<UsbDevice>()
    private var cameraHandlers = ArrayList<UVCCameraHandlerMultiSurface>()
    private var previewSurfaces = arrayOfNulls<Surface>(previewCount)

    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
            Log.d(packageName, "onConnect")
            device?.let {
                connectedDevices.add(it)
                val index = connectedDevices.size - 1
                cameraHandlers[index].open(ctrlBlock)
                startPreview(index)
            }
        }

        override fun onCancel(device: UsbDevice?) {
            Log.d(packageName, "onCancel")
            usbMonitor.requestPermission(device)
        }

        override fun onAttach(device: UsbDevice?) {
            Log.d(packageName, "onAttach")
            usbMonitor.requestPermission(device)
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Log.d(packageName, "onDisconnect")
            val index = connectedDevices.indexOf(device)
            stopPreview(index)
            cameraHandlers[index].release()
        }

        override fun onDetach(device: UsbDevice?) {
            Log.d(packageName, "onDetach")
            connectedDevices.remove(device)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbMonitor = USBMonitor(this, onDeviceConnectListener)

        cameraPreviews = getTextureViews(surfaceViewsContainer).toList().subList(0, previewCount)
        cameraPreviews
                .map {
                    UVCCameraHandlerMultiSurface.createHandler(this,
                            it, encoderType,
                            previewWidth, previewHeight, previewMode)
                }
                .forEach { cameraHandlers.add(it) }
    }

    override fun onStart() {
        super.onStart()
        usbMonitor.register()
    }

    override fun onStop() {
        stopPreviews()
        usbMonitor.unregister()
        super.onStop()
    }

    override fun onDestroy() {
        cameraHandlers.map { it.close() }
        cameraHandlers.clear()
        usbMonitor.destroy()
        super.onDestroy()
    }

    private fun startPreview(index: Int) {
        cameraHandlers[index].startPreview()
        runOnUiThread {
            val surfaceTexture = cameraPreviews[index].surfaceTexture
            val surface = Surface(surfaceTexture)
            previewSurfaces[index] = surface
            cameraHandlers[index].addSurface(surface.hashCode(), surface, false)
        }
    }

    private fun stopPreview(index: Int) {
        previewSurfaces[index]?.let {
            cameraHandlers[index].removeSurface(it.hashCode())
        }
        previewSurfaces[index] = null
        cameraHandlers[index].close()
    }

    private fun stopPreviews() {
        previewSurfaces.indices.forEach { stopPreview(it) }
    }

    private fun getTextureViews(viewGroup: ViewGroup): Collection<UVCCameraTextureView> {
        val children = ArrayList<UVCCameraTextureView>()
        (0 until viewGroup.childCount)
                .map { viewGroup.getChildAt(it) }
                .forEach {
                    if (it is UVCCameraTextureView) {
                        children.add(it)
                    } else if (it is ViewGroup) {
                        children.addAll(getTextureViews(it))
                    }
                }
        return children
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
//    external fun stringFromJNI(): String
//
//    companion object {
//
//        // Used to load the 'native-lib' library on application startup.
//        init {
//            System.loadLibrary("native-lib")
//        }
//    }
}
