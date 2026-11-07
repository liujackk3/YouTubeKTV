package com.example.youtubektv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var player: WebView
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var query: EditText
    private var localPlayer: LocalAudioPlayer? = null
    private var channel = "stereo"
    private var removeVoice = false
    private val pickAudio = 2026
    private var nextPageToken = ""
    private var lastTerm = ""
    private val favorites by lazy { getSharedPreferences("favorites", MODE_PRIVATE) }
    private var currentVideoId: String? = null
    private var currentVideoTitle = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 8)
            setBackgroundColor(0xff10151e.toInt())
        }
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        query = EditText(this).apply {
            hint = "搜尋 YouTube 歌名 / 歌手 / KTV"
            setSingleLine(true)
            setTextColor(-1)
            setHintTextColor(0xffaaaaaa.toInt())
        }
        searchRow.addView(query, LinearLayout.LayoutParams(0, -2, 1f))
        val searchButton = button("搜尋") { search(query.text.toString()) }
        searchRow.addView(searchButton)
        root.addView(searchRow)
        val libraryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        libraryRow.addView(button("我的收藏") { showFavorites() }, LinearLayout.LayoutParams(0, -2, 1f))
        libraryRow.addView(button("下一頁") {
            if (nextPageToken.isNotEmpty()) search(lastTerm, nextPageToken)
            else status.text = "目前沒有下一頁"
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(libraryRow)
        player = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            setBackgroundColor(android.graphics.Color.BLACK)
            loadUrl("file:///android_asset/player.html")
        }
        root.addView(player, LinearLayout.LayoutParams(-1, 0, 3f))
        val channelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, value) in listOf("左聲道" to "left", "右聲道" to "right", "立體聲" to "stereo")) {
            channelRow.addView(button(label) {
                channel = value
                localPlayer?.setAudioMode(channel, removeVoice)
                status.text = "聲道：$label（僅適用本機音訊）"
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(channelRow)
        val playbackRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        playbackRow.addView(button("暫停影片") {
            player.evaluateJavascript("pauseVideo()", null)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        playbackRow.addView(button("繼續播放") {
            player.evaluateJavascript("resumeVideo()", null)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        playbackRow.addView(button("收藏目前影片") {
            val id = currentVideoId
            if (id != null) {
                favorites.edit().putString(id, currentVideoTitle).apply()
                status.text = "已收藏：$currentVideoTitle"
            } else status.text = "請先選擇影片"
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(playbackRow)
        val optionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        optionsRow.addView(button("消除人聲：關") { view ->
            removeVoice = !removeVoice
            (view as Button).text = if (removeVoice) "消除人聲：開" else "消除人聲：關"
            localPlayer?.setAudioMode(channel, removeVoice)
            status.text = "人聲處理僅適用本機音訊；中央聲道抵銷不等於 AI 分離"
        }, LinearLayout.LayoutParams(0, -2, 1f))
        optionsRow.addView(button("開啟本機音訊") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
            startActivityForResult(intent, pickAudio)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(optionsRow)
        status = TextView(this).apply {
            text = "YouTube 官方播放器不支援直接攔截音訊做聲道切換或消音。"
            setTextColor(0xffdddddd.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(status)
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 2f))
        setContentView(root)
    }

    private fun button(label: String, action: (View) -> Unit): Button =
        Button(this).apply {
            text = label
            isFocusable = true
            setOnClickListener { action(it) }
        }

    private fun showFavorites() {
        results.removeAllViews()
        val saved = favorites.all.entries.sortedBy { it.value.toString() }
        status.text = "我的收藏：${saved.size} 部"
        saved.forEach { entry ->
            val id = entry.key
            val title = entry.value.toString()
            results.addView(button("▶ $title") { playVideo(id, title) })
            results.addView(button("移除收藏：$title") {
                favorites.edit().remove(id).apply()
                showFavorites()
            })
        }
    }

    private fun playVideo(id: String, title: String) {
        currentVideoId = id
        currentVideoTitle = title
        player.evaluateJavascript("loadVideo('$id')", null)
        status.text = "正在播放：$title"
    }

    private fun search(term: String, pageToken: String = "") {
        if (term.isBlank()) { status.text = "請輸入關鍵字"; return }
        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            status.text = "請先在 gradle.properties 設定 YOUTUBE_API_KEY"
            return
        }
        lastTerm = term
        status.text = "搜尋中…"
        thread {
            try {
                val endpoint = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=20&q=" +
                    URLEncoder.encode(term, "UTF-8") + "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8") + "&key=" + URLEncoder.encode(BuildConfig.YOUTUBE_API_KEY, "UTF-8")
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    .bufferedReader().use { it.readText() }
                connection.disconnect()
                if (code !in 200..299) throw IllegalStateException("YouTube API HTTP $code: ${body.take(160)}")
                val json = JSONObject(body)
                val next = json.optString("nextPageToken")
                val array = json.getJSONArray("items")
                val videos = (0 until array.length()).mapNotNull { i ->
                    val item = array.getJSONObject(i)
                    val id = item.optJSONObject("id")?.optString("videoId") ?: ""
                    if (id.length != 11) null else id to item.getJSONObject("snippet").optString("title")
                }
                runOnUiThread {
                    nextPageToken = next
                    results.removeAllViews()
                    status.text = "找到 ${videos.size} 部影片（播放由 YouTube 官方嵌入播放器提供）"
                    videos.forEach { (id, title) ->
                        results.addView(button(title) { playVideo(id, title) })
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "搜尋失敗：${e.message}" }
            }
        }
    }

    @Deprecated("Uses Android document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickAudio && resultCode == RESULT_OK && data?.data != null) {
            try {
                localPlayer?.close()
                localPlayer = LocalAudioPlayer(this, data.data!!).apply {
                    setAudioMode(channel, removeVoice)
                    start()
                }
                status.text = "正在播放本機音訊，可切換聲道與中央人聲抵銷"
            } catch (e: Exception) {
                status.text = "本機音訊播放失敗：${e.message}"
            }
        }
    }

    override fun onDestroy() {
        localPlayer?.close()
        player.destroy()
        super.onDestroy()
    }
}
