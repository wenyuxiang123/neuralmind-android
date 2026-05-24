package com.neuralmind.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.neuralmind.R
import com.neuralmind.core.Logger

class WebViewActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleText: TextView
    private lateinit var urlText: TextView
    private lateinit var errorText: TextView
    private lateinit var backButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var shareButton: ImageButton
    
    private val handler = Handler(Looper.getMainLooper())
    private var currentUrl: String? = null
    
    companion object {
        private const val TAG = "WebViewActivity"
        private const val EXTRA_URL = "extra_url"
        
        // 安全协议白名单
        private val SAFE_SCHEMES = setOf("http", "https")
        
        // 特殊协议白名单
        private val ALLOWED_SPECIAL_SCHEMES = setOf("tel", "mailto", "market")
        
        // 危险网站黑名单（基础保护）
        private val DANGEROUS_KEYWORDS = setOf(
            "phishing", "malware", "virus", "hack", "keylogger",
            "free-gift", "win-money", "lottery", "click-here",
            "bank-login", "verify-account", "password", "credit-card"
        )
        
        fun openUrl(context: android.content.Context, url: String) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContentView(R.layout.activity_webview)
        
        // 初始化视图
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        titleText = findViewById(R.id.titleText)
        urlText = findViewById(R.id.urlText)
        errorText = findViewById(R.id.errorText)
        backButton = findViewById(R.id.backButton)
        closeButton = findViewById(R.id.closeButton)
        homeButton = findViewById(R.id.homeButton)
        refreshButton = findViewById(R.id.refreshButton)
        shareButton = findViewById(R.id.shareButton)
        
        // 配置 WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            
            // 安全：混合内容模式（仅允许HTTPS）
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            
            // 安全：禁用文件访问（防止本地文件泄露）
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            
            // 安全：禁用多窗口
            setSupportMultipleWindows(false)
            
            // 安全：禁用地理定位
            setGeolocationEnabled(false)
            
            // 安全：保存表单数据（可选，提升用户体验）
            saveFormData = true
            
            // 安全：安全浏览（Android 8.0+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                currentUrl = url
                progressBar.visibility = View.VISIBLE
                urlText.text = url ?: ""
                Logger.d(TAG, "Page started: $url")
                
                // 安全检查：检测潜在危险网站
                if (url != null && isPotentiallyDangerous(url)) {
                    Logger.w(TAG, "Potentially dangerous URL detected: $url")
                    showSecurityWarning(url)
                }
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                urlText.text = url ?: ""
                Logger.d(TAG, "Page finished: $url")
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    errorText.visibility = View.VISIBLE
                    errorText.text = "页面加载失败: ${error?.description}"
                    Logger.e(TAG, "Page error: ${error?.description}")
                }
            }
            
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Logger.e(TAG, "SSL error detected: ${error?.primaryError} on ${error?.url}")
                
                // 安全：SSL 错误时不要自动继续，询问用户
                handler?.cancel()
                
                // 显示安全警告
                runOnUiThread {
                    showSslErrorDialog(error) { proceed ->
                        if (proceed) {
                            handler?.proceed()
                        }
                    }
                }
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val scheme = request.url?.scheme ?: ""
                
                Logger.d(TAG, "shouldOverrideUrlLoading: $url, scheme=$scheme")
                
                // 安全检查：验证协议
                if (!isSchemeSafe(scheme)) {
                    Logger.w(TAG, "Blocked unsafe scheme: $scheme, URL: $url")
                    return true
                }
                
                // 处理特殊协议
                if (scheme in ALLOWED_SPECIAL_SCHEMES) {
                    return handleSpecialScheme(url, scheme)
                }
                
                // 安全检查：验证 URL 是否安全
                if (!isUrlSafe(url)) {
                    Logger.w(TAG, "Blocked potentially dangerous URL: $url")
                    return true
                }
                
                // 安全 URL 继续在 WebView 中加载
                return false
            }
            
            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                // 可以在这里添加资源加载的安全检查
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
            }
            
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { titleText.text = it }
            }
            
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                Logger.i(TAG, "JavaScript alert: $message from $url")
                return super.onJsAlert(view, url, message, result)
            }
            
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                Logger.i(TAG, "JavaScript confirm: $message from $url")
                return super.onJsConfirm(view, url, message, result)
            }
        }
        
        // 返回按钮
        backButton.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
        
        // 关闭按钮
        closeButton.setOnClickListener {
            finish()
        }
        
        // 刷新按钮
        refreshButton.setOnClickListener {
            currentUrl?.let {
                errorText.visibility = View.GONE
                webView.reload()
            }
        }
        
        // 主页按钮
        homeButton.setOnClickListener {
            webView.loadUrl("https://www.google.com")
        }
        
        // 分享按钮
        shareButton.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, webView.url ?: "")
                putExtra(Intent.EXTRA_SUBJECT, titleText.text.toString())
            }
            startActivity(Intent.createChooser(shareIntent, "分享网页"))
        }
        
        // 加载 URL
        val url = intent.getStringExtra(EXTRA_URL) ?: "https://www.google.com"
        loadUrl(url)
    }
    
    // ========== 安全检查方法 ==========
    
    private fun isSchemeSafe(scheme: String): Boolean {
        return scheme in SAFE_SCHEMES || scheme in ALLOWED_SPECIAL_SCHEMES
    }
    
    private fun isUrlSafe(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        // 检查危险关键词
        DANGEROUS_KEYWORDS.forEach { keyword ->
            if (lowerUrl.contains(keyword)) {
                Logger.w(TAG, "URL contains dangerous keyword: $keyword")
                return false
            }
        }
        
        return true
    }
    
    private fun isPotentiallyDangerous(url: String): Boolean {
        val lowerUrl = url.lowercase()
        
        // 检查是否是 HTTPS（除了 localhost 等特殊情况）
        if (!lowerUrl.startsWith("https://") && 
            !lowerUrl.contains("localhost") && 
            !lowerUrl.contains("127.0.0.1") &&
            !lowerUrl.contains("192.168.") &&
            !lowerUrl.contains("10.0.") &&
            !lowerUrl.contains("172.16.")) {
            Logger.w(TAG, "Non-HTTPS URL: $url")
        }
        
        return false
    }
    
    // ========== 特殊协议处理 ==========
    
    private fun handleSpecialScheme(url: String, scheme: String): Boolean {
        return when (scheme) {
            "tel" -> {
                try {
                    Logger.i(TAG, "Opening dialer: $url")
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to dial: $url", e)
                }
                true
            }
            "mailto" -> {
                try {
                    Logger.i(TAG, "Opening email: $url")
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to send email: $url", e)
                }
                true
            }
            "market" -> {
                try {
                    Logger.i(TAG, "Opening market: $url")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to open market: $url", e)
                }
                true
            }
            else -> {
                Logger.w(TAG, "Unhandled special scheme: $scheme")
                true
            }
        }
    }
    
    // ========== 安全对话框 ==========
    
    private fun showSecurityWarning(url: String) {
        errorText.visibility = View.VISIBLE
        errorText.text = "⚠️ 安全警告：此网站可能不安全，请谨慎访问"
    }
    
    private fun showSslErrorDialog(error: SslError?, onProceed: (Boolean) -> Unit) {
        val errorMessage = when (error?.primaryError) {
            SslError.SSL_EXPIRED -> "SSL 证书已过期"
            SslError.SSL_IDMISMATCH -> "SSL 证书域名不匹配"
            SslError.SSL_NOTYETVALID -> "SSL 证书尚未生效"
            SslError.SSL_UNTRUSTED -> "SSL 证书不受信任"
            SslError.SSL_INVALID -> "SSL 证书无效"
            else -> "SSL 错误"
        }
        
        AlertDialog.Builder(this)
            .setTitle("⚠️ 安全警告")
            .setMessage("$errorMessage\n\n是否继续访问此网站？")
            .setPositiveButton("继续（不安全）") { _, _ -> onProceed(true)
            }
            .setNegativeButton("取消") { _, _ -> onProceed(false)
            }
            .setCancelable(false)
            .show()
    }
    
    // ========== URL 加载 ==========
    
    private fun loadUrl(url: String) {
        Logger.d(TAG, "Loading URL: $url")
        
        // 如果不是以 http(s):// 开头，尝试添加 https://
        val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (Patterns.WEB_URL.matcher(url).matches()) {
                "https://$url"
            } else {
                // 如果是搜索词，使用 Google 搜索
                "https://www.google.com/search?q=${Uri.encode(url)}"
            }
        } else {
            url
        }
        
        // 安全检查
        if (!isUrlSafe(finalUrl)) {
            Logger.w(TAG, "Blocked loading unsafe URL: $finalUrl")
            errorText.visibility = View.VISIBLE
            errorText.text = "⚠️ 已阻止访问潜在危险的网站"
            return
        }
        
        errorText.visibility = View.GONE
        webView.loadUrl(finalUrl)
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        webView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        webView.onPause()
    }
    
    override fun onDestroy() {
        webView.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            clearFormData()
            clearSslPreferences()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
