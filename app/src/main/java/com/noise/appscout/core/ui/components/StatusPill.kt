package com.noise.appscout.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.noise.appscout.R
import com.noise.appscout.domain.model.ReleaseStatus

/** User facing text for a release status. */
@Composable
fun statusLabel(status: ReleaseStatus): String = when (status) {
    ReleaseStatus.UPDATE_AVAILABLE -> stringResource(R.string.status_update_available)
    ReleaseStatus.UP_TO_DATE -> stringResource(R.string.status_up_to_date)
    ReleaseStatus.VERSION_COMPARISON_UNCERTAIN -> stringResource(R.string.status_version_uncertain)
    ReleaseStatus.SOURCE_ERROR -> stringResource(R.string.status_source_error)
    ReleaseStatus.NOT_INSTALLED -> stringResource(R.string.status_not_installed)
}

/** Accessory colour used consistently for a status across every screen. */
@Composable
fun statusColor(status: ReleaseStatus): Color = when (status) {
    ReleaseStatus.UPDATE_AVAILABLE -> MaterialTheme.colorScheme.primary
    ReleaseStatus.UP_TO_DATE -> MaterialTheme.colorScheme.secondary
    ReleaseStatus.VERSION_COMPARISON_UNCERTAIN -> MaterialTheme.colorScheme.tertiary
    ReleaseStatus.SOURCE_ERROR -> MaterialTheme.colorScheme.error
    ReleaseStatus.NOT_INSTALLED -> MaterialTheme.colorScheme.outline
}

/**
 * Compact, accessible status pill.
 *
 * The label is part of the semantics, so TalkBack announces the state instead of leaving the
 * user with an unlabelled coloured dot.
 */
@Composable
fun StatusPill(
    status: ReleaseStatus,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
) {
    val label = statusLabel(status)
    val staleLabel = stringResource(R.string.status_stale)
    val color = statusColor(status)
    val text = if (stale) "$label · $staleLabel" else label

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
