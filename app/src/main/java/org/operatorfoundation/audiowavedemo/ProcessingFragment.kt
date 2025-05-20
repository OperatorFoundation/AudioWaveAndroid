package org.operatorfoundation.audiowavedemo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.operatorfoundation.audiowavedemo.AudioViewModel
import org.operatorfoundation.audiowavedemo.MainActivity
import org.operatorfoundation.audiowavedemo.R

class ProcessingFragment : Fragment() {

    private lateinit var viewModel: AudioViewModel

    private lateinit var deviceStatusTextView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var audioLevelProgressBar: ProgressBar
    private lateinit var decoderSpinner: Spinner
    private lateinit var visualizerView: VisualizerView

    private var decoderAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_processing, container, false)

        deviceStatusTextView = root.findViewById(R.id.deviceStatusTextView)
        startButton = root.findViewById(R.id.startButton)
        stopButton = root.findViewById(R.id.stopButton)
        audioLevelProgressBar = root.findViewById(R.id.audioLevelProgressBar)
        decoderSpinner = root.findViewById(R.id.decoderSpinner)
        visualizerView = root.findViewById(R.id.visualizerView)

        // Initialize decoder spinner
        decoderAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            ArrayList<String>()
        )
        decoderAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        decoderSpinner.adapter = decoderAdapter

        setupListeners()

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AudioViewModel::class.java]

        // Observe changes to the connected device
        viewModel.connectedDevice.observe(viewLifecycleOwner) { device ->
            updateDeviceStatus(device != null)
        }

        // Observe processing status
        viewModel.isProcessingActive.observe(viewLifecycleOwner) { isActive ->
            updateButtonState(isActive)
        }

        // Observe audio level
        viewModel.audioLevel.observe(viewLifecycleOwner) { level ->
            updateAudioLevel(level)
        }

        // Observe available decoders
        viewModel.availableDecoders.observe(viewLifecycleOwner) { decoders ->
            updateDecoderList(decoders)
        }

        // Observe active decoder
        viewModel.activeDecoder.observe(viewLifecycleOwner) { decoder ->
            updateActiveDecoder(decoder)
        }

        // Observe decoded data
        viewModel.decodedData.observe(viewLifecycleOwner) { data ->
            data?.let { visualizerView.updateData(it) }
        }
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            (requireActivity() as MainActivity).startAudioCapture()
        }

        stopButton.setOnClickListener {
            (requireActivity() as MainActivity).stopAudioCapture()
        }

        decoderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val decoderId = decoderAdapter?.getItem(position)
                decoderId?.let {
                    (requireActivity() as MainActivity).setDecoder(it)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun updateDeviceStatus(connected: Boolean) {
        if (connected) {
            deviceStatusTextView.text = "Device connected"
            startButton.isEnabled = true
        } else {
            deviceStatusTextView.text = "No device connected"
            startButton.isEnabled = false
            stopButton.isEnabled = false
        }
    }

    private fun updateButtonState(isProcessing: Boolean) {
        startButton.isEnabled = !isProcessing
        stopButton.isEnabled = isProcessing
    }

    private fun updateAudioLevel(level: Float) {
        // Convert 0.0-1.0 level to progress (0-100)
        val progress = (level * 100).toInt().coerceIn(0, 100)
        audioLevelProgressBar.progress = progress
    }

    private fun updateDecoderList(decoders: List<String>) {
        decoderAdapter?.clear()
        decoderAdapter?.add("None (Raw Audio)")
        decoders.forEach { decoder ->
            decoderAdapter?.add(decoder)
        }
        decoderAdapter?.notifyDataSetChanged()
    }

    private fun updateActiveDecoder(decoderId: String?) {
        if (decoderId != null) {
            // Find the position of the decoder in the adapter
            for (i in 0 until (decoderAdapter?.count ?: 0)) {
                if (decoderAdapter?.getItem(i) == decoderId) {
                    decoderSpinner.setSelection(i)
                    break
                }
            }
        } else {
            // Select "None" option
            decoderSpinner.setSelection(0)
        }
    }
}
