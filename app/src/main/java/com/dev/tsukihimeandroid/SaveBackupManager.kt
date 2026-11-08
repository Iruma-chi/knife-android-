package com.dev.tsukihimeandroid

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Profile-aware backup format for the engine's complete savedata000 image.
 * Imports are staged and validated before AtomicFile replaces the live profile.
 */
internal class SaveBackupManager(private val context: Context) {
    data class Profile(val id: String, val relativePath: String, val expectedBytes: Long)

    companion object {
        private const val TSUKIHIME_BYTES = 0x3E7000L
        private const val WITCH_BYTES = 0x3FB800L
        private const val DATA_ENTRY = "savedata000.bin"
        private const val META_ENTRY = "manifest.txt"
        private const val MAX_BACKUP_BYTES = WITCH_BYTES
    }

    fun activeProfile(): Profile {
        val assetsPath = context.getSharedPreferences("game_data", Context.MODE_PRIVATE)
            .getString("assets_path", null)
            ?: throw IllegalStateException("Select the game assets folder before importing or exporting saves.")
        val assets = File(assetsPath)
        return when {
            // Keep the same priority as engine::assets::detect_build().
            File(assets, "w_allpac.nam").isFile ->
                Profile("overseas", "knife/overseas/savedata000", TSUKIHIME_BYTES)
            File(assets, "jallpac.nam").isFile ->
                Profile("witch", "knife/witch/savedata000", WITCH_BYTES)
            else ->
                Profile("tsukihime-jp", "knife/savedata000", TSUKIHIME_BYTES)
        }
    }

    fun export(uri: Uri) {
        val profile = activeProfile()
        val source = File(context.filesDir, profile.relativePath)
        require(source.isFile) { "No save data exists yet for this game profile." }
        require(source.length() == profile.expectedBytes) {
            "The current save has an unexpected size. It was not exported."
        }
        val digest = sha256(source)
        context.contentResolver.openOutputStream(uri, "w").use { raw ->
            requireNotNull(raw) { "Android could not open the selected backup destination." }
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry(META_ENTRY))
                val manifest = "format=tsukihime-save-backup-v1\n" +
                    "profile=${profile.id}\n" +
                    "size=${profile.expectedBytes}\n" +
                    "sha256=$digest\n"
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DATA_ENTRY))
                FileInputStream(source).use { input -> input.copyTo(zip) }
                zip.closeEntry()
                zip.finish()
            }
        }
    }

    fun import(uri: Uri): String {
        val profile = activeProfile()
        val staged = File(context.cacheDir, "save-import-${System.nanoTime()}.tmp")
        var manifestText: String? = null
        try {
            context.contentResolver.openInputStream(uri).use { raw ->
                requireNotNull(raw) { "Android could not open the selected backup." }
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var foundData = false
                    var foundManifest = false
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(!entry.isDirectory) { "Backup contains an unexpected directory." }
                        when (entry.name) {
                            META_ENTRY -> {
                                require(!foundManifest) { "Backup contains duplicate metadata." }
                                val bytes = readEntryLimited(zip, 4096)
                                manifestText = bytes.toString(Charsets.UTF_8)
                                foundManifest = true
                            }
                            DATA_ENTRY -> {
                                require(!foundData) { "Backup contains duplicate save data." }
                                FileOutputStream(staged).use { output ->
                                    val buffer = ByteArray(32 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        require(total <= MAX_BACKUP_BYTES) { "Backup save data is too large." }
                                        output.write(buffer, 0, count)
                                    }
                                    output.fd.sync()
                                }
                                foundData = true
                            }
                            else -> throw IllegalArgumentException("Backup contains an unsupported file: ${entry.name}")
                        }
                        zip.closeEntry()
                    }
                    require(foundManifest && foundData) { "Backup is missing required contents." }
                }
            }

            val fields = requireNotNull(manifestText).lineSequence()
                .filter { it.contains('=') }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            require(fields["format"] == "tsukihime-save-backup-v1") { "Unsupported backup format." }
            require(fields["profile"] == profile.id) {
                "This backup belongs to '${fields["profile"]}', but the selected game is '${profile.id}'."
            }
            require(fields["size"] == profile.expectedBytes.toString()) { "Backup profile size does not match." }
            require(staged.length() == profile.expectedBytes) { "Backup save file has an invalid size." }
            require(fields["sha256"] == sha256(staged)) { "Backup checksum failed. The original save was not changed." }

            val destination = File(context.filesDir, profile.relativePath)
            destination.parentFile?.mkdirs()
            val rollback = if (destination.isFile) {
                File(destination.parentFile, "savedata000.pre-import-${System.currentTimeMillis()}.bak")
                    .also { destination.copyTo(it, overwrite = false) }
            } else null

            val atomic = AtomicFile(destination)
            try {
                val output = atomic.startWrite()
                try {
                    FileInputStream(staged).use { input -> input.copyTo(output) }
                    output.fd.sync()
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
                require(destination.isFile && destination.length() == profile.expectedBytes &&
                    sha256(destination) == fields["sha256"]) {
                    "Restored save verification failed."
                }
            } catch (error: Exception) {
                if (rollback != null && rollback.isFile) {
                    val restore = AtomicFile(destination)
                    val output = restore.startWrite()
                    try {
                        FileInputStream(rollback).use { input -> input.copyTo(output) }
                        output.fd.sync()
                        restore.finishWrite(output)
                    } catch (restoreError: Exception) {
                        restore.failWrite(output)
                        throw IllegalStateException(
                            "Import failed and automatic rollback also failed. Keep the backup file ${rollback.name}.",
                            restoreError
                        )
                    }
                } else {
                    destination.delete()
                }
                throw error
            }
            return if (rollback != null) {
                "Save imported and verified. A rollback copy was kept at ${rollback.name}."
            } else {
                "Save imported and verified. This profile had no previous save."
            }
        } finally {
            staged.delete()
        }
    }


    private fun readEntryLimited(input: ZipInputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Backup metadata is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
