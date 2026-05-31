package uz.daftar.app.ui.screen.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.daftar.app.ui.common.HomeButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("❓ Yordam") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { HomeButton() }
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
            Section("📊 Shu oy qarz") {
                Cmd("shu oy qarz")
                Body("Joriy oyda har bir mijoz qancha yuk olgan, qancha to'lov qilgan va qancha qarz qolganini ko'rsatadi. Faqat shu oydagini — umumiy qarzni emas. Shu oyda kim qarzdor bo'lganini bilish uchun.")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("📈 Shu oy foyda") {
                Cmd("shu oy foyda")
                Body("Joriy oyda qancha foyda ko'rganingizni ko'rsatadi:\n• Daromad (N) — sotilgan narx\n• Tannarx (T) — ulgurji narx\n• Foyda = N − T\n• Sof foyda = Foyda − Rasxod")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("⏰ Qarz eslatma") {
                Cmd("eslatma")
                Body("Muddati o'tgan qarzdorlarni kun bo'yicha guruhlab ko'rsatadi:\n🔴 60+ kun  🟠 30–59  🟡 14–29  🔵 7–13  ⚪ 7 kundan kam\nQarz boshlanган kunidan hisoblanadi. To'lov qilinса, qoldiq qarz davom etadi.")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("📊 Mijoz foydasi") {
                Cmd("ali foyda        (mijoz nomi + foyda)")
                Body("Bitta mijozдан qancha foyda ko'rganingiz — oylik (joriy yil) va yillik bo'yicha.")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("📅 Kunlik / sana hisoboti") {
                Cmd("bugun   •   kecha   •   30.05")
                Body("Tanlangan kun hisoboti (T narx, J jami). Hafta uchun ☰ menyuda 📆 Hafta.")
                Spacer(Modifier.height(6.dp))
                Cmd("bugun n   •   30.05 n")
                Body("N narx (sotilgan) bo'yicha — 🔢 JAMI pul. Tur filtri: 30.05 a b")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("👤 Mijoz tarixi") {
                Cmd("ali        (mijoz nomini yozing)")
                Body("Mijozning oyма-oy tarixi to'liq oynaga chiqadi (⬅️➡️ bilan oy almashtiriladi). Raqamli nomlar ham ishlaydi: vali2")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("✍️ Yozuv kiritish") {
                Cmd("ali a10 c4.3 p200")
                Body("ali — mijoz, a10 — A turdan 10, c4.3 — C dan 4.3, p200 — 200 to'lov, q100 — qo'lda qarz.")
                Spacer(Modifier.height(6.dp))
                Cmd("ali n c20   •   ali t a25")
                Body("n — mijozga maxsus narx (N), t — global ulgurji narx (T).")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("💰 T va T1 narx (tannarx)") {
                Cmd("T a10 b20 c3.3")
                Body("Global asosiy tannarx (T) — mijozsiz, hammaga.")
                Spacer(Modifier.height(6.dp))
                Cmd("T1 a16.5 b1.9")
                Body("Global 2-daraja tannarx (T1) — ba'zi yuklar boshqa narxda olinса.")
                Spacer(Modifier.height(6.dp))
                Cmd("Ali a100 b200 n a20 t1a")
                Body("t1a — faqat A yuki T1 tarifda hisoblanadi, B esa T da. Foyda = N − (T yoki T1). Qarzga ta'sir qilmaydi.")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("🗑 O'chirish") {
                Cmd("x ali        •        30.05 x ali")
                Body("Mijozning yozuvini o'chiradi (ixtiyoriy sana bilan).")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("🧭 Menyu") {
                Body("📊 Dashboard — foyda grafigi (oylik/yillik)\n📦 Yuk hisoboti — pul va soni\n🗂 Zaxira / Backup — ma'lumotni saqlash/tiklash\n🔍 Qidirish — tez qidiruv")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    content()
}

@Composable
private fun Cmd(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
