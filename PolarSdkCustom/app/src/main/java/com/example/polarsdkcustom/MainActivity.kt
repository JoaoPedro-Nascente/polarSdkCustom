package com.example.polarsdkcustom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.polarsdkcustom.ui.theme.PolarSdkCustomTheme
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        //private const val API_LOGGER_TAG = "API LOGGER"
        private const val PERMISSION_REQUEST_CODE = 1
    }

    private var deviceId = "C621D624"//"C61E8A23"

    private var deviceConnected = false

    private var autoConnectDisposable: Disposable? = null
    private var scanDevicesDisposable: Disposable? = null
    private var hrDisposable: Disposable? = null
    private var ecgDisposable: Disposable? = null
    private var accDisposable: Disposable? = null

    private lateinit var connectButton: Button
    private lateinit var autoConnectButton: Button
    private lateinit var scanDevicesButton: Button
    private lateinit var hrButton: Button
    private lateinit var ecgButton: Button
    private lateinit var accButton: Button
    private lateinit var treadmillConnect: Button

    private lateinit var dbConnection: InfluxDBConnection

    private val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectButton = findViewById(R.id.connect_button)
        autoConnectButton = findViewById(R.id.autoConnect_button)
        scanDevicesButton = findViewById(R.id.scanDevices_button)
        hrButton = findViewById(R.id.hr_button)
        ecgButton = findViewById(R.id.ecg_button)
        accButton = findViewById(R.id.acc_button)

        treadmillConnect = findViewById(R.id.connectTreadmill)

        api.setPolarFilter(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        dbConnection = InfluxDBConnection()
        //treadmillController = TreadmillController(this)

        api.setApiCallback(object : PolarBleApiCallback() {

            override fun blePowerStateChanged(powered: Boolean) {
                Log.d("MyApp", "BLE: power: $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                deviceConnected = true
                deviceId = polarDeviceInfo.deviceId

                Log.d("MyApp", "CONNECTED: ${polarDeviceInfo.deviceId}")

                toggleButton(connectButton, false, getString(R.string.disconnect, deviceId))
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                deviceConnected = false

                Log.d("MyApp", "DISCONNECTED: ${polarDeviceInfo.deviceId}")

                toggleButton(connectButton, true, getString(R.string.connect, deviceId))
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature
            ) {
                Log.d(TAG, "Polar BLE SDK feature $feature is ready")
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d("MyApp", "DIS INFO uuid: $uuid value $value")
            }

            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                TODO("Not yet implemented")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d("MyApp", "BATTERY LEVEL: $level")
            }
        })

        connectButton.text = getString(R.string.connect, deviceId)
        connectButton.setOnClickListener {
            try {
                if (deviceConnected) {
                    api.disconnectFromDevice(deviceId)
                } else {
                    api.connectToDevice(deviceId)
                }
            } catch (polarInvalidArgument: PolarInvalidArgument){
                val attempt = if (deviceConnected){
                    "disconnect"
                } else {
                    "connect"
                }
                Log.e(TAG, "Failed to $attempt. Reason $polarInvalidArgument")
            }
        }

        autoConnectButton.setOnClickListener {
            if (autoConnectDisposable != null) {
                autoConnectDisposable?.dispose()
            }
            autoConnectDisposable = api.autoConnectToDevice(-60, "180D", null)
                .subscribe(
                    { Log.d(TAG, "Auto connect search complete") },
                    { throwable: Throwable -> Log.e(TAG, ""+throwable.toString())}
                )
        }

        scanDevicesButton.setOnClickListener {
            val isDisposed = scanDevicesDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButton(scanDevicesButton, false, getString(R.string.scanDevicesOff))
                scanDevicesDisposable = api.searchForDevice()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { polarDeviceInfo: PolarDeviceInfo ->
                            Log.d(TAG, "polar device found id: ${polarDeviceInfo.deviceId} address: ${polarDeviceInfo.address} rssi: ${polarDeviceInfo.rssi} name: ${polarDeviceInfo.name} isConnectable: ${polarDeviceInfo.isConnectable}")
                        },
                        { error: Throwable ->
                            toggleButton(scanDevicesButton, true, getString(R.string.scanDevices))
                            Log.e(TAG, "Device search failed. Reason $error")
                        },
                        {
                            toggleButton(scanDevicesButton, true, getString(R.string.scanDevices))
                            Log.d(TAG, "complete")
                        }
                    )
            } else {
                toggleButton(scanDevicesButton, true, getString(R.string.scanDevices))
                scanDevicesDisposable?.dispose()
            }
        }


        hrButton.setOnClickListener {
            val isDisposed = hrDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButton(hrButton, false, getString(R.string.hr_off))
                hrDisposable = api.startHrStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { hrData: PolarHrData ->
                            for (sample in hrData.samples) {
                                Log.d(TAG, "HR     bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}")
                                CoroutineScope(Dispatchers.Main).launch {
                                    dbConnection.writeData("polarHr", "hr", sample.hr)
                                    if(sample.rrAvailable){
                                        for(rrs in sample.rrsMs){
                                            dbConnection.writeData("polarRrs", "rrs", rrs)
                                        }
                                    }
                                }
                            }
                        },
                        { error: Throwable ->
                            toggleButton(hrButton, true, getString(R.string.hr))
                            Log.e(TAG, "HR stream failed. Reason $error")
                        },
                        { Log.d(TAG, "Stream complete") }
                    )
            } else {
                toggleButton(hrButton, true, getString(R.string.hr))
                hrDisposable?.dispose()
            }
        }

        ecgButton.setOnClickListener {
            val isDisposed = ecgDisposable?.isDisposed ?: true

            if(isDisposed) {
                toggleButton(ecgButton, false, getString(R.string.ecg_off))

                val defaultSampleRate = 130
                val defaultResolution = 14

                val ecgSettings = PolarSensorSetting(
                    mapOf(
                        PolarSensorSetting.SettingType.SAMPLE_RATE to defaultSampleRate,
                        PolarSensorSetting.SettingType.RESOLUTION to defaultResolution
                    )
                )
                ecgDisposable = api.startEcgStreaming(deviceId, ecgSettings)
                    .subscribe(
                        { polarEcgData: PolarEcgData ->
                            for (data in polarEcgData.samples) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    dbConnection.writeData("polarEcg", "voltage", data.voltage)
                                }
                            }
                        },
                        { error: Throwable ->
                            toggleButton(ecgButton, true, getString(R.string.ecg))
                            Log.e(TAG, "ECG stream failed. Reason $error")
                        },
                        { Log.d(TAG, "ECG stream complete") }
                    )

                //dbConnection.startPublishing(pointsList, dbConnection)

            } else {
                toggleButton(ecgButton, true, getString(R.string.ecg))
                // NOTE stops streaming if it is "running"
                ecgDisposable?.dispose()
            }
        }

        accButton.setOnClickListener {
            val isDisposed = accDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButton(accButton, false, getString(R.string.acc_off))
                val defaultSampleRate = 200
                val defaultResolution = 16
                val defaultRange = 8

                val accSettings = PolarSensorSetting(
                    mapOf(
                        PolarSensorSetting.SettingType.SAMPLE_RATE to defaultSampleRate,
                        PolarSensorSetting.SettingType.RESOLUTION to defaultResolution,
                        PolarSensorSetting.SettingType.RANGE to defaultRange
                    )
                )

                accDisposable = api.startAccStreaming(deviceId, accSettings)
                    .subscribe(
                        { polarAccelerometerData: PolarAccelerometerData ->
                            for (data in polarAccelerometerData.samples) {
                                Log.d(TAG, "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timestamp: ${data.timeStamp}")
                            }
                        },
                        { error: Throwable ->
                            toggleButton(accButton, true, getString(R.string.acc))
                            Log.e(TAG, "ACC stream failed. Reason $error")
                        },
                        {
                            Log.d(TAG, "ACC stream complete")
                        }
                    )
            } else {
                toggleButton(accButton, true, getString(R.string.acc))
                accDisposable?.dispose()
            }
        }

        treadmillConnect.setOnClickListener {

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
        dbConnection.closeConnection()
    }

    private fun toggleButton(button: Button, isDown: Boolean, text: String? = null) {
        if (text != null) button.text = text

        var buttonDrawable = button.background
        buttonDrawable = DrawableCompat.wrap(buttonDrawable!!)
        if (isDown) {
            DrawableCompat.setTint(buttonDrawable, ContextCompat.getColor(this, R.color.primary_color))
        } else {
            DrawableCompat.setTint(buttonDrawable, ContextCompat.getColor(this, R.color.secondary_color))
        }
        button.background = buttonDrawable
    }
}