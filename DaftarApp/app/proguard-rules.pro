# Daftar ProGuard rules
# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep,allowobfuscation,allowshrinking class dagger.hilt.android.internal.managers.* { *; }

# Kotlinx
-keepclassmembers class kotlinx.coroutines.flow.** { *; }

# App entities (Room)
-keep class uz.daftar.app.data.db.entity.** { *; }
