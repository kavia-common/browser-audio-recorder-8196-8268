package org.example.app

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

internal class WavWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channelCount: Int
) {
    private val raf = RandomAccessFile(file, "rw")
    private var pcmBytesWritten: Long = 0

    fun open() {
        raf.setLength(0)
        writeWavHeaderPlaceholder()
    }

    fun writePcm16Le(buffer: ByteArray, length: Int) {
        raf.write(buffer, 0, length)
        pcmBytesWritten += length.toLong()
    }

    fun close() {
        finalizeHeader()
        raf.close()
    }

    private fun writeWavHeaderPlaceholder() {
        // RIFF header (44 bytes)
        raf.seek(0)
        raf.writeBytes("RIFF")
        writeIntLe(0) // file size - 8 (placeholder)
        raf.writeBytes("WAVE")

        raf.writeBytes("fmt ")
        writeIntLe(16) // PCM fmt chunk size
        writeShortLe(1) // AudioFormat = PCM
        writeShortLe(channelCount.toShort()) // NumChannels
        writeIntLe(sampleRate) // SampleRate
        val byteRate = sampleRate * channelCount * 2
        writeIntLe(byteRate) // ByteRate
        val blockAlign = (channelCount * 2).toShort()
        writeShortLe(blockAlign) // BlockAlign
        writeShortLe(16) // BitsPerSample

        raf.writeBytes("data")
        writeIntLe(0) // data chunk size (placeholder)
    }

    private fun finalizeHeader() {
        val dataSize = pcmBytesWritten
        val riffSize = 36L + dataSize // 4 + (8+fmt) + (8+data)
        raf.seek(4)
        writeIntLe(riffSize.toInt())
        raf.seek(40)
        writeIntLe(dataSize.toInt())
    }

    private fun writeIntLe(value: Int) {
        raf.write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        ))
    }

    private fun writeShortLe(value: Short) {
        val v = value.toInt()
        raf.write(byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte()
        ))
    }

    companion object {
        fun safeWriteAll(writer: WavWriter, pcm: ByteArray) {
            var off = 0
            while (off < pcm.size) {
                val len = min(4096, pcm.size - off)
                writer.writePcm16Le(pcm, len)
                off += len
            }
        }
    }
}
