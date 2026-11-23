package com.app.drivedata.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.drivedata.R
import com.app.drivedata.ui.theme.Dimens
import com.app.drivedata.ui.theme.HomeBlue
import com.app.drivedata.ui.theme.HomeDivider
import com.app.drivedata.ui.theme.HomeSurface
import com.app.drivedata.ui.theme.HomeTextPrimary
import com.app.drivedata.ui.theme.HomeTextSecondary

/** Standard white card: one radius, soft shadow, one padding value. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Dimens.CardPadding),
    border: BorderStroke? = null,
    elevation: Dp = 2.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = HomeSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** Section title rendered outside the cards, as in the dashboard mockup. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = HomeTextPrimary,
        modifier = modifier,
    )
}

/** Small colored dot followed by a readable status label. */
@Composable
fun StatusLabel(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = HomeTextPrimary,
            maxLines = 2,
        )
    }
}

/** A label/value row whose value is a status dot plus text. */
@Composable
fun StatusInfoRow(
    label: String,
    color: Color,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HomeTextSecondary,
            modifier = Modifier.weight(1f),
        )
        StatusLabel(color = color, label = value)
    }
}

/** Icon inside a circle tinted with the icon color, as used across the dashboard. */
@Composable
fun TintedIconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Aligned label/value row used inside detail cards. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HomeTextPrimary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HomeTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

/** Filled call-to-action button in the brand blue. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    containerColor: Color = HomeBlue,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(Dimens.InnerCorner),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = 0.35f),
                disabledContentColor = contentColor.copy(alpha = 0.75f),
            ),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Muted outlined button used for secondary actions. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(Dimens.InnerCorner),
        border = BorderStroke(1.dp, HomeDivider),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = HomeBlue,
                disabledContentColor = HomeTextSecondary,
            ),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Lightweight line chart with a gradient fill. Empty history draws nothing, so unavailable
 * readings never imply a value.
 */
@Composable
fun MetricSparkline(
    points: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas

        if (points.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val min = points.min()
        val max = points.max()
        val span = max - min
        val stepX = size.width / points.lastIndex
        val verticalPadding = 3.dp.toPx()
        val usableHeight = (size.height - verticalPadding * 2f).coerceAtLeast(1f)

        fun pointAt(index: Int): Offset {
            val ratio =
                if (span > 1e-9) ((points[index] - min) / span).toFloat() else 0.5f
            return Offset(
                x = index * stepX,
                y = size.height - verticalPadding - ratio * usableHeight,
            )
        }

        val linePath = Path().apply {
            val first = pointAt(0)
            moveTo(first.x, first.y)
            for (index in 1..points.lastIndex) {
                val point = pointAt(index)
                lineTo(point.x, point.y)
            }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)),
            ),
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = color,
            radius = 2.5.dp.toPx(),
            center = pointAt(points.lastIndex),
        )
    }
}

/** Keeps vertical rhythm between stacked rows inside cards. */
@Composable
fun RowDivider(verticalPadding: Dp = 10.dp) {
    Spacer(Modifier.height(verticalPadding))
    HorizontalDivider(color = HomeDivider)
    Spacer(Modifier.height(verticalPadding))
}
