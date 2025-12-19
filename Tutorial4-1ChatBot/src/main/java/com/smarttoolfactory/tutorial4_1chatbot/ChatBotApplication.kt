package com.smarttoolfactory.tutorial4_1chatbot

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChatBotApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        // There might be some initializations here
    }
}