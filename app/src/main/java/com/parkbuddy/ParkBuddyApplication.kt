package com.parkbuddy

import android.app.Application
import com.parkbuddy.core.data.di.DataGraph
import dev.zacsweers.metro.createGraphFactory

class ParkBuddyApplication : Application() {

    lateinit var dataGraph: DataGraph
        private set

    override fun onCreate() {
        super.onCreate()
        dataGraph = createGraphFactory<DataGraph.Factory>().create(this)
    }
}