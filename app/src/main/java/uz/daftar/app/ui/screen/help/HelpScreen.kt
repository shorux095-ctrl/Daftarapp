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

            Section("📊 Tahlil") {
                Cmd("solishtir")
                Body("Shu oy ↔ o'tgan oy: daromad, foyda, to'lov, sof foyda (🔼🔽 farqi bilan).")
                Spacer(Modifier.height(6.dp))
                Cmd("foyda top   •   top 5")
                Body("foyda top — eng ko'p foyda bergan mijozlar (shu yil). top 5 — eng katta 5 qarzdor.")
                Spacer(Modifier.height(6.dp))
                Cmd("faol   •   nofaol")
                Body("Oxirgi 30 kunda harakat bor (faol) yoki yo'q (nofaol) mijozlar.")
                Spacer(Modifier.height(6.dp))
                Cmd("qarz tahlil ali")
                Body("Mijoz qarzi oyma-oy qanday o'sgani + hozirgi qarz.")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("💲 Narx ko'rish") {
                Cmd("narx tarix")
                Body("Hozirgi T va T1 narxlar + qachon qo'yilgani (sana, vaqt).")
                Spacer(Modifier.height(6.dp))
                Cmd("narx korish ali")
                Body("Mijozning N narxlari + qachon qo'yilgani.")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("💸 Rasxod") {
                Cmd("r100 gaz     •     r 300 aka moshinga")
                Body("Xarajat yozish: r + summa + izoh. Masalan r20 non.")
                Spacer(Modifier.height(6.dp))
                Cmd("rasxod     •     rasxod oy     •     rasxod yil")
                Body("Bugungi / oylik / yillik xarajatlar ro'yxati + JAMI.")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("🗑 O'chirish") {
                Cmd("x ali")
                Body("Bugungi Ali yozuvlarini o'chiradi. x ali a — faqat A turini.")
                Spacer(Modifier.height(6.dp))
                Cmd("01.06 x ali     •     01.06 x ali a")
                Body("01.06 dagi Ali yozuvlari (yoki faqat A turi).")
                Spacer(Modifier.height(6.dp))
                Cmd("01.06 x     •     delete 02.06     •     delete bugun")
                Body("O'sha kunning BARCHA yozuvlari. Ha/Yo'q so'raydi.")
                Spacer(Modifier.height(6.dp))
                Cmd("ochir ali")
                Body("Ali'ning BUTUN tarixini (barcha sanalar) o'chiradi. Ha/Yo'q so'raydi.")
                Spacer(Modifier.height(6.dp))
                Body("Eslatma: chatdagi kartani uzoq bossangiz — faqat ekrandan o'chadi, ma'lumot saqlanadi.")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("📅 Kunlik / sana hisoboti") {
                Cmd("bugun   •   kecha   •   30.05")
                Body("Tanlangan kun hisoboti (T narx, J jami). Hafta uchun ☰ menyuda 📆 Hafta.")
                Spacer(Modifier.height(6.dp))
                Cmd("bugun n   •   30.05 n")
                Body("N narx (sotilgan) bo'yicha — 🔢 JAMI pul. Tur filtri: 30.05 a b")
                Spacer(Modifier.height(6.dp))
                Cmd("01.05 25.05        •        01.05 25.05 p")
                Body("Sana ORALIG'I hisoboti (yozib yuboring):\n• 01.05 20.05 — hamma yuklar (soni) + pullar\n• 01.05 25.05 pul — faqat P (to'lov) qilganlar, ism + summa + jami\n• 10.05 15.05 a c — faqat A va C yuklar\n• 10.05 15.05 ali a — faqat Ali, A yuki")
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
                Spacer(Modifier.height(6.dp))
                Cmd("t1set Ali c       •   t1aset Ali")
                Body("Mavjud yozuvlarni T1 ga o'tkazadi:\n• t1set Ali c — bugungi Ali C yuklari\n• t1set Ali c 15.05 — shu kun\n• t1set Ali 22.05 — shu kun barchasi\n• t1set Ali c 01.05-15.05 — oraliq\n• t1aset/t1bset/t1cset Ali — faqat A/B/C\n• Ko'p qatorli (sana 1-qatorda): hammasiga o'sha sana")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("✏️ Tuzatish (tahrir)") {
                Cmd("edit ali a10 a15")
                Body("Mijozning A:10 yozuvini A:15 ga o'zgartiradi (eng oxirgi mos yozuv).")
                Spacer(Modifier.height(6.dp))
                Cmd("undo   •   bekor")
                Body("Eng oxirgi saqlangan yozuvni bekor qiladi.")
                Spacer(Modifier.height(6.dp))
                Body("Narxni o'zgartirish: shunchaki yangi narx yozing — \"ali n a25\". Bugungi va keyingi yuklarga shu narx amal qiladi.")
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
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("🤖 GPT (sun'iy intellekt)") {
                Cmd("gpt kalit <provider> <KALIT>")
                Body("Provayderlar: gemini, groq, cerebras, openrouter. Bir nechtasini qo'shing — biri limitga yetsa, avtomatik keyingisi ishlaydi (ko'proq limit). Kalitlar faqat telefoningizda.")
                Spacer(Modifier.height(6.dp))
                Cmd("gpt <savol>   •   prognoz")
                Body("Biznesingiz bo'yicha aniq javob (\"gpt eng ko'p qarzdor kim?\"). prognoz — keyingi oy taxmini.\n\nBepul: aistudio.google.com/apikey, console.groq.com, cloud.cerebras.ai, openrouter.ai")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Section("🔔 Avtomatik bildirishnomalar") {
                Body("Ilova o'zi telefonga eslatma yuboradi (yopiq bo'lsa ham):\n• Har kuni 08:00 — kechagi hisobot + DB zaxira\n• Har dushanba — haftalik xulosa\n• Oy 1-kuni — o'tgan oy hisoboti + umumiy qarz\n• Har kuni 11:00 — qarz eslatma (7+ kun)\n\nBirinchi ochishda bildirishnoma ruxsatini bering.")
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
