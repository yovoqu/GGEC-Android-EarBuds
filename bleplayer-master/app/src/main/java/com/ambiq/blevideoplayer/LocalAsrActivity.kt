package com.ambiq.blevideoplayer

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** 独立本地转写页：不连接 BLE，便于单独验证音频解码、模型与中英文识别效果。 */
class LocalAsrActivity : AppCompatActivity(),
    LocalAudioDecoder.Listener,
    SpeechRecognizer.Listener {

    private lateinit var spModel: Spinner
    private lateinit var spSampleRate: Spinner
    private lateinit var spChannels: Spinner
    private lateinit var btnPick: Button
    private lateinit var btnStart: Button
    private lateinit var btnCancel: Button
    private lateinit var tvFile: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var transcriptScroll: ScrollView

    private lateinit var audioDecoder: LocalAudioDecoder
    private lateinit var speechRecognizer: SpeechRecognizer
    private var selectedUri: Uri? = null
    private var transcribing = false
    private val finalText = StringBuilder()
    private var partialText = ""

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            tvFile.text = uri.toString()
            tvStatus.text = "已选择音频，可以开始转写"
            updateButtons()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_asr)
        title = "本地音频转写"

        spModel = findViewById(R.id.spLocalModel)
        spSampleRate = findViewById(R.id.spLocalSampleRate)
        spChannels = findViewById(R.id.spLocalChannels)
        btnPick = findViewById(R.id.btnPickAudio)
        btnStart = findViewById(R.id.btnStartTranscribe)
        btnCancel = findViewById(R.id.btnCancelTranscribe)
        tvFile = findViewById(R.id.tvSelectedFile)
        tvStatus = findViewById(R.id.tvLocalStatus)
        tvTranscript = findViewById(R.id.tvLocalTranscript)
        transcriptScroll = findViewById(R.id.localTranscriptScroll)

        spModel.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, AsrModel.entries.map { it.displayName }
        )
        spSampleRate.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, listOf("16000", "8000", "44100", "48000")
        )
        spChannels.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, listOf("单声道", "双声道")
        )

        audioDecoder = LocalAudioDecoder(applicationContext)
        audioDecoder.listener = this
        speechRecognizer = SpeechRecognizer(applicationContext)
        speechRecognizer.listener = this

        btnPick.setOnClickListener {
            fileLauncher.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream"))
        }
        btnStart.setOnClickListener { startTranscription() }
        btnCancel.setOnClickListener { cancelTranscription("已取消") }
        findViewById<Button>(R.id.btnExportTranscript).setOnClickListener { exportTranscript() }
        updateButtons()
    }

    private fun startTranscription() {
        val uri = selectedUri ?: run {
            Toast.makeText(this, "请先选择音频文件", Toast.LENGTH_SHORT).show()
            return
        }
        finalText.clear()
        partialText = ""
        updateTranscript()
        transcribing = true
        tvStatus.text = "正在加载模型…"
        speechRecognizer.start(AsrModel.entries[spModel.selectedItemPosition])
        audioDecoder.start(
            uri,
            spSampleRate.selectedItem.toString().toInt(),
            spChannels.selectedItemPosition + 1
        )
        updateButtons()
    }

    private fun cancelTranscription(message: String) {
        transcribing = false
        audioDecoder.cancel()
        speechRecognizer.stop()
        partialText = ""
        tvStatus.text = message
        updateTranscript()
        updateButtons()
    }

    private fun updateButtons() {
        btnStart.isEnabled = !transcribing && selectedUri != null
        btnCancel.isEnabled = transcribing
        btnPick.isEnabled = !transcribing
        spModel.isEnabled = !transcribing
        spSampleRate.isEnabled = !transcribing
        spChannels.isEnabled = !transcribing
    }

    private fun updateTranscript() {
        tvTranscript.text = finalText.toString() + partialText
        transcriptScroll.post { transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun exportTranscript() {
        val text = tvTranscript.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "没有可导出的文字", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = File(getExternalFilesDir(null), "asr_${System.currentTimeMillis()}.txt")
            file.writeText(text)
            Toast.makeText(this, "已导出:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPcm(samples: FloatArray) {
        speechRecognizer.acceptPcm(samples)
    }

    override fun onProgress(percent: Int) {
        runOnUiThread { tvStatus.text = "音频解码 $percent%" }
    }

    override fun onCompleted() {
        speechRecognizer.finishInput()
        runOnUiThread { tvStatus.text = "音频解码完成，等待识别…" }
    }

    override fun onError(message: String) {
        runOnUiThread { cancelTranscription(message) }
    }

    override fun onAsrStatus(status: AsrStatus) {
        runOnUiThread {
            when (status) {
                AsrStatus.LOADING -> tvStatus.text = "正在加载模型…"
                AsrStatus.READY -> tvStatus.text = "模型就绪，正在转写…"
                AsrStatus.FINISHED -> {
                    transcribing = false
                    partialText = ""
                    tvStatus.text = "转写完成"
                    updateTranscript()
                    updateButtons()
                }
                AsrStatus.IDLE -> Unit
            }
        }
    }

    override fun onAsrPartial(text: String) {
        runOnUiThread {
            partialText = text
            updateTranscript()
        }
    }

    override fun onAsrFinal(text: String) {
        runOnUiThread {
            if (text.isNotBlank()) finalText.append(text).append('\n')
            partialText = ""
            updateTranscript()
        }
    }

    override fun onAsrError(message: String) {
        runOnUiThread { cancelTranscription(message) }
    }

    override fun onDestroy() {
        audioDecoder.cancel()
        speechRecognizer.stop()
        super.onDestroy()
    }
}
