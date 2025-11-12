package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import mihon.feature.translation.domain.TranslationPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TranslationSettingsScreen : Screen, SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }

        return listOf(
            getGeneralGroup(translationPreferences),
            getOCRGroup(translationPreferences),
            getTranslatorGroup(translationPreferences),
            getAdvancedGroup(translationPreferences),
            getDebugGroup(),
        )
    }

    @Composable
    private fun getGeneralGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_general),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.enabled(),
                    title = stringResource(MR.strings.pref_translation_enable),
                    subtitle = stringResource(MR.strings.pref_translation_enable_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.sourceLanguage(),
                    title = stringResource(MR.strings.pref_translation_source_language),
                    subtitle = stringResource(MR.strings.pref_translation_source_language_summary),
                    entries = persistentMapOf(
                        "ja" to stringResource(MR.strings.language_japanese),
                        "zh-CN" to stringResource(MR.strings.language_chinese_simplified),
                        "zh-TW" to stringResource(MR.strings.language_chinese_traditional),
                        "ko" to stringResource(MR.strings.language_korean),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.targetLanguage(),
                    title = stringResource(MR.strings.pref_translation_target_language),
                    subtitle = stringResource(MR.strings.pref_translation_target_language_summary),
                    entries = persistentMapOf(
                        "en" to stringResource(MR.strings.language_english),
                        "ja" to stringResource(MR.strings.language_japanese),
                        "zh-CN" to stringResource(MR.strings.language_chinese_simplified),
                        "zh-TW" to stringResource(MR.strings.language_chinese_traditional),
                        "ko" to stringResource(MR.strings.language_korean),
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun getOCRGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_ocr),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.ocrProvider(),
                    title = stringResource(MR.strings.pref_translation_ocr_provider),
                    subtitle = stringResource(MR.strings.pref_translation_ocr_provider_summary),
                    entries = persistentMapOf(
                        TranslationPreferences.OCR_AUTO to stringResource(MR.strings.pref_translation_ocr_auto),
                        TranslationPreferences.OCR_ML_KIT to stringResource(MR.strings.pref_translation_ocr_mlkit),
                        TranslationPreferences.OCR_PADDLE to stringResource(MR.strings.pref_translation_ocr_paddle),
                    ),
                ),
                // TODO: Add ModelDownloadSection as CustomPreference
            ),
        )
    }

    @Composable
    private fun getTranslatorGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val translatorProvider by translationPreferences.translatorProvider().collectAsState()

        val preferenceItems = mutableListOf<Preference.PreferenceItem<out Any, out Any>>(
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translatorProvider(),
                title = stringResource(MR.strings.pref_translation_provider),
                entries = persistentMapOf(
                    TranslationPreferences.TRANSLATOR_GOOGLE to
                        stringResource(MR.strings.pref_translation_provider_google),
                    TranslationPreferences.TRANSLATOR_GEMINI to
                        stringResource(MR.strings.pref_translation_provider_gemini),
                    TranslationPreferences.TRANSLATOR_OPENAI to
                        stringResource(MR.strings.pref_translation_provider_openai),
                ),
            ),
        )

        // Add API key input based on selected provider
        when (translatorProvider) {
            TranslationPreferences.TRANSLATOR_GEMINI,
            TranslationPreferences.PROVIDER_GEMINI,
            -> {
                preferenceItems.add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.geminiApiKey(),
                        title = stringResource(MR.strings.pref_translation_gemini_api_key),
                        subtitle = stringResource(MR.strings.pref_translation_gemini_api_key_summary),
                    ),
                )
            }
            TranslationPreferences.TRANSLATOR_OPENAI,
            TranslationPreferences.PROVIDER_OPENAI_COMPAT,
            "openai_compat",
            -> {
                preferenceItems.add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openAiBaseUrl(),
                        title = stringResource(MR.strings.pref_translation_openai_base_url),
                        subtitle = stringResource(MR.strings.pref_translation_openai_base_url_summary),
                    ),
                )
                preferenceItems.add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openAiModelName(),
                        title = stringResource(MR.strings.pref_translation_openai_model_name),
                        subtitle = stringResource(MR.strings.pref_translation_openai_model_name_summary),
                    ),
                )
                preferenceItems.add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openaiApiKey(),
                        title = stringResource(MR.strings.pref_translation_openai_api_key),
                        subtitle = stringResource(MR.strings.pref_translation_openai_api_key_summary),
                    ),
                )
            }
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_translator),
            preferenceItems = preferenceItems.toPersistentList(),
        )
    }

    @Composable
    private fun getAdvancedGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val cache = remember { uy.kohesive.injekt.Injekt.get<mihon.feature.translation.data.TranslationCache>() }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_translation_advanced),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.cacheTranslations(),
                    title = stringResource(MR.strings.pref_translation_cache),
                    subtitle = stringResource(MR.strings.pref_translation_cache_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_translation_clear_cache),
                    subtitle = stringResource(MR.strings.pref_translation_clear_cache_summary),
                    onClick = {
                        scope.launch {
                            try {
                                cache.clearAll()
                                android.widget.Toast.makeText(
                                    context,
                                    "Translation cache cleared successfully",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            } catch (e: Exception) {
                                android.util.Log.e("TranslationSettings", "Failed to clear cache", e)
                                android.widget.Toast.makeText(
                                    context,
                                    "Failed to clear cache: ${e.message}",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDebugGroup(): Preference.PreferenceGroup {
        val context = androidx.compose.ui.platform.LocalContext.current
        val logger = remember { mihon.feature.translation.data.TranslationLogger.getInstance(context) }

        return Preference.PreferenceGroup(
            title = "Debug & Logs",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.InfoPreference(
                    "Log files are saved to help diagnose translation issues. " +
                        "Logs include details about bubble detection, OCR, translation API, and rendering.",
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "View Current Log",
                    subtitle = logger.getLogFile().name,
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    logger.getLogFile(),
                                )
                                setDataAndType(uri, "text/plain")
                                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("TranslationSettings", "Failed to open log file", e)
                            android.widget.Toast.makeText(
                                context,
                                "Failed to open log file: ${e.message}",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Share Logs",
                    subtitle = "Share log files for debugging",
                    onClick = {
                        try {
                            val logFiles = logger.getAllLogFiles()
                            if (logFiles.isEmpty()) {
                                android.widget.Toast.makeText(
                                    context,
                                    "No log files available",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@TextPreference
                            }

                            val uris = logFiles.map { file ->
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file,
                                )
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "text/plain"
                                putParcelableArrayListExtra(
                                    android.content.Intent.EXTRA_STREAM,
                                    ArrayList(uris),
                                )
                                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(intent, "Share Translation Logs"),
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("TranslationSettings", "Failed to share logs", e)
                            android.widget.Toast.makeText(
                                context,
                                "Failed to share logs: ${e.message}",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Log Directory",
                    subtitle = logger.getLogDirectory(),
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText(
                                "Log Directory",
                                logger.getLogDirectory(),
                            )
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(
                                context,
                                "Log directory path copied to clipboard",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } catch (e: Exception) {
                            android.util.Log.e("TranslationSettings", "Failed to copy path", e)
                            android.widget.Toast.makeText(
                                context,
                                "Failed to copy path: ${e.message}",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ),
            ),
        )
    }
}
