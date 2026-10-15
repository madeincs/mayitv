package com.mayitv.app

import android.annotation.SuppressLint
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import com.mayitv.app.databinding.ActivityPlayerBinding

/**
 * 播放界面：后台解析出真实 m3u8，用 ExoPlayer 原生播放（硬件解码、流畅）。
 * 遥控器：OK 播放/暂停，← → 快退/快进 10s，↑↓ 音量，MediaNext/Prev 切集，返回退出。
 * 解析失败时回退到 WebView 加载播放页（站点自带播放器兜底）。
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var audio: AudioManager? = null
    private var nextLink: String? = null
    private var prevLink: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideStatus = Runnable { binding.status.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audio = getSystemService(AUDIO_SERVICE) as? AudioManager
        // 沉浸全屏
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        val url = intent.getStringExtra("url") ?: run { finish(); return }
        binding.title.text = intent.getStringExtra("title") ?: ""
        resolveAndPlay(url)
    }

    private fun resolveAndPlay(url: String) {
        showStatus("解析中…")
        L.d("Player: resolveAndPlay $url")
        Thread {
            val info = PlayResolver.resolve(url)
            L.d("Player: resolve done m3u8=${info.m3u8} from=${info.from}")
            runOnUiThread {
                nextLink = info.nextLink
                prevLink = info.prevLink
                if (info.m3u8 != null) {
                    startPlayback(info.m3u8)
                } else {
                    L.w("Player: 解析失败，回退 WebView")
                    showStatus("解析失败，使用网页播放")
                    fallbackWebView(url)
                }
            }
        }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fallbackWebView(url: String) {
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = SiteConfig.UA
            webViewClient = WebViewClient()
            layoutParams = binding.playerView.layoutParams
        }
        (binding.root as android.widget.FrameLayout).addView(wv)
        wv.loadUrl(url)
    }

    private fun startPlayback(m3u8: String) {
        releasePlayer()
        L.d("Player: startPlayback $m3u8")

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(SiteConfig.UA)
            .setDefaultRequestProperties(mapOf("Referer" to SiteConfig.SITE))
        val msf = DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory)

        val p = ExoPlayer.Builder(this).setMediaSourceFactory(msf).build()
        p.setMediaItem(MediaItem.fromUri(m3u8))
        p.prepare()
        p.playWhenReady = true
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                L.d("Player: state=$state (${stateName(state)})")
                if (state == Player.STATE_BUFFERING) showStatus("缓冲中…")
                else if (state == Player.STATE_READY) showStatus("▶ 播放中")
            }

            override fun onPlayerError(error: PlaybackException) {
                L.e("Player: onPlayerError ${error.errorCodeName} ${error.message}", error)
                showStatus("播放失败，请重试或换源")
            }
        })
        binding.playerView.player = p
        player = p
    }

    private fun stateName(s: Int) = when (s) {
        1 -> "IDLE"; 2 -> "BUFFERING"; 3 -> "READY"; 4 -> "ENDED"; else -> "?"
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val p = player ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                p.playWhenReady = !p.playWhenReady
                showStatus(if (p.playWhenReady) "▶ 播放" else "⏸ 暂停")
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                p.seekTo((p.currentPosition - 10000).coerceAtLeast(0))
                showStatus("« 快退 10s")
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                p.seekTo(p.currentPosition + 10000)
                showStatus("快进 10s »")
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                showStatus("音量 +")
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                showStatus("音量 -")
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                nextLink?.let { resolveAndPlay(it) }
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                prevLink?.let { resolveAndPlay(it) }
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun showStatus(text: String) {
        binding.status.text = text
        binding.status.visibility = View.VISIBLE
        handler.removeCallbacks(hideStatus)
        handler.postDelayed(hideStatus, 1600)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
