package com.example.epubreader.ui.novelia

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.epubreader.data.novelia.NoveliaUserSession

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NoveliaLoginDialog(
    onDismiss: () -> Unit,
    onSaveSession: (NoveliaUserSession) -> Unit
) {
    var isWebViewMode by remember { mutableStateOf(true) }
    var manualCookieText by remember { mutableStateOf("") }
    var usernameText by remember { mutableStateOf("") }

    val isDark = isSystemInDarkTheme()
    val bgCard = if (isDark) Color(0xFF1E1E2E) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1E293B)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "登录 Novelia 账号",
                            color = textColor,
                            fontSize = 18.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = subTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Switch Mode Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF2D2D3F) else Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = { isWebViewMode = true },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isWebViewMode) (if (isDark) Color(0xFF3E3E55) else Color.White) else Color.Transparent)
                    ) {
                        Text(
                            text = "网页一键登录",
                            color = if (isWebViewMode) Color(0xFF3B82F6) else subTextColor,
                            fontSize = 14.sp
                        )
                    }

                    TextButton(
                        onClick = { isWebViewMode = false },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isWebViewMode) (if (isDark) Color(0xFF3E3E55) else Color.White) else Color.Transparent)
                    ) {
                        Text(
                            text = "手动填入 Cookie",
                            color = if (!isWebViewMode) Color(0xFF3B82F6) else subTextColor,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isWebViewMode) {
                    // WebView for official auth.novelia.cc login
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                    webViewClient = object : WebViewClient() {
                                        private fun checkLoginStatus(currentUrl: String?) {
                                            val cookieMgr = CookieManager.getInstance()
                                            val authCookies = cookieMgr.getCookie("https://auth.novelia.cc") ?: ""
                                            val noveliaCookies = cookieMgr.getCookie("https://n.novelia.cc") ?: ""
                                            val combined = listOf(authCookies, noveliaCookies).filter { it.isNotBlank() }.joinToString("; ")

                                            if (combined.contains("auth") || combined.contains("token") || combined.contains("session") || combined.contains("user") || (currentUrl != null && currentUrl.contains("n.novelia.cc") && !currentUrl.contains("auth."))) {
                                                onSaveSession(
                                                    NoveliaUserSession(
                                                        isLoggedIn = true,
                                                        username = "Novelia 用户",
                                                        cookies = combined
                                                    )
                                                )
                                                onDismiss()
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            checkLoginStatus(url)
                                        }
                                    }
                                    loadUrl("https://auth.novelia.cc/?app=novel&redirect_uri=https%3A%2F%2Fn.novelia.cc%2F")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // Manual Cookie paste
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = usernameText,
                            onValueChange = { usernameText = it },
                            label = { Text("用户名 / 昵称 (选填)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = manualCookieText,
                            onValueChange = { manualCookieText = it },
                            label = { Text("Cookie / Token 凭据") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (manualCookieText.isNotBlank()) {
                                    onSaveSession(
                                        NoveliaUserSession(
                                            isLoggedIn = true,
                                            username = usernameText.ifEmpty { "已登录用户" },
                                            cookies = manualCookieText.trim()
                                        )
                                    )
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text("保存并使用凭据", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
