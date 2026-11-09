package com.dev.tsukihimeandroid

import androidx.annotation.Keep

/**
 * JNI facade for the MIT-licensed Switch NSP/XCI importer.
 * The caller owns and closes every file descriptor passed to run().
 * Key files are read for this import only and are never copied to app storage.
 */
@Keep
class NativeImporter {
    init {
        System.loadLibrary("nximport")
    }

    external fun run(
        outputDirectory: String,
        names: Array<String>,
        fileDescriptors: IntArray,
        titleId: String,
        verifyOnly: Boolean,
        callback: ImportCallbacks
    ): Int

    external fun cancel()

    external fun lastError(): String
}

@Keep
class ImportCallbacks(
    private val onProgress: (String, Long, Long, String) -> Unit,
    private val onLog: (String) -> Unit
) {
    fun progress(phase: String, done: Long, total: Long, item: String) {
        onProgress(phase, done, total, item)
    }

    fun log(line: String) {
        onLog(line)
    }
}
