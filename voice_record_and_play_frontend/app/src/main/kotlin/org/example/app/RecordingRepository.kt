package org.example.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Recording(
    val id: String,
    val createdAtMillis: Long,
    val file: File,
    val durationMs: Long?,
    val sizeBytes: Long
)

internal object RecordingRepository {
    private const val DIR_NAME = "recordings"
    private const val FILE_PREFIX = "rec_"
    private const val FILE_EXT = ".wav"

    fun recordingsDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listRecordings(context: Context): List<Recording> {
        val dir = recordingsDir(context)
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_EXT) }
            ?.toList()
            ?: emptyList()

        return files
            .sortedByDescending { it.lastModified() }
            .map { f ->
                val createdAt = parseCreatedAtFromName(f.name) ?: f.lastModified()
                Recording(
                    id = f.nameWithoutExtension,
                    createdAtMillis = createdAt,
                    file = f,
                    durationMs = null,
                    sizeBytes = f.length()
                )
            }
    }

    fun createNewRecordingFile(context: Context, createdAtMillis: Long = System.currentTimeMillis()): File {
        val id = buildId(createdAtMillis)
        return File(recordingsDir(context), "$FILE_PREFIX$id$FILE_EXT")
    }

    fun deleteRecording(recording: Recording): Boolean = recording.file.delete()

    fun formatTitle(createdAtMillis: Long): String {
        val df = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "Recording ${df.format(Date(createdAtMillis))}"
    }

    fun formatMeta(createdAtMillis: Long, sizeBytes: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return "${df.format(Date(createdAtMillis))} • ${bytesToHuman(sizeBytes)} • WAV"
    }

    private fun buildId(createdAtMillis: Long): String {
        val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return df.format(Date(createdAtMillis))
    }

    private fun parseCreatedAtFromName(name: String): Long? {
        // Name example: rec_20260309_075500.wav
        return try {
            val base = name.removePrefix(FILE_PREFIX).removeSuffix(FILE_EXT)
            val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            df.parse(base)?.time
        } catch (_: Throwable) {
            null
        }
    }

    private fun bytesToHuman(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.size - 1) {
            v /= 1024.0
            i += 1
        }
        val digits = if (i == 0) 0 else if (i == 1) 1 else 2
        return "%.${digits}f %s".format(Locale.US, v, units[i])
    }
}
