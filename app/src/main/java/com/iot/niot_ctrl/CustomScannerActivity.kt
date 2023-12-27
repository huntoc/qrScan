package com.iot.niot_ctrl

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.Size

/**
 *维护人员: jingpeng
 *最新修改时间:2021/11/24
 */
class CustomScannerActivity : Activity(), DecoratedBarcodeView.TorchListener {
    private lateinit var capture: CaptureManager
    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var btnFlash: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_scanner)
        btnFlash = findViewById(R.id.btn_switch_light)
        barcodeView = findViewById(R.id.zxing_barcode_scanner)
        barcodeView.setTorchListener(this)

        if (!hasFlash()) {
            btnFlash.visibility = View.GONE

        }
//        //动态设置扫描框大小
//        barcodeView.barcodeView.framingRectSize = Size((220*density).toInt(),(220*density).toInt())
        //CaptureManager与DecoratedBarcodeView绑定，处理扫描结果、回调等逻辑
        capture = CaptureManager(this, barcodeView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.setShowMissingCameraPermissionDialog(false)
        capture.decode()
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        capture.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    /**
     * 检查手机是否带闪光灯
     */
    private fun hasFlash(): Boolean {
        return applicationContext.packageManager
            .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    override fun onTorchOn() {
        btnFlash.text = "关闭闪光灯"
    }

    override fun onTorchOff() {
        btnFlash.text = "打开闪光灯"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun switchFlashlight(view: View) {
        if("打开闪光灯" == btnFlash.text.toString()){
            barcodeView.setTorchOn()
        }else{
            barcodeView.setTorchOff()
        }
    }
}