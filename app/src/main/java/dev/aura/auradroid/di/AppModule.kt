package dev.aura.auradroid.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.aura.auradroid.data.local.AuraDatabase
import dev.aura.auradroid.data.local.MessageDao
import dev.aura.auradroid.data.local.SessionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAuraDatabase(
        @ApplicationContext context: Context
    ): AuraDatabase {
        return AuraDatabase.getInstance(context)
    }

    @Provides
    fun provideSessionDao(database: AuraDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideMessageDao(database: AuraDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideMemoryDao(database: AuraDatabase): dev.aura.auradroid.data.local.MemoryDao {
        return database.memoryDao()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    // AuraSocket and AuraHttp are @Singleton with @Inject constructors, so
    // Hilt builds them without a provider here.
}
