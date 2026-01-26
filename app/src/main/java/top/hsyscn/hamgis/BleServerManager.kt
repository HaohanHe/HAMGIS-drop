package top.hsyscn.hamgis

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.net.Uri
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.CRC32

@SuppressLint("MissingPermission") // Permissions handled in MainActivity
class BleServerManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null

    // UUIDs
    // Use a simple, fresh UUID to avoid caching issues
    val SERVICE_UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
    val CHAR_WRITE_UUID = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages = _logMessages.asStateFlow()

    private val _connectionState = MutableStateFlow(context.getString(R.string.status_disconnected))
    val connectionState = _connectionState.asStateFlow()
    
    // Data callback
    var onDataReceived: ((String) -> Unit)? = null

    // State for receiving data
    private val receivedBytes = ByteArrayOutputStream()
    private var expectedLength = -1
    private var expectedCrc32: Long = -1

    fun startServer() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            log(context.getString(R.string.log_ble_not_enabled))
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            log(context.getString(R.string.log_adv_not_supported))
            return
        }

        startGattServer()
        startAdvertising()
    }

    fun stopServer() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        log(context.getString(R.string.log_server_stopped))
    }

    private fun startGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        if (gattServer == null) {
            log(context.getString(R.string.log_gatt_failed))
            return
        }

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val writeChar = BluetoothGattCharacteristic(
            CHAR_WRITE_UUID,
            // Add READ property to help with Service Discovery
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(writeChar)
        gattServer?.addService(service)
        log(context.getString(R.string.log_gatt_started))
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED) // Changed to Balanced for stability
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // Changed to Medium
            .build()

        // 简化广播包，确保兼容性
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // 有的手机开启名字会导致包超长
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
            
        // 扫描响应包放名字
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

            
        // Also change the Bluetooth Adapter Name temporarily to ensure it's recognizable
        val originalName = bluetoothAdapter?.name
        try {
            bluetoothAdapter?.name = "HAMGIS_Receiver"
        } catch (e: SecurityException) {
            // Ignore permission error if any
        }

        try {
            advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
            log(context.getString(R.string.log_adv_started) + " UUID: $SERVICE_UUID")
        } catch (e: Exception) {
            log("Start Advertising Failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            log(context.getString(R.string.log_adv_success))
        }

        override fun onStartFailure(errorCode: Int) {
            log(context.getString(R.string.log_adv_failure, errorCode))
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                 log("Service Added Successfully: ${service?.uuid}")
            } else {
                 log("Service Add Failed: $status")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            
            // Log raw status for debugging
            log("GATT State Change: Device=${device?.address} Status=$status NewState=$newState")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = context.getString(R.string.status_connected, device?.name ?: context.getString(R.string.status_unknown))
                log(context.getString(R.string.log_device_connected, device?.address))
                resetReception()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = context.getString(R.string.status_disconnected)
                log(context.getString(R.string.log_device_disconnected))
                resetReception()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            
            if (characteristic?.uuid == CHAR_WRITE_UUID) {
                value?.let { processIncomingData(it) }
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
        }
    }

    private fun processIncomingData(data: ByteArray) {
        val str = String(data, Charsets.UTF_8)
        
        // Check for Header
        if (str.startsWith("HAMGIS_HEAD:")) {
            // Format: HAMGIS_HEAD:<LEN>:<CRC32>
            try {
                val parts = str.split(":")
                expectedLength = parts[1].toInt()
                expectedCrc32 = parts[2].toLong(16) // Hex string to long
                receivedBytes.reset()
                log(context.getString(R.string.log_header_received, expectedLength, expectedCrc32.toString(16)))
            } catch (e: Exception) {
                log(context.getString(R.string.log_header_error, str))
                resetReception()
            }
            return
        }

        // Check for EOF
        if (str == "HAMGIS_EOF") {
            log(context.getString(R.string.log_eof_received))
            verifyAndSave()
            return
        }

        // Normal Data Chunk
        receivedBytes.write(data)
        log(context.getString(R.string.log_chunk_received, data.size, receivedBytes.size(), if(expectedLength>0) expectedLength.toString() else "?"))
    }

    private fun verifyAndSave() {
        if (expectedLength == -1) {
            log(context.getString(R.string.log_err_no_header))
            return
        }

        val fullData = receivedBytes.toByteArray()
        if (fullData.size != expectedLength) {
            log(context.getString(R.string.log_err_length_mismatch, expectedLength, fullData.size))
            // return // strict mode?
        }

        // Calculate CRC32
        val crc = CRC32()
        crc.update(fullData)
        val calculatedCrc = crc.value

        if (calculatedCrc == expectedCrc32) {
            log(context.getString(R.string.log_crc_verified, calculatedCrc.toString(16)))
            val jsonStr = String(fullData, Charsets.UTF_8)
            onDataReceived?.invoke(jsonStr)
        } else {
            log(context.getString(R.string.log_crc_mismatch, expectedCrc32.toString(16), calculatedCrc.toString(16)))
        }
    }

    private fun resetReception() {
        receivedBytes.reset()
        expectedLength = -1
        expectedCrc32 = -1
    }

    private fun log(msg: String) {
        Log.d("BleServer", msg)
        val list = _logMessages.value.toMutableList()
        list.add(0, msg) // Add to top
        _logMessages.value = list
    }
}
