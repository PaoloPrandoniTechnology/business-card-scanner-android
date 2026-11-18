package com.paoloprandoni.businesscard

import android.app.Application
import com.paoloprandoni.businesscard.core.ServiceLocator

class BusinessCardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
