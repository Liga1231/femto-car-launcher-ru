package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MapColorScheme
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapSchemeTest {
    @Test
    fun `accent resolves to the light base in the light context`() {
        val ref = mapStyleRefFor(MapColorScheme.ACCENT, isDark = false)
        assertTrue(ref is MapStyleRef.Accent)
        assertEquals(LIGHT_STYLE_ASSET, ref.baseAsset)
    }

    @Test
    fun `accent resolves to the dark base in the dark context`() {
        val ref = mapStyleRefFor(MapColorScheme.ACCENT, isDark = true)
        assertTrue(ref is MapStyleRef.Accent)
        assertEquals(DARK_STYLE_ASSET, ref.baseAsset)
    }

    @Test
    fun `muted warm and slate resolve to accent with tuned palettes`() {
        val mutedLight = mapStyleRefFor(MapColorScheme.MUTED, isDark = false)
        assertTrue(mutedLight is MapStyleRef.Accent)
        assertEquals(MutedLightMapColors, mutedLight.customColors)

        val mutedDark = mapStyleRefFor(MapColorScheme.MUTED, isDark = true)
        assertTrue(mutedDark is MapStyleRef.Accent)
        assertEquals(MutedDarkMapColors, mutedDark.customColors)

        val warmLight = mapStyleRefFor(MapColorScheme.WARM, isDark = false)
        assertTrue(warmLight is MapStyleRef.Accent)
        assertEquals(WarmLightMapColors, warmLight.customColors)

        val slateLight = mapStyleRefFor(MapColorScheme.SLATE, isDark = false)
        assertTrue(slateLight is MapStyleRef.Accent)
        assertEquals(SlateLightMapColors, slateLight.customColors)
    }

    @Test
    fun `dark matter resolves to the bundled dark asset`() {
        val ref = mapStyleRefFor(MapColorScheme.DARK_MATTER, isDark = true)
        assertEquals(MapStyleRef.Bundled(DARK_STYLE_ASSET), ref)
    }

    @Test
    fun `fixed schemes resolve to their hosted OpenFreeMap urls`() {
        val expected =
            mapOf(
                MapColorScheme.POSITRON to POSITRON_STYLE_URL,
                MapColorScheme.BRIGHT to OFM_STYLE_BASE + "bright",
                MapColorScheme.LIBERTY to OFM_STYLE_BASE + "liberty",
                MapColorScheme.DARK to OFM_STYLE_BASE + "dark",
                MapColorScheme.FIORD to OFM_STYLE_BASE + "fiord",
            )
        expected.forEach { (scheme, url) ->
            assertEquals(MapStyleRef.Hosted(url), mapStyleRefFor(scheme, isDark = false))
        }
    }

    @Test
    fun `custom resolves to the user's url, marked custom, with surrounding whitespace dropped`() {
        val ref =
            mapStyleRefFor(
                MapColorScheme.CUSTOM,
                isDark = false,
                customStyleUrl = " https://example.test/style.json?key=k ",
            )
        assertEquals(MapStyleRef.Hosted("https://example.test/style.json?key=k", custom = true), ref)
    }

    @Test
    fun `custom with no url renders as accent for the active context`() {
        // Never a blank map: until a URL is entered the choice falls back to the
        // adaptive default, matching what the Style URL row's summary promises.
        assertEquals(
            mapStyleRefFor(MapColorScheme.ACCENT, isDark = true),
            mapStyleRefFor(MapColorScheme.CUSTOM, isDark = true, customStyleUrl = "  "),
        )
    }
}
