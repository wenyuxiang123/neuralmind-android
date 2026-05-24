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
import androidx.appcompat.app.AppCompatActivity
import com.neuralmind.databinding.ActivityWebviewBinding
import com.neuralmind.core.Logger

class WebViewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWebviewBinding
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "WebViewActivity"
        private const val EXTRA_URL = "extra_url"
        
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
        
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 配置 WebView
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(false)
                allowFileAccess = true
                allowContentAccess = true
            }
            
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.progressBar.visibility = View.VISIBLE
                    binding.urlText.text = url ?: ""
                    Logger.d(TAG, "Page started: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    binding.urlText.text = url ?: ""
                    Logger.d(TAG, "Page finished: $url")
                }
                
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        binding.errorText.visibility = View.VISIBLE
                        binding.errorText.text = "页面加载失败: ${error?.description}"
                        Logger.e(TAG, "Page error: ${error?.description}")
                    }
                }
                
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // 对于 SSL 错误，允许继续加载（开发环境常见）
                    Logger.w(TAG, "SSL error: ${error?.primaryError}, proceeding anyway")
                    handler?.proceed()
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    
                    // 处理特殊协议
                    return when (request.url?.scheme) {
                        "tel" -> {
                            // 拨打电话
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Logger.e(TAG, "Failed to dial: $url", e)
                            }
                            true
                        }
                        "mailto" -> {
                            // 发送邮件
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Logger.e(TAG, "Failed to send email: $url", e)
                            }
                            true
                        }
                        "market" -> {
                            // 应用市场
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Logger.e(TAG, "Failed to open market: $url", e)
                            }
                            true
                        }
                        else -> {
                            // 其他 URL 继续在 WebView 中加载
                            false
                        }
                    }
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    binding.progressBar.progress = newProgress
                }
                
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    title?.let { binding.titleText.text = it }
                }
            }
        }
        
        // 返回按钮
        binding.backButton.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                finish()
            }
        }
        
        // 关闭按钮
        binding.closeButton.setOnClickListener {
            finish()
        }
        
        // 刷新按钮
        binding.refreshButton.setOnClickListener {
            binding.webView.reload()
        }
        
        // 主页按钮
        binding.homeButton.setOnClickListener {
            binding.webView.loadUrl("https://www.google.com")
        }
        
        // 分享按钮
        binding.shareButton.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, binding.webView.url ?: "")
                putExtra(Intent.EXTRA_SUBJECT, binding.titleText.text.toString())
            }
            startActivity(Intent.createChooser(shareIntent, "分享网页"))
        }
        
        // 加载 URL
        val url = intent.getStringExtra(EXTRA_URL) ?: "https://www.google.com"
        loadUrl(url)
    }
    
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
        
        binding.errorText.visibility = View.GONE
        binding.webView.loadUrl(finalUrl)
    }
    
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }
    
    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
