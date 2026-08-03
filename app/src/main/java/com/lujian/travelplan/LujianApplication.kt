package com.lujian.travelplan

import android.app.Application
import androidx.room.Room
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.data.db.LujianDatabase
import com.lujian.travelplan.importing.PlanImportService
import org.maplibre.android.MapLibre

class LujianApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        graph = AppGraph(this)
    }
}

class AppGraph(application: Application) {
    val database: LujianDatabase = Room.databaseBuilder(
        application,
        LujianDatabase::class.java,
        "lujian.db",
    ).build()
    val repository = PlanRepository(application, database)
    val importService = PlanImportService(application, repository)
}
