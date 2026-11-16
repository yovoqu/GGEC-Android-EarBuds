package com.ambiq.blevideoplayer

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 主界面：BLE 音视频接收、音频参数与模型控制、实时字幕、视频录制和配置下发。
 */
class MainActivity : AppCompatActivity(),
    BleManager.Listener,
    TextureView.SurfaceTextureListener,
    VideoStreamDecoder.Listener,
    AudioStreamDecoder.Listener,
    SpeechRecognizer.Listener {

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var btnConnect: Button
    private lateinit var btnCodec: Button
    private lateinit var btnRecord: Button
    private lateinit var btnSend: Button
    private lateinit var etMessage: EditText
    private lateinit var lvDevices: ListView
    private lateinit var textureView: TextureView
    private var codecSurface: Surface? = null

    private lateinit var spAudioCodec: Spinner
    private lateinit var spSampleRate: Spinner
    private lateinit var spChannels: Spinner
    private lateinit var spAsrModel: Spinner
    private lateinit var etAudioUuid: EditText
    private lateinit var btnMute: Button
    private lateinit var btnAsr: Button
    private lateinit var tvTranscript: TextView
    private lateinit var transcriptScroll: ScrollView

    private lateinit var bleManager: BleManager
    private val decoder = VideoStreamDecoder()
    private val audioDecoder = AudioStreamDecoder()
    private lateinit var speechRecognizer: SpeechRecognizer
    private val finalizedTranscript = StringBuilder()
    private var partialTranscript = ""
    private var asrEnabled = true
    private var controlsReady = false
    private var activityResumed = false

    // 扫描结果缓存：address -> device / rssi
    private val devices = LinkedHashMap<String, BluetoothDevice>()
    private val rssiMap = HashMap<String, Int>()
    private val rowLabels = ArrayList<String>()
    private var rowAddresses = listOf<String>() // 与 rowLabels 对齐
    private lateinit var listAdapter: ArrayAdapter<String>
    private var selectedAddress: String? = null

    private var scanning = false
    private var connected = false
    private var surfaceReady = false

    // 码流录制：把收到的原始字节写入文件，用于离线分析（VLC/ffplay 验证格式）
    private var recordStream: BufferedOutputStream? = null
    private var recordBytes = 0L

    // 当前解码器 MIME：对端码流为 H.264 (video/avc) 或 H.265 (video/hevc)
    private var useHevc = false

    // 视频真实分辨率（SPS 解析后由解码器回调），用于等比缩放
    private var videoWidth = 0
    private var videoHeight = 0

    private val uiHandler = Handler(Looper.getMainLooper())

    // 扫描结果批量刷新：binder 线程先累积，UI 线程每 300ms 最多重建一次列表。
    // 低时延扫描下广播包非常密集，若每条都 post 刷新会把主线程消息队列塞满，
    // 导致"停止扫描"等点击事件长时间得不到处理（看起来就像无法停止）。
    private val pendingScanLock = Any()
    private val pendingScanResults = HashMap<String, Pair<BluetoothDevice, Int>>()
    private var scanRefreshScheduled = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                Toast.makeText(this, "BLE 权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "缺少 BLE 权限，无法扫描/连接", Toast.LENGTH_LONG).show()
            }
        }

    // 每秒刷新一次统计：音视频分别计数，纯音频时不会触发视频格式误报。
    private val statsRunnable = object : Runnable {
        private var lastVideoBytes = 0L
        private var lastAudioBytes = 0L
        private var lastFrames = 0L

        override fun run() {
            val videoBytes = bleManager.videoRxBytes.get()
            val audioBytes = bleManager.audioRxBytes.get()
            val frames = decoder.decodedFrames.get()
            val videoKbps = (videoBytes - lastVideoBytes) * 8 / 1000
            val audioKbps = (audioBytes - lastAudioBytes) * 8 / 1000
            val fps = frames - lastFrames
            lastVideoBytes = videoBytes
            lastAudioBytes = audioBytes
            lastFrames = frames
            val startCodes = decoder.startCodeCount.get()
            val hint = when {
                !connected -> ""
                videoBytes > 512 * 1024 && startCodes == 0L -> " | 视频码流可能不是 Annex B"
                videoBytes > 512 * 1024 && frames == 0L -> " | 等待关键帧…"
                else -> ""
            }
            tvStatus.text = String.format(
                Locale.US,
                "%s | 视频 %d kbps | 音频 %d kbps | %d fps | 起始码 %d\n" +
                    "队列 V%d/A%d KB | 丢弃 V%d/A%d | ASR %s%s",
                if (connected) "已连接" else "未连接",
                videoKbps, audioKbps, fps, startCodes,
                decoder.queuedBytes() / 1024, audioDecoder.queuedBytes() / 1024,
                decoder.droppedChunks.get(), audioDecoder.droppedChunks.get(),
                asrStatusLabel(), hint
            )
            uiHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnScan = findViewById(R.id.btnScan)
        btnConnect = findViewById(R.id.btnConnect)
        btnCodec = findViewById(R.id.btnCodec)
        btnSend = findViewById(R.id.btnSend)
        etMessage = findViewById(R.id.etMessage)
        lvDevices = findViewById(R.id.lvDevices)
        textureView = findViewById(R.id.svVideo)
        spAudioCodec = findViewById(R.id.spAudioCodec)
        spSampleRate = findViewById(R.id.spSampleRate)
        spChannels = findViewById(R.id.spChannels)
        spAsrModel = findViewById(R.id.spAsrModel)
        etAudioUuid = findViewById(R.id.etAudioUuid)
        btnMute = findViewById(R.id.btnMute)
        btnAsr = findViewById(R.id.btnAsr)
        tvTranscript = findViewById(R.id.tvTranscript)
        transcriptScroll = findViewById(R.id.transcriptScroll)

        bleManager = BleManager(applicationContext, this)
        decoder.listener = this
        audioDecoder.listener = this
        speechRecognizer = SpeechRecognizer(applicationContext)
        speechRecognizer.listener = this

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rowLabels)
        lvDevices.adapter = listAdapter
        lvDevices.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position in rowAddresses.indices) {
                selectedAddress = rowAddresses[position]
                Toast.makeText(this, "已选择 ${rowLabels[position]}", Toast.LENGTH_SHORT).show()
            }
        }

        textureView.surfaceTextureListener = this

        btnScan.setOnClickListener { onScanClicked() }
        btnConnect.setOnClickListener { onConnectClicked() }
        btnCodec.setOnClickListener {
            useHevc = !useHevc
            btnCodec.text = if (useHevc) "H.265" else "H.264"
            restartDecoder() // 切换编码格式需重建解码器
        }
        btnSend.setOnClickListener {
            val text = etMessage.text.toString()
            if (text.isEmpty()) return@setOnClickListener
            // ASCII 文本写入 RX 特征，作为将来分辨率/帧率配置下发通道
            val ok = bleManager.sendText(text)
            Toast.makeText(this, if (ok) "已发送" else "发送失败", Toast.LENGTH_SHORT).show()
            if (ok) etMessage.text.clear()
        }
        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.setOnClickListener { toggleRecord() }
        initAudioControls()

        if (!hasBlePermissions()) {
            permissionLauncher.launch(requiredPermissions())
        }

        uiHandler.post(statsRunnable)
    }

    // ---------------- 权限 ----------------

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private fun hasBlePermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------- UI 操作 ----------------

    private fun currentMime(): String =
        if (useHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

    private fun initAudioControls() {
        setSpinnerItems(spAudioCodec, BleAudioCodec.entries.map { it.displayName })
        setSpinnerItems(spSampleRate, listOf("16000", "8000", "44100", "48000"))
        setSpinnerItems(spChannels, listOf("单声道", "双声道"))
        setSpinnerItems(spAsrModel, AsrModel.entries.map { it.displayName })

        val prefs = getSharedPreferences("audio_settings", MODE_PRIVATE)
        spAudioCodec.setSelection(prefs.getInt("codec", 0).coerceIn(0, 2))
        val savedRate = prefs.getInt("sample_rate", 16_000).toString()
        spSampleRate.setSelection(
            listOf("16000", "8000", "44100", "48000").indexOf(savedRate).coerceAtLeast(0)
        )
        spChannels.setSelection(prefs.getInt("channels", 1).coerceIn(1, 2) - 1)
        spAsrModel.setSelection(prefs.getInt("asr_model", 0).coerceIn(0, 1))
        etAudioUuid.setText(
            prefs.getString("audio_uuid", BleManager.DEFAULT_AUDIO_TX_CHAR_UUID.toString())
        )
        bleManager.setAudioTxUuid(etAudioUuid.text.toString())
        asrEnabled = prefs.getBoolean("asr_enabled", true)
        btnAsr.text = if (asrEnabled) "关闭识别" else "开启识别"
        btnMute.isSelected = prefs.getBoolean("muted", false)
        updateMuteButton()

        spAudioCodec.onSelectionChanged {
            prefs.edit().putInt("codec", spAudioCodec.selectedItemPosition).apply()
            restartAudioPipeline()
        }
        spSampleRate.onSelectionChanged {
            prefs.edit().putInt("sample_rate", currentAudioConfig().sampleRate).apply()
            restartAudioPipeline()
        }
        spChannels.onSelectionChanged {
            prefs.edit().putInt("channels", currentAudioConfig().channels).apply()
            restartAudioPipeline()
        }
        spAsrModel.onSelectionChanged {
            prefs.edit().putInt("asr_model", spAsrModel.selectedItemPosition).apply()
            startSpeechRecognizer()
        }
        etAudioUuid.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveAudioUuid()
        }
        btnMute.setOnClickListener {
            btnMute.isSelected = !btnMute.isSelected
            prefs.edit().putBoolean("muted", btnMute.isSelected).apply()
            updateMuteButton()
        }
        btnAsr.setOnClickListener {
            asrEnabled = !asrEnabled
            prefs.edit().putBoolean("asr_enabled", asrEnabled).apply()
            btnAsr.text = if (asrEnabled) "关闭识别" else "开启识别"
            if (asrEnabled) startSpeechRecognizer() else speechRecognizer.stop()
        }
        findViewById<Button>(R.id.btnClearTranscript).setOnClickListener {
            finalizedTranscript.clear()
            partialTranscript = ""
            updateTranscript()
        }
        findViewById<Button>(R.id.btnLocalAsr).setOnClickListener {
            startActivity(Intent(this, LocalAsrActivity::class.java))
        }
        controlsReady = true
    }

    private fun setSpinnerItems(spinner: Spinner, items: List<String>) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun Spinner.onSelectionChanged(action: () -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (controlsReady) action()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun currentAudioConfig() = AudioDecoderConfig(
        codec = BleAudioCodec.entries[spAudioCodec.selectedItemPosition],
        sampleRate = spSampleRate.selectedItem.toString().toInt(),
        channels = spChannels.selectedItemPosition + 1
    )

    private fun restartAudioPipeline() {
        if (!controlsReady || !activityResumed) return
        audioDecoder.start(currentAudioConfig())
        startSpeechRecognizer()
    }

    private fun startSpeechRecognizer() {
        if (!controlsReady || !activityResumed || !asrEnabled) return
        partialTranscript = ""
        speechRecognizer.start(AsrModel.entries[spAsrModel.selectedItemPosition])
        updateTranscript()
    }

    private fun saveAudioUuid(): Boolean {
        val value = etAudioUuid.text.toString()
        if (!bleManager.setAudioTxUuid(value)) {
            Toast.makeText(this, "音频 UUID 格式无效", Toast.LENGTH_LONG).show()
            return false
        }
        getSharedPreferences("audio_settings", MODE_PRIVATE)
            .edit().putString("audio_uuid", value.trim()).apply()
        return true
    }

    private fun updateMuteButton() {
        btnMute.text = if (btnMute.isSelected) "取消静音" else "静音"
        audioDecoder.setMuted(btnMute.isSelected)
    }

    private fun asrStatusLabel(): String {
        if (!asrEnabled) return "已关闭"
        return when (speechRecognizer.status) {
            AsrStatus.IDLE -> "空闲"
            AsrStatus.LOADING -> "加载模型…"
            AsrStatus.READY -> "就绪"
            AsrStatus.FINISHED -> "已完成"
        }
    }

    private fun updateTranscript() {
        tvTranscript.text = finalizedTranscript.toString() + partialTranscript
        transcriptScroll.post { transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun onScanClicked() {
        if (!hasBlePermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }
        if (scanning) {
            bleManager.stopScan()
            scanning = false
            btnScan.text = "扫描"
        } else {
            devices.clear()
            rssiMap.clear()
            refreshDeviceList()
            if (bleManager.startScan()) {
                scanning = true
                btnScan.text = "停止扫描"
            }
        }
    }

    private fun onConnectClicked() {
        if (!hasBlePermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }
        if (!saveAudioUuid()) return
        if (connected) {
            bleManager.disconnect()
        } else {
            val dev = selectedAddress?.let { devices[it] }
            if (dev == null) {
                Toast.makeText(this, "请先在列表中选择设备", Toast.LENGTH_SHORT).show()
                return
            }
            if (scanning) {
                bleManager.stopScan()
                scanning = false
                btnScan.text = "扫描"
            }
            bleManager.connect(dev)
            Toast.makeText(this, "连接中…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDeviceList() {
        // dabaicai--test 置顶，其余按信号强度排序，均可手动选择
        val sorted = devices.entries.sortedWith(
            compareByDescending<Map.Entry<String, BluetoothDevice>> {
                safeName(it.value) == BleManager.TARGET_NAME
            }.thenByDescending { rssiMap[it.key] ?: -128 }
        )
        rowAddresses = sorted.map { it.key }
        rowLabels.clear()
        sorted.forEach { e ->
            rowLabels.add("${safeName(e.value)}  ${e.key}  ${rssiMap[e.key] ?: 0} dBm")
        }
        listAdapter.notifyDataSetChanged()
    }

    private fun safeName(d: BluetoothDevice): String =
        try {
            d.name
        } catch (e: SecurityException) {
            null
        } ?: "未知设备"

    private fun restartDecoder() {
        val s = codecSurface ?: return
        decoder.stop()
        decoder.start(s, currentMime())
    }

    // ---------------- TextureView.SurfaceTextureListener ----------------

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        surfaceReady = true
        // Surface 就绪即启动解码线程（未连接时线程空转等待数据）
        codecSurface = Surface(st)
        decoder.start(codecSurface!!, currentMime())
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        applyVideoTransform()
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        surfaceReady = false
        decoder.stop() // Surface 销毁前必须停掉绑定它的 codec
        codecSurface?.release()
        codecSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    /**
     * 按视频真实宽高比等比缩放 TextureView 内容（fit-center，居中留黑边），
     * 修正默认"铺满控件"导致的拉伸变形。
     */
    private fun applyVideoTransform() {
        val vw = textureView.width
        val vh = textureView.height
        if (vw == 0 || vh == 0 || videoWidth == 0 || videoHeight == 0) return
        val videoAspect = videoWidth.toFloat() / videoHeight
        val viewAspect = vw.toFloat() / vh
        val scaleX: Float
        val scaleY: Float
        if (videoAspect > viewAspect) {
            // 视频比控件更宽：宽度铺满，上下留边
            scaleX = 1f
            scaleY = viewAspect / videoAspect
        } else {
            // 视频比控件更高：高度铺满，左右留边
            scaleX = videoAspect / viewAspect
            scaleY = 1f
        }
        val m = Matrix()
        m.setScale(scaleX, scaleY, vw / 2f, vh / 2f)
        textureView.setTransform(m)
    }

    // ---------------- BleManager.Listener（回调在 binder 线程，UI 操作需切主线程） ----------------

    override fun onScanResult(device: BluetoothDevice, rssi: Int) {
        synchronized(pendingScanLock) {
            pendingScanResults[device.address] = device to rssi
        }
        synchronized(pendingScanLock) {
            if (scanRefreshScheduled) return
            scanRefreshScheduled = true
        }
        uiHandler.postDelayed({
            val batch: Map<String, Pair<BluetoothDevice, Int>>
            synchronized(pendingScanLock) {
                scanRefreshScheduled = false
                batch = HashMap(pendingScanResults)
                pendingScanResults.clear()
            }
            batch.forEach { (addr, p) ->
                devices[addr] = p.first
                rssiMap[addr] = p.second
            }
            refreshDeviceList()
        }, 300)
    }

    override fun onScanFailed(errorCode: Int) {
        uiHandler.post {
            scanning = false
            btnScan.text = "扫描"
            Toast.makeText(this, "扫描失败: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionStateChanged(connected: Boolean) {
        uiHandler.post {
            this.connected = connected
            btnConnect.text = if (connected) "断开" else "连接"
            if (connected) restartAudioPipeline() else {
                audioDecoder.stop()
                speechRecognizer.stop()
            }
        }
    }

    override fun onReadyToSend(ready: Boolean) {
        uiHandler.post { btnSend.isEnabled = ready }
    }

    override fun onVideoData(data: ByteArray) {
        // BLE 通知回调线程：直接入队，由解码线程消费；队列满丢最旧块，不阻塞 BLE
        decoder.enqueue(data)
        // 录制中则同步写文件（1.2Mbps 下 BufferedOutputStream 开销可忽略）
        recordStream?.let {
            try {
                it.write(data)
                recordBytes += data.size
            } catch (_: Exception) {
            }
        }
    }

    override fun onAudioData(data: ByteArray) {
        audioDecoder.enqueue(data)
    }

    // ---------------- 码流录制 ----------------

    private fun toggleRecord() {
        val s = recordStream
        if (s != null) {
            // 停止录制
            recordStream = null
            try {
                s.flush(); s.close()
            } catch (_: Exception) {
            }
            btnRecord.text = "录制"
            Toast.makeText(this, "已保存 ${recordBytes / 1024} KB", Toast.LENGTH_LONG).show()
        } else {
            // 文件写到应用私有目录，无需存储权限；可用 adb pull 或文件管理器取出
            val f = File(
                getExternalFilesDir(null),
                "ble_stream_${System.currentTimeMillis()}.${if (useHevc) "h265" else "h264"}"
            )
            try {
                recordStream = BufferedOutputStream(FileOutputStream(f), 64 * 1024)
                recordBytes = 0
                btnRecord.text = "停止录制"
                Toast.makeText(this, "录制到:\n${f.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "无法创建文件: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onError(message: String) {
        uiHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    // ---------------- VideoStreamDecoder.Listener ----------------

    override fun onDecoderError(message: String) {
        uiHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            restartDecoder() // 解码器异常后自动重建
        }
    }

    override fun onVideoSizeChanged(width: Int, height: Int) {
        uiHandler.post {
            videoWidth = width
            videoHeight = height
            applyVideoTransform()
        }
    }

    // ---------------- 音频解码与语音识别回调 ----------------

    override fun onAudioSamples(samples: FloatArray) {
        if (asrEnabled) speechRecognizer.acceptPcm(samples)
    }

    override fun onAudioError(message: String) {
        uiHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    override fun onAsrStatus(status: AsrStatus) = Unit

    override fun onAsrPartial(text: String) {
        uiHandler.post {
            partialTranscript = text
            updateTranscript()
        }
    }

    override fun onAsrFinal(text: String) {
        uiHandler.post {
            if (text.isNotBlank()) finalizedTranscript.append(text).append('\n')
            if (finalizedTranscript.length > 64 * 1024) {
                finalizedTranscript.delete(0, finalizedTranscript.length - 32 * 1024)
            }
            partialTranscript = ""
            updateTranscript()
        }
    }

    override fun onAsrError(message: String) {
        uiHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    // ---------------- 生命周期 ----------------

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (surfaceReady && !decoder.isRunning()) restartDecoder()
        restartAudioPipeline()
    }

    override fun onPause() {
        activityResumed = false
        decoder.stop()
        audioDecoder.stop()
        speechRecognizer.stop()
        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(statsRunnable)
        decoder.stop()
        audioDecoder.stop()
        speechRecognizer.stop()
        bleManager.close()
        recordStream?.let {
            try {
                it.flush(); it.close()
            } catch (_: Exception) {
            }
        }
        recordStream = null
        super.onDestroy()
    }
}
