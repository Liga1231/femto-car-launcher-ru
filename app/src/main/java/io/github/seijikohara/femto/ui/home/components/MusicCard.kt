package io.github.seijikohara.femto.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.display.MusicCardHeight
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.sourceLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

// Gap between the album art and the meta column in the playing-state row.
private val RowContentGap = 16.dp

// Test tags for the art / meta pair. The card's contract is that the square art
// matches the meta column's height, so the padding reads evenly on all four
// sides; MusicCardArtHeightTest asserts it rather than leaving it to the eye.
internal const val MUSIC_ART_TAG = "music-card-art"
internal const val MUSIC_META_TAG = "music-card-meta"

/**
 * Music card. Vertical layout inherited from the `.music-card` rules of the
 * retired dashboard-v2 design mockup:
 *
 *  1. Album art, a square matching the meta column's height (14 dp corner),
 *     capped at 140 dp — and narrower on a narrow card so the meta column
 *     beside it keeps a fair share of the width
 *     ([FemtoDimens.MusicMetaMinWidth]).
 *  2. Meta — uppercase source eyebrow (12sp), 20sp title, 12sp artist
 *     / album, plus the progress bar. Title and progress always show; the
 *     album drops first, then the eyebrow, then the artist when the row is too
 *     short for all of them ([MusicMetaAndProgress]).
 *  3. Transport row — 64 dp prev / next + 72 dp primary play / pause
 *
 * Empty variants render in the same outer dimensions, reserved by measuring
 * an unplaced sample of the Playing layout (see `PlayingHeightReserve` in
 * `MusicCardStates.kt`): `NeedsPermission` is
 * the connect CTA; `NoActiveSession` is a "nothing is playing" Play
 * affordance (tap resumes the last session via a media key, falling back to
 * launching the user's music app — see [MusicEmptyState]).
 */
@Composable
internal fun MusicCard(
    state: MusicCardState,
    onCommand: (MusicCommand) -> Unit,
    onConnect: () -> Unit,
    onLaunchSource: (String) -> Unit,
    onExpand: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    // Spectrum band levels for the transport strip's background, or null when
    // the visualization is absent (setting off, previews, tests).
    spectrum: StateFlow<FloatArray?>? = null,
    // Whether to render the album line / album art; both default true. Off yields
    // a metadata-only, minimal card (album line dropped, art block dropped).
    showAlbum: Boolean = true,
    showArt: Boolean = true,
    motionTier: MotionTier = MotionTier.STANDARD,
    // Scroll long title / artist / album to full length while the vehicle is
    // stationary; static ellipsis while moving (see MusicCardMeta.MetaLine).
    stationary: Boolean = false,
    // Card height: grows the album art, and at EXTRA stacks it centred above the
    // title and timeline (see PlayingState).
    height: MusicCardHeight = MusicCardHeight.NORMAL,
    fillHeight: Boolean = false,
) = Surface(
    modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
    shape = MaterialTheme.shapes.large,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
) {
    when (state) {
        MusicCardState.NeedsPermission -> {
            MusicConnectState(onConnect = onConnect, showAlbum = showAlbum, fillHeight = fillHeight)
        }

        MusicCardState.NoActiveSession -> {
            MusicEmptyState(onPlay = onPlay, showAlbum = showAlbum, fillHeight = fillHeight)
        }

        is MusicCardState.Playing -> {
            PlayingState(
                nowPlaying = state.nowPlaying,
                onCommand = onCommand,
                onLaunchSource = onLaunchSource,
                onExpand = onExpand,
                spectrum = spectrum,
                showAlbum = showAlbum,
                showArt = showArt,
                motionTier = motionTier,
                stationary = stationary,
                height = height,
                fillHeight = fillHeight,
            )
        }
    }
}

@Composable
private fun PlayingState(
    nowPlaying: NowPlaying,
    onCommand: (MusicCommand) -> Unit,
    onLaunchSource: (String) -> Unit,
    onExpand: () -> Unit,
    spectrum: StateFlow<FloatArray?>?,
    showAlbum: Boolean,
    showArt: Boolean,
    motionTier: MotionTier,
    stationary: Boolean,
    height: MusicCardHeight,
    fillHeight: Boolean = false,
) {
    if (height == MusicCardHeight.NORMAL && !fillHeight) {
        val openLabel = stringResource(R.string.music_open_source, sourceLabel(nowPlaying.packageName))
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = openLabel) { onLaunchSource(nowPlaying.packageName) }
                    .padding(FemtoDimens.CardPaddingCompact),
        ) {
            val artMaxWidth =
                if (showArt) {
                    minOf(
                        maxWidth - FemtoDimens.MusicMetaMinWidth - RowContentGap,
                        FemtoDimens.MusicArtSize,
                    ).coerceAtLeast(0.dp)
                } else {
                    0.dp
                }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGapCompact),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(RowContentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showArt) {
                        ZeroIntrinsicHeight {
                            AlbumArt(
                                nowPlaying = nowPlaying,
                                onTap = { onCommand(MusicCommand.PlayPause) },
                                onLongClick = onExpand,
                                modifier =
                                    Modifier
                                        .testTag(MUSIC_ART_TAG)
                                        .sizeIn(maxWidth = artMaxWidth, maxHeight = FemtoDimens.MusicArtSize)
                                        .aspectRatio(1f, matchHeightConstraintsFirst = true),
                                motionTier = motionTier,
                            )
                        }
                    }
                    MusicMetaAndProgress(
                        source = sourceLabel(nowPlaying.packageName),
                        sourceIcon = nowPlaying.sourceIcon,
                        title = nowPlaying.title,
                        artist = nowPlaying.artist,
                        album = nowPlaying.album,
                        positionMs = nowPlaying.positionMs,
                        durationMs = nowPlaying.durationMs,
                        positionUpdateTimeMs = nowPlaying.positionUpdateTimeMs,
                        isPlaying = nowPlaying.isPlaying,
                        playbackSpeed = nowPlaying.playbackSpeed,
                        showAlbum = showAlbum,
                        showEyebrow = true,
                        onExpand = onExpand,
                        motionTier = motionTier,
                        stationary = stationary,
                        modifier = Modifier.weight(1f).testTag(MUSIC_META_TAG),
                    )
                }
                TransportRow(
                    isPlaying = nowPlaying.isPlaying,
                    onCommand = onCommand,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        val artCap =
            when (height) {
                MusicCardHeight.NORMAL -> FemtoDimens.MusicArtSize
                MusicCardHeight.TALL -> 255.dp
                MusicCardHeight.EXTRA -> 300.dp
            }
        val isPlaying = nowPlaying.isPlaying
        val paused = !isPlaying
        val artScale by animateFloatAsState(
            targetValue = if (paused) 0.88f else 1f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "artScale",
        )

        val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulseScale",
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.40f,
            targetValue = 0.75f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulseAlpha",
        )
        val ambientGlowActiveAlpha by animateFloatAsState(
            targetValue = if (isPlaying) pulseAlpha else 0f,
            animationSpec = tween(durationMillis = 400),
            label = "ambientGlowActiveAlpha",
        )
        val trackKey = nowPlaying.title to nowPlaying.artist

        var skipDirection by remember { mutableIntStateOf(1) }

        LaunchedEffect(trackKey) {
            delay(500L)
            skipDirection = 1
        }

        BoxWithConstraints(
            modifier =
                if (fillHeight) {
                    Modifier
                        .fillMaxSize()
                        .padding(FemtoDimens.CardPaddingCompact)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(FemtoDimens.CardPaddingCompact)
                },
        ) {
            Column(
                modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                verticalArrangement =
                    if (fillHeight) {
                        Arrangement.SpaceBetween
                    } else {
                        Arrangement.spacedBy(FemtoDimens.CardSectionGapCompact)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showArt) {
                        val glowPrimaryColor = MaterialTheme.colorScheme.primary
                        val glowBorderColor = MaterialTheme.colorScheme.outlineVariant
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(artCap)
                                        .testTag(MUSIC_ART_TAG)
                                        .drawBehind {
                                            if (ambientGlowActiveAlpha > 0f) {
                                                val glowRadius = size.maxDimension * 1.10f
                                                drawCircle(
                                                    brush =
                                                        Brush.radialGradient(
                                                            0.0f to
                                                                Color(0xFF00E5FF).copy(
                                                                    alpha =
                                                                        0.85f * ambientGlowActiveAlpha,
                                                                ),
                                                            0.42f to
                                                                Color(0xFF00E5FF).copy(
                                                                    alpha =
                                                                        0.80f * ambientGlowActiveAlpha,
                                                                ),
                                                            0.70f to
                                                                glowPrimaryColor.copy(
                                                                    alpha =
                                                                        0.45f * ambientGlowActiveAlpha,
                                                                ),
                                                            1.0f to Color.Transparent,
                                                            center = center,
                                                            radius = glowRadius,
                                                        ),
                                                    radius = glowRadius,
                                                    center = center,
                                                )
                                            }
                                        }.pointerInput(Unit) {
                                            var dragged = 0f
                                            val threshold = 60.dp.toPx()
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    when {
                                                        dragged < -threshold -> {
                                                            skipDirection = 1
                                                            onCommand(MusicCommand.SkipNext)
                                                        }

                                                        dragged > threshold -> {
                                                            skipDirection = -1
                                                            onCommand(MusicCommand.SkipPrevious)
                                                        }
                                                    }
                                                    dragged = 0f
                                                },
                                                onHorizontalDrag = { _, dragAmount -> dragged += dragAmount },
                                            )
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                val neonBorderBrush =
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                Color(0xFF00E5FF).copy(alpha = 0.85f * ambientGlowActiveAlpha),
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.95f * ambientGlowActiveAlpha,
                                                ),
                                                Color(0xFF80D8FF).copy(alpha = 0.80f * ambientGlowActiveAlpha),
                                            ),
                                    )

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = artScale
                                                scaleY = artScale
                                            }.clip(MaterialTheme.shapes.medium)
                                            .then(
                                                if (ambientGlowActiveAlpha > 0f) {
                                                    Modifier.border(
                                                        width = 2.dp,
                                                        brush = neonBorderBrush,
                                                        shape = MaterialTheme.shapes.medium,
                                                    )
                                                } else if (paused) {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        color =
                                                            MaterialTheme.colorScheme.outlineVariant.copy(
                                                                alpha = 0.45f,
                                                            ),
                                                        shape = MaterialTheme.shapes.medium,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                ) {
                                    AnimatedContent(
                                        targetState = trackKey,
                                        transitionSpec = {
                                            if (skipDirection >= 0) {
                                                (slideInHorizontally { fullWidth -> fullWidth } + fadeIn()) togetherWith
                                                    (slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut())
                                            } else {
                                                (
                                                    slideInHorizontally { fullWidth ->
                                                        -fullWidth
                                                    } + fadeIn()
                                                ) togetherWith
                                                    (slideOutHorizontally { fullWidth -> fullWidth } + fadeOut())
                                            }
                                        },
                                        label = "artSwap",
                                    ) { _ ->
                                        AlbumArt(
                                            nowPlaying = nowPlaying,
                                            onTap = { onCommand(MusicCommand.PlayPause) },
                                            onLongClick = onExpand,
                                            modifier = Modifier.fillMaxSize(),
                                            motionTier = motionTier,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(FemtoDimens.CardPaddingCompact))
                    MusicMetaAndProgress(
                        source = sourceLabel(nowPlaying.packageName),
                        sourceIcon = nowPlaying.sourceIcon,
                        title = nowPlaying.title,
                        artist = nowPlaying.artist,
                        album = "",
                        positionMs = nowPlaying.positionMs,
                        durationMs = nowPlaying.durationMs,
                        positionUpdateTimeMs = nowPlaying.positionUpdateTimeMs,
                        isPlaying = nowPlaying.isPlaying,
                        playbackSpeed = nowPlaying.playbackSpeed,
                        showAlbum = false,
                        showEyebrow = false,
                        onExpand = onExpand,
                        motionTier = motionTier,
                        stationary = stationary,
                        modifier = Modifier.fillMaxWidth().testTag(MUSIC_META_TAG),
                    )
                }
                TransportRow(
                    isPlaying = nowPlaying.isPlaying,
                    onCommand = { cmd ->
                        when (cmd) {
                            MusicCommand.SkipNext -> {
                                skipDirection = 1
                            }

                            MusicCommand.SkipPrevious -> {
                                skipDirection = -1
                            }

                            else -> {}
                        }
                        onCommand(cmd)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    gap = 16.dp,
                )
            }
        }
    }
}

@PreviewLightDark
@PreviewTextStress
@Preview(name = "Music card · playing", widthDp = 360, heightDp = 200)
@Composable
private fun MusicCardPlayingPreview() {
    FemtoTheme {
        MusicCard(
            state =
                MusicCardState.Playing(
                    nowPlaying =
                        NowPlaying(
                            title = "Midnight Drive",
                            artist = "The Wayfinders",
                            album = "Night Routes",
                            albumArt = null,
                            isPlaying = true,
                            positionMs = 98_000,
                            durationMs = 168_000,
                            packageName = "com.example.music",
                        ),
                ),
            onCommand = {},
            onConnect = {},
            onLaunchSource = {},
            onExpand = {},
            onPlay = {},
        )
    }
}

@PreviewLightDark
@PreviewTextStress
@Preview(name = "Music card · empty", widthDp = 360, heightDp = 360)
@Composable
private fun MusicCardEmptyPreview() {
    FemtoTheme {
        MusicCard(
            state = MusicCardState.NoActiveSession,
            onCommand = {},
            onConnect = {},
            onLaunchSource = {},
            onExpand = {},
            onPlay = {},
        )
    }
}
