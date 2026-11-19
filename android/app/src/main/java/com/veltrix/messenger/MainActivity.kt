package com.veltrix.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0B1018)
private val Panel = Color(0xFF151F2D)
private val Accent = Color(0xFFE04455)
private val Soft = Color(0xFF9AAEC5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Ink, surface = Panel, primary = Accent,
                onBackground = Color.White, onSurface = Color.White
            )) { NativeLoginScreen() }
        }
    }
}

@Composable
private fun NativeLoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("نسخهٔ اندرویدی VELTRIX در حال آماده‌سازی است") }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier.fillMaxSize().background(Ink).padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("VELTRIX", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("MESSENGER", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Spacer(Modifier.height(12.dp))
            Text("ارتباط خصوصی، ساده و سریع", color = Soft, fontSize = 14.sp)
            Spacer(Modifier.height(34.dp))
            Column(
                modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(24.dp)).padding(22.dp)
            ) {
                Text("خوش برگشتی", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("ایمیل") }, shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("رمز عبور") }, visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { message = "اتصال حساب در مرحلهٔ بعد فعال می‌شود." },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("ورود", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                Text(message, color = Soft, fontSize = 12.sp)
            }
            Spacer(Modifier.height(22.dp))
            Text("نسخهٔ آزمایشی توسعه", color = Soft, fontSize = 11.sp)
        }
    }
}
