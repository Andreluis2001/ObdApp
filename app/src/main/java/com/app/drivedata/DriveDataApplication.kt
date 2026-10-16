package com.app.drivedata

import android.app.Application
import com.app.drivedata.di.AppContainer

class DriveDataApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
