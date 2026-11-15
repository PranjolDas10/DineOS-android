package dev.dineos.app.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dineos.api.AuthApi
import dev.dineos.api.ChecklistsApi
import dev.dineos.app.BuildConfig
import dev.dineos.app.data.AppDatabase
import dev.dineos.app.data.BearerInterceptor
import dev.dineos.app.data.ChecklistDao
import dev.dineos.app.data.KeystoreVault
import dev.dineos.app.data.Session
import dev.dineos.app.data.TokenRefresher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID
import javax.inject.Singleton

/** The generated models mark UUID fields @Contextual: this is the serializer they look up. */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** Unknown keys are ignored so the API can add a field without breaking installed apps. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule { contextual(UUID::class, UuidSerializer) }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun session(@ApplicationContext context: Context) =
        Session(KeystoreVault(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }))

    @Provides
    @Singleton
    fun retrofit(session: Session): Retrofit {
        val baseUrl = BuildConfig.API_URL.toHttpUrl()
        val bare = OkHttpClient()
        // Sharing the bare client's connection pool: one pool, two configurations.
        val client = bare.newBuilder()
            .addInterceptor(BearerInterceptor(session))
            .authenticator(TokenRefresher(session, bare, baseUrl, ApiJson))
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun checklistsApi(retrofit: Retrofit): ChecklistsApi = retrofit.create(ChecklistsApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dineos.db").build()

    @Provides
    fun checklistDao(db: AppDatabase): ChecklistDao = db.checklist()

    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
