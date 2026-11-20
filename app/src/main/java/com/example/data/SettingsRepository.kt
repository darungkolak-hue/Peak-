package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ExportAspectRatio
import com.example.model.ExportFps
import com.example.model.ExportResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lyrix_settings")

data class AppSettings(
    val defaultResolution: ExportResolution = ExportResolution.FHD_1080P,
    val defaultAspectRatio: ExportAspectRatio = ExportAspectRatio.PORTRAIT_9_16,
    val defaultFps: ExportFps = ExportFps.FPS_30,
    val autoEditOnUpload: Boolean = true,
    val highQualityBlurPreview: Boolean = true
)

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val DEFAULT_RESOLUTION = stringPreferencesKey("default_resolution")
        val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val DEFAULT_FPS = stringPreferencesKey("default_fps")
        val AUTO_EDIT_ON_UPLOAD = booleanPreferencesKey("auto_edit_on_upload")
        val HIGH_QUALITY_BLUR = booleanPreferencesKey("high_quality_blur")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        val res = prefs[Keys.DEFAULT_RESOLUTION]?.let { raw ->
            ExportResolution.entries.firstOrNull { it.name == raw }
        } ?: ExportResolution.FHD_1080P
        val ratio = prefs[Keys.DEFAULT_ASPECT_RATIO]?.let { raw ->
            ExportAspectRatio.entries.firstOrNull { it.name == raw }
        } ?: ExportAspectRatio.PORTRAIT_9_16
        val fps = prefs[Keys.DEFAULT_FPS]?.let { raw ->
            ExportFps.entries.firstOrNull { it.name == raw }
        } ?: ExportFps.FPS_30
        AppSettings(
            defaultResolution = res,
            defaultAspectRatio = ratio,
            defaultFps = fps,
            autoEditOnUpload = prefs[Keys.AUTO_EDIT_ON_UPLOAD] ?: true,
            highQualityBlurPreview = prefs[Keys.HIGH_QUALITY_BLUR] ?: true
        )
    }

    suspend fun setDefaultResolution(resolution: ExportResolution) {
        dataStore.edit { it[Keys.DEFAULT_RESOLUTION] = resolution.name }
    }

    suspend fun setDefaultAspectRatio(ratio: ExportAspectRatio) {
        dataStore.edit { it[Keys.DEFAULT_ASPECT_RATIO] = ratio.name }
    }

    suspend fun setDefaultFps(fps: ExportFps) {
        dataStore.edit { it[Keys.DEFAULT_FPS] = fps.name }
    }

    suspend fun setAutoEditOnUpload(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_EDIT_ON_UPLOAD] = enabled }
    }

    suspend fun setHighQualityBlur(enabled: Boolean) {
        dataStore.edit { it[Keys.HIGH_QUALITY_BLUR] = enabled }
    }
}
