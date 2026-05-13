package com.neuralmind.tools

import android.content.Context
import com.neuralmind.domain.model.ToolModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Allowed commands whitelist for security.
 * Only commands in this list can be executed via executeCommand().
 */
private val ALLOWED_COMMANDS = setOf(
    "ls", "cat", "echo", "date", "whoami", "pwd", "df", "free", "uptime",
    "head", "tail", "grep", "find", "wc", "sort", "uniq"
)

@Singleton
class ToolExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Execute a whitelisted shell command.
     * Only safe, read-only commands are allowed.
     * @param command The command to execute (will be validated against whitelist)
     * @return CommandResult with output, error, and exit code
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        val parts = command.trim().split("""\s+""".toRegex())
        val baseCommand = parts.firstOrNull() ?: ""
        
        if (baseCommand.isEmpty()) {
            return@withContext CommandResult(
                output = "",
                error = "Empty command",
                exitCode = -1,
                isSuccess = false
            )
        }
        
        if (baseCommand !in ALLOWED_COMMANDS) {
            return@withContext CommandResult(
                output = "",
                error = "Command not allowed: $baseCommand. Only whitelisted commands can be executed.",
                exitCode = -1,
                isSuccess = false
            )
        }
        
        try {
            val process = Runtime.getRuntime().exec(command)
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()

            CommandResult(
                output = output,
                error = error,
                exitCode = exitCode,
                isSuccess = exitCode == 0
            )
        } catch (e: Exception) {
            CommandResult(
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1,
                isSuccess = false
            )
        }
    }

    suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        try {
            File(path).readText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun writeFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listFiles(directory: String): List<FileInfo> = withContext(Dispatchers.IO) {
        try {
            val dir = File(directory)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.map { file ->
                    FileInfo(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getGitStatus(repoPath: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("git status", null, File(repoPath))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun gitCommit(repoPath: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val addProcess = Runtime.getRuntime().exec("git add .", null, File(repoPath))
            addProcess.waitFor()
            
            val commitProcess = Runtime.getRuntime().exec(arrayOf("git", "commit", "-m", message), null, File(repoPath))
            commitProcess.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun getCodeSnippets(language: String): List<CodeSnippet> {
        return when (language.lowercase()) {
            "kotlin" -> listOf(
                CodeSnippet("Hello World", """
                    fun main() {
                        println("Hello, World!")
                    }
                """),
                CodeSnippet("Function", """
                    fun greet(name: String): String {
                        return "Hello, ${'$'}name!"
                    }
                """),
                CodeSnippet("Class", """
                    class Person(val name: String, val age: Int) {
                        fun greet(): String = "Hello, ${'$'}name"
                    }
                """)
            )
            "java" -> listOf(
                CodeSnippet("Hello World", """
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello, World!");
                        }
                    }
                """),
                CodeSnippet("Class", """
                    public class Person {
                        private String name;
                        private int age;
                        
                        public Person(String name, int age) {
                            this.name = name;
                            this.age = age;
                        }
                    }
                """)
            )
            else -> listOf(
                CodeSnippet("Template", "// Code template for $language")
            )
        }
    }
}

data class CommandResult(
    val output: String,
    val error: String,
    val exitCode: Int,
    val isSuccess: Boolean
)

data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

data class CodeSnippet(
    val name: String,
    val code: String
)
