package com.ambiq.blevideoplayer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * BLE 链路管理：扫描、连接、链路参数协商、音视频通知订阅、自动重连。
 *
 * 对端固件约定（勿改）：
 *  - 广播名 "dabaicai--test"（扫描列表置顶，其余设备也可手动选择）
 *  - 视频 TX 特征（Notify）：连续 H.264/H.265 Annex B 裸码流，无私有帧头
 *  - 音频 TX 特征（Notify）：PCM / ADTS AAC / Opus，默认 UUID 可由界面修改
 *  - RX 特征（Write）：ASCII 配置下发通道，固件侧解析待开发
 *  - 固件侧已请求 DLE 251 / MTU 247，App 侧仍主动协商 MTU 517 与 2M PHY
 */
@SuppressLint("MissingPermission") // 运行时权限由 MainActivity 统一申请与检查
class BleManager(context: Context, private val listener: Listener) {

    companion object {
        const val TARGET_NAME = "dabaicai--test"
        val SERVICE_UUID: UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5450")
        val TX_CHAR_UUID: UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5402")
        val DEFAULT_AUDIO_TX_CHAR_UUID: UUID =
            UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5403")
        val RX_CHAR_UUID: UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e5401")
        private val CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAX_RECONNECT_DELAY_MS = 15_000L
    }

    interface Listener {
        fun onScanResult(device: BluetoothDevice, rssi: Int)
        fun onScanFailed(errorCode: Int)
        fun onConnectionStateChanged(connected: Boolean)
        fun onReadyToSend(ready: Boolean)
        fun onVideoData(data: ByteArray)
        fun onAudioData(data: ByteArray)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var lastDevice: BluetoothDevice? = null
    private var wantConnected = false
    private var reconnectAttempts = 0
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var audioTxUuid = DEFAULT_AUDIO_TX_CHAR_UUID
    private val pendingSubscriptions = ArrayDeque<BluetoothGattCharacteristic>()
    private var subscriptionInProgress = false

    val rxBytes = AtomicLong(0)
    val videoRxBytes = AtomicLong(0)
    val audioRxBytes = AtomicLong(0)

    /** 修改下次服务发现时使用的音频 Notify UUID。 */
    fun setAudioTxUuid(value: String): Boolean = try {
        audioTxUuid = UUID.fromString(value.trim())
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    // ---------------- 扫描 ----------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            listener.onScanResult(result.device, result.rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onScanFailed(errorCode)
        }
    }

    fun startScan(): Boolean {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            listener.onError("蓝牙不可用或未开启")
            return false
        }
        if (scanning) return true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // 不设 ScanFilter：名称排序在 App 侧做，dabaicai--test 置顶，其余设备也列出
        scanner.startScan(null, settings, scanCallback)
        scanning = true
        return true
    }

    fun stopScan() {
        if (scanning) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            scanning = false
        }
    }

    // ---------------- 连接 / 断开 ----------------

    fun connect(device: BluetoothDevice) {
        stopScan()
        wantConnected = true
        reconnectAttempts = 0
        lastDevice = device
        resetSubscriptions()
        gatt?.close()
        // autoConnect = false：直连，首次连接更快
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        wantConnected = false
        handler.removeCallbacksAndMessages(null)
        gatt?.disconnect()
    }

    fun close() {
        wantConnected = false
        handler.removeCallbacksAndMessages(null)
        stopScan()
        resetSubscriptions()
        gatt?.close()
        gatt = null
        rxCharacteristic = null
    }

    private fun resetSubscriptions() {
        pendingSubscriptions.clear()
        subscriptionInProgress = false
    }

    /** 断线后指数退避自动重连：1s / 2s / 4s / 8s / 15s 封顶 */
    private fun scheduleReconnect() {
        if (!wantConnected) return
        val dev = lastDevice ?: return
        val delay = (1_000L shl reconnectAttempts.coerceAtMost(4))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempts++
        handler.postDelayed({
            if (wantConnected) {
                gatt = dev.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }, delay)
    }

    // ---------------- GATT 回调 ----------------

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    reconnectAttempts = 0
                    listener.onConnectionStateChanged(true)
                    // 连接后立即按顺序协商链路参数：
                    // 高优先级(无回调) -> MTU 517 -> 2M PHY -> 服务发现
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    g.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    rxCharacteristic = null
                    resetSubscriptions()
                    listener.onConnectionStateChanged(false)
                    listener.onReadyToSend(false)
                    g.close()
                    if (gatt === g) gatt = null
                    scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            // MTU 协商完成后切 2M PHY，提升吞吐（链路目标约 1.2 Mbps）
            g.setPreferredPhy(
                BluetoothDevice.PHY_LE_2M_MASK,
                BluetoothDevice.PHY_LE_2M_MASK,
                BluetoothDevice.PHY_OPTION_NO_PREFERRED
            )
        }

        override fun onPhyUpdate(g: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(SERVICE_UUID)
            rxCharacteristic = service?.getCharacteristic(RX_CHAR_UUID)
            resetSubscriptions()
            service?.getCharacteristic(TX_CHAR_UUID)?.let(pendingSubscriptions::addLast)
            if (audioTxUuid != TX_CHAR_UUID) {
                service?.getCharacteristic(audioTxUuid)?.let(pendingSubscriptions::addLast)
            }
            if (pendingSubscriptions.isEmpty()) {
                listener.onError("未找到视频或音频 TX 特征")
                listener.onReadyToSend(false)
                return
            }
            subscribeNext(g)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("订阅 ${descriptor.characteristic.uuid} 失败: $status")
            }
            subscriptionInProgress = false
            subscribeNext(g)
        }

        // API 33+ 新签名回调（value 直接作为参数，避免在 binder 线程上读 characteristic.value）
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            deliver(characteristic.uuid, value)
        }

        // API 32 及以下旧签名回调
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // API 33+ 若重写了新回调，系统不会再调旧回调；这里加 SDK 判断双保险
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                deliver(characteristic.uuid, characteristic.value)
            }
        }
    }

    private fun deliver(uuid: UUID, value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        when (uuid) {
            TX_CHAR_UUID -> {
                rxBytes.addAndGet(value.size.toLong())
                videoRxBytes.addAndGet(value.size.toLong())
                listener.onVideoData(value)
            }
            audioTxUuid -> {
                rxBytes.addAndGet(value.size.toLong())
                audioRxBytes.addAndGet(value.size.toLong())
                listener.onAudioData(value)
            }
        }
    }

    /** GATT 同一时间只写一个 CCCD，全部订阅完成后才开放 RX 配置通道。 */
    private fun subscribeNext(g: BluetoothGatt) {
        if (subscriptionInProgress) return
        val tx = pendingSubscriptions.pollFirst() ?: run {
            listener.onReadyToSend(rxCharacteristic != null)
            return
        }
        g.setCharacteristicNotification(tx, true)
        val cccd = tx.getDescriptor(CCC_UUID) ?: run {
            listener.onError("${tx.uuid} 缺少 CCC descriptor")
            subscribeNext(g)
            return
        }
        subscriptionInProgress = true
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(
                cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
        if (!started) {
            subscriptionInProgress = false
            listener.onError("无法订阅 ${tx.uuid}")
            subscribeNext(g)
        }
    }

    // ---------------- 配置下发（手机 → 设备） ----------------

    /**
     * 把 ASCII 文本写入 RX 特征。目前仅实现 App 侧写入；
     * 固件侧对分辨率/帧率配置的解析待开发。
     */
    fun sendText(text: String): Boolean {
        val g = gatt ?: return false
        val c = rxCharacteristic ?: return false
        val data = text.toByteArray(Charsets.US_ASCII)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(
                c, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            c.value = data
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }
}
