package mihon.feature.translation.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mihon.feature.translation.domain.TranslationManager
import mihon.feature.translation.domain.models.Language

/**
 * Translation overlay that appears on top of the reader
 * Shows translation status and controls
 */
@Composable
fun TranslationOverlay(
    translationManager: TranslationManager,
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preference by translationManager.preference.collectAsState()
    val isEnabled by translationManager.isEnabled.collectAsState(initial = false)

    AnimatedVisibility(
        visible = isEnabled,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            TranslationBanner(
                sourceLanguage = preference.sourceLanguage,
                targetLanguage = preference.targetLanguage,
                onSettingsClick = onSettingsClick,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun TranslationBanner(
    sourceLanguage: Language,
    targetLanguage: Language,
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Translation Active",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "${sourceLanguage.displayName} → ${targetLanguage.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = onSettingsClick,
                modifier = Modifier,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Translation settings",
                )
            }

            FilledTonalIconButton(
                onClick = onDismiss,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Disable translation",
                )
            }
        }
    }
}

/**
 * Progress indicator for translation processing
 */
@Composable
fun TranslationProgressIndicator(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = progress != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                )
                .padding(16.dp),
        ) {
            if (progress != null && progress < 1f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier,
                )
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
