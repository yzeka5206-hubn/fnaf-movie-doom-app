package com.msa.freedoom

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import java.security.MessageDigest

/** Controller only: the visual menu is the game's converted native launcher. */
class FnafMenuActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private var gameStarted = false
    private var menuLaunched = false
    private var menuLaunchTime = 0L

    private val originalMenu = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        menuLaunched = false
        if (result.resultCode != Activity.RESULT_OK) {
            // A crash in the separate SDL process otherwise looks like a silent
            // exit. Java reports and Android's own exit reason remain local.
            lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) {
                    var found: String? = null
                    repeat(4) {
                        if (found == null) {
                            found = MenuCrashReporter.diagnose(this@FnafMenuActivity, menuLaunchTime)
                            if (found == null) Thread.sleep(150)
                        }
                    }
                    found
                }
                if (report == null) finish() else showFailure(
                    "Menü açılırken hata oluştu. Lütfen bu ekranı paylaş:\n\n$report"
                )
            }
        } else {
            val selection = FnafMenuSelection.fromIntent(result.data)
            if (selection == null) showFailure("Menüden geçersiz seçim geldi.")
            else startSingleplayer(selection)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.reloadSettings(application)
        Utils.setImmersionMode(this)
        status = TextView(this).apply {
            text = "Özgün menü hazırlanıyor..."
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        setContentView(status)
        if (savedInstanceState == null) prepareMenu()
        else {
            gameStarted = savedInstanceState.getBoolean("game_started", false)
            menuLaunched = savedInstanceState.getBoolean("menu_launched", false)
            menuLaunchTime = savedInstanceState.getLong("menu_launch_time", 0L)
            if (!gameStarted && !menuLaunched) prepareMenu()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("game_started", gameStarted)
        outState.putBoolean("menu_launched", menuLaunched)
        outState.putLong("menu_launch_time", menuLaunchTime)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        AppSettings.reloadSettings(application)
        StatsStore.finalizeSession(this)
        if (gameStarted) {
            gameStarted = false
            prepareMenu()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Utils.onWindowFocusChanged(this, hasFocus)
    }

    private fun prepareMenu() {
        if (menuLaunched || isFinishing || isDestroyed) return
        status.text = "Özgün menü hazırlanıyor..."
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { prepareOriginalAssets() }
                if (!isFinishing && !isDestroyed && !menuLaunched) {
                    MenuCrashReporter.clear(this@FnafMenuActivity)
                    menuLaunchTime = System.currentTimeMillis()
                    menuLaunched = true
                    originalMenu.launch(Intent(this@FnafMenuActivity, OriginalMenuActivity::class.java))
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                showFailure(e.message ?: "Menü verileri hazırlanamadı.")
            }
        }
    }

    private fun prepareOriginalAssets() {
        val dir = File(filesDir, "original-menu")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Menü klasörü oluşturulamadı.")
        val target = File(dir, "Assets.dat")
        if (target.isFile && target.length() == ASSET_SIZE && digest(target) == ASSET_SHA256) return
        val temp = File(dir, "Assets.dat.part")
        try {
            assets.open("original-menu/Assets.dat").use { input ->
                temp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (temp.length() != ASSET_SIZE || digest(temp) != ASSET_SHA256)
                throw IOException("Menü verilerinin doğrulaması başarısız.")
            if (!temp.renameTo(target)) throw IOException("Menü verileri kaydedilemedi.")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                hash.update(buffer, 0, n)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun startSingleplayer(selection: FnafMenuSelection) {
        status.text = "Oyun hazırlanıyor..."
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
                launch.launchGame(selection)
                launch.errorMessage?.let { throw IOException(it) }
                gameStarted = true
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                showFailure(e.message ?: "Oyun başlatılamadı.")
            }
        }
    }

    private fun showFailure(message: String) {
        if (isFinishing || isDestroyed) return
        val dialog = AlertDialog.Builder(this).setTitle("Başlatılamadı").setMessage(message)
            .setPositiveButton("Menüye dön") { _, _ -> prepareMenu() }
            .setNegativeButton("Çık") { _, _ -> finish() }.show()
        // A user can copy the local crash text or simply share a screenshot.
        dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
    }

    companion object {
        private const val ASSET_SIZE = 30_100_410L
        private const val ASSET_SHA256 = "eb7c62aa6f6f05ffc280122f92a4555ed4e6b59e66977c383e0c07202ee36846"
    }
}
