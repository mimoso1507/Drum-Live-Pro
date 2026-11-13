package com.drummatch.deluxe.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

/**
 * MIDI input from USB, virtual and Bluetooth LE controllers.
 * Note-on messages are reported through [onNote] on the main thread.
 */
@SuppressLint("MissingPermission")
class MidiInput(private val ctx: Context) {
    companion object { val BLE_MIDI_SERVICE: ParcelUuid = ParcelUuid.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700") }

    private val main = Handler(Looper.getMainLooper())
    val manager: MidiManager? = ctx.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val open = HashMap<Int, Pair<MidiDevice, List<MidiOutputPort>>>()
    val connectedNames: List<String> get() = open.values.map { name(it.first.info) }

    var onNote: ((note: Int, vel: Int, channel: Int) -> Unit)? = null
    var onDevicesChanged: (() -> Unit)? = null
    var onScanResult: ((BluetoothDevice, String) -> Unit)? = null

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) { openDevice(device) }
        override fun onDeviceRemoved(device: MidiDeviceInfo) { close(device.id) }
    }

    fun start() {
        val m = manager ?: return
        @Suppress("DEPRECATION")
        m.devices.forEach { openDevice(it) }
        m.registerDeviceCallback(deviceCallback, main)
    }

    fun stop() {
        manager?.unregisterDeviceCallback(deviceCallback)
        stopScan()
        open.keys.toList().forEach { close(it) }
    }

    fun name(info: MidiDeviceInfo): String {
        val p = info.properties
        return p.getString(MidiDeviceInfo.PROPERTY_NAME) ?: p.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "MIDI ${info.id}"
    }

    private fun openDevice(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0 || open.containsKey(info.id)) return
        manager?.openDevice(info, { dev -> if (dev != null) attach(dev) }, main)
    }

    private fun attach(dev: MidiDevice) {
        val ports = (0 until dev.info.outputPortCount).mapNotNull { idx ->
            dev.openOutputPort(idx)?.also { it.connect(Parser()) }
        }
        open[dev.info.id] = dev to ports
        onDevicesChanged?.invoke()
    }

    private fun close(id: Int) {
        open.remove(id)?.let { (d, ports) -> ports.forEach { runCatching { it.close() } }; runCatching { d.close() } }
        onDevicesChanged?.invoke()
    }

    /** Connects a Bluetooth LE MIDI device found by [scan]. */
    fun connectBluetooth(device: BluetoothDevice) {
        manager?.openBluetoothDevice(device, { dev -> if (dev != null) attach(dev) }, main)
    }

    private var scanCb: ScanCallback? = null
    fun scan(): Boolean {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val scanner = bm.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false
        stopScan()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val nm = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: result.device.address
                main.post { onScanResult?.invoke(result.device, nm) }
            }
        }
        scanCb = cb
        scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(BLE_MIDI_SERVICE).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        main.postDelayed({ stopScan() }, 20000)
        return true
    }

    fun stopScan() {
        val cb = scanCb ?: return
        scanCb = null
        runCatching {
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner?.stopScan(cb)
        }
    }

    val scanning get() = scanCb != null

    /** Running-status aware MIDI byte parser that extracts note-on events. */
    private inner class Parser : MidiReceiver() {
        private var status = 0
        private val data = IntArray(2)
        private var count = 0
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            for (i in offset until offset + count) feed(msg[i].toInt() and 0xff)
        }
        private fun feed(b: Int) {
            if (b >= 0xF8) return // realtime
            if (b >= 0x80) { status = if (b < 0xF0) b else 0; count = 0; return }
            if (status == 0) return
            val type = status and 0xF0
            val need = if (type == 0xC0 || type == 0xD0) 1 else 2
            data[count++] = b
            if (count < need) return
            count = 0
            if (type == 0x90 && data[1] > 0) {
                val n = data[0]; val v = data[1]; val ch = status and 0x0F
                onNote?.invoke(n, v, ch) // called on the MIDI thread for lowest latency
            }
        }
    }
}
