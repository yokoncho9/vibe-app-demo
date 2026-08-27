package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentFcmToken: String = ""

    // Android 13(API 33) 이상 알림 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("FCM_PERMISSION", "알림 권한이 허용되었습니다.")
        } else {
            Log.w("FCM_PERMISSION", "알림 권한이 거부되었습니다.")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = findViewById(R.id.webView)

        setupWebView()

        // 알림 권한 확인 및 요청 (Android 13+)
        askNotificationPermission()

        // FCM 등록 토큰 발급 및 확인
        fetchFcmToken()

        // 뒤로가기 제어: WebView 뒤로갈 수 있으면 히스토리 뒤로가기
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // 온라인 주소 로드 (오프라인/에러 시 로컬 asset 대체 지원)
        webView.loadUrl("https://vibecode.dothome.co.kr/sat/")
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun fetchFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM_TOKEN", "FCM 등록 토큰 가져오기 실패", task.exception)
                    return@addOnCompleteListener
                }

                // FCM 토큰 획득
                val token = task.result
                currentFcmToken = token
                Log.d("FCM_TOKEN", "==================================================")
                Log.d("FCM_TOKEN", "FCM 디바이스 등록 토큰:")
                Log.d("FCM_TOKEN", token)
                Log.d("FCM_TOKEN", "==================================================")
            }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // 전화 걸기 (tel:) 링크 처리
                if (url.startsWith("tel:")) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url))
                    startActivity(intent)
                    return true
                }

                // 앱 내부 도메인 및 리소스는 WebView 내에서 로드
                if (url.contains("vibecode.dothome.co.kr") || 
                    url.startsWith("file:///android_asset/")) {
                    return false
                }

                return false
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                // 네트워크 접속 불가 등 에러 발생 시 로컬 에셋으로 오프라인 로드
                if (failingUrl?.startsWith("https://vibecode.dothome.co.kr") == true) {
                    view?.loadUrl("file:///android_asset/index.html")
                }
            }
        }

        // 네이티브 자바스크립트 인터페이스 브릿지 연결
        webView.addJavascriptInterface(WebAppInterface(this, ::currentFcmToken), "AndroidBridge")
    }

    // JS <-> Kotlin 통신 브릿지
    class WebAppInterface(
        private val context: Context,
        private val tokenProvider: () -> String
    ) {
        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun makePhoneCall(phone: String) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            context.startActivity(intent)
        }

        @JavascriptInterface
        fun getFcmToken(): String {
            return tokenProvider()
        }
    }
}