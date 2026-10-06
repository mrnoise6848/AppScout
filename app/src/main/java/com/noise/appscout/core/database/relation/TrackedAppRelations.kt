package com.noise.appscout.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.noise.appscout.core.database.entity.ReleaseEntity
import com.noise.appscout.core.database.entity.SourceEntity
import com.noise.appscout.core.database.entity.TrackedAppEntity

/** A tracked app together with its source and every cached release, loaded in one transaction. */
data class TrackedAppRelations(
    @Embedded val app: TrackedAppEntity,
    @Relation(parentColumn = "sourceId", entityColumn = "id")
    val source: SourceEntity?,
    @Relation(parentColumn = "sourceId", entityColumn = "sourceId")
    val releases: List<ReleaseEntity>,
)
