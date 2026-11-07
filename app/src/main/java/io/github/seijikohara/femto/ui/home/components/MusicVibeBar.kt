package io.github.seijikohara.femto.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.WifiOff
import android.widget.Toast
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.composables.icons.lucide.Lucide
import io.github.seijikohara.femto.data.music.MusicVibe
import io.github.seijikohara.femto.data.music.MusicVibes
import io.github.seijikohara.femto.ui.theme.FemtoIcon

@Composable
internal fun MusicVibeBar(
    currentVibeTitle: String?,
    onSelectVibe: (MusicVibe) -> Unit,
    onOpenSheet: (() -> Unit)? = null,
    isOnline: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isOnline) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FemtoIcon(
                        imageVector = Lucide.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = "Офлайн",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        if (onOpenSheet != null) {
            Surface(
                onClick = {
                    if (isOnline) {
                        onOpenSheet()
                    } else {
                        Toast.makeText(context, "«Моя Волна» работает только с интернетом", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isOnline) 0.50f else 0.20f),
                contentColor = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 2.dp).then(if (!isOnline) Modifier.alpha(0.38f) else Modifier),
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FemtoIcon(
                        imageVector = Lucide.LayoutGrid,
                        contentDescription = "All vibes",
                        modifier = Modifier.size(16.dp),
                        tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    )
                }
            }
        }

        MusicVibes.ALL.forEach { vibe ->
            val title = stringResource(vibe.titleRes)
            val isActive =
                isOnline && currentVibeTitle != null &&
                    (
                        currentVibeTitle.equals(vibe.titleRu, ignoreCase = true) ||
                            currentVibeTitle.equals(title, ignoreCase = true) ||
                            vibe.titleRu.contains(currentVibeTitle, ignoreCase = true) ||
                            currentVibeTitle.contains(vibe.titleRu, ignoreCase = true)
                    )

            val backgroundColor by animateColorAsState(
                targetValue =
                    if (isActive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                    },
                label = "vibeBg",
            )
            val contentColor by animateColorAsState(
                targetValue =
                    if (isActive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                label = "vibeContent",
            )
            val borderColor =
                if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                }

            Box(
                modifier =
                    Modifier
                        .then(if (!isOnline) Modifier.alpha(0.38f) else Modifier)
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .border(1.dp, borderColor, CircleShape)
                        .clickable {
                            if (isOnline) {
                                onSelectVibe(vibe)
                            } else {
                                Toast.makeText(context, "«Моя Волна» работает только с интернетом", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FemtoIcon(
                        imageVector = vibe.icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = contentColor,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = contentColor,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
