package com.iot.niot_ctrl

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity

import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.lang.Exception
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import java.io.*
import java.net.*

import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import androidx.core.content.ContentProviderCompat.requireContext


data class Tcp_Login_d(var charging_id: String, var charging_gun: String)

public class Tcp_Login {
    public var charging_id : String = "0";
    public var charging_gun: String = "0";

    constructor( charging_id: String,  charging_gun: String) {
        this.charging_id = charging_id
        this.charging_gun = charging_gun;
    }

    public fun set(charging_id: String,  charging_gun:String){
        this.charging_id = charging_id
        this.charging_gun = charging_gun;
    }

    public fun tcp_pak() : Tcp_Login_d {
        return Tcp_Login_d(this.charging_id, this.charging_gun)
    }
}


class MainActivity : AppCompatActivity() {
    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>
    private lateinit var ivImage:ImageView
    private lateinit var qr_info:TextView
    private lateinit var btn_cpy: Button
    private lateinit var txt_tcp_client_receive : TextView
    private lateinit var btn_tcp_client_receive_clear:Button
    private lateinit var switch_tcp_client_status:Switch
    private lateinit var edit_tcp_client_target_ip: EditText
    private lateinit var edit_tcp_client_target_port:EditText
    private lateinit var txt_tcp_client_local_ip:TextView
    private lateinit var btn_tcp_client_send:Button
    private lateinit var check_tcp_client_add_newline: CheckBox
    private lateinit var edit_tcp_client_send: EditText
    private lateinit var check_tcp_client_add_renew: CheckBox


    companion object {
        private const val TCP_CLIENT_GET_MSG_BUNDLE = "tcpClientGetMsg"
        private const val TCP_CLIENT_GET_MSG = 4555
        private const val TCP_CLIENT_CONNECT_STATUS_BUNDLE = "TCPConnectStatus"
        private const val TCP_CLIENT_CONNECT_STATUS_MSG = 1245
    }

    private var tcpClient = Socket()
    private val encodingFormat = "UTF-8"
    private var tcpClientConnectStatus = false
    private var tcpClientTargetServerIP = String()
    private var tcpClientTargetServerPort = 5666
    private var tcpClientOutputStream: OutputStream? = null
    private var tcpClientInputStreamReader: InputStreamReader? = null
    private val tcpClientReceiveBuffer = StringBuffer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initTcp()
        initQrcode()

    }

    //客户端连接 需要子线程
    private fun funTCPClientConnect() {
        if (tcpClientTargetServerIP.isEmpty()) {
            Log.e("目标服务端IP不能为空,否则无法连接", "")
            return
        }
        try {
            //一定要注意，每次连接必须是一个新的Socket对象，否则如果在其他地方关闭了socket对象，那么就无法
            //继续连接了，因为默认对象已经关闭了
            tcpClient = Socket()
            tcpClient.connect(
                InetSocketAddress(tcpClientTargetServerIP, tcpClientTargetServerPort),
                4000
            )

            //发送心跳包
            tcpClient.keepAlive = true
            //注意这里，不同的电脑PC端可能用到编码方式不同，通常会使用GBK格式而不是UTF-8格式
            val printWriter =
                PrintWriter(OutputStreamWriter(tcpClient.getOutputStream(), encodingFormat), true)
            //注意
            // 将缓冲区的数据强制输出，用于清空缓冲区，若直接调用close()方法，则可能会丢失缓冲区的数据。所以通俗来讲它起到的是刷新的作用。
            //printWriter.flush();
            // 用于关闭数据流
            ///printWriter.close();
            printWriter.write("客户端连接成功")
            printWriter.flush()
            tcpClientConnectStatus = true

            Log.e("连接服务端成功", "TCPClient")
            Log.e("开启客户端接收", "TCPClient")
            funTCPClientReceive()
        } catch (e: Exception) {
            when (e) {
                is SocketTimeoutException -> {
                    //一般TCP连接第一次都会失败，不知道是编程的问题还是这个协议本身的问题
                    //所以自己设置下重连，如果第二次依旧重连失败，那就表明连接确实是有问题了，
                    //就需要手动重连了
                    Log.e("连接超时，重新连接", "dd");
                    e.printStackTrace()
                }
                is NoRouteToHostException -> {
                    Log.e("该地址不存在，请检查", "DD");
                    e.printStackTrace()
                }
                is ConnectException -> {
                    Log.e("连接异常或被拒绝，请检查", "DD");
                    e.printStackTrace()
                }
                else -> {
                    e.printStackTrace()
                    Log.e("连接结束", e.toString())
                }
            }
            val message = Message()
            val bundle = Bundle()
            bundle.putBoolean(TCP_CLIENT_CONNECT_STATUS_BUNDLE, false)
            message.what = TCP_CLIENT_CONNECT_STATUS_MSG
            message.data = bundle
            handler.sendMessage(message)
            tcpClientConnectStatus = false
            tcpClient.close()
        }
    }

    //客户端发送
    //需要子线程
    private fun funTCPClientSend(msg: String) {
        if (msg.isNotEmpty() && tcpClientConnectStatus) {
            //这里要注意，只要曾经连接过，isConnected便一直返回true，无论现在是否正在连接
            if (tcpClient.isConnected) {
                try {
                    tcpClientOutputStream = tcpClient.getOutputStream()
                    val printWriter =
                        PrintWriter(
                            OutputStreamWriter(
                                tcpClientOutputStream,
                                encodingFormat
                            ), true
                        )
                    printWriter.write(msg)
                    printWriter.flush()
                    Log.e("信息发送成功", msg)

                } catch (e: IOException) {
                    Log.e("信息发送失败", msg)
                    e.printStackTrace()
                    tcpClientInputStreamReader?.close()
                    tcpClientOutputStream?.close()
                    tcpClient.close()
                }
            }
        }
    }


    //客户端接收的消息
    //添加子线程
    private fun funTCPClientReceive() {
        Log.e("开启客户端接收成功", "TCPClient")
        while (tcpClientConnectStatus) {
            if (tcpClient.isConnected) {
                tcpClientInputStreamReader = InputStreamReader(tcpClient.getInputStream(), "UTF-8")
                val bufferedReader = BufferedReader(tcpClientInputStreamReader)
                val readLine = bufferedReader.readLine()
                val message = Message()
                val bundle = Bundle()
                bundle.putString(TCP_CLIENT_GET_MSG_BUNDLE, readLine)
                message.what = TCP_CLIENT_GET_MSG
                message.data = bundle
                handler.sendMessage(message)
                Log.e("客户端收到的消息", readLine)
            } else {
                Log.e("开启客户端接收失败", "TCPClient")
                tcpClientInputStreamReader?.close()
                tcpClientOutputStream?.close()
                tcpClient.close()
                break
            }
        }
    }

    private fun tcp_send_msg(msg: String){
        if (tcpClientConnectStatus) {
            thread {
                funTCPClientSend(msg)
            }
        }
    }


    //官方推荐的方法
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                TCP_CLIENT_GET_MSG -> {
                    val string = msg.data.getString(TCP_CLIENT_GET_MSG_BUNDLE)
                    tcpClientReceiveBuffer.append(string)
                    txt_tcp_client_receive.text = tcpClientReceiveBuffer.toString()
                }
                TCP_CLIENT_CONNECT_STATUS_MSG -> {
                    val boolean = msg.data.getBoolean(TCP_CLIENT_CONNECT_STATUS_BUNDLE)
                    if (!boolean) {
                        switch_tcp_client_status.isChecked = false
                    }
                }
            }
        }
    }

    //获取设备局域网IP,没开wifi的情况下获取的会是内网ip
    private fun funGetLocalAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        val localIP =
            (ipAddress and 0xff).toString() + "." + (ipAddress shr 8 and 0xff) + "." + (ipAddress shr 16 and 0xff) + "." + (ipAddress shr 24 and 0xff)
        Log.e("localIP", localIP)
        return localIP
    }

    private fun initTcp()  {
        txt_tcp_client_receive = findViewById(R.id.txt_tcp_client_receive)
        btn_tcp_client_receive_clear = findViewById(R.id.btn_tcp_client_receive_clear)
        switch_tcp_client_status = findViewById(R.id.switch_tcp_client_status)
        edit_tcp_client_target_ip = findViewById(R.id.edit_tcp_client_target_ip)
        edit_tcp_client_target_port = findViewById(R.id.edit_tcp_client_target_port)
        txt_tcp_client_local_ip = findViewById(R.id.txt_tcp_client_local_ip)
        btn_tcp_client_send = findViewById(R.id.btn_tcp_client_send)
        check_tcp_client_add_newline = findViewById(R.id.check_tcp_client_add_newline)
        edit_tcp_client_send = findViewById(R.id.edit_tcp_client_send)
        check_tcp_client_add_renew = findViewById(R.id.check_tcp_client_add_renew)


        //设置接收信息框上下滚动，如果设置了多个，只会有一个起作用
        txt_tcp_client_receive.movementMethod = ScrollingMovementMethod.getInstance()

        //清除客户端接收
        btn_tcp_client_receive_clear.setOnClickListener {
            tcpClientReceiveBuffer.delete(0, tcpClientReceiveBuffer.length)
            if(txt_tcp_client_receive.text.isNotEmpty())
            {
                this.copyToClipboard(txt_tcp_client_receive.text.toString())
                Toast.makeText(this, "复制成功", Toast.LENGTH_LONG).show()
            }

            // txt_tcp_client_receive.text = tcpClientReceiveBuffer
        }

        //连接服务端或者断开连接
        switch_tcp_client_status.setOnClickListener {
            tcpClientTargetServerIP = edit_tcp_client_target_ip.text.toString()
            tcpClientTargetServerPort = edit_tcp_client_target_port.text.toString().toInt()

            if (switch_tcp_client_status.isChecked) {
                switch_tcp_client_status.isChecked = true
                thread {
                    funTCPClientConnect()
                }
            } else {
                switch_tcp_client_status.isChecked = false
                tcpClientConnectStatus = false
                tcpClient.close()
            }

            txt_tcp_client_local_ip.text = funGetLocalAddress()
        }
        //客户端发送消息
        btn_tcp_client_send.setOnClickListener {
            var text = edit_tcp_client_send.text.toString()
            if (check_tcp_client_add_newline.isChecked) {
                text += "\n"
            }
            if (check_tcp_client_add_renew.isChecked) {
                txt_tcp_client_receive.text = tcpClientReceiveBuffer.append("客户端发送的消息:$text")
            }
            if (tcpClientConnectStatus) {
                thread {
                    funTCPClientSend(text)
                }
            }
        }
    }


    // =============================================              二维码扫描模块
    fun screen(view: View) {
        //配置扫描时的基本参数
        val options = ScanOptions()
        options.apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)   // 图形码的格式：商品码、一维码、二维码、数据矩阵、全部类型
            setPrompt("请将二维码置于取景框内扫描")
            setCameraId(0) //0 后置摄像头  1 前置摄像头
            setBeepEnabled(true)//开启成功声音
            // setTimeout(0)//设置超时时间
        }
        //启动扫描二维码界面
        barcodeLauncher.launch(options)
    }

    private fun initQrcode()
    {
        txt_tcp_client_receive = findViewById(R.id.txt_tcp_client_receive)

        barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
            //获取回调结果
            var login = Tcp_Login(result.contents, "0")
            if (result.contents == null) {
                Toast.makeText(this, "取消", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    this,
                    "扫描结果: " + result.contents + ",${result.barcodeImagePath}",
                    Toast.LENGTH_LONG
                ).show()

                tcp_send_msg(login.charging_gun + login.charging_id)
                txt_tcp_client_receive.setText(result.contents)
            }
        }
    }

    private fun copyToClipboard(info: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label",info)
        clipboard.setPrimaryClip(clip)
    }
}

