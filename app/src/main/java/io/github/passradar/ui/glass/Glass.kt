package io.github.passradar.ui.glass

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import io.github.passradar.ui.theme.LocalDarkTheme
import io.github.passradar.ui.SearchKeyboardOptions
import io.github.passradar.ui.searchKeyboardActions

/** Blur needs Android 12; the refraction at the edges needs Android 13. Older versions get frosted panels. */
val SupportsBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val PillShape = RoundedCornerShape(percent = 50)

/** Glass bar along the top edge, behind the status bar. */
val TopBarShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

/** The see-through colour laid over the blurred background, strong enough to read text on. */
@Composable
fun glassTint(): Color {
    val dark = LocalDarkTheme.current
    val alpha = when {
        // Without blur, sharp text behind would show through; a solid panel reads better.
        !SupportsBlur -> 1f
        dark -> 0.55f
        else -> 0.62f
    }
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

/**
 * Liquid glass: blurs and refracts whatever [backdrop] holds behind this element,
 * with a light rim and a soft shadow.
 */
@Composable
fun Modifier.glass(
    backdrop: Backdrop,
    shape: RoundedCornerShape,
    tint: Color = glassTint(),
    blurRadius: Dp = 16.dp,
    lensHeight: Dp = 12.dp,
): Modifier {
    val dark = LocalDarkTheme.current
    val rim = if (dark) 0.35f else 0.85f
    val shadow = Color.Black.copy(alpha = if (dark) 0.4f else 0.12f)
    // At the screen edge the blur has nothing beyond to sample and turns see-through,
    // which would let the sharp content underneath show. A solid base hides it.
    val base = MaterialTheme.colorScheme.background
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (lensHeight > 0.dp) lens(refractionHeight = lensHeight.toPx(), refractionAmount = (lensHeight * 2).toPx(), depthEffect = true)
        },
        highlight = { Highlight(width = 1.dp, alpha = rim) },
        shadow = { Shadow(radius = 18.dp, color = shadow) },
        onDrawBehind = { drawRect(base) },
        onDrawSurface = { drawRect(tint) },
    )
}

/** A rounded glass card for floating content. */
@Composable
fun GlassPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.glass(backdrop, shape, lensHeight = 16.dp).padding(16.dp), content = content)
}

/** Faint fill that sets controls apart on a glass panel. */
@Composable
private fun inGlassFill(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalDarkTheme.current) 0.10f else 0.06f)

/** A glass panel along the top of the screen; content scrolls underneath it. */
@Composable
fun GlassTopPanel(backdrop: Backdrop, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .glass(backdrop, TopBarShape, lensHeight = 16.dp)
            .statusBarsPadding(),
        content = content,
    )
}

/** Glass title bar that fades in once the screen's own big title has scrolled away. */
@Composable
fun GlassTitleBar(backdrop: Backdrop, title: String, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        GlassTopPanel(backdrop) {
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A pill-shaped search field for use on a glass panel. */
@Composable
fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.height(52.dp).clip(PillShape).background(inGlassFill()).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = SearchKeyboardOptions,
            keyboardActions = searchKeyboardActions(),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Filled.Clear, contentDescription = clearDescription, tint = colors.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** A filter chip for use on a glass panel: faint when off, filled with the primary colour when on. */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) colors.primary else inGlassFill(), label = "chip")
    val content = if (selected) colors.onPrimary else colors.onSurface
    Row(
        modifier
            .height(38.dp)
            .clip(PillShape)
            .background(fill)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

data class GlassNavItem(val label: String, val icon: ImageVector, val badge: String? = null)

/** Floating glass tab bar with a sliding selection pill. */
@Composable
fun GlassNavigationBar(
    backdrop: Backdrop,
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.height(68.dp).glass(backdrop, PillShape, lensHeight = 18.dp).padding(6.dp)) {
        val itemWidth = maxWidth / items.size
        val offset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "tab indicator",
        )
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(itemWidth)
                .fillMaxHeight()
                .clip(PillShape)
                .background(colors.primary.copy(alpha = if (LocalDarkTheme.current) 0.24f else 0.14f)),
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant, label = "tab color")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .semantics { this.selected = selected },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        if (item.badge != null) {
                            Text(
                                item.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-4).dp)
                                    .clip(PillShape)
                                    .background(colors.primary)
                                    .padding(horizontal = 5.dp),
                            )
                        }
                    }
                    Text(item.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
                }
            }
        }
    }
}
