package com.yuguang.lumo.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yuguang.lumo.data.local.AppDatabase
import com.yuguang.lumo.data.local.ArenaDao
import com.yuguang.lumo.data.local.AttachmentDao
import com.yuguang.lumo.data.local.ConversationDao
import com.yuguang.lumo.data.local.DATABASE_VERSION
import com.yuguang.lumo.data.local.DatabaseOpener
import com.yuguang.lumo.data.local.MemoryDao
import com.yuguang.lumo.data.local.MessageDao
import com.yuguang.lumo.data.local.SearchDao
import com.yuguang.lumo.data.local.StatsDao
import com.yuguang.lumo.data.local.TranslationDao
import com.yuguang.lumo.data.prefs.InMemorySharedPreferences
import com.yuguang.lumo.data.prefs.RecoveryNotice
import com.yuguang.lumo.data.prefs.SecretStorageState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(@ApplicationContext context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        // 只在调试包记录请求行，并丢掉含密钥的日志。
        val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            val logging = HttpLoggingInterceptor { message ->
                val secret = message.contains("Authorization", ignoreCase = true) ||
                    message.contains("Bearer ", ignoreCase = true) ||
                    message.contains("X-Api-Key", ignoreCase = true) ||
                    message.contains("X-Api-Access-Key", ignoreCase = true) ||
                    message.contains("X-Api-App-Key", ignoreCase = true)
                if (!secret) android.util.Log.d("AiHttp", message)
            }.apply { level = HttpLoggingInterceptor.Level.BASIC }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        notice: RecoveryNotice,
    ): AppDatabase = DatabaseOpener.build(context, DATABASE_VERSION, notice)

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideAttachmentDao(database: AppDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideTranslationDao(database: AppDatabase): TranslationDao = database.translationDao()

    @Provides
    fun provideSearchDao(database: AppDatabase): SearchDao = database.searchDao()

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideArenaDao(database: AppDatabase): ArenaDao = database.arenaDao()

    @Provides
    fun provideStatsDao(database: AppDatabase): StatsDao = database.statsDao()

    @Provides
    @Singleton
    fun provideSecretPrefs(
        @ApplicationContext context: Context,
        storage: SecretStorageState,
    ): SharedPreferences {
        // 密钥用 Keystore 加密后落盘。
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "provider_secrets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (error: Exception) {
            // Keystore 建不起来（定制 ROM、恢复出厂后主密钥失效等）时只留在内存：
            // 功能照旧可用，但绝不退化成明文落盘——那会让 API Key 以可读形式留在
            // /data/data 下，被云备份、设备迁移或提权后的取证直接读走。
            android.util.Log.e("AiSecrets", "encrypted storage unavailable; keeping secrets in memory only", error)
            storage.markInMemoryOnly()
            InMemorySharedPreferences()
        }
    }
}
