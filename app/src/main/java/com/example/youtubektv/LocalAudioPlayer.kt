package com.example.youtubektv

import android.content.Context
import android.media.*
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * 本機音訊：MediaExtractor -> MediaCodec -> PCM 16bit -> AudioTrack。
 * 對 YouTube WebView 不生效；不擷取或下載 YouTube 串流。
 */
class LocalAudioPlayer(private val context: Context, private val uri: Uri) {
    @Volatile private var running = false
    @Volatile private var mode = "stereo"
    @Volatile private var centerCancel = false
    private var worker: Thread? = null
    private var track: AudioTrack? = null

    fun setAudioMode(channel: String, removeVoice: Boolean) {
        mode = channel
        centerCancel = removeVoice
    }

    fun start() {
        close()
        running = true
        worker = thread(name = "LocalAudioDecoder") { decode() }
    }

    private fun decode() {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } ?: throw IllegalArgumentException("無法讀取音訊")
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("找不到音軌")
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            val decoder = codec!!
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (running && !outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = decoder.outputFormat
                    val sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (channels !in 1..2) throw IllegalArgumentException("目前支援單聲道及雙聲道音訊")
                    val encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT)
                        throw IllegalArgumentException("此音訊解碼輸出非 PCM16，暫不支援")
                    val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT)
                    track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(maxOf(min, 16384))
                        .setTransferMode(AudioTrack.MODE_STREAM).build()
                    track!!.play()
                } else if (outIndex >= 0) {
                    val buf = decoder.getOutputBuffer(outIndex)!!
                    if (info.size > 0) {
                        val outFormat = decoder.outputFormat
                        val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val bytes = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.get(bytes)
                        val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        val frames = bytes.size / (channels * 2)
                        val output = ByteArray(frames * 4)
                        val pcm = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until frames) {
                            val l = samples.short.toInt()
                            val r = if (channels == 2) samples.short.toInt() else l
                            // 中央抵銷：L-R。對混音效果有限，並非 AI 人聲分離。
                            val diff = ((l - r) / 2).coerceIn(-32768, 32767)
                            val left = if (centerCancel && channels == 2) diff else l
                            val right = if (centerCancel && channels == 2) -diff else r
                            val (a, b) = when (mode) {
                                "left" -> left to left
                                "right" -> right to right
                                else -> left to right
                            }
                            pcm.putShort(a.toShort())
                            pcm.putShort(b.toShort())
                        }
                        var offset = 0
                        while (running && offset < output.size) {
                            val written = track?.write(output, offset, output.size - offset) ?: -1
                            if (written <= 0) break
                            offset += written
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                }
            }
        } catch (_: Exception) {
            // 解碼錯誤時停止；可在後續版本加入 UI 錯誤回報。
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            extractor.release()
            try { track?.stop(); track?.release() } catch (_: Exception) {}
            track = null
        }
    }

    fun close() {
        running = false
        if (worker != Thread.currentThread()) {
            try { worker?.join(700) } catch (_: InterruptedException) {}
        }
        worker = null
    }
}
