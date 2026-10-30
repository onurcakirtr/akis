package com.akis.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.annotation.PluralsRes
import com.akis.app.R
import com.akis.app.ui.theme.Backdrop
import com.akis.app.ui.theme.LocalAppTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew

/** Temanın arka plan deseni (nokta ızgarası, çizgi ızgarası, tarama çizgileri ya da hiçbiri). */
fun Modifier.backdrop(kind: Backdrop, color: Color): Modifier = when (kind) {
    Backdrop.NONE -> this
    Backdrop.DOTS -> dotGrid(color)
    Backdrop.GRID -> drawWithCache {
        val step = 28.dp.toPx()
        val w = 1.dp.toPx()
        onDrawBehind {
            var x = 0f
            while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), w); x += step }
            var y = 0f
            while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), w); y += step }
        }
    }
    Backdrop.SCANLINES -> drawWithCache {
        val step = 4.dp.toPx()
        val w = 1.dp.toPx()
        onDrawBehind {
            var y = 0f
            while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), w); y += step }
        }
    }
}

/** Nothing tarzı nokta ızgarası arka planı. */
fun Modifier.dotGrid(color: Color, spacing: Dp = 18.dp, dot: Dp = 2.dp): Modifier = drawWithCache {
    val step = spacing.toPx()
    val pts = ArrayList<Offset>()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            pts += Offset(x, y)
            x += step
        }
        y += step
    }
    val w = dot.toPx()
    onDrawBehind { drawPoints(pts, PointMode.Points, color, strokeWidth = w, cap = StrokeCap.Round) }
}

/** Büyük, yaylı (spring) animasyonlu aç/kapa düğmesi: kapalıyken daire, açıkken dönen yuvarlak kare. */
@Composable
fun PowerButton(running: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bouncy = spring<Dp>(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)

    val buttonSize by animateDpAsState(if (running) 188.dp else 168.dp, bouncy, label = "size")
    val corner by animateIntAsState(
        if (running) 32 else 50, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "corner",
    )
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "scale",
    )
    val container by animateColorAsState(if (running) cs.primary else cs.surfaceContainerHighest, tween(450), label = "c")
    val content by animateColorAsState(if (running) cs.onPrimary else cs.onSurfaceVariant, tween(450), label = "fg")

    val inf = rememberInfiniteTransition(label = "spin")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rot")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)), label = "pulse")
    val rotation = if (running) rot else 0f

    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        if (running) {
            val ring = cs.primary
            Canvas(Modifier.size(300.dp)) {
                val baseR = 188.dp.toPx() / 2
                val maxR = size.minDimension / 2
                for (k in 0..1) {
                    val p = (pulse + k * 0.5f) % 1f
                    drawCircle(
                        color = ring.copy(alpha = (1f - p) * 0.35f),
                        radius = baseR + (maxR - baseR) * p,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(percent = corner),
            color = container,
            contentColor = content,
            interactionSource = interaction,
            shadowElevation = if (running) 10.dp else 2.dp,
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = stringResource(if (running) R.string.power_stop else R.string.power_start),
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer { rotationZ = -rotation },
                )
            }
        }
    }
}

/** Yanıp sönen kırmızı nokta (kayıt/yayın ışığı). */
@Composable
fun LiveDot(active: Boolean, size: Dp = 12.dp) {
    val inf = rememberInfiniteTransition(label = "dot")
    val a by inf.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    val color = if (active) com.akis.app.ui.theme.NothingRed.copy(alpha = a) else MaterialTheme.colorScheme.outline
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val upper = LocalAppTheme.current.uppercaseTitles
    Text(
        if (upper) text.uppercase() else text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (LocalAppTheme.current.cardBorder) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color, border = border) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color, border = border) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

/** Yuvarlatılmış kare içinde ikon — expressive "şekilli" ikon kabı. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer, bg: Color = MaterialTheme.colorScheme.primaryContainer, size: Dp = 48.dp) {
    Surface(shape = LocalAppTheme.current.badge, color = bg, contentColor = tint, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.5f)) }
    }
}

@Composable
fun StatPill(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Seçili dilde çoğul metin ("1 video" / "5 videos"). */
@Composable
fun plural(@PluralsRes id: Int, n: Int): String = LocalContext.current.resources.getQuantityString(id, n, n)

@Composable
fun RowScope.Gap(w: Dp = 12.dp) = Spacer(Modifier.width(w))

/** Noktalardan oluşan QR kod (Nothing havası, ama okunabilir). */
@Composable
fun DotQrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val n = matrix.width
        val cell = size.minDimension / n
        fun finder(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
        for (y in 0 until n) for (x in 0 until n) {
            if (!matrix.get(x, y)) continue
            if (finder(x, y)) {
                drawRoundRect(
                    color, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f),
                    cornerRadius = CornerRadius(cell * 0.15f),
                )
            } else {
                drawCircle(color, radius = cell * 0.48f, center = Offset(x * cell + cell / 2, y * cell + cell / 2))
            }
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.2f GB", b / 1073741824.0)
    b >= 1L shl 20 -> String.format("%.1f MB", b / 1048576.0)
    b >= 1L shl 10 -> String.format("%d KB", b / 1024)
    else -> "$b B"
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s / 60) % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s % 60) else String.format("%02d:%02d", m, s % 60)
}
