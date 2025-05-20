package org.operatorfoundation.audiowavedemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.operatorfoundation.audiowavedemo.AudioViewModel
import org.operatorfoundation.audiowavedemo.MainActivity
import org.operatorfoundation.audiowavedemo.R

class DeviceFragment : Fragment() {

    private lateinit var viewModel: AudioViewModel
    private lateinit var deviceListView: ListView
    private lateinit var statusTextView: TextView
    private lateinit var refreshButton: Button

    private var deviceAdapter: ArrayAdapter<String>? = null
    private var deviceList: List<UsbDevice> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_device, container, false)

        deviceListView = root.findViewById(R.id.deviceListView)
        statusTextView = root.findViewById(R.id.statusTextView)
        refreshButton = root.findViewById(R.id.refreshButton)

        // Initialize the adapter
        deviceAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            ArrayList<String>()
        )
        deviceListView.adapter = deviceAdapter

        setupListeners()

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AudioViewModel::class.java]

        // Observe changes to connected devices
        viewModel.connectedDevices.observe(viewLifecycleOwner) { devices ->
            updateDeviceList(devices)
        }

        // Observe changes to the connected device
        viewModel.connectedDevice.observe(viewLifecycleOwner) { device ->
            updateConnectedDeviceStatus(device)
        }

        // Observe processing status
        viewModel.isProcessingActive.observe(viewLifecycleOwner) { isActive ->
            updateProcessingStatus(isActive)
        }
    }

    private fun setupListeners() {
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            if (position >= 0 && position < deviceList.size) {
                val device = deviceList[position]
                connectToDevice(device)
            }
        }

        refreshButton.setOnClickListener {
            (requireActivity() as MainActivity).initializeAudioWave()
        }
    }

    private fun updateDeviceList(devices: List<UsbDevice>) {
        deviceList = devices
        deviceAdapter?.clear()

        if (devices.isEmpty()) {
            deviceAdapter?.add("No USB audio devices found")
            statusTextView.text = "No devices connected"
        } else {
            devices.forEach { device ->
                deviceAdapter?.add(
                    "Device: ${device.deviceName} (ID: ${device.deviceId})"
                )
            }
            statusTextView.text = "${devices.size} device(s) found"
        }

        deviceAdapter?.notifyDataSetChanged()
    }

    private fun updateConnectedDeviceStatus(device: UsbDevice?) {
        if (device != null) {
            statusTextView.text = "Connected to: ${device.deviceName}"
        }
    }

    private fun updateProcessingStatus(isActive: Boolean) {
        if (isActive) {
            statusTextView.text = "${statusTextView.text} (Processing Active)"
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        (requireActivity() as MainActivity).connectToDevice(device)
    }
}