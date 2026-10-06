package com.noise.appscout.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.noise.appscout.core.database.dao.AiSummaryDao
import com.noise.appscout.core.database.dao.ReleaseDao
import com.noise.appscout.core.database.dao.SourceDao
import com.noise.appscout.core.database.dao.TrackedAppDao
import com.noise.appscout.core.database.entity.AiSummaryEntity
import com.noise.appscout.core.database.entity.ReleaseEntity
import com.noise.appscout.core.database.entity.SourceEntity
import com.noise.appscout.core.database.entity.TrackedAppEntity

@Database(
    entities = [
        TrackedAppEntity::class,
        SourceEntity::class,
        ReleaseEntity::class,
        AiSummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppScoutDatabase : RoomDatabase() {

    abstract fun trackedAppDao(): TrackedAppDao
    abstract fun sourceDao(): SourceDao
    abstract fun releaseDao(): ReleaseDao
    abstract fun aiSummaryDao(): AiSummaryDao

    companion object {
        const val NAME = "appscout.db"
    }
}
