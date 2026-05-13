package com.neuralmind.core

import android.content.Context
import android.util.Log
import com.neuralmind.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 全局日志工具类
 * 支持 VERBOSE/DEBUG/INFO/WARN/ERROR 五个级别
 * DEBUG 模式输出所有级别，Release 模式只输出 WARN 和 ERROR
 * 支持日志写入文件，保留最近7天日志
 */
object Logger {
    
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    object Tags {
        const val REPO = "NM-Repo"
        const val VM = "NM-VM"
        const val UI = "NM-UI"
        const val ENGINE = "NM-Engine"
        const val NET = "NM-Net"
        const val DB = "NM-DB"
        const val SKILL = "NM-Skill"
    }
    
    private var context: Context? = null
    private var isInitialized = false
    
    val isDebugMode: Boolean
        get() = BuildConfig.DEBUG
    
    private val logDir: File?
        get() = context?.filesDir?.let { File(it, "logs") }
    
    private val writeExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    
    private const val MAX_LOG_DAYS = 7
    private const val BATCH_SIZE = 10
    
    private data class LogEntry(
        val tag: String,
        val level: Level,
        val message: String,
        val throwable: Throwable? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun init(ctx: Context) {
        if (isInitialized) return
        context = ctx.applicationContext
        
        logDir?.let { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        
        cleanOldLogs()
        startAsyncWriter()
        
        isInitialized = true
    }
    
    fun v(tag: String, msg: String) {
        if (shouldLog(Level.VERBOSE)) {
            Log.v(tag, msg)
            writeToFile(tag, Level.VERBOSE, msg)
        }
    }
    
    fun d(tag: String, msg: String) {
        if (shouldLog(Level.DEBUG)) {
            Log.d(tag, msg)
            writeToFile(tag, Level.DEBUG, msg)
        }
    }
    
    fun i(tag: String, msg: String) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, msg)
            writeToFile(tag, Level.INFO, msg)
        }
    }
    
    fun w(tag: String, msg: String) {
        if (shouldLog(Level.WARN)) {
            Log.w(tag, msg)
            writeToFile(tag, Level.WARN, msg)
        }
    }
    
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (shouldLog(Level.ERROR)) {
            if (throwable != null) {
                Log.e(tag, msg, throwable)
            } else {
                Log.e(tag, msg)
            }
            writeToFile(tag, Level.ERROR, msg, throwable)
        }
    }
    
    /**
     * 便捷方法：DEBUG 日志，使用 lambda 避免字符串拼接开销
     */
    fun debug(tag: String, msg: () -> String) {
        if (shouldLog(Level.DEBUG)) {
            val message = msg()
            Log.d(tag, message)
            writeToFile(tag, Level.DEBUG, message)
        }
    }
    
    /**
     * 便捷方法：INFO 日志
     */
    fun info(tag: String, msg: () -> String) {
        if (shouldLog(Level.INFO)) {
            val message = msg()
            Log.i(tag, message)
            writeToFile(tag, Level.INFO, message)
        }
    }
    
    @PublishedApi
    internal fun shouldLog(level: Level): Boolean {
        return when {
            isDebugMode -> true
            level == Level.WARN || level == Level.ERROR -> true
            else -> false
        }
    }
    
    @PublishedApi
    internal fun writeToFile(tag: String, level: Level, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(tag, level, message, throwable)
        logQueue.offer(entry)
    }
    
    private fun startAsyncWriter() {
        writeExecutor.scheduleWithFixedDelay({
            flushLogs()
        }, 1, 1, TimeUnit.SECONDS)
    }
    
    private fun flushLogs() {
        val entries = mutableListOf<LogEntry>()
        repeat(BATCH_SIZE) {
            logQueue.poll()?.let { entries.add(it) }
        }
        
        if (entries.isEmpty()) return
        
        val logFile = getTodayLogFile() ?: return
        
        try {
            PrintWriter(FileWriter(logFile, true)).use { writer ->
                entries.forEach { entry ->
                    writer.println(formatLogEntry(entry))
                }
            }
        } catch (e: Exception) {
            // 忽略写入错误
        }
    }
    
    private fun formatLogEntry(entry: LogEntry): String {
        val timestamp = dateFormatter.format(Date(entry.timestamp))
        val levelStr = when (entry.level) {
            Level.VERBOSE -> "V"
            Level.DEBUG -> "D"
            Level.INFO -> "I"
            Level.WARN -> "W"
            Level.ERROR -> "E"
        }
        val sb = StringBuilder()
        sb.append("$timestamp $levelStr/${entry.tag}: ${entry.message}")
        entry.throwable?.let { t ->
            sb.append("\n")
            sb.append(Log.getStackTraceString(t))
        }
        return sb.toString()
    }
    
    private fun getTodayLogFile(): File? {
        return logDir?.let { dir ->
            if (!dir.exists()) dir.mkdirs()
            File(dir, "log_${fileNameFormatter.format(Date())}.txt")
        }
    }
    
    private fun cleanOldLogs() {
        val dir = logDir ?: return
        if (!dir.exists()) return
        
        val cutoffTime = System.currentTimeMillis() - (MAX_LOG_DAYS * 24 * 60 * 60 * 1000L)
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith("log_") && file.name.endsWith(".txt")) {
                val dateStr = file.name.removePrefix("log_").removeSuffix(".txt")
                try {
                    val fileDate = dateFormat.parse(dateStr)
                    if (fileDate != null && fileDate.time < cutoffTime) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                    }
                }
            }
        }
    }
    
    fun getLogDirPath(): String? {
        return logDir?.absolutePath
    }
    
    fun flush() {
        while (logQueue.isNotEmpty()) {
            flushLogs()
        }
    }
}
