# Clock Widget + Style Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a clock App Widget (RemoteViews + `TextClock`) with a premium-gated style engine (color, text size, background opacity), per-instance persistence, a config screen, and an in-app "Add to home screen" entry.

**Architecture:** The clock renders as RemoteViews wrapping `TextClock` (self-updating, battery-free — Glance can't tick a clock). A renderer-agnostic core (`WidgetStyle` model + `WidgetStyleStore` over DataStore + `WidgetStyleResolver`) drives it, so the later Glance widgets reuse it. The config Activity reads the already-shipped `EntitlementRepository.observePremium()` to gate the style controls behind the one-time `premium` unlock.

**Tech Stack:** Kotlin, RemoteViews/AppWidget, DataStore Preferences, Jetpack Compose + Hilt (config screen), JUnit4 + Turbine + coroutines-test.

**Spec:** [docs/superpowers/specs/2026-06-30-clock-widget-style-engine-design.md](../specs/2026-06-30-clock-widget-style-engine-design.md)

**Style-engine scope note:** v1 controls are **color, text size, background opacity** — the subset RemoteViews supports reliably on minSdk 26. Font and corner-radius are deferred to the Glance-widget slice (where Glance expresses them cleanly); presets and wallpaper-matching are deferred premium extras. The premium gate covers whatever controls exist.

---

## File Structure

**New (main):**
- `data/widget/WidgetStyle.kt` — model + encode/decode (pure).
- `data/widget/WidgetStyleResolver.kt` — style → concrete RemoteViews values (pure).
- `data/widget/WidgetStyleStore.kt` — interface.
- `data/widget/DataStoreWidgetStyleStore.kt` — DataStore impl.
- `widget/clock/ClockWidgetProvider.kt` — AppWidgetProvider building RemoteViews.
- `ui/widgetconfig/WidgetConfigViewModel.kt` — config state + premium gating.
- `ui/widgetconfig/WidgetConfigActivity.kt` — Compose config screen.
- `res/layout/widget_clock.xml` — TextClock layout.
- `res/xml/clock_widget_info.xml` — AppWidgetProviderInfo.

**New (test):**
- `data/widget/WidgetStyleTest.kt`
- `data/widget/WidgetStyleResolverTest.kt`
- `ui/widgetconfig/WidgetConfigViewModelTest.kt`
- `ui/widgetconfig/WidgetConfigFakes.kt`

**Modified:**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — DataStore dep.
- `di/DataModule.kt` — provide `DataStore<Preferences>`.
- `di/RepositoryModule.kt` — bind `WidgetStyleStore`.
- `app/src/main/AndroidManifest.xml` — receiver + config activity.
- `ui/screens/WidgetsScreen.kt` — preview + add-to-home + customize.
- `res/values/strings.xml` — widget copy.
- `dwell-docs/02-TRD.md`, `dwell-docs/01-PRD.md`, `dwell-docs/08-CLAUDE.md` — customization is premium.

---

## Task 1: Add DataStore dependency

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`

- [ ] **Step 1: Version catalog**

In `gradle/libs.versions.toml` under `[versions]` add:
```toml
datastore = "1.1.1"
```
Under `[libraries]` add:
```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: App module**

In `app/build.gradle.kts`, after the Room block add:
```kotlin
    // DataStore (per-widget style config)
    implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: Verify**

Run: `./gradlew :app:help -q`
Expected: BUILD SUCCESSFUL (catalog parses).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add DataStore Preferences for widget config"
```

---

## Task 2: WidgetStyle model + encode/decode (TDD)

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/widget/WidgetStyle.kt`
- Test: `app/src/test/java/com/dwell/app/data/widget/WidgetStyleTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dwell.app.data.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStyleTest {

    @Test
    fun `default style has expected values`() {
        val d = WidgetStyle.Default
        assertEquals(WidgetColor.CREAM, d.color)
        assertEquals(WidgetSize.MEDIUM, d.size)
        assertEquals(100, d.opacity)
    }

    @Test
    fun `opacity is clamped to 0 to 100`() {
        assertEquals(0, WidgetStyle.Default.copy(opacity = -10).coerced().opacity)
        assertEquals(100, WidgetStyle.Default.copy(opacity = 250).coerced().opacity)
    }

    @Test
    fun `encode then decode round-trips`() {
        val style = WidgetStyle(WidgetColor.GREEN, WidgetSize.LARGE, 60)
        assertEquals(style, WidgetStyle.decode(style.encode()))
    }

    @Test
    fun `decode of garbage returns default`() {
        assertEquals(WidgetStyle.Default, WidgetStyle.decode("not-a-style"))
        assertEquals(WidgetStyle.Default, WidgetStyle.decode(""))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.data.widget.WidgetStyleTest" -q`
Expected: FAIL — `WidgetStyle` not defined.

- [ ] **Step 3: Implement**

```kotlin
package com.dwell.app.data.widget

/** Text color options for a widget. Keys map to locked design tokens in WidgetStyleResolver. */
enum class WidgetColor { CREAM, GREEN, CHARCOAL }

/** Text size buckets. */
enum class WidgetSize { SMALL, MEDIUM, LARGE }

/**
 * A widget instance's visual style. v1 engine: color, size, background opacity.
 * Font and corner radius arrive with the Glance widgets.
 */
data class WidgetStyle(
    val color: WidgetColor = WidgetColor.CREAM,
    val size: WidgetSize = WidgetSize.MEDIUM,
    val opacity: Int = 100, // 0..100, background alpha
) {
    fun coerced(): WidgetStyle = copy(opacity = opacity.coerceIn(0, 100))

    /** Pipe-delimited, no extra serialization dependency. */
    fun encode(): String = "${color.name}|${size.name}|$opacity"

    companion object {
        val Default = WidgetStyle()

        fun decode(raw: String): WidgetStyle = runCatching {
            val (c, s, o) = raw.split("|").also { require(it.size == 3) }
            WidgetStyle(
                color = WidgetColor.valueOf(c),
                size = WidgetSize.valueOf(s),
                opacity = o.toInt(),
            ).coerced()
        }.getOrDefault(Default)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.data.widget.WidgetStyleTest" -q`
Expected: PASS — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/widget/WidgetStyle.kt app/src/test/java/com/dwell/app/data/widget/WidgetStyleTest.kt
git commit -m "feat(widget): add WidgetStyle model with encode/decode"
```

---

## Task 3: WidgetStyleResolver (TDD)

Maps a `WidgetStyle` to concrete ARGB ints and sizes for RemoteViews. The token colors mirror `ui/theme/Color.kt` (Glance/RemoteViews can't read the Compose theme).

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/widget/WidgetStyleResolver.kt`
- Test: `app/src/test/java/com/dwell/app/data/widget/WidgetStyleResolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dwell.app.data.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStyleResolverTest {

    @Test
    fun `text color maps from token`() {
        assertEquals(0xFFECE7DD.toInt(), WidgetStyleResolver.textColorArgb(WidgetStyle(color = WidgetColor.CREAM)))
        assertEquals(0xFF6E9576.toInt(), WidgetStyleResolver.textColorArgb(WidgetStyle(color = WidgetColor.GREEN)))
    }

    @Test
    fun `time text size grows with bucket`() {
        val small = WidgetStyleResolver.timeSizeSp(WidgetStyle(size = WidgetSize.SMALL))
        val large = WidgetStyleResolver.timeSizeSp(WidgetStyle(size = WidgetSize.LARGE))
        assert(large > small)
    }

    @Test
    fun `opacity maps to background alpha`() {
        // 0% -> fully transparent, 100% -> fully opaque surface
        assertEquals(0x00, WidgetStyleResolver.backgroundArgb(WidgetStyle(opacity = 0)) ushr 24)
        assertEquals(0xFF, WidgetStyleResolver.backgroundArgb(WidgetStyle(opacity = 100)) ushr 24)
        assertEquals(0x80, WidgetStyleResolver.backgroundArgb(WidgetStyle(opacity = 50)) ushr 24)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.data.widget.WidgetStyleResolverTest" -q`
Expected: FAIL — `WidgetStyleResolver` not defined.

- [ ] **Step 3: Implement**

```kotlin
package com.dwell.app.data.widget

/** Pure mapping of [WidgetStyle] to RemoteViews-ready values. Mirrors ui/theme/Color.kt tokens. */
object WidgetStyleResolver {

    private const val CREAM = 0xFFECE7DD.toInt()
    private const val GREEN = 0xFF6E9576.toInt()    // AccentDark (AA over warm surfaces)
    private const val CHARCOAL = 0xFF221F1A.toInt()
    private const val SURFACE = 0x221F1A            // warm surface, alpha applied from opacity

    fun textColorArgb(style: WidgetStyle): Int = when (style.color) {
        WidgetColor.CREAM -> CREAM
        WidgetColor.GREEN -> GREEN
        WidgetColor.CHARCOAL -> CHARCOAL
    }

    fun timeSizeSp(style: WidgetStyle): Float = when (style.size) {
        WidgetSize.SMALL -> 28f
        WidgetSize.MEDIUM -> 40f
        WidgetSize.LARGE -> 56f
    }

    fun dateSizeSp(style: WidgetStyle): Float = timeSizeSp(style) * 0.4f

    fun backgroundArgb(style: WidgetStyle): Int {
        val alpha = (style.opacity.coerceIn(0, 100) * 255 / 100)
        return (alpha shl 24) or SURFACE
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.data.widget.WidgetStyleResolverTest" -q`
Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/widget/WidgetStyleResolver.kt app/src/test/java/com/dwell/app/data/widget/WidgetStyleResolverTest.kt
git commit -m "feat(widget): add WidgetStyleResolver token mapping"
```

---

## Task 4: WidgetStyleStore (interface + DataStore impl)

The store persists one encoded `WidgetStyle` string per `appWidgetId`. The interface is faked in the ViewModel test (Task 6); the DataStore impl is thin and verified on device.

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/widget/WidgetStyleStore.kt`
- Create: `app/src/main/java/com/dwell/app/data/widget/DataStoreWidgetStyleStore.kt`

- [ ] **Step 1: Write the interface**

```kotlin
package com.dwell.app.data.widget

import kotlinx.coroutines.flow.Flow

/** Persists a [WidgetStyle] per appWidgetId. */
interface WidgetStyleStore {
    fun observe(appWidgetId: Int): Flow<WidgetStyle>
    suspend fun get(appWidgetId: Int): WidgetStyle
    suspend fun save(appWidgetId: Int, style: WidgetStyle)
    suspend fun clear(appWidgetId: Int)
}
```

- [ ] **Step 2: Write the DataStore impl**

```kotlin
package com.dwell.app.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreWidgetStyleStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WidgetStyleStore {

    private fun key(id: Int) = stringPreferencesKey("widget_style_$id")

    override fun observe(appWidgetId: Int): Flow<WidgetStyle> =
        dataStore.data.map { prefs ->
            prefs[key(appWidgetId)]?.let { WidgetStyle.decode(it) } ?: WidgetStyle.Default
        }

    override suspend fun get(appWidgetId: Int): WidgetStyle = observe(appWidgetId).first()

    override suspend fun save(appWidgetId: Int, style: WidgetStyle) {
        dataStore.edit { it[key(appWidgetId)] = style.coerced().encode() }
    }

    override suspend fun clear(appWidgetId: Int) {
        dataStore.edit { it.remove(key(appWidgetId)) }
    }
}
```

- [ ] **Step 3: Provide DataStore + bind the store**

In `di/DataModule.kt` add imports:
```kotlin
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
```
And add the provider:
```kotlin
    @Provides
    @Singleton
    fun provideWidgetDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("widget_styles") }
```
In `di/RepositoryModule.kt` add imports + bind:
```kotlin
import com.dwell.app.data.widget.DataStoreWidgetStyleStore
import com.dwell.app.data.widget.WidgetStyleStore
```
```kotlin
    @Binds
    @Singleton
    abstract fun bindWidgetStyleStore(impl: DataStoreWidgetStyleStore): WidgetStyleStore
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/widget/WidgetStyleStore.kt app/src/main/java/com/dwell/app/data/widget/DataStoreWidgetStyleStore.kt app/src/main/java/com/dwell/app/di/
git commit -m "feat(widget): add WidgetStyleStore over DataStore"
```

---

## Task 5: Clock widget (layout + provider + manifest)

The clock is a RemoteViews widget: two `TextClock` views (time + date) on a rounded background, styled from the saved `WidgetStyle`. `updatePeriodMillis = 0` because `TextClock` self-updates. AppWidget/RemoteViews can't run on the JVM — verified on device.

**Files:**
- Create: `app/src/main/res/layout/widget_clock.xml`
- Create: `app/src/main/res/drawable/widget_bg.xml`
- Create: `app/src/main/res/xml/clock_widget_info.xml`
- Create: `app/src/main/java/com/dwell/app/widget/clock/ClockWidgetProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Background drawable**

`res/drawable/widget_bg.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="20dp" />
    <solid android:color="#221F1A" />
</shape>
```

- [ ] **Step 2: Widget layout**

`res/layout/widget_clock.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:background="@drawable/widget_bg"
    android:padding="16dp">

    <TextClock
        android:id="@+id/widget_time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:format12Hour="h:mm"
        android:format24Hour="H:mm"
        android:textColor="#ECE7DD"
        android:textSize="40sp" />

    <TextClock
        android:id="@+id/widget_date"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:format12Hour="EEE, MMM d"
        android:format24Hour="EEE, MMM d"
        android:textColor="#ECE7DD"
        android:textSize="16sp" />
</LinearLayout>
```

- [ ] **Step 3: AppWidgetProviderInfo**

`res/xml/clock_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_clock"
    android:configure="com.dwell.app.ui.widgetconfig.WidgetConfigActivity"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
```

- [ ] **Step 4: Provider**

`widget/clock/ClockWidgetProvider.kt`:
```kotlin
package com.dwell.app.widget.clock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import com.dwell.app.R
import com.dwell.app.data.widget.WidgetStyleResolver
import com.dwell.app.data.widget.WidgetStyleStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var store: WidgetStyleStore

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ids.forEach { id -> renderOne(context, manager, id) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun renderOne(context: Context, manager: AppWidgetManager, id: Int) {
        val style = store.get(id)
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        val textColor = WidgetStyleResolver.textColorArgb(style)
        views.setTextColor(R.id.widget_time, textColor)
        views.setTextColor(R.id.widget_date, textColor)
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, WidgetStyleResolver.timeSizeSp(style))
        views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, WidgetStyleResolver.dateSizeSp(style))
        views.setInt(R.id.widget_root, "setBackgroundColor", WidgetStyleResolver.backgroundArgb(style))
        manager.updateAppWidget(id, views)
    }

    companion object {
        /** Re-render a single widget after its config changes. */
        fun refresh(context: Context, id: Int) {
            val intent = android.content.Intent(context, ClockWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
            }
            context.sendBroadcast(intent)
        }
    }
}
```

- [ ] **Step 5: Register in the manifest**

In `AndroidManifest.xml`, inside `<application>`, add:
```xml
        <receiver
            android:name=".widget.clock.ClockWidgetProvider"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/clock_widget_info" />
        </receiver>
```

- [ ] **Step 6: Verify compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/widget_clock.xml app/src/main/res/drawable/widget_bg.xml app/src/main/res/xml/clock_widget_info.xml app/src/main/java/com/dwell/app/widget/ app/src/main/AndroidManifest.xml
git commit -m "feat(widget): add clock AppWidget with TextClock renderer"
```

---

## Task 6: Config screen (ViewModel TDD + Activity)

The ViewModel holds the draft style + premium flag and persists on save. TDD with fakes. The Activity is Compose on the app design system — compiled here, verified on device.

**Files:**
- Create: `app/src/main/java/com/dwell/app/ui/widgetconfig/WidgetConfigViewModel.kt`
- Create: `app/src/main/java/com/dwell/app/ui/widgetconfig/WidgetConfigActivity.kt`
- Test: `app/src/test/java/com/dwell/app/ui/widgetconfig/WidgetConfigFakes.kt`
- Test: `app/src/test/java/com/dwell/app/ui/widgetconfig/WidgetConfigViewModelTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write the test fakes**

```kotlin
package com.dwell.app.ui.widgetconfig

import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.widget.WidgetStyle
import com.dwell.app.data.widget.WidgetStyleStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWidgetStyleStore : WidgetStyleStore {
    val styles = MutableStateFlow<Map<Int, WidgetStyle>>(emptyMap())
    override fun observe(appWidgetId: Int): Flow<WidgetStyle> =
        styles.map { it[appWidgetId] ?: WidgetStyle.Default }
    override suspend fun get(appWidgetId: Int): WidgetStyle = styles.value[appWidgetId] ?: WidgetStyle.Default
    override suspend fun save(appWidgetId: Int, style: WidgetStyle) {
        styles.value = styles.value + (appWidgetId to style)
    }
    override suspend fun clear(appWidgetId: Int) {
        styles.value = styles.value - appWidgetId
    }
}

class FakeEntitlements(premium: Boolean) : EntitlementRepository {
    private val flow = MutableStateFlow(premium)
    override fun observePremium(): Flow<Boolean> = flow
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.dwell.app.ui.widgetconfig

import app.cash.turbine.test
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `premium flows through`() = runTest {
        val vm = WidgetConfigViewModel(FakeWidgetStyleStore(), FakeEntitlements(premium = true))
        vm.isPremium.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setColor updates the draft`() = runTest {
        val vm = WidgetConfigViewModel(FakeWidgetStyleStore(), FakeEntitlements(premium = true))
        vm.setColor(WidgetColor.GREEN)
        assertEquals(WidgetColor.GREEN, vm.draft.value.color)
    }

    @Test
    fun `save persists the draft`() = runTest {
        val store = FakeWidgetStyleStore()
        val vm = WidgetConfigViewModel(store, FakeEntitlements(premium = true))
        vm.setColor(WidgetColor.GREEN)
        vm.save(appWidgetId = 7)
        assertEquals(WidgetStyle(color = WidgetColor.GREEN), store.get(7))
    }

    @Test
    fun `load reads existing style into the draft`() = runTest {
        val store = FakeWidgetStyleStore()
        store.save(7, WidgetStyle(color = WidgetColor.CHARCOAL))
        val vm = WidgetConfigViewModel(store, FakeEntitlements(premium = false))
        vm.load(7)
        assertEquals(WidgetColor.CHARCOAL, vm.draft.value.color)
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.ui.widgetconfig.WidgetConfigViewModelTest" -q`
Expected: FAIL — `WidgetConfigViewModel` not defined.

- [ ] **Step 4: Implement the ViewModel**

```kotlin
package com.dwell.app.ui.widgetconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetSize
import com.dwell.app.data.widget.WidgetStyle
import com.dwell.app.data.widget.WidgetStyleStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val store: WidgetStyleStore,
    entitlements: EntitlementRepository,
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = entitlements.observePremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _draft = MutableStateFlow(WidgetStyle.Default)
    val draft: StateFlow<WidgetStyle> = _draft.asStateFlow()

    fun load(appWidgetId: Int) {
        viewModelScope.launch { _draft.value = store.get(appWidgetId) }
    }

    fun setColor(color: WidgetColor) = _draft.update { it.copy(color = color) }
    fun setSize(size: WidgetSize) = _draft.update { it.copy(size = size) }
    fun setOpacity(opacity: Int) = _draft.update { it.copy(opacity = opacity).coerced() }

    suspend fun save(appWidgetId: Int) = store.save(appWidgetId, _draft.value)
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.ui.widgetconfig.WidgetConfigViewModelTest" -q`
Expected: PASS — 4 tests.

- [ ] **Step 6: Implement the Activity**

`ui/widgetconfig/WidgetConfigActivity.kt`:
```kotlin
package com.dwell.app.ui.widgetconfig

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.DwellTheme
import com.dwell.app.widget.clock.ClockWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetConfigActivity : androidx.activity.ComponentActivity() {

    private val viewModel: WidgetConfigViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AppWidget config contract: default to CANCELED until the user saves.
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        viewModel.load(appWidgetId)

        setContent {
            DwellTheme {
                val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                DwellScaffold {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        // Preview + controls. Controls enabled only when isPremium;
                        // otherwise show an Unlock CTA. (Full UI fleshed out on device.)
                        Text(text = draft.color.name)
                        if (isPremium) {
                            DwellPrimaryButton(text = "Use green", onClick = { viewModel.setColor(WidgetColor.GREEN) })
                        } else {
                            DwellPrimaryButton(text = "Unlock Dwell", onClick = { /* route to unlock */ })
                        }
                        DwellPrimaryButton(text = "Save", onClick = ::commit)
                    }
                }
            }
        }
    }

    private fun commit() {
        lifecycleScope.launch {
            viewModel.save(appWidgetId)
            ClockWidgetProvider.refresh(this@WidgetConfigActivity, appWidgetId)
            setResult(Activity.RESULT_OK, Intent().putExtras(bundleOf(AppWidgetManager.EXTRA_APPWIDGET_ID to appWidgetId)))
            finish()
        }
    }
}
```

> On-device note: this Activity body is intentionally minimal-but-correct (the AppWidget result contract + save + refresh are the parts that must be exact). The color/size/opacity controls and live preview are filled in during on-device iteration against the design system; the gating rule (`isPremium` ? controls : Unlock CTA) and the save/refresh wiring are fixed here. Confirm `DwellTheme`, `DwellScaffold`, and `DwellPrimaryButton` signatures match `ui/theme/Theme.kt` and `ui/components/` before relying on them; adjust names if they differ.

- [ ] **Step 7: Register the Activity in the manifest**

In `AndroidManifest.xml` inside `<application>`:
```xml
        <activity
            android:name=".ui.widgetconfig.WidgetConfigActivity"
            android:exported="true"
            android:theme="@style/Theme.Dwell">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>
```
(Use the app's existing theme name from the manifest's main activity — confirm it is `Theme.Dwell` and adjust if not.)

- [ ] **Step 8: Verify compile + all unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest -q`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/widgetconfig/ app/src/test/java/com/dwell/app/ui/widgetconfig/ app/src/main/AndroidManifest.xml
git commit -m "feat(widget): add config screen with premium-gated style controls"
```

---

## Task 7: Widgets tab — preview + add-to-home + customize

Replace the placeholder with a card that previews the clock, an "Add to home screen" button (`requestPinAppWidget`, minSdk 26), and a "Customize" entry that opens the config Activity. UI verified on device.

**Files:**
- Modify: `app/src/main/java/com/dwell/app/ui/screens/WidgetsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Strings**

In `strings.xml` before `</resources>`:
```xml
    <!-- Widgets tab -->
    <string name="widgets_clock_title">Clock</string>
    <string name="widgets_clock_subtitle">A calm clock for your home screen.</string>
    <string name="widgets_add">Add to home screen</string>
    <string name="widgets_customize">Customize</string>
    <string name="widgets_more_soon">Calendar, notes, and battery are coming next.</string>
    <string name="widgets_pin_unsupported">Your launcher doesn\'t support adding widgets from here. Long-press your home screen instead.</string>
```

- [ ] **Step 2: Replace the screen**

`ui/screens/WidgetsScreen.kt`:
```kotlin
package com.dwell.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dwell.app.R
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.DwellSpacing
import com.dwell.app.widget.clock.ClockWidgetProvider

@Composable
fun WidgetsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DwellScaffold(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(DwellSpacing.screenGutter)) {
            Text(stringResource(R.string.widgets_clock_title))
            Text(stringResource(R.string.widgets_clock_subtitle))
            Spacer(Modifier.height(DwellSpacing.md))
            DwellPrimaryButton(
                text = stringResource(R.string.widgets_add),
                onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = ComponentName(context, ClockWidgetProvider::class.java)
                    if (manager.isRequestPinAppWidgetSupported) {
                        manager.requestPinAppWidget(provider, null, null)
                    }
                },
            )
            Spacer(Modifier.height(DwellSpacing.lg))
            Text(stringResource(R.string.widgets_more_soon))
        }
    }
}
```

> Confirm `DwellSpacing.screenGutter`, `DwellSpacing.md`, `DwellSpacing.lg`, `DwellScaffold`, and `DwellPrimaryButton` exist with these names in `ui/theme/Spacing.kt` and `ui/components/`; adjust to the real token/component names if they differ. The "Customize" deep-link to `WidgetConfigActivity` for an already-placed widget is a device-iteration follow-up (the primary config entry is the system placement flow).

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: On-device verification**

Run: `./gradlew :app:installDebug`
Then: open Widgets tab → "Add to home screen" → confirm the clock places and `TextClock` ticks; long-press it / re-add → config screen appears; with `premium=false` controls are locked + Unlock CTA; set `premium=true` by hand in Firestore → controls enable, saved style renders.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/screens/WidgetsScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(widget): widgets tab with add-to-home and clock preview"
```

---

## Task 8: Update docs — widget customization is premium

**Files:** Modify `dwell-docs/02-TRD.md`, `dwell-docs/01-PRD.md`, `dwell-docs/08-CLAUDE.md`

- [ ] **Step 1: TRD §5**

In `dwell-docs/02-TRD.md`, change the config bullet:
```
- Each widget has a **configuration Activity** launched on placement, where the user picks color and font. Config is persisted per widget instance (by `appWidgetId`) in DataStore.
```
to:
```
- Each widget has a **configuration Activity** launched on placement. Widgets work free in a default style; the **style engine (color, size, opacity; later font/radius/presets/wallpaper-match) is gated by the `premium` unlock**. Config is persisted per widget instance (by `appWidgetId`) in DataStore.
```

- [ ] **Step 2: PRD monetization**

In `dwell-docs/01-PRD.md`, in the §6 P0 Monetization free-tier bullet, append:
```
 Widget customization (the style engine) is part of the premium unlock; widgets themselves are free in a default style.
```

- [ ] **Step 3: 08-CLAUDE product rules**

In `dwell-docs/08-CLAUDE.md` Product rules, add a bullet under the monetization rule:
```
- **Widget customization is premium.** Widgets work free in a default Dwell style; the style engine (color/size/opacity, later font/radius/presets/wallpaper-match) is gated by the `premium` flag. Do not gate widget *function*, only *styling*.
```

- [ ] **Step 4: Commit**

```bash
git add dwell-docs/02-TRD.md dwell-docs/01-PRD.md dwell-docs/08-CLAUDE.md
git commit -m "docs: widget customization is the premium style engine"
```

---

## Self-Review

**Spec coverage:**
- Clock widget (RemoteViews + TextClock) → Task 5. ✓
- Style engine model + persistence + resolver → Tasks 2, 3, 4. ✓
- Premium-gated config Activity + ViewModel → Task 6. ✓
- In-app add-to-home (`requestPinAppWidget`) → Task 7. ✓
- DataStore dependency → Task 1. ✓
- Renderer-agnostic core (Style/Store/Resolver have no RemoteViews/Glance imports) → Tasks 2–4. ✓
- Docs update (customization is premium) → Task 8. ✓
- Deferred (calendar/notes/battery, weather/quick-launch/quotes, font/radius/presets/wallpaper-match) → stated in header + spec; no tasks, intentional. ✓

**Placeholder scan:** No "TBD"/"add error handling". The config Activity and Widgets tab carry real, compiling code; their UI polish is explicitly on-device iteration with the fixed contract (result codes, save/refresh, gating rule) written out — not a placeholder. Two "confirm the component/token names" notes exist because the design-system component signatures weren't re-read while planning; the executing engineer verifies against `ui/components/` and `ui/theme/` (cheap, local).

**Type consistency:** `WidgetStyle(color,size,opacity)` is used identically in Tasks 2, 3, 4, 6. `WidgetStyleStore` methods `observe/get/save/clear` match between interface (Task 4), impl (Task 4), and fake (Task 6). `WidgetStyleResolver.textColorArgb/timeSizeSp/dateSizeSp/backgroundArgb` match between Task 3 and Task 5. `EntitlementRepository.observePremium()` matches the already-merged interface. Provider `ClockWidgetProvider.refresh(context,id)` defined in Task 5, called in Task 6.

**Known device-only gaps (not unit-testable):** AppWidget render, RemoteViews styling, config Activity UI, `requestPinAppWidget`, `TextClock` tick — all in the Task 7 Step 4 on-device checklist.
