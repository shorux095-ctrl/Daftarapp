package uz.daftar.app.ui.screen.qoshimcha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QoshimchaScreen(
    onBack: () -> Unit,
    onQarz: () -> Unit,
    onStatistika: () -> Unit,
    onGrafik: () -> Unit,
    onSearch: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📋 Qo'shimcha") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Qarz va tahlil vositalari", fontSize = 13.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(12.dp))

            Tile("💳", "Qarzdorlar reytingi", "Kim ko'p qarzdor — saralangan", Color(0xFFFFEEC2), onQarz)
            Spacer(Modifier.height(10.dp))
            Tile("📊", "Statistika", "Mijozlar, qarz, top mijozlar", Color(0xFFE3F2FD), onStatistika)
            Spacer(Modifier.height(10.dp))
            Tile("🔍", "Qidiruv", "Hamma yozuvda qidirish", Color(0xFFD5F2EE), onSearch)

            Spacer(Modifier.height(22.dp))
            Text("Tez orada", fontSize = 13.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(10.dp))
            SoonTile("📤", "Hisob ulashish (PDF)", "Mijoz hisobini PDF qilib yuborish")
            Spacer(Modifier.height(10.dp))
            SoonTile("📝", "Yozuvga izoh", "Har yozuvga eslatma qo'shish")
            Spacer(Modifier.height(10.dp))
            SoonTile("🔄", "Avto kunlik hisobot", "Har kuni kechagi hisobot chatda")
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Tile(emoji: String, title: String, subtitle: String, bg: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(bg),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 26.sp) }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF777777))
        }
        Text("›", fontSize = 24.sp, color = Color(0xFFBBBBBB))
    }
}

@Composable
private fun SoonTile(emoji: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(4.dp).alpha(0.55f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEEEEE)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF555555))
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF999999))
        }
        Surface(color = Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp)) {
            Text("tez orada", fontSize = 10.sp, color = Color(0xFF666666),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}
