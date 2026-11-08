package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.X
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.MusicVibe
import io.github.seijikohara.femto.data.music.MusicVibes
import io.github.seijikohara.femto.ui.common.ImmersiveSheetEffect
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon

/**
 * Modal sheet displaying all Yandex Music moods/vibes in a driving-friendly grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicMoodSheet(
    currentVibeTitle: String?,
    onSelectVibe: (MusicVibe) -> Unit,
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    isOnline: Boolean = true,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ImmersiveSheetEffect(fullscreen)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
        ) {
            // Header
            if (!isOnline) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.music_mood_no_internet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        FemtoIcon(
                            imageVector = Lucide.Sparkles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.music_vibes_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.music_source_yandex),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    FemtoIcon(
                        imageVector = Lucide.X,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Grid of Vibes
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(MusicVibes.ALL, key = { it.id }) { vibe ->
                    val title = stringResource(vibe.titleRes)
                    val isActive =
                        currentVibeTitle != null &&
                            (
                                currentVibeTitle.equals(vibe.titleRu, ignoreCase = true) ||
                                    currentVibeTitle.equals(title, ignoreCase = true) ||
                                    vibe.titleRu.contains(currentVibeTitle, ignoreCase = true) ||
                                    currentVibeTitle.contains(vibe.titleRu, ignoreCase = true)
                            )

                    Surface(
                        onClick = {
                            if (isOnline) {
                                onSelectVibe(vibe)
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color =
                            if (isActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                        modifier =
                            Modifier
                                .then(if (!isOnline) Modifier.alpha(0.38f) else Modifier)
                                .fillMaxWidth()
                                .height(84.dp)
                                .then(
                                    if (isActive) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(16.dp),
                                        )
                                    } else {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            RoundedCornerShape(16.dp),
                                        )
                                    },
                                ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                            },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                FemtoIcon(
                                    imageVector = vibe.icon,
                                    contentDescription = null,
                                    tint =
                                        if (isActive) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color =
                                    if (isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
