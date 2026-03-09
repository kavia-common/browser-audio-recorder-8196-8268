package org.example.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : FragmentActivity() {

    private lateinit var statusText: TextView
    private lateinit var nowPlayingMeta: TextView

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSave: Button
    private lateinit var btnPlay: Button
    private lateinit var btnStopPlayback: Button

    private lateinit var recordingsList: RecyclerView
    private lateinit var adapter: RecordingsAdapter

    private var pendingFilePathForSave: String? = null
    private var currentOutputFile = null as java.io.File?
    private var recorder: AudioRecorder? = null

    private var selectedRecording: Recording? = null
    private var mediaPlayer: MediaPlayer? = null

    private var tempRecordingCreatedAt: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        nowPlayingMeta = findViewById(R.id.nowPlayingMeta)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnSave = findViewById(R.id.btnSave)
        btnPlay = findViewById(R.id.btnPlay)
        btnStopPlayback = findViewById(R.id.btnStopPlayback)

        recordingsList = findViewById(R.id.recordingsList)
        recordingsList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        adapter = RecordingsAdapter(
            items = emptyList(),
            onSelected = { rec ->
                selectedRecording = rec
                updateNowPlaying(rec)
                updatePlaybackButtons()
            },
            onDelete = { rec ->
                stopPlayback()
                RecordingRepository.deleteRecording(rec)
                refreshRecordings(focusFirst = true)
            }
        )
        recordingsList.adapter = adapter

        btnStart.setOnClickListener { ensurePermissionAndStartRecording() }
        btnStop.setOnClickListener { stopRecording() }
        btnSave.setOnClickListener { saveLastRecording() }

        btnPlay.setOnClickListener { playSelected() }
        btnStopPlayback.setOnClickListener { stopPlayback() }

        refreshRecordings(focusFirst = false)
        setStatus(getString(R.string.status_idle))

        // Give initial focus to Start.
        btnStart.post { btnStart.requestFocus() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        recorder?.stop()
    }

    private fun ensurePermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
            return
        }

        setStatus(getString(R.string.status_permission_needed))
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) startRecording()
            else setStatus(getString(R.string.status_permission_needed))
        }
    }

    private fun startRecording() {
        stopPlayback()

        tempRecordingCreatedAt = System.currentTimeMillis()
        val outFile = RecordingRepository.createNewRecordingFile(this, createdAtMillis = tempRecordingCreatedAt!!)
        currentOutputFile = outFile

        recorder = AudioRecorder(outFile).also { rec ->
            setStatus(getString(R.string.status_recording))
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            btnSave.isEnabled = false

            rec.start { t ->
                runOnUiThread {
                    setStatus("Error: ${t.message ?: t.javaClass.simpleName}")
                    btnStart.isEnabled = true
                    btnStop.isEnabled = false
                    btnSave.isEnabled = false
                }
            }
        }
    }

    private fun stopRecording() {
        recorder?.stop()
        recorder = null

        setStatus(getString(R.string.status_ready))
        btnStart.isEnabled = true
        btnStop.isEnabled = false

        // Enable save only if file exists and has some data.
        val f = currentOutputFile
        btnSave.isEnabled = f != null && f.exists() && f.length() > 44 // header only is 44 bytes
    }

    private fun saveLastRecording() {
        // We already record directly into the final file inside internal storage, so "Save"
        // just refreshes list and locks the save button to avoid duplicates.
        val f = currentOutputFile
        if (f == null || !f.exists()) return

        btnSave.isEnabled = false
        refreshRecordings(focusFirst = true)

        // After saving, auto-select the newest for quick playback.
        val first = RecordingRepository.listRecordings(this).firstOrNull()
        if (first != null) {
            selectedRecording = first
            updateNowPlaying(first)
            updatePlaybackButtons()
        }
    }

    private fun refreshRecordings(focusFirst: Boolean) {
        val items = RecordingRepository.listRecordings(this)
        adapter.submit(items)

        if (items.isEmpty()) {
            selectedRecording = null
            nowPlayingMeta.text = "No recordings"
            updatePlaybackButtons()
            return
        }

        if (focusFirst) {
            recordingsList.post {
                recordingsList.scrollToPosition(0)
                val vh = recordingsList.findViewHolderForAdapterPosition(0)
                (vh?.itemView ?: recordingsList).requestFocus()
            }
        }
    }

    private fun updateNowPlaying(rec: Recording) {
        nowPlayingMeta.text = RecordingRepository.formatMeta(rec.createdAtMillis, rec.sizeBytes)
    }

    private fun playSelected() {
        val rec = selectedRecording ?: return
        stopPlayback()

        val mp = MediaPlayer()
        mediaPlayer = mp

        try {
            mp.setDataSource(rec.file.absolutePath)
            mp.setOnPreparedListener {
                it.start()
                updatePlaybackButtons()
            }
            mp.setOnCompletionListener {
                stopPlayback()
            }
            mp.prepareAsync()
            setStatus("Playing…")
        } catch (t: Throwable) {
            setStatus("Playback error: ${t.message ?: t.javaClass.simpleName}")
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.let {
            try {
                it.stop()
            } catch (_: Throwable) {
            }
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        mediaPlayer = null
        updatePlaybackButtons()
    }

    private fun updatePlaybackButtons() {
        val hasSelection = selectedRecording != null
        val isPlaying = mediaPlayer != null

        btnPlay.isEnabled = hasSelection && !isPlaying
        btnStopPlayback.isEnabled = isPlaying

        // If actively recording, keep playback disabled.
        val recordingNow = btnStop.isEnabled
        if (recordingNow) {
            btnPlay.isEnabled = false
            btnStopPlayback.isEnabled = false
        }
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    companion object {
        private const val REQ_RECORD_AUDIO = 1001
    }
}
