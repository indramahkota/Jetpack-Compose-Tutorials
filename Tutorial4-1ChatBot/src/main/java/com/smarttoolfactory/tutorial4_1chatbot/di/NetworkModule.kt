package com.smarttoolfactory.tutorial4_1chatbot.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.smarttoolfactory.tutorial4_1chatbot.data.api.OpenAiInterceptor
import com.smarttoolfactory.tutorial4_1chatbot.data.sseclient.ChatSseDataSource
import com.smarttoolfactory.tutorial4_1chatbot.data.sseclient.ChatSseDataSourceImpl
import com.smarttoolfactory.tutorial4_1chatbot.domain.ChatStreamRepository
import com.smarttoolfactory.tutorial4_1chatbot.domain.ChatStreamRepositoryImpl
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOpenAiInterceptor(
    ): OpenAiInterceptor = OpenAiInterceptor()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        interceptor: OpenAiInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ChuckerInterceptor(context))
            .addInterceptor(interceptor)
            .readTimeout(0, TimeUnit.MILLISECONDS) // SSE safe
            .build()
}


@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindSseClient(impl: ChatSseDataSourceImpl): ChatSseDataSource

    @Binds
    @Singleton
    abstract fun bindChatStreamRepository(impl: ChatStreamRepositoryImpl): ChatStreamRepository
}
