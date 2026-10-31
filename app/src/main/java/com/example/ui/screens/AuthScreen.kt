package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleSignInButton
import com.example.auth.attemptAutoSignIn
import com.example.data.WallpaperPreset
import com.example.data.WidgetDesign
import com.example.data.WidgetStylePreset
import com.example.ui.components.LiveWidgetCard

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    val showcaseWidget = remember {
        WidgetDesign(
            title = "Погода • Liquid Glass",
            subtitle = "Адаптация под обои • УФ 2",
            primaryValue = "+23°C",
            secondaryValue = "iOS 26 & M3",
            stylePreset = WidgetStylePreset.LIQUID_GLASS.name,
            primaryColorHex = "#38BDF8",
            secondaryColorHex = "#A855F7",
            surfaceColorHex = "#0F172A",
            glassOpacity = 0.34,
            specularIntensity = 0.90,
            cornerRadiusDp = 30.0
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B14),
                        Color(0xFF111827),
                        Color(0xFF1E1B4B)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("auth_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color(0xFF38BDF8).copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                    shape = CircleShape
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.auth_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.auth_title),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Live Liquid Glass Demo Card on Auth Screen
            LiveWidgetCard(
                widget = showcaseWidget,
                effectiveDark = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            FeatureHighlightRow(
                icon = Icons.Default.AutoAwesome,
                text = stringResource(R.string.auth_feature_glass),
                accent = Color(0xFF38BDF8)
            )
            Spacer(modifier = Modifier.height(12.dp))
            FeatureHighlightRow(
                icon = Icons.Default.Palette,
                text = stringResource(R.string.auth_feature_palette),
                accent = Color(0xFFC084FC)
            )
            Spacer(modifier = Modifier.height(12.dp))
            FeatureHighlightRow(
                icon = Icons.Default.CloudDone,
                text = stringResource(R.string.auth_feature_sync),
                accent = Color(0xFFF472B6)
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("auth_error_banner")
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            GoogleSignInButton(
                onAuthSuccess = onAuthSuccess,
                onAuthError = { msg -> errorMessage = msg }
            )
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    text: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE2E8F0)
        )
    }
}
