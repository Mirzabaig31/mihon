package mihon.feature.translation.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger that writes translation logs to both logcat and a file in storage
 *
 * Features:
 * - Writes to app's external files directory (Android/data/[package]/files/translation_logs/)
 * - Includes timestamp for each log entry
 * - Thread-safe file writing using synchronized blocks
 * - Automatic log rotation (keeps last 5 log files)
 * - Separate log file per session
 */
class TranslationLogger(private val context: Context) {

    private val logDir = File(context.getExternalFilesDir(null), "translation_logs")
    private val currentLogFile: File
    private val fileLock = Any() // Lock object for synchronized blocks

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    init {
        // Create log directory if it doesn't exist
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        // Create new log file for this session
        val timestamp = fileNameFormat.format(Date())
        currentLogFile = File(logDir, "translation_log_$timestamp.txt")

        // Write session header
        writeToFile("========================================")
        writeToFile("Translation Log Session Started")
        writeToFile("Time: ${dateFormat.format(Date())}")
        writeToFile("========================================\n")

        // Clean up old log files (keep only last 5)
        cleanupOldLogs()

        Log.i(TAG, "Translation logs will be saved to: ${currentLogFile.absolutePath}")
    }

    /**
     * Log debug message
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("[DEBUG] [$tag] $message")
    }

    /**
     * Log info message
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("[INFO] [$tag] $message")
    }

    /**
     * Log warning message
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToFile("[WARN] [$tag] $message")
    }

    /**
     * Log error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        writeToFile("[ERROR] [$tag] $message")
        if (throwable != null) {
            writeToFile("Exception: ${throwable.javaClass.simpleName}")
            writeToFile("Message: ${throwable.message}")
            writeToFile("Stack trace:")
            throwable.stackTrace.take(10).forEach { element ->
                writeToFile("  at $element")
            }
        }
    }

    /**
     * Write raw message to log file (without logcat)
     * Thread-safe using synchronized block
     */
    private fun writeToFile(message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val logEntry = "$timestamp | $message\n"

            // Write synchronously with explicit lock object
            synchronized(fileLock) {
                currentLogFile.appendText(logEntry)
            }
        } catch (e: Exception) {
            // Fallback to logcat if file writing fails
            Log.e(TAG, "Failed to write to log file: ${e.message}")
        }
    }

    /**
     * Write a separator line
     */
    fun separator() {
        writeToFile("========================================")
    }

    /**
     * Clean up old log files, keeping only the 5 most recent
     */
    private fun cleanupOldLogs() {
        try {
            val logFiles = logDir.listFiles { file ->
                file.name.startsWith("translation_log_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() } ?: return

            // Keep only the 5 most recent files
            if (logFiles.size > 5) {
                logFiles.drop(5).forEach { file ->
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "Deleted old log file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old logs: ${e.message}")
        }
    }

    /**
     * Get the current log file
     */
    fun getLogFile(): File = currentLogFile

    /**
     * Get all log files
     */
    fun getAllLogFiles(): List<File> {
        return logDir.listFiles { file ->
            file.name.startsWith("translation_log_") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Clear all log files (except current session)
     * Thread-safe operation
     */
    fun clearAllLogs() {
        synchronized(fileLock) {
            try {
                val logFiles = getAllLogFiles()
                var cleared = 0
                logFiles.forEach { file ->
                    if (file != currentLogFile && file.delete()) {
                        cleared++
                    }
                }
                Log.i(TAG, "Cleared $cleared old log files")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logs: ${e.message}")
            }
        }
    }

    /**
     * Get log directory path
     */
    fun getLogDirectory(): String = logDir.absolutePath

    companion object {
        private const val TAG = "TranslationLogger"

        @Volatile
        private var instance: TranslationLogger? = null

        fun getInstance(context: Context): TranslationLogger {
            return instance ?: synchronized(this) {
                instance ?: TranslationLogger(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
