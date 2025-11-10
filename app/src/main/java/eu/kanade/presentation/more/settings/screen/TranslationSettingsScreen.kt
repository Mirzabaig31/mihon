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
            TranslationPreferences.TRANSLATOR_GEMINI -> {
                preferenceItems.add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.geminiApiKey(),
                        title = stringResource(MR.strings.pref_translation_gemini_api_key),
                        subtitle = stringResource(MR.strings.pref_translation_gemini_api_key_summary),
                    ),
                )
            }
            TranslationPreferences.TRANSLATOR_OPENAI -> {
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
                        // TODO: Implement cache clearing
                    },
                ),
            ),
        )
    }
}
