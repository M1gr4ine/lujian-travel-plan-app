package com.lujian.travelplan

import android.app.Application
import androidx.room.Room
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.data.db.LujianDatabase
import com.lujian.travelplan.importing.PlanImportService
import com.lujian.travelplan.importing.PlanReindexService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class LujianApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        graph = AppGraph(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            graph.reindexService.reindexMissingDestinations()
        }
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
    val reindexService = PlanReindexService(application, repository)
}
