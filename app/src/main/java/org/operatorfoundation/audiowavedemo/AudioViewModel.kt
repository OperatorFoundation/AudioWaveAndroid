package org.operatorfoundation.audiowavedemo

import android.hardware.usb.UsbDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AudioViewModel : ViewModel() {

    // Connected devices
    private val _connectedDevices = MutableLiveData<List<UsbDevice>>()
    val connectedDevices: LiveData<List<UsbDevice>> = _connectedDevices

    // Currently connected device
    private val _connectedDevice = MutableLiveData<UsbDevice?>()
    val connectedDevice: LiveData<UsbDevice?> = _connectedDevice

    // Processing status
    private val _isProcessingActive = MutableLiveData(false)
    val isProcessingActive: LiveData<Boolean> = _isProcessingActive

    // Audio level meter (0.0 - 1.0)
    private val _audioLevel = MutableLiveData(0.0f)
    val audioLevel: LiveData<Float> = _audioLevel

    // Available decoders
    private val _availableDecoders = MutableLiveData<List<String>>()
    val availableDecoders: LiveData<List<String>> = _availableDecoders

    // Active decoder
    private val _activeDecoder = MutableLiveData<String?>()
    val activeDecoder: LiveData<String?> = _activeDecoder

    // Decoded data
    private val _decodedData = MutableLiveData<ByteArray?>()
    val decodedData: LiveData<ByteArray?> = _decodedData

    // Update the list of connected devices
    fun updateConnectedDevices(devices: List<UsbDevice>) {
        _connectedDevices.value = devices
    }

    // Set the currently connected device
    fun setConnectedDevice(device: UsbDevice?) {
        _connectedDevice.value = device
    }

    // Set whether audio processing is active
    fun setProcessingActive(active: Boolean) {
        _isProcessingActive.value = active
    }

    // Update the audio level meter
    fun setAudioLevel(level: Float) {
        _audioLevel.value = level
    }

    // Update available decoders
    fun updateAvailableDecoders(decoders: List<String>) {
        _availableDecoders.value = decoders
    }

    // Set the active decoder
    fun setActiveDecoder(decoderId: String?) {
        _activeDecoder.value = decoderId
    }

    // Set decoded data
    fun setDecodedData(data: ByteArray) {
        _decodedData.value = data
    }
}
