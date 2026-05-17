package com.neuralmind.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.neuralmind.R
import com.neuralmind.core.Logger
import com.neuralmind.llama.LlamaEngine
import com.neuralmind.voice.VoiceInputManager
import com.neuralmind.voice.TtsManager
import com.neuralmind.tools.DeviceToolExecutor
import kotlinx.coroutines.*

class FloatingBallService : Service() {

    companion object {
        private const val TAG = "FloatingBall"
        private var instance: FloatingBallService? = null
        fun getInstance(): FloatingBallService? = instance
        fun isRunning(): Boolean = instance != null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null

    // Views
    private var ballView: View? = null
    private var panelView: View? = null

    // Ball state
    private var isExpanded = false
    private var isListening = false
    private var isProcessing = false

    // AI components
    private var llamaEngine: LlamaEngine? = null
    private var voiceInputManager: VoiceInputManager? = null
    private var ttsManager: TtsManager? = null
    private var deviceToolExecutor: DeviceToolExecutor? = null

    // Panel views
    private var statusText: TextView? = null
    private var resultText: TextView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        initVoiceInput()
        showFloatingBall()
        Logger.i(TAG, "FloatingBallService created")
    }

    fun initComponents(engine: LlamaEngine, tts: TtsManager, toolExecutor: DeviceToolExecutor) {
        this.llamaEngine = engine
        this.ttsManager = tts
        this.deviceToolExecutor = toolExecutor
    }

    private fun initVoiceInput() {
        voiceInputManager = VoiceInputManager(this)
        voiceInputManager?.onResult = { text ->
            Logger.d(TAG, "Voice result: $text")
            onVoiceInput(text)
        }
        voiceInputManager?.onError = { error ->
            Logger.e(TAG, "Voice error: $error")
            updateStatus("语音识别失败，请重试")
            stopListening()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBall() {
        val ballSize = dpToPx(48)

        val ballContainer = FrameLayout(this)

        val ballIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundResource(R.drawable.floating_ball_bg)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        ballContainer.addView(ballIcon, FrameLayout.LayoutParams(ballSize, ballSize).apply {
            gravity = Gravity.CENTER
        })

        ballView = ballContainer

        val params = WindowManager.LayoutParams(
            ballSize + dpToPx(8),
            ballSize + dpToPx(8),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = getScreenWidth() - ballSize - dpToPx(16)
            y = getScreenHeight() / 2
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        ballContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX - dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(ballView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onBallClicked()
                    snapToEdge(params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(ballView, params)
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val screenWidth = getScreenWidth()
        val centerX = params.x + dpToPx(24)
        val targetX = if (centerX < screenWidth / 2) dpToPx(4) else screenWidth - dpToPx(60)
        params.x = targetX
        windowManager?.updateViewLayout(ballView, params)
    }

    private fun onBallClicked() {
        if (isExpanded) collapsePanel() else expandPanelAndListen()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun expandPanelAndListen() {
        if (panelView != null) return
        isExpanded = true

        val panelWidth = (getScreenWidth() * 0.8).toInt()
        val panelHeight = dpToPx(240)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF01E1E2E.toInt())
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        statusText = TextView(this).apply {
            text = "正在聆听..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
        }
        panel.addView(statusText)

        val scrollView = ScrollView(this)
        resultText = TextView(this).apply {
            text = ""
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 13f
            setPadding(0, dpToPx(8), 0, 0)
        }
        scrollView.addView(resultText)
        panel.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        val micBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setBackgroundColor(0x406C63FF.toInt())
            setOnClickListener { startListening() }
        }
        panel.addView(micBtn, LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(8)
        })

        panelView = panel

        val params = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager?.addView(panelView, params)
        startListening()
    }

    private fun collapsePanel() {
        panelView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        statusText = null
        resultText = null
        isExpanded = false
        isListening = false
        voiceInputManager?.stopListening()
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        voiceInputManager?.startListening()
        updateStatus("正在聆听...")
    }

    private fun stopListening() {
        isListening = false
        voiceInputManager?.stopListening()
    }

    private fun onVoiceInput(text: String) {
        stopListening()
        if (text.isBlank()) {
            updateStatus("未检测到语音，请重试")
            return
        }
        updateStatus("你说: $text")
        processUserInput(text)
    }

    private fun processUserInput(userInput: String) {
        isProcessing = true
        serviceScope.launch {
            try {
                val engine = llamaEngine
                if (engine == null || !engine.isModelLoaded.value) {
                    updateResult("AI 模型未加载，请先在 NeuralMind 中加载模型")
                    ttsManager?.speak("模型未加载")
                    isProcessing = false
                    return@launch
                }

                updateStatus("AI 思考中...")
                val prompt = buildPromptWithScreenContext(userInput)

                var response = ""
                engine.generate(
                    prompt = prompt,
                    onToken = { token -> response += token },
                    onComplete = { finalResponse ->
                        serviceScope.launch { handleAIResponse(finalResponse, userInput) }
                    },
                    onError = { error ->
                        serviceScope.launch {
                            updateResult("AI 错误: $error")
                            ttsManager?.speak("出错了")
                            isProcessing = false
                        }
                    }
                )
            } catch (e: Exception) {
                updateResult("处理失败: ${e.message}")
                isProcessing = false
            }
        }
    }

    private suspend fun handleAIResponse(response: String, userInput: String) {
        val executor = deviceToolExecutor ?: run {
            updateResult(response)
            isProcessing = false
            return
        }

        val (cleanText, toolCalls) = executor.parseToolCalls(response)

        if (toolCalls.isEmpty()) {
            updateResult(cleanText)
            ttsManager?.speak(cleanText.take(200))
            isProcessing = false
            return
        }

        var displayText = if (cleanText.isNotBlank()) cleanText + "\n" else ""

        for (call in toolCalls) {
            updateStatus("执行: ${call.name}(${call.params})")
            val result = executor.executeTool(call)
            displayText += "${result.message}\n"
        }

        updateResult(displayText)
        ttsManager?.speak(displayText.take(200))
        isProcessing = false
    }

    private fun buildPromptWithScreenContext(userInput: String): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append("你是NeuralMind AI助手，一个运行在手机上的智能助手，可以操控手机。")
        sb.append("\n\n【设备操控工具】\n")
        sb.append("你可以使用以下工具来操控手机。当需要执行操作时，在回复中包含工具调用。\n")
        sb.append("格式：[ACTION:工具名]参数[/ACTION]\n\n")
        sb.append("可用工具：\n")
        sb.append("- launch_app: 打开应用。例：[ACTION:launch_app]微信[/ACTION]\n")
        sb.append("- click_text: 点击屏幕上的文字。例：[ACTION:click_text]确定[/ACTION]\n")
        sb.append("- input_text: 输入文字。格式\"提示|内容\"。例：[ACTION:input_text]搜索|天气[/ACTION]\n")
        sb.append("- go_back: 返回\n")
        sb.append("- go_home: 回到主页\n")
        sb.append("- open_notifications: 打开通知栏\n")
        sb.append("- swipe_up/swipe_down/swipe_left/swipe_right: 滑动\n")
        sb.append("- get_screen: 获取当前屏幕内容\n\n")
        sb.append("重要规则：\n")
        sb.append("1. 需要执行操作时才使用工具\n")
        sb.append("2. 直接执行，不需要问用户确认\n")
        sb.append("3. 不确定时先用get_screen查看屏幕\n")

        val service = NeuralMindAccessibilityService.getInstance()
        if (service != null) {
            val currentApp = service.getCurrentApp()
            val screenText = service.getScreenText()
            sb.append("\n\n【当前屏幕状态】\n")
            if (currentApp.isNotBlank()) sb.append("当前应用: $currentApp\n")
            if (screenText.isNotBlank()) sb.append("屏幕内容: ${screenText.take(800)}\n")
        } else {
            sb.append("\n\n【屏幕状态】无障碍服务未开启\n")
        }

        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>user\n")
        sb.append(userInput)
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun updateStatus(text: String) {
        serviceScope.launch(Dispatchers.Main) { statusText?.text = text }
    }

    private fun updateResult(text: String) {
        serviceScope.launch(Dispatchers.Main) {
            resultText?.text = text
            statusText?.text = "完成"
            serviceScope.launch {
                delay(5000)
                collapsePanel()
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "neuralmind_floating"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "NeuralMind 悬浮球", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("NeuralMind AI 助手")
            .setContentText("悬浮球已启动，点击麦克风说话")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        collapsePanel()
        ballView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        ballView = null
        voiceInputManager?.destroy()
        instance = null
        Logger.i(TAG, "FloatingBallService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun getScreenWidth(): Int = resources.displayMetrics.widthPixels
    private fun getScreenHeight(): Int = resources.displayMetrics.heightPixels
}
