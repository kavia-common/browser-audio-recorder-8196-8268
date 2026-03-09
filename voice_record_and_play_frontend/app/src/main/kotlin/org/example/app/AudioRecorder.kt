package org.example.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class AudioRecorder(
    private val outputFile: File
) {
    private val isRecording = AtomicBoolean(false)
    private var thread: Thread? = null
    private var audioRecord: AudioRecord? = null

    // Common, supported defaults.
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val channelCount = 1
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    fun start(onError: (Throwable) -> Unit) {
        if (isRecording.get()) return

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            onError(IllegalStateException("Unsupported audio record configuration"))
            return
        }

        val bufferSize = minBuffer.coerceAtLeast(sampleRate * 2) // ~1s buffer minimum

        val record: AudioRecord
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                bufferSize
            )
        } catch (se: SecurityException) {
            // Permission can be revoked at runtime; callers must handle this gracefully.
            onError(se)
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            onError(IllegalStateException("AudioRecord not initialized"))
            record.release()
            return
        }

        val wavWriter = WavWriter(outputFile, sampleRate, channelCount)
        try {
            wavWriter.open()
        } catch (t: Throwable) {
            record.release()
            onError(t)
            return
        }

        audioRecord = record
        isRecording.set(true)

        thread = Thread {
            try {
                try {
                    record.startRecording()
                } catch (se: SecurityException) {
                    throw se
                }

                val buf = ByteArray(bufferSize)
                while (isRecording.get()) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        wavWriter.writePcm16Le(buf, read)
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (t: Throwable) {
                onError(t)
            } finally {
                try {
                    record.stop()
                } catch (_: Throwable) {
                }
                record.release()
                audioRecord = null
                try {
                    wavWriter.close()
                } catch (_: Throwable) {
                }
                isRecording.set(false)
            }
        }.also { it.start() }
    }

    fun stop() {
        if (!isRecording.get()) return
        isRecording.set(false)
        // Thread will stop after loop; no busy wait here.
    }

    fun isRecording(): Boolean = isRecording.get()
}
