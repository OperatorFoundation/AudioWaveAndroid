package org.operatorfoundation.audiowave

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Processes audio data with configurable effects chain
 */
class AudioProcessor
{
    private val effects = CopyOnWriteArrayList<Effect>()
}