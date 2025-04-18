# AudioWave Library

A powerful Android library for capturing, processing, and decoding USB audio input with support for radio wave signal analysis and transformations.

## Features

- **USB Audio Device Support**: Detect, connect to, and capture audio from USB audio devices
- **Audio Processing**: Real-time audio processing with configurable effects chain
- **Signal Decoders**: Support for various signal decoders, including AM/FM radio wave decoding
- **Extensible Architecture**: Easy to add custom effects and decoders
- **Thread Safety**: Proper background threading for audio processing

## Getting Started

### Setup

1. Add the library to your project:

```gradle
dependencies {
    implementation project(':audiowave-library')
}
```

2. Add USB permissions to your AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-feature android:name="android.hardware.usb.host" android:required="true" />
```

3. Initialize the library in your Activity or Service:

```kotlin
private lateinit var audioWaveManager: AudioWaveManager

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Get the singleton instance
    audioWaveManager = AudioWaveManager.getInstance(applicationContext)
    
    // Initialize the library
    audioWaveManager.initialize { success ->
        if (success) {
            // Library initialized successfully
        }
    }
}
```

### Capturing Audio

1. Set a callback to receive audio data:

```kotlin
audioWaveManager.setAudioCaptureCallback(object : AudioCaptureCallback {
    override fun onAudioDataCaptured(data: ByteArray) {
        // Process raw audio data
        // Note: This is called on a background thread!
    }
    
    override fun onAudioDataDecoded(data: ByteArray) {
        // Process decoded data (if a decoder is active)
    }
})
```

2. Get available devices and start capturing:

```kotlin
// Get connected USB audio devices
val devices = audioWaveManager.getConnectedDevices()

// Connect to the first device
if (devices.isNotEmpty()) {
    audioWaveManager.startCapture(devices[0]) { success ->
        if (success) {
            // Started capturing audio
        }
    }
}
```

### Processing Audio

You can add effects to the audio processing chain:

```kotlin
// Import effect classes
import com.audiowave.effects.GainEffect
import com.audiowave.effects.EchoEffect

// Add a gain effect to control volume
val gainEffect = GainEffect()
gainEffect.setGain(1.5f)  // Increase volume by 50%
audioWaveManager.addEffect(gainEffect)

// Add an echo effect
val echoEffect = EchoEffect()
echoEffect.setDelay(0.3f)  // 300ms delay
echoEffect.setDecay(0.6f)  // 60% decay
audioWaveManager.addEffect(echoEffect)
```

### Decoding Radio Signals

You can set a decoder to process and decode radio signals:

```kotlin
// Get available decoders
val decoders = audioWaveManager.getAvailableDecoders()

// Set the radio wave decoder
audioWaveManager.setDecoder("radio_wave")

// Configure the decoder (for AM/FM radio)
val radioDecoder = audioWaveManager.getDecoder("radio_wave")
radioDecoder?.configure(mapOf(
    "mode" to "fm",
    "centerFrequency" to 98.5f,  // FM frequency in MHz
    "bandwidth" to 15.0f         // Bandwidth in kHz
))
```

### Cleaning Up

Don't forget to release resources when you're done:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    audioWaveManager.release()
}
```

## Architecture

The AudioWave library consists of several key components:

- **AudioWaveManager**: Main entry point for the library
- **UsbDeviceManager**: Handles USB device connection and data transfer
- **AudioProcessor**: Processes audio with a chain of effects
- **DecoderRegistry**: Manages available audio decoders
- **Effects**: Modifies audio in real-time (gain, echo, etc.)
- **Decoders**: Translates audio into meaningful data (AM/FM signals, etc.)

## Creating Custom Components

### Custom Effects

You can create custom audio effects by implementing the `Effect` interface:

```kotlin
class MyCustomEffect : Effect {
    override val id: String = "my_custom_effect"
    override val name: String = "My Custom Effect"
    
    private var enabled = true
    
    override fun process(samples: ShortArray): ShortArray {
        if (!enabled) return samples
        
        // Process samples here
        val result = ShortArray(samples.size)
        for (i in samples.indices) {
            // Apply your effect transformation
            result[i] = transformSample(samples[i])
        }
        
        return result
    }
    
    override fun isEnabled(): Boolean = enabled
    
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    private fun transformSample(sample: Short): Short {
        // Your custom transformation
        return sample
    }
}
```

### Custom Decoders

You can create custom signal decoders by implementing the `AudioDecoder` interface:

```kotlin
class MyCustomDecoder : AudioDecoder {
    override val id: String = "my_custom_decoder"
    override val name: String = "My Custom Decoder"
    override val description: String = "Decodes custom protocol signals"
    
    override fun decode(audioData: ByteArray): ByteArray {
        // Decode the audio data
        // Convert to meaningful information
        
        return decodedData
    }
    
    override fun configure(params: Map<String, Any>) {
        // Configure decoder parameters
    }
}

// Then register your decoder
val decoderRegistry = DecoderRegistry()
decoderRegistry.registerDecoder(MyCustomDecoder())
```

## Advanced Usage

### Signal Processing

The library includes a `SignalProcessor` utility class with common signal processing operations:

```kotlin
import com.audiowave.utils.SignalProcessor

val signalProcessor = SignalProcessor()

// Apply filters
val filteredSamples = signalProcessor.applyLowPassFilter(samples, 1000f, 44100)

// Perform FFT for frequency analysis
val frequencyData = signalProcessor.fft(samples)
```

### Thread Management

The AudioWave library handles threading internally, but be aware that callbacks occur on background threads. Always use proper thread synchronization when updating UI:

```kotlin
override fun onAudioDataCaptured(data: ByteArray) {
    // Calculate audio level
    val level = calculateLevel(data)
    
    // Update UI on main thread
    runOnUiThread {
        levelMeter.progress = (level * 100).toInt()
    }
}
```

## License

AudioWave Library is licensed under the MIT License.