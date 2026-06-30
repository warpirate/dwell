package com.dwell.app.data.widget

import kotlinx.coroutines.flow.Flow

/** Persists a [WidgetStyle] per appWidgetId. */
interface WidgetStyleStore {
    fun observe(appWidgetId: Int): Flow<WidgetStyle>
    suspend fun get(appWidgetId: Int): WidgetStyle
    suspend fun save(appWidgetId: Int, style: WidgetStyle)
    suspend fun clear(appWidgetId: Int)
}
