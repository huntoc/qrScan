package com.iot.niot_ctrl

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.util.*

/**
 *维护人员: jingpeng
 *最新修改时间:2021/11/24
 */
class ContinuousCaptureActivity : Activity() {
    private var lastText: String = ""
    private lateinit var tvResult: TextView
    private lateinit var barcodeView: DecoratedBarcodeView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_continuous_capture)
        tvResult = findViewById(R.id.tv_result)
        barcodeView = findViewById(R.id.barcode_scanner)

        val formats: Collection<BarcodeFormat> =
            listOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_39)//码格式
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        barcodeView.initializeFromIntent(intent)
        barcodeView.decodeContinuous(callBack)
    }

    private val callBack: BarcodeCallback = object : BarcodeCallback {
        @SuppressLint("SetTextI18n")
        override fun barcodeResult(result: BarcodeResult?) {
            result?.let {
                //扫描结果为空或者两次扫描的结果相同
                if (it.text.isNullOrEmpty() || it.text == lastText) {
                    return
                }
                lastText = it.text
                tvResult.text = "${tvResult.text}\n$it"
                Log.e("yufs",lastText)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }
}