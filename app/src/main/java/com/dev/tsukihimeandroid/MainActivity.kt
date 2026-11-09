package com.dev.tsukihimeandroid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * TsukiPort launcher. It imports a user's own NSP/XCI using their local keys, prepares RomFS
 * under app-private storage, and hands the resulting path to the existing Knife engine.
 * GameActivity and the in-game controls are deliberately not modified by this launcher change.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var gameButton: Button
    private lateinit var prodKeysButton: Button
    private lateinit var titleKeysButton: Button
    private lateinit var fontButton: Button
    private lateinit var prepareButton: Button
    private lateinit var cancelButton: Button
    private lateinit var playButton: Button
    private lateinit var forgetButton: Button
    private lateinit var exportSaveButton: Button
    private lateinit var importSaveButton: Button
    private lateinit var progressBar: ProgressBar

    private val prefs by lazy { getSharedPreferences("game_data", MODE_PRIVATE) }
    private val saveBackups by lazy { SaveBackupManager(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private var importer: NativeImporter? = null
    private var selectedGameUris: List<Uri> = emptyList()
    private var selectedProdKeysUri: Uri? = null
    private var selectedTitleKeysUri: Uri? = null
    @Volatile private var importing = false

    private val outputDirectory get() = File(filesDir, "tsukihime-import")
    private val stagingDirectory get() = File(filesDir, "tsukihime-import-staging")
    private val romfsDirectory get() = File(outputDirectory, "extract/romfs")
    private val stagingRomfsDirectory get() = File(stagingDirectory, "extract/romfs")
    private val systemFontSource get() = File(File(filesDir, "system-fonts"), SYSTEM_FONT)

    private val gamePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) rememberGameFiles(uris)
    }
    private val prodKeysPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedProdKeysUri = uri
            prodKeysButton.text = "PROD.KEYS SELECTED"
            refreshButtons()
            updateStatus()
        }
    }
    private val titleKeysPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedTitleKeysUri = uri
            titleKeysButton.text = "TITLE.KEYS SELECTED (OPTIONAL)"
            updateStatus()
        }
    }
    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installSystemFont(uri)
    }
    private val exportSavePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runSaveOperation("Exporting save…") { saveBackups.export(uri); "Save backup exported successfully." }
    }
    private val importSavePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmSaveImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedGameUris = prefs.getStringSet("game_uris", mutableSetOf<String>()).orEmpty().mapNotNull {
            runCatching { Uri.parse(it) }.getOrNull()
        }
        importer = runCatching { NativeImporter() }.getOrElse {
            Log.e(TAG, "Native importer is unavailable", it)
            null
        }
        buildScreen()
        updateStatus()
        refreshButtons()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            updateStatus()
            refreshButtons()
        }
    }

    override fun onDestroy() {
        if (importing) runCatching { importer?.cancel() }
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            gravity = Gravity.CENTER_HORIZONTAL
            background = LayerDrawable(arrayOf(
                BitmapDrawable(resources, BitmapFactory.decodeResource(resources, R.drawable.launcher_background)).apply {
                    gravity = android.graphics.Gravity.FILL
                },
                ColorDrawable(0x52061122)
            ))
        }
        column.addView(ImageView(this).apply {
            setImageResource(R.drawable.tsukihime_official_logo)
            contentDescription = "Tsukihime - A piece of blue glass moon"
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(260), dp(104)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(4)
        })
        column.addView(TextView(this).apply {
            text = "TsukiPort · Development build"
            textSize = 13f
            setTextColor(0xFFB8B5C4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        }, fullWidth())
        column.addView(TextView(this).apply {
            text = "Import your legally dumped Tsukihime Switch game. Select the base NSP/XCI and optionally its matching update package; for split XCIs, select every part. The importer applies a matching update when supplied. DLC is not supported yet. Keys are used locally and are not retained or uploaded."
            textSize = 15f
            setTextColor(0xFFE1DFE8.toInt())
            setLineSpacing(dp(4).toFloat(), 1f)
        }, fullWidth())

        gameButton = Button(this).apply {
            text = "SELECT NSP / XCI (OR SPLIT PARTS)"
            setOnClickListener { gamePicker.launch(arrayOf("*/*")) }
        }
        column.addView(gameButton, fullWidth().apply { topMargin = dp(16) })

        prodKeysButton = Button(this).apply {
            text = "SELECT PROD.KEYS"
            setOnClickListener { prodKeysPicker.launch(arrayOf("*/*")) }
        }
        column.addView(prodKeysButton, fullWidth().apply { topMargin = dp(8) })

        titleKeysButton = Button(this).apply {
            text = "SELECT TITLE.KEYS (OPTIONAL)"
            setOnClickListener { titleKeysPicker.launch(arrayOf("*/*")) }
        }
        column.addView(titleKeysButton, fullWidth().apply { topMargin = dp(8) })

        fontButton = Button(this).apply {
            text = "SELECT SWITCH SYSTEM FONT"
            setOnClickListener { fontPicker.launch(arrayOf("*/*")) }
        }
        column.addView(fontButton, fullWidth().apply { topMargin = dp(8) })

        status = TextView(this).apply {
            text = "Choose your game package and prod.keys."
            textSize = 14f
            setTextColor(0xFFD5D2DE.toInt())
            setPadding(0, dp(14), 0, dp(10))
        }
        column.addView(status, fullWidth())

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = android.view.View.GONE
        }
        column.addView(progressBar, fullWidth())

        prepareButton = Button(this).apply {
            text = "PREPARE GAME DATA"
            setOnClickListener { prepareGame() }
        }
        column.addView(prepareButton, fullWidth().apply { topMargin = dp(12) })

        cancelButton = Button(this).apply {
            text = "CANCEL IMPORT"
            visibility = android.view.View.GONE
            setOnClickListener {
                runCatching { importer?.cancel() }
                status.text = "Cancellation requested…"
            }
        }
        column.addView(cancelButton, fullWidth().apply { topMargin = dp(6) })

        playButton = Button(this).apply {
            text = "PLAY TSUKIHIME"
            setOnClickListener {
                val path = prefs.getString("assets_path", null)
                if (path == null || !validateAssets(File(path)).first ||
                    !File(filesDir, "assets-path.txt").isFile) {
                    status.text = "Prepare and validate the game data before playing."
                } else if (!nativeEngineAvailable()) {
                    status.text = "The Rust Knife engine is not packaged. Run the ARM64 build workflow first."
                } else {
                    startActivity(Intent(this@MainActivity, GameActivity::class.java))
                }
            }
        }
        column.addView(playButton, fullWidth().apply { topMargin = dp(12) })

        val pathButton = Button(this).apply {
            text = "SHOW PREPARED GAME DATA LOCATION"
            setOnClickListener {
                val path = prefs.getString("assets_path", null) ?: "No prepared game data"
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Prepared game data")
                    .setMessage("$path\n\nThis is TsukiPort's private extracted copy. Your original NSP/XCI and key files are not modified.")
                    .setPositiveButton("COPY PATH") { _, _ ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("TsukiPort game data path", path))
                    }
                    .setNegativeButton("CLOSE", null).show()
            }
        }
        column.addView(pathButton, fullWidth().apply { topMargin = dp(8) })

        forgetButton = Button(this).apply {
            text = "FORGET PREPARED GAME DATA"
            setOnClickListener { confirmForgetPreparedData() }
        }
        column.addView(forgetButton, fullWidth().apply { topMargin = dp(8) })

        exportSaveButton = Button(this).apply {
            text = "EXPORT SAVE DATA"
            setOnClickListener {
                try {
                    val profile = saveBackups.activeProfile()
                    exportSavePicker.launch("Tsukihime-${profile.id}-save-backup.zip")
                } catch (error: Exception) {
                    showSaveMessage("Cannot export save", error.message ?: "Save export is unavailable.")
                }
            }
        }
        column.addView(exportSaveButton, fullWidth().apply { topMargin = dp(14) })

        importSaveButton = Button(this).apply {
            text = "IMPORT SAVE DATA"
            setOnClickListener {
                try {
                    saveBackups.activeProfile()
                    importSavePicker.launch(arrayOf("application/zip", "application/octet-stream"))
                } catch (error: Exception) {
                    showSaveMessage("Import unavailable", error.message ?: "Prepare and validate game data first.")
                }
            }
        }
        column.addView(importSaveButton, fullWidth().apply { topMargin = dp(8) })

        column.addView(TextView(this).apply {
            text = "No game files or keys are included. The importer verifies Switch content and extracts RomFS into app-private storage. A separate Switch shared system font is required by Knife; it is not included in the game NSP/XCI. A matching game update can be selected with the base package; DLC is not supported yet."
            textSize = 12f
            setTextColor(0xFF9F9BAA.toInt())
            setPadding(0, dp(22), 0, dp(8))
        }, fullWidth())

        scroll.addView(column)
        setContentView(scroll)
    }

    private fun rememberGameFiles(uris: List<Uri>) {
        val names = uris.map { displayName(it) }
        val invalid = names.filterNot(::isSupportedGameFile)
        if (invalid.isNotEmpty()) {
            status.text = "Unsupported file(s): ${invalid.joinToString()}. Choose .nsp, .xci, .xc0/.xc1… or .xci.00/.xci.01… parts."
            return
        }
        if (names.map { it.lowercase() }.distinct().size != names.size) {
            status.text = "Two selected files have the same name. Rename one package part and select the files again."
            return
        }
        if (!isSupportedPackageSelection(names)) {
            status.text = "Select one base NSP/XCI (or its split parts) and optionally one matching update package (or its split parts). DLC and more than one update are not supported."
            return
        }
        try {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // The current grant remains usable for this session; the user can select again if it expires.
                }
            }
            if (uris.map { it.toString() }.toSet() != selectedGameUris.map { it.toString() }.toSet()) {
                selectedTitleKeysUri = null
                titleKeysButton.text = "SELECT TITLE.KEYS (OPTIONAL)"
            }
            selectedGameUris = uris
            prefs.edit().putStringSet("game_uris", uris.map { it.toString() }.toMutableSet()).apply()
            gameButton.text = if (uris.size == 1) "GAME SELECTED: ${names.first()}" else "GAME PARTS SELECTED: ${uris.size}"
            updateStatus()
            refreshButtons()
        } catch (error: Exception) {
            status.text = "Could not remember the selected game files: ${error.message}"
        }
    }

    private fun isSupportedGameFile(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".nsp") || n.endsWith(".xci") ||
            Regex(""".*\.xc\d$""").matches(n) || Regex(""".*\.xci\.\d+$""").matches(n)
    }

    private fun isSupportedPackageSelection(names: List<String>): Boolean {
        // Allow one base container plus at most one update container. Split parts are grouped by stem.
        val groups = names.mapNotNull { name ->
            val n = name.lowercase()
            when {
                n.endsWith(".nsp") || n.endsWith(".xci") -> "container:$n"
                Regex(""".*\.xc\d$""").matches(n) -> "split:${n.dropLast(4)}"
                Regex(""".*\.xci\.\d+$""").matches(n) -> "split:${n.substringBeforeLast(".xci.")}"
                else -> null
            }
        }.distinct()
        return groups.size in 1..2
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: uri.lastPathSegment.orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun installSystemFont(uri: Uri) {
        if (importing) return
        setBusy(true, "Copying and validating the selected Switch system font…")
        worker.execute {
            try {
                systemFontSource.parentFile?.mkdirs()
                val temp = File(systemFontSource.parentFile, "$SYSTEM_FONT.tmp")
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Android could not open the selected font file." }
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                require(isValidBfttf(temp)) {
                    "That file is not a valid BFTTF shared font. Select nintendo_udsg-r_std_003.bfttf."
                }
                val backup = File(systemFontSource.parentFile, "$SYSTEM_FONT.previous")
                if (systemFontSource.exists()) {
                    if (backup.exists()) backup.delete()
                    require(systemFontSource.renameTo(backup)) { "Could not stage the existing font file." }
                }
                if (!temp.renameTo(systemFontSource)) {
                    if (backup.exists()) backup.renameTo(systemFontSource)
                    error("Could not install the selected system font.")
                }
                backup.delete()
                runOnUiThread {
                    setBusy(false)
                    fontButton.text = "SWITCH SYSTEM FONT INSTALLED"
                    updateStatus()
                    refreshButtons()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    setBusy(false)
                    status.text = "Font selection failed: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    private fun isValidBfttf(file: File): Boolean {
        if (!file.isFile || file.length() < 9L) return false
        val header = ByteArray(8)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) return false
                offset += count
            }
        }
        val key = 0x06186249
        val magic = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int xor key
        if (magic != 0x18029A7F) return false
        val encodedSize = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int xor key
        val decodedSize = Integer.reverseBytes(encodedSize).toLong() and 0xFFFFFFFFL
        return decodedSize > 0 && decodedSize <= file.length() - 8L
    }

    private fun prepareGame() {
        if (importing) return
        val games = selectedGameUris
        val prod = selectedProdKeysUri
        val title = selectedTitleKeysUri
        if (games.isEmpty()) {
            status.text = "Select your base-game NSP/XCI first."
            return
        }
        if (prod == null) {
            status.text = "Select your prod.keys file first."
            return
        }
        if (!systemFontSource.isFile || !isValidBfttf(systemFontSource)) {
            status.text = "Select the Switch shared system font first. Knife requires nintendo_udsg-r_std_003.bfttf."
            return
        }
        val native = importer
        if (native == null) {
            status.text = "The native NSP/XCI importer is not packaged in this build."
            return
        }

        setBusy(true, "Opening game package and keys…")
        worker.execute {
            val descriptors = mutableListOf<ParcelFileDescriptor>()
            try {
                val names = mutableListOf<String>()
                val fds = mutableListOf<Int>()

                fun addInput(uri: Uri, inputName: String) {
                    val descriptor = contentResolver.openFileDescriptor(uri, "r")
                        ?: error("Android could not open $inputName.")
                    if (descriptor.statSize <= 0L) {
                        descriptor.close()
                        error("The selected $inputName is not a seekable local file. Download it to device storage and select it again.")
                    }
                    try {
                        Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
                    } catch (seekError: ErrnoException) {
                        descriptor.close()
                        error("The selected $inputName does not support random-access reads. Use a local file stored on the phone.")
                    }
                    descriptors += descriptor
                    names += inputName
                    fds += descriptor.fd
                }

                games.forEach { uri -> addInput(uri, displayName(uri)) }
                addInput(prod, "prod.keys")
                title?.let { addInput(it, "title.keys") }

                val callback = ImportCallbacks(
                    onProgress = { phase, done, total, item ->
                        runOnUiThread {
                            val percent = if (total > 0L) (done * 100L / total).coerceIn(0L, 100L) else 0L
                            status.text = "Import: $phase · $percent%${if (item.isNotBlank()) "\n$item" else ""}"
                        }
                    },
                    onLog = { line -> Log.i(TAG, "nximport: $line") }
                )
                val rc = native.run(
                    stagingDirectory.absolutePath,
                    names.toTypedArray(),
                    fds.toIntArray(),
                    "",
                    false,
                    callback
                )
                if (rc != 0) {
                    val detail = native.lastError().ifBlank { "Importer failed with code $rc." }
                    error(detail)
                }

                val complete = File(stagingDirectory, ".import/complete")
                require(complete.isFile) { "Importer returned success but its completion marker is missing." }

                // Independently re-read every imported output and compare it with the importer's CRC journal.
                val verifyRc = native.run(
                    stagingDirectory.absolutePath,
                    emptyArray(),
                    intArrayOf(),
                    "",
                    true,
                    callback
                )
                if (verifyRc != 0) {
                    val detail = native.lastError().ifBlank { "Post-import verification failed with code $verifyRc." }
                    error(detail)
                }

                stagingRomfsDirectory.mkdirs()
                systemFontSource.copyTo(File(stagingRomfsDirectory, SYSTEM_FONT), overwrite = true)
                val check = validateAssets(stagingRomfsDirectory)
                require(check.first) { "The decrypted RomFS is incomplete: ${check.second}" }

                // Transactional swap: keep the previous prepared install intact until the new import validates.
                val backupDirectory = File(filesDir, "tsukihime-import-previous")
                if (backupDirectory.exists()) require(backupDirectory.deleteRecursively()) {
                    "Could not clear the previous import backup."
                }
                if (outputDirectory.exists()) require(outputDirectory.renameTo(backupDirectory)) {
                    "Could not stage the previous prepared game data."
                }
                if (!stagingDirectory.renameTo(outputDirectory)) {
                    if (backupDirectory.exists()) backupDirectory.renameTo(outputDirectory)
                    error("Could not activate the newly prepared game data.")
                }
                val path = romfsDirectory.canonicalPath
                File(filesDir, "assets-path.txt").writeText(path)
                prefs.edit().putString("assets_path", path).apply()
                backupDirectory.deleteRecursively()

                runOnUiThread {
                    setBusy(false)
                    status.text = "Game data prepared and validated. Knife is ready to launch."
                    updateStatus()
                    refreshButtons()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    setBusy(false)
                    status.text = "Import failed: ${error.message ?: error.javaClass.simpleName}\nYour original NSP/XCI and key files were not modified."
                    refreshButtons()
                }
            } finally {
                descriptors.forEach { runCatching { it.close() } }
            }
        }
    }

    private fun validateAssets(dir: File): Pair<Boolean, String> {
        if (!dir.isDirectory) return false to "RomFS directory not found"
        val overseas = File(dir, "w_allpac.nam").isFile
        val witch = File(dir, "jallpac.nam").isFile
        val allPac = when {
            overseas -> "w_allpac"
            witch -> "jallpac"
            else -> "allpac"
        }
        val allUi = if (overseas) "w_allui" else "allui"
        val soundArchive = if (witch) "witch.bfsar" else "knife1.bfsar"
        val missing = buildList {
            if (!File(dir, "allscr.mrg").isFile) add("allscr.mrg")
            if (!File(dir, "script_text.mrg").isFile) add("script_text.mrg")
            listOf("$allPac.hed", "$allPac.mrg").forEach {
                if (!File(dir, it).isFile) add(it)
            }
            listOf("$allUi.hed", "$allUi.mrg").forEach {
                if (!File(dir, it).isFile) add(it)
            }
            if (!File(dir, soundArchive).isFile) add(soundArchive)
            if (!File(dir, SYSTEM_FONT).isFile || !isValidBfttf(File(dir, SYSTEM_FONT))) add(SYSTEM_FONT)
        }
        return if (missing.isEmpty()) true to "OK" else false to "Missing or invalid: ${missing.joinToString()}"
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        val path = prefs.getString("assets_path", null)
        val check = path?.let { validateAssets(File(it)) }
        status.text = when {
            check?.first == true && nativeEngineAvailable() ->
                "Prepared game data verified. Import again only if you change the game package. Ready to play."
            check?.first == true ->
                "Game data is prepared, but the Rust Knife engine is not packaged in this APK."
            path != null ->
                "Previously prepared game data is incomplete: ${check?.second ?: "unknown error"}"
            selectedGameUris.isNotEmpty() ->
                "Game package selected. Select prod.keys and the required Switch system font, then prepare."
            else -> "Choose your base-game NSP/XCI, prod.keys and Switch shared system font."
        }
    }

    private fun refreshButtons() {
        if (!::status.isInitialized) return
        if (::gameButton.isInitialized) {
            gameButton.isEnabled = !importing
            if (selectedGameUris.isNotEmpty() && gameButton.text == "SELECT NSP / XCI (OR SPLIT PARTS)") {
                gameButton.text = "GAME PACKAGE(S) SELECTED: ${selectedGameUris.size}"
            }
        }
        if (::prodKeysButton.isInitialized) prodKeysButton.isEnabled = !importing
        if (::titleKeysButton.isInitialized) titleKeysButton.isEnabled = !importing
        if (::fontButton.isInitialized) {
            fontButton.isEnabled = !importing
            if (systemFontSource.isFile && isValidBfttf(systemFontSource)) fontButton.text = "SWITCH SYSTEM FONT SELECTED"
        }
        if (::prepareButton.isInitialized) {
            prepareButton.isEnabled = !importing && selectedGameUris.isNotEmpty() &&
                selectedProdKeysUri != null && systemFontSource.isFile && isValidBfttf(systemFontSource) &&
                importer != null
        }
        if (::playButton.isInitialized) {
            val path = prefs.getString("assets_path", null)
            playButton.isEnabled = !importing && path != null && validateAssets(File(path)).first &&
                File(filesDir, "assets-path.txt").isFile && nativeEngineAvailable()
        }
        if (::forgetButton.isInitialized) forgetButton.isEnabled = !importing &&
            (prefs.getString("assets_path", null) != null || outputDirectory.exists() || stagingDirectory.exists())
        if (::exportSaveButton.isInitialized) exportSaveButton.isEnabled = !importing && prefs.getString("assets_path", null) != null
        if (::importSaveButton.isInitialized) importSaveButton.isEnabled = !importing && prefs.getString("assets_path", null) != null
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        importing = busy
        if (::status.isInitialized && message != null) status.text = message
        if (::progressBar.isInitialized) progressBar.visibility = if (busy) android.view.View.VISIBLE else android.view.View.GONE
        if (::cancelButton.isInitialized) cancelButton.visibility = if (busy) android.view.View.VISIBLE else android.view.View.GONE
        refreshButtons()
    }

    private fun nativeEngineAvailable(): Boolean =
        File(applicationInfo.nativeLibraryDir, "libmain.so").isFile

    private fun confirmForgetPreparedData() {
        AlertDialog.Builder(this)
            .setTitle("Forget prepared game data?")
            .setMessage("This deletes only TsukiPort's private extracted copy and its saved launch path. Your original NSP/XCI and key files will remain untouched.")
            .setPositiveButton("DELETE PREPARED COPY") { _, _ ->
                outputDirectory.deleteRecursively()
                stagingDirectory.deleteRecursively()
                File(filesDir, "tsukihime-import-previous").deleteRecursively()
                File(filesDir, "assets-path.txt").delete()
                prefs.edit().remove("assets_path").apply()
                updateStatus()
                refreshButtons()
            }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun confirmSaveImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Import save backup?")
            .setMessage("The selected backup will be validated against the currently selected game profile. Your existing save will be preserved as a rollback copy before replacement. Do not start the game during import.")
            .setPositiveButton("IMPORT") { _, _ ->
                runSaveOperation("Importing save…") { saveBackups.import(uri) }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun runSaveOperation(progress: String, operation: () -> String) {
        status.text = progress
        try {
            status.text = operation()
        } catch (error: Exception) {
            status.text = "Save operation failed: ${error.message ?: error.javaClass.simpleName}. Your existing save was not intentionally discarded."
        }
    }

    private fun showSaveMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "TsukiPort"
        private const val SYSTEM_FONT = "nintendo_udsg-r_std_003.bfttf"
    }
}
