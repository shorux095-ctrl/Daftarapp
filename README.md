# Daftar — Android APK loyihasi

Bot.py'dan Kotlin/Android'ga ko'chirish. Birinchi versiya: foundation (skeleton).

## Nima ishlaydi (1-versiya)

- ✅ Loyiha build bo'ladi
- ✅ Bugun ekrani — bo'sh holatda ko'rinadi (DB hali bo'sh)
- ✅ Pastdan **+ Yozuv** tugma → "Yangi yozuv" ekraniga o'tadi
- ✅ Pastki menu: **Bugun / Mijozlar / Hisobot**
- ✅ Yuqori o'ng — sozlamalar
- ✅ Splash screen, Material 3, dark/light mode, dynamic colors (Android 12+)

## Hali ishlamaydi (keyingi sessiyalar)

- ❌ "ali a10 n a20" parser — `NewTransactionScreen` placeholder
- ❌ Mijozlar ro'yxati — placeholder
- ❌ Hisobotlar (oylik, kunlik, qarz) — placeholder
- ❌ Sozlamalar (backup, alias, sxema) — placeholder
- ❌ Charts (Vico), PDF generation, widget

## Android Studio'da ochish

1. **File → Open** → loyiha papkasini tanlang (DaftarApp)
2. "Trust Project" — **Trust**
3. **Gradle Sync** avtomatik boshlanadi (~3-10 daqiqa, internet kerak)
   - "Gradle wrapper not found" desa → **Generate Gradle Wrapper** bosing
4. Gradle dependencies yuklanadi (~500 MB - 1 GB)

## Telefonga build

1. Telefonni USB orqali ulang (Developer Options + USB Debugging yoqilgan bo'lsin)
2. Yuqorida telefoningiz nomi ko'rinadi
3. Yashil ▶ **Run** tugmasini bosing
4. Birinchi build 5-15 daqiqa (Gradle hammasini yuklaydi)
5. Tugagandan keyin telefonda app ochiladi

## Loyiha tuzilmasi

```
DaftarApp/
├── app/
│   └── src/main/
│       ├── java/uz/daftar/app/
│       │   ├── DaftarApplication.kt       Hilt entry point
│       │   ├── MainActivity.kt            Compose host
│       │   ├── core/
│       │   │   ├── theme/                 Material 3 theme
│       │   │   └── util/Money.kt          Pul formatlash
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── DaftarDatabase.kt  Room DB (16 jadval)
│       │   │   │   ├── entity/            DB entitylar
│       │   │   │   └── dao/               DB so'rovlar
│       │   │   └── repository/            Data access
│       │   ├── di/                        Hilt modullar
│       │   ├── domain/model/              UI modellar
│       │   └── ui/
│       │       ├── nav/                   Navigation
│       │       └── screen/                Compose ekranlar
│       └── res/                           Resurslar
├── build.gradle.kts                       Project gradle
├── gradle.properties                      4GB RAM optimizatsiya
└── gradle/libs.versions.toml              Versiyalar
```

## Texnologiyalar

- **Kotlin 2.0.21** + **AGP 8.7.3**
- **Jetpack Compose** + **Material 3** + dynamic colors
- **Room 2.6.1** — 16 ta jadval, bot.py bilan bir xil sxema
- **Hilt** DI
- **Coroutines + Flow** reactive
- **DataStore Preferences** (kelajakda foydalaniladi)
- **WorkManager** (background — kelajakda)
- **LeakCanary** (faqat debug)
- **Splash Screen API**

## Bot.py bilan moslik

Room DB sxemasi bot.py'dagi 16 ta jadval bilan **bir xil**:
- clients, transactions, price_history, client_prices, yuk_narx
- aliases, rasxod, yuk_rasxod_narx
- reminder_log, auto_daily_log, auto_monthly_log, backup_messages
- deleted_transactions, client_reminders, client_debt_cache, client_limits

Sizning **`qarz_bot.db` faylini** ilovaning DB papkasiga ko'chirib qo'ysangiz, Room avtomatik o'qiy boshlaydi (migration siyosati: `fallbackToDestructiveMigration` — sxema mos kelsa qaytadan yaratmaydi).

## 4GB RAM uchun

`gradle.properties` allaqachon 4GB uchun moslashtirilgan:
- Gradle JVM: 1280 MB
- Kotlin daemon: 768 MB
- Parallel + caching + on-demand yoqilgan

Build paytida boshqa dasturlarni yoping (Chrome, Telegram desktop).

## Keyingi sessiya

Telefonda "Hello Daftar" ochilganini ko'rsam:
- "ali a10 n a20" **parser** (bot.py'dagi mantiq)
- **Yangi yozuv** ekrani forma bilan
- DB ga yozish va Bugun ekraniga o'tish
- Birinchi haqiqiy ishlatish

## Muammolar bo'lsa

Build xatosi yoki app crash bo'lsa:
1. **Logcat** oching (Studio pastida)
2. Xato matnini to'liq ko'chiring
3. Menga yuboring
