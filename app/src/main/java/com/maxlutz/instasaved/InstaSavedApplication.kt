package com.maxlutz.instasaved

import android.app.Application
import com.maxlutz.instasaved.data.AppDatabase

class InstaSavedApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.open(this) }
}
