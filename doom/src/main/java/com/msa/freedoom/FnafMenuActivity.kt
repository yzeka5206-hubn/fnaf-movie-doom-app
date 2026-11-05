package com.msa.freedoom

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.msa.freedoom.ui.launch.LaunchState
import com.msa.freedoom.ui.launch.ModEntry
import com.msa.freedoom.ui.stats.StatsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Android home menu; never runs the Windows launcher or asks for game files. */
class FnafMenuActivity : AppCompatActivity() {
    private lateinit var menu: FnafMenuView
    private var preparing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.reloadSettings(application)
        Utils.setImmersionMode(this)
        menu = FnafMenuView(this, object : FnafMenuView.Listener {
            override fun onSingleplayer() = startSingleplayer()
            override fun onMultiplayer() {
                Toast.makeText(this@FnafMenuActivity, "Bu sürüm tek oyunculu; çok oyunculu kapalı.", Toast.LENGTH_LONG).show()
            }
            override fun onOptions() {
                startActivity(Intent(this@FnafMenuActivity, FnafOptionsActivity::class.java))
            }
            override fun onYouTube() = openLink("https://www.youtube.com/watch?v=VznYMQ0hnf4")
            override fun onDiscord() = openLink("https://discord.gg/bkPhs73Xsx")
            override fun onVolume() {
                // The screenshot's speaker opens the actual device media-volume panel.
                val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audio != null) {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                } else {
                    Toast.makeText(this@FnafMenuActivity, "Ses ayarları açılamadı.", Toast.LENGTH_SHORT).show()
                }
            }
        })
        setContentView(menu)
    }

    override fun onResume() {
        super.onResume()
        // Settings may have been restored/changed, and a Game session may have just ended.
        AppSettings.reloadSettings(application)
        StatsStore.finalizeSession(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Utils.onWindowFocusChanged(this, hasFocus)
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Bağlantıyı açacak uygulama bulunamadı.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startSingleplayer() {
        if (preparing) return
        preparing = true
        menu.setBusy(true)
        // Reuse the same v7 extraction, engine settings, savedir and Game intent path.
        val launch = LaunchState(this)
        lifecycleScope.launch {
            try {
                launch.initialize()
                if (!launch.initialized) throw IOException(launch.errorMessage ?: "Oyun verileri hazırlanamadı.")
                val validation = withContext(Dispatchers.IO) {
                    FnafBundledGame.verifyExtracted(File(AppSettings.getQuakeFullDir()))
                }
                if (validation != null) throw IOException(validation)
                val game = launch.games.firstOrNull { it.file.equals(FnafBundledGame.IWAD, ignoreCase = true) }
                    ?: throw IOException("FNAF oyun dosyası bulunamadı.")
                launch.selectGame(game)
                launch.selectedMods.clear()
                FnafBundledGame.modPaths.forEach { launch.selectedMods.add(ModEntry(it)) }
                launch.launchGame()
                launch.errorMessage?.let { throw IOException(it) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this@FnafMenuActivity)
                        .setTitle("Oyun başlatılamadı")
                        .setMessage(e.message ?: "Bilinmeyen hata")
                        .setPositiveButton("Tamam", null)
                        .show()
                }
            } finally {
                preparing = false
                if (!isFinishing && !isDestroyed) menu.setBusy(false)
            }
        }
    }
}
