package com.neuralmind.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 全局日志工具类
 * 支持 VERBOSE/DEBUG/INFO/WARN/ERROR 五个级别
 * DEBUG 模式输出所有级别，Release 模式只输出 WARN 和 ERROR
 * 支持日志写入文件，保留最近7天日志
 */
object Logger {
    
    // 日志级别枚举
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    // Tag 前缀常量
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
    
    // DEBUG 模式下输出所有级别，Release 模式下只输出 WARN 和 ERROR
    val isDebugMode: Boolean
        get() = BuildConfig.DEBUG
    
    // 日志文件目录
    private val logDir: File?
        get() = context?.filesDir?.let { File(it, "logs") }
    
    // 异步写入线程池
    private val writeExecutor = Executors.newSingleThreadExecutor()
    
    // 待写入的日志队列
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    
    // 日志格式器
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    
    // 最大保留天数
    private const val MAX_LOG_DAYS = 7
    
    // 批次写入大小
    private const val BATCH_SIZE = 10
    
    // 日志条目
    private data class LogEntry(
        val tag: String,
        val level: Level,
        val message: String,
        val throwable: Throwable? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 初始化 Logger，必须在 Application.onCreate() 中调用
     */
    fun init(ctx: Context) {
        if (isInitialized) return
        context = ctx.applicationContext
        
        // 创建日志目录
        logDir?.let { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        
        // 清理过期日志
        cleanOldLogs()
        
        // 启动异步写入任务
        startAsyncWriter()
        
        isInitialized = true
    }
    
    /**
     * 检查是否应该输出日志（考虑 Release 模式过滤）
     */
    private fun shouldLog(level: Level): Boolean {
        return when {
            isDebugMode -> true
            // Release 模式只输出 WARN 和 ERROR
            level == Level.WARN || level == Level.ERROR -> true
            else -> false
        }
    }
    
    /**
     * 输出 VERBOSE 级别日志
     */
    fun v(tag: String, msg: String) {
        if (shouldLog(Level.VERBOSE)) {
            Log.v(tag, msg)
            writeToFile(tag, Level.VERBOSE, msg)
        }
    }
    
    /**
     * 输出 DEBUG 级别日志
     */
    fun d(tag: String, msg: String) {
        if (shouldLog(Level.DEBUG)) {
            Log.d(tag, msg)
            writeToFile(tag, Level.DEBUG, msg)
        }
    }
    
    /**
     * 输出 INFO 级别日志
     */
    fun i(tag: String, msg: String) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, msg)
            writeToFile(tag, Level.INFO, msg)
        }
    }
    
    /**
     * 输出 WARN 级别日志
     */
    fun w(tag: String, msg: String) {
        if (shouldLog(Level.WARN)) {
            Log.w(tag, msg)
            writeToFile(tag, Level.WARN, msg)
        }
    }
    
    /**
     * 输出 ERROR 级别日志
     */
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
     * 便捷方法：DEBUG 日志，使用 if (Logger.isDebugMode()) 模式避免字符串拼接开销
     */
    inline fun debug(tag: String, msg: () -> String) {
        if (shouldLog(Level.DEBUG)) {
            val message = msg()
            Log.d(tag, message)
            writeToFile(tag, Level.DEBUG, message)
        }
    }
    
    /**
     * 便捷方法：INFO 日志
     */
    inline fun info(tag: String, msg: () -> String) {
        if (shouldLog(Level.INFO)) {
            val message = msg()
            Log.i(tag, message)
            writeToFile(tag, Level.INFO, message)
        }
    }
    
    /**
     * 将日志写入文件（异步）
     */
    private fun writeToFile(tag: String, level: Level, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(tag, level, message, throwable)
        logQueue.offer(entry)
    }
    
    /**
     * 启动异步写入任务
     */
    private fun startAsyncWriter() {
        writeExecutor.scheduleAtFixedRate({
            flushLogs()
        }, 1, 1, TimeUnit.SECONDS)
    }
    
    /**
     * 刷新并写入日志
     */
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
    
    /**
     * 格式化日志条目
     */
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
    
    /**
     * 获取今天的日志文件
     */
    private fun getTodayLogFile(): File? {
        return logDir?.let { dir ->
            if (!dir.exists()) dir.mkdirs()
            File(dir, "log_${fileNameFormatter.format(Date())}.txt")
        }
    }
    
    /**
     * 清理超过7天的日志文件
     */
    private fun cleanOldLogs() {
        val dir = logDir ?: return
        if (!dir.exists()) return
        
        val cutoffTime = System.currentTimeMillis() - (MAX_LOG_DAYS * 24 * 60 * 60 * 1000L)
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith("log_") && file.name.endsWith(".txt")) {
                // 尝试从文件名提取日期
                val dateStr = file.name.removePrefix("log_").removeSuffix(".txt")
                try {
                    val fileDate = dateFormat.parse(dateStr)
                    if (fileDate != null && fileDate.time < cutoffTime) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    // 解析失败，检查修改时间
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                    }
                }
            }
        }
    }
    
    /**
     * 获取日志目录路径
     */
    fun getLogDirPath(): String? {
        return logDir?.absolutePath
    }
    
    /**
     * 强制刷新所有待写入日志
     */
    fun flush() {
        while (logQueue.isNotEmpty()) {
            flushLogs()
        }
    }
}
