package io.github.seijikohara.femto.data.music

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Briefcase
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Smile
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Zap
import io.github.seijikohara.femto.R

internal data class MusicVibe(
    val id: String,
    val titleRes: Int,
    val titleRu: String,
    val icon: ImageVector,
    val uriString: String,
    /** stationId для MediaBrowser API (напр. "user:onyourwave") */
    val stationId: String = "user:onyourwave",
    /** seeds для MediaBrowser API (напр. "settingMoodEnergy:active"), null если не нужны */
    val seeds: String? = null,
)

internal object MusicVibes {
    val MY_WAVE get() = ALL.first()

    val ALL =
        listOf(
            MusicVibe(
                id = "wave",
                titleRes = R.string.vibe_my_wave,
                titleRu = "Моя волна",
                icon = Lucide.Sparkles,
                uriString = "yandexmusic://play-vibe/?stationId=user:onyourwave",
                stationId = "user:onyourwave",
                seeds = null,
            ),
            MusicVibe(
                id = "favorite",
                titleRes = R.string.vibe_favorite,
                titleRu = "Любимое",
                icon = Lucide.Heart,
                uriString = "yandexmusic://play-vibe/?stationId=user:onyourwave&seeds=settingDiversity:favorite",
                stationId = "user:onyourwave",
                seeds = "settingDiversity:favorite",
            ),
            MusicVibe(
                id = "energetic",
                titleRes = R.string.vibe_energetic,
                titleRu = "Бодрое",
                icon = Lucide.Zap,
                uriString = "yandexmusic://play-vibe/?stationId=user:onyourwave&seeds=settingMoodEnergy:active",
                stationId = "user:onyourwave",
                seeds = "settingMoodEnergy:active",
            ),
            MusicVibe(
                id = "calm",
                titleRes = R.string.vibe_calm,
                titleRu = "Спокойное",
                icon = Lucide.Coffee,
                uriString = "yandexmusic://play-vibe/?stationId=user:onyourwave&seeds=settingMoodEnergy:calm",
                stationId = "user:onyourwave",
                seeds = "settingMoodEnergy:calm",
            ),
            MusicVibe(
                id = "wake_up",
                titleRes = R.string.vibe_wake_up,
                titleRu = "Просыпаюсь",
                icon = Lucide.Sun,
                uriString = "yandexmusic://play-vibe/?stationId=activity:wake-up",
                stationId = "activity:wake-up",
                seeds = null,
            ),
            MusicVibe(
                id = "road_trip",
                titleRes = R.string.vibe_road_trip,
                titleRu = "В дороге",
                icon = Lucide.Car,
                uriString = "yandexmusic://play-vibe/?stationId=activity:road-trip",
                stationId = "activity:road-trip",
                seeds = null,
            ),
            MusicVibe(
                id = "work",
                titleRes = R.string.vibe_work,
                titleRu = "Работаю",
                icon = Lucide.Briefcase,
                uriString = "yandexmusic://play-vibe/?stationId=activity:work-background",
                stationId = "activity:work-background",
                seeds = null,
            ),
            MusicVibe(
                id = "workout",
                titleRes = R.string.vibe_workout,
                titleRu = "Тренируюсь",
                icon = Lucide.Dumbbell,
                uriString = "yandexmusic://play-vibe/?stationId=activity:workout",
                stationId = "activity:workout",
                seeds = null,
            ),
            MusicVibe(
                id = "sleep",
                titleRes = R.string.vibe_sleep,
                titleRu = "Засыпаю",
                icon = Lucide.Moon,
                uriString = "yandexmusic://play-vibe/?stationId=activity:fall-asleep",
                stationId = "activity:fall-asleep",
                seeds = null,
            ),
            MusicVibe(
                id = "happy",
                titleRes = R.string.vibe_happy,
                titleRu = "Радостное",
                icon = Lucide.Smile,
                uriString = "yandexmusic://play-vibe/?stationId=user:onyourwave&seeds=settingMoodEnergy:fun",
                stationId = "user:onyourwave",
                seeds = "settingMoodEnergy:fun",
            ),
            MusicVibe(
                id = "popular",
                titleRes = R.string.vibe_popular,
                titleRu = "Популярное",
                icon = Lucide.Flame,
                uriString = "yandexmusic://play-vibe/?stationId=user:onyourwave&seeds=settingDiversity:popular",
                stationId = "user:onyourwave",
                seeds = "settingDiversity:popular",
            ),
        )
}
