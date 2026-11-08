package com.dev.tsukihimeandroid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Tsukihime Android 0.1.3 - no-copy asset folder selector and NativeActivity launcher.
 * The user selects an existing local shared-storage folder. The app stores its URI/path,
 * validates in place and never copies or deletes the user's game data.
 * The selected absolute assets path is written to filesDir/assets-path.txt for the Rust NativeActivity.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var permissionButton: Button
    private lateinit var chooseButton: Button
    private lateinit var forgetButton: Button
    private lateinit var playButton: Button

    private val prefs by lazy { getSharedPreferences("game_data", MODE_PRIVATE) }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) rememberFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refreshState()
        if (!hasAllFilesAccess()) showStorageExplanation()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshState()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF101522.toInt())
        }
        column.addView(TextView(this).apply {
            text = "TSUKIHIME ANDROID"
            textSize = 25f
            setTextColor(0xFFF4F0F7.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, fullWidth())
        column.addView(TextView(this).apply {
            text = "Version 0.1.3 · Development build"
            textSize = 13f
            setTextColor(0xFFB8B5C4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }, fullWidth())
        column.addView(TextView(this).apply {
            text = "Select the existing extracted RomFS assets folder (or its parent) from a normal shared-storage location such as Download/Tsukihime/assets. The app reads it in place; nothing is copied, moved or deleted. Android blocks access to another app's Android/data folder, even with All files access, so do not keep the game data inside another app's private folder."
            textSize = 15f
            setTextColor(0xFFE1DFE8.toInt())
            setLineSpacing(dp(4).toFloat(), 1f)
        }, fullWidth())

        permissionButton = Button(this).apply {
            text = "GRANT FILE ACCESS"
            setOnClickListener { requestAllFilesAccess() }
        }
        column.addView(permissionButton, fullWidth().apply { topMargin = dp(18) })

        chooseButton = Button(this).apply {
            text = "SELECT EXISTING ASSETS FOLDER"
            setOnClickListener {
                if (!hasAllFilesAccess()) requestAllFilesAccess() else folderPicker.launch(null)
            }
        }
        column.addView(chooseButton, fullWidth().apply { topMargin = dp(10) })

        status = TextView(this).apply {
            text = "Checking selected game folder…"
            textSize = 14f
            setTextColor(0xFFD5D2DE.toInt())
            setPadding(0, dp(18), 0, dp(18))
        }
        column.addView(status, fullWidth())

        playButton = Button(this).apply {
            text = "PLAY TSUKIHIME"
            isEnabled = false
            setOnClickListener {
                val path = prefs.getString("assets_path", null)
                if (path == null || !validateAssets(File(path)).first || !File(filesDir, "assets-path.txt").isFile) {
                    status.text = "Select and validate your assets folder before playing."
                } else if (!nativeEngineAvailable()) {
                    status.text = "The Rust game engine is not packaged in this APK yet. Build the ARM64 APK using the project's GitHub Actions workflow."
                } else {
                    startActivity(Intent(this@MainActivity, android.app.NativeActivity::class.java))
                }
            }
        }
        column.addView(playButton, fullWidth().apply { topMargin = dp(12) })

        val pathButton = Button(this).apply {
            text = "SHOW SELECTED GAME DATA LOCATION"
            setOnClickListener {
                val path = prefs.getString("assets_path", null) ?: "No folder selected"
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Selected assets folder")
                    .setMessage(path + "\n\nThis is the original folder. The app does not make a second copy.")
                    .setPositiveButton("COPY PATH") { _, _ ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Tsukihime assets path", path))
                    }
                    .setNegativeButton("CLOSE", null).show()
            }
        }
        column.addView(pathButton, fullWidth())

        forgetButton = Button(this).apply {
            text = "FORGET SELECTED FOLDER"
            setOnClickListener { confirmForgetFolder() }
        }
        column.addView(forgetButton, fullWidth().apply { topMargin = dp(8) })

        column.addView(TextView(this).apply {
            text = "No game files are included in the APK. The selected folder is read in place. Forgetting the selected folder only removes this app's saved location and permission; it never deletes your assets."
            textSize = 12f
            setTextColor(0xFF9F9BAA.toInt())
            setPadding(0, dp(24), 0, dp(8))
        }, fullWidth())
        scroll.addView(column)
        setContentView(scroll)
    }

    private fun showStorageExplanation() {
        AlertDialog.Builder(this)
            .setTitle("Allow file access")
            .setMessage("The current Rust engine reads game assets through normal filesystem paths. To avoid copying a large 20 GB folder, this development build needs Android's special 'Allow access to manage all files' setting so it can read a normal shared-storage folder you choose. Android still blocks another app's Android/data folder. Put the assets folder under Download or Documents. The app will not copy, move or delete your game data.")
            .setPositiveButton("CONTINUE") { _, _ -> requestAllFilesAccess() }
            .setNegativeButton("NOT NOW", null).show()
    }

    private fun requestAllFilesAccess() {
        if (hasAllFilesAccess()) { refreshState(); return }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            catch (e: Exception) { status.text = "Cannot open Android file-access settings: ${e.message}" }
        }
    }

    private fun hasAllFilesAccess(): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    private fun rememberFolder(uri: Uri) {
        if (!hasAllFilesAccess()) {
            status.text = "Please enable file access first, then select the game folder again."
            return
        }
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) { }
            val selected = resolveLocalFolder(uri)
            val assets = if (selected.name.equals("assets", ignoreCase = true)) selected else File(selected, "assets")
            val check = validateAssets(assets)
            if (!check.first) {
                status.text = "Folder not accepted: ${check.second}. Select the assets folder itself, or its parent folder. Nothing was copied or changed."
                return
            }
            // Handoff file is read by android_main() through AndroidApp::internal_data_path().
            File(filesDir, "assets-path.txt").writeText(assets.canonicalPath)
            prefs.edit()
                .putString("tree_uri", uri.toString())
                .putString("assets_path", assets.canonicalPath)
                .apply()
            status.text = "Game data verified in place. No files copied. Ready to launch the native engine. Location: ${assets.canonicalPath}"
        } catch (e: Exception) {
            status.text = "Could not use this folder: ${e.message ?: e.javaClass.simpleName}. Choose a normal shared-storage folder such as Download/Tsukihime (not another app's Android/data folder or a cloud provider)."
        }
        refreshButtonsOnly()
    }

    /** Resolve the standard Android ExternalStorageProvider tree ID to its real shared-storage path. */
    private fun resolveLocalFolder(uri: Uri): File {
        if (uri.authority != "com.android.externalstorage.documents") {
            throw IllegalArgumentException("Please choose a folder under shared Internal storage. Cloud providers and another app's Android/data folder are not supported by Android's storage rules.")
        }
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val split = documentId.split(":", limit = 2)
        if (split.size != 2) throw IllegalArgumentException("Unrecognized Android storage folder ID")
        val volume = split[0]
        val relative = split[1].trimStart('/')
        if (relative.split('/').any { it == ".." }) throw IllegalArgumentException("Invalid folder path")
        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$volume")
        }
        val result = File(base, relative).canonicalFile
        val canonicalBase = base.canonicalFile
        if (result != canonicalBase && !result.path.startsWith(canonicalBase.path + File.separator)) {
            throw IllegalArgumentException("Selected folder is outside its storage volume")
        }
        if (!result.isDirectory) throw IllegalArgumentException("Selected folder is not accessible as a local directory")
        return result
    }

    private fun validateAssets(dir: File): Pair<Boolean, String> {
        if (!dir.isDirectory) return false to "assets folder not found"
        val missing = buildList {
            if (!File(dir, "nintendo_udsg-r_std_003.bfttf").isFile) add("Nintendo standard font missing")
            if (!File(dir, "allscr.mrg").isFile) add("allscr.mrg missing")
            if (!File(dir, "script_text.mrg").isFile) add("script_text.mrg missing")
        }
        return if (missing.isEmpty()) true to "OK" else false to missing.joinToString(", ")
    }

    private fun refreshState() {
        if (!::status.isInitialized) return
        if (!hasAllFilesAccess()) {
            permissionButton.isEnabled = true
            permissionButton.text = "GRANT FILE ACCESS"
            chooseButton.isEnabled = false
            if (::playButton.isInitialized) playButton.isEnabled = false
            status.text = "File access is not enabled. Grant it, then select your existing assets folder."
            return
        }
        permissionButton.isEnabled = false
        permissionButton.text = "FILE ACCESS ENABLED"
        chooseButton.isEnabled = true
        val path = prefs.getString("assets_path", null)
        if (path == null) {
            status.text = "No game folder selected. Choose the existing assets folder or its parent."
        } else {
            val check = validateAssets(File(path))
            status.text = if (check.first && nativeEngineAvailable()) {
                "Game data found and validated in place. Native engine is packaged. Location: $path. Ready to play."
            } else if (check.first) {
                "Game data validated in place. The Rust engine is not packaged in this APK yet; use the ARM64 GitHub Actions build. Location: $path"
            } else {
                "Previously selected folder is no longer valid: ${check.second}. Select it again. Your original files were not changed."
            }
        }
        refreshButtonsOnly()
    }

    private fun nativeEngineAvailable(): Boolean =
        File(applicationInfo.nativeLibraryDir, "libmain.so").isFile

    private fun refreshButtonsOnly() {
        val path = prefs.getString("assets_path", null)
        if (::forgetButton.isInitialized) forgetButton.isEnabled = path != null
        if (::playButton.isInitialized) {
            playButton.isEnabled = path != null && validateAssets(File(path)).first &&
                File(filesDir, "assets-path.txt").isFile && nativeEngineAvailable()
        }
    }

    private fun confirmForgetFolder() {
        AlertDialog.Builder(this)
            .setTitle("Forget selected folder?")
            .setMessage("This only forgets the folder location and releases the saved folder permission. Your original assets remain exactly where they are. No files will be deleted.")
            .setPositiveButton("FORGET") { _, _ ->
                val old = prefs.getString("tree_uri", null)
                if (old != null) try {
                    contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }
                prefs.edit().clear().apply()
                File(filesDir, "assets-path.txt").delete()
                refreshState()
            }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
