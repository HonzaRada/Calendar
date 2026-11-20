package cz.honza.mycalendar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.honza.mycalendar.R

internal val PinkCloudShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

internal val PinkCloudTypography = Typography.copy(
    headlineMedium = Typography.headlineMedium.copy(fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold),
    titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
internal fun pinkCloudColorScheme() = lightColorScheme(
    primary = colorResource(R.color.pink_cloud_berry),
    onPrimary = colorResource(R.color.pink_cloud_white),
    primaryContainer = colorResource(R.color.pink_cloud_rose),
    onPrimaryContainer = colorResource(R.color.pink_cloud_deep_berry),
    secondary = colorResource(R.color.pink_cloud_raspberry),
    onSecondary = colorResource(R.color.pink_cloud_white),
    secondaryContainer = colorResource(R.color.pink_cloud_blush),
    onSecondaryContainer = colorResource(R.color.pink_cloud_deep_berry),
    tertiary = colorResource(R.color.pink_cloud_lavender),
    onTertiary = colorResource(R.color.pink_cloud_white),
    tertiaryContainer = colorResource(R.color.pink_cloud_lilac),
    onTertiaryContainer = colorResource(R.color.pink_cloud_deep_plum),
    background = colorResource(R.color.pink_cloud_surface),
    onBackground = colorResource(R.color.pink_cloud_ink),
    surface = colorResource(R.color.pink_cloud_surface),
    onSurface = colorResource(R.color.pink_cloud_ink),
    surfaceVariant = colorResource(R.color.pink_cloud_tint),
    onSurfaceVariant = colorResource(R.color.pink_cloud_muted_ink),
    surfaceTint = colorResource(R.color.pink_cloud_berry),
    surfaceBright = colorResource(R.color.pink_cloud_surface),
    surfaceDim = colorResource(R.color.pink_cloud_dim),
    surfaceContainerLowest = colorResource(R.color.pink_cloud_white),
    surfaceContainerLow = colorResource(R.color.pink_cloud_low),
    surfaceContainer = colorResource(R.color.pink_cloud_tint),
    surfaceContainerHigh = colorResource(R.color.pink_cloud_high),
    surfaceContainerHighest = colorResource(R.color.pink_cloud_dim),
    outline = colorResource(R.color.pink_cloud_outline),
    outlineVariant = colorResource(R.color.pink_cloud_divider),
    inverseSurface = colorResource(R.color.pink_cloud_ink),
    inverseOnSurface = colorResource(R.color.pink_cloud_surface),
    inversePrimary = colorResource(R.color.pink_cloud_rose)
)
