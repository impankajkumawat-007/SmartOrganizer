package com.smartorganizer.agent

import android.app.Application
import com.smartorganizer.agent.runtime.OrganizerRuntime

class SmartOrganizerApp : Application() {
    lateinit var runtime: OrganizerRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = OrganizerRuntime(this)
    }
}
