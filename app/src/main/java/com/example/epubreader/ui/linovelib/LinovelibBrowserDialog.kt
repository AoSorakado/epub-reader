package com.example.epubreader.ui.linovelib

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LinovelibBrowserDialog(
    initialUrl: String = "https://tw.linovelib.com/login.php",
    savedUsername: String = "",
    onDismiss: () -> Unit,
    onCookiesExtracted: (cookies: String, userAgent: String, username: String) -> Unit,
    onNovelSelected: ((novelId: String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF181824) else Color(0xFFFFFFFF)
    val headerBg = if (isDark) Color(0xFF222233) else Color(0xFFF1F5F9)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("哔哩轻小说 (tw.linovelib.com)") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var detectedUsername by remember { mutableStateOf(savedUsername) }

    val jsUsernameScript = """
        (function() {
            var name = "";
            var cookies = document.cookie || "";
            
            // 1. Check jieqi cookies
            var m = cookies.match(/jieqiUserName=([^;]+)/) ||
                    cookies.match(/jieqiUserUname=([^;]+)/) ||
                    cookies.match(/jieqiUserInfo=([^;]+)/);
            if (m) {
                var decoded = decodeURIComponent(m[1]);
                var subM = decoded.match(/jieqiUserName[=:]\s*([^,;]+)/i) || decoded.match(/jieqiUserUname[=:]\s*([^,;]+)/i);
                if (subM) name = subM[1].trim();
                else if (!decoded.includes('=')) name = decoded.trim();
            }
            
            // 2. Check DOM user indicators
            if (!name) {
                var selectors = [
                    '.user-name', '.username', '#username', '.user_name',
                    '.profile-name', '.nav-user', 'a[href*="userdetail"]',
                    'a[href*="mybook"]', 'a[href*="space"]', '.avatar-name'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) {
                        var text = (el.innerText || el.textContent || "").trim();
                        if (text && text !== '登入' && text !== '登录' && text !== '注册' && text !== '註冊' && text !== '書架' && text !== '书架') {
                            name = text;
                            break;
                        }
                    }
                }
            }
            
            // 3. Check input field
            if (!name) {
                var uInput = document.querySelector('input[name="username"]');
                if (uInput && uInput.value) name = uInput.value.trim();
            }
            
            return name || "";
        })()
    """.trimIndent()

    val extractAndFinish: (novelIdToOpen: String?) -> Unit = { novelIdToOpen ->
        val cookies = CookieManager.getInstance().getCookie("https://tw.linovelib.com") ?: ""
        val ua = webViewInstance?.settings?.userAgentString ?: ""
        webViewInstance?.evaluateJavascript(jsUsernameScript) { result ->
            val cleanJsName = result?.trim('"', ' ', '\\', '\'')?.takeIf { it != "null" && it.isNotBlank() } ?: ""
            val finalName = when {
                detectedUsername.isNotBlank() -> detectedUsername.trim()
                cleanJsName.isNotBlank() -> cleanJsName
                cookies.contains("jieqiUserId") -> "哔哩轻小说用户"
                else -> ""
            }
            onCookiesExtracted(cookies, ua, finalName)
            if (novelIdToOpen != null && onNovelSelected != null) {
                onNovelSelected(novelIdToOpen)
            }
            onDismiss()
        } ?: run {
            val fallbackName = if (detectedUsername.isNotBlank()) detectedUsername.trim() else ""
            onCookiesExtracted(cookies, ua, fallbackName)
            if (novelIdToOpen != null && onNovelSelected != null) {
                onNovelSelected(novelIdToOpen)
            }
            onDismiss()
        }
    }

    // Match novel ID from current URL
    val currentNovelId = remember(currentUrl) {
        val m = java.util.regex.Pattern.compile("/novel/(\\d+)").matcher(currentUrl)
        if (m.find()) m.group(1) else null
    }

    Dialog(
        onDismissRequest = { extractAndFinish(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance?.goBack()
                            } else {
                                extractAndFinish(null)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回",
                                tint = textColor
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pageTitle,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUrl,
                                color = subTextColor,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = textColor
                            )
                        }

                        // If user is viewing a novel page, show one-click import button
                        if (currentNovelId != null) {
                            Button(
                                onClick = { extractAndFinish(currentNovelId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("导入此书", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Complete / Save Cookies Button
                        Button(
                            onClick = { extractAndFinish(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "完成验证",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("完成同步", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { extractAndFinish(null) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = subTextColor
                            )
                        }
                    }
                }

                // Account Name Quick Input / Display Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8FAFC))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "账号昵称:",
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = detectedUsername,
                        onValueChange = { detectedUsername = it },
                        placeholder = { Text("若未自动识别可在此手动输入昵称", fontSize = 11.sp, color = subTextColor) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = subTextColor.copy(alpha = 0.3f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Progress Bar
                AnimatedVisibility(
                    visible = isLoading && loadProgress < 1f,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LinearProgressIndicator(
                        progress = { loadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(0xFF10B981),
                        trackColor = Color.Transparent,
                    )
                }

                // WebView Container
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            }

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    url?.let {
                                        currentUrl = it
                                        CookieManager.getInstance().flush()
                                    }
                                    view?.title?.let { pageTitle = it }

                                    // Auto evaluate username on page finish
                                    view?.evaluateJavascript(jsUsernameScript) { result ->
                                        val cleanName = result?.trim('"', ' ', '\\', '\'')?.takeIf { it != "null" && it.isNotBlank() }
                                        if (cleanName != null && detectedUsername.isBlank()) {
                                            detectedUsername = cleanName
                                        }
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url?.toString() ?: ""
                                    // If user clicked on a novel detail link or catalog link
                                    val m = java.util.regex.Pattern.compile("/novel/(\\d+)(?:/|\\.html|/catalog)?").matcher(reqUrl)
                                    if (m.find()) {
                                        val id = m.group(1)
                                        if (id != null && onNovelSelected != null) {
                                            extractAndFinish(id)
                                            return true
                                        }
                                    }
                                    return false
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    loadProgress = newProgress / 100f
                                    if (newProgress >= 100) {
                                        isLoading = false
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    title?.let { pageTitle = it }
                                }
                            }

                            webViewInstance = this
                            loadUrl(initialUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
