package com.jsos.phone.ui.components.conversation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsos.phone.ui.theme.JsosConversationTokens
import com.jsos.phone.ui.theme.JsosPalette
import java.util.Locale

/** Visual alignment only. It does not represent a transport or protocol role. */
enum class JsosConversationSide {
    USER,
    PEER,
    SYSTEM,
}

/** Selects body typography only; parsing and formatting remain screen-owned. */
enum class JsosConversationBodyStyle {
    STANDARD,
    MONOSPACE,
}

private val PublicPeerAccents = listOf(
    Color(0xFF6F5ACD),
    Color(0xFF00695C),
    Color(0xFF1B3A8A),
    Color(0xFF795548),
    Color(0xFF8E3A8C),
    Color(0xFF607D8B),
)

/** Stable, name-agnostic accent selection for public chat identities. */
fun jsosConversationPeerAccent(identity: String): Color {
    val hash = identity
        .trim()
        .lowercase(Locale.ROOT)
        .fold(0) { current, character -> (current * 31) + character.code }
    return PublicPeerAccents[(hash and Int.MAX_VALUE) % PublicPeerAccents.size]
}

@Composable
fun JsosConversationScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(JsosConversationTokens.SectionSpacing),
        content = content,
    )
}

@Composable
fun JsosConversationHeader(
    title: String,
    statusLabel: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = JsosPalette.Card,
        border = BorderStroke(1.dp, JsosPalette.BorderSubtle),
        shape = RoundedCornerShape(JsosConversationTokens.PanelRadius),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = JsosPalette.Text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = JsosPalette.Muted,
                            fontSize = 11.sp,
                        )
                    }
                }
                JsosConversationStatusPill(statusLabel, statusColor)
                action()
            }
            content()
        }
    }
}

@Composable
fun JsosConversationStatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = JsosConversationTokens.StatusFontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun JsosConversationPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = JsosPalette.CardAlt,
    borderColor: Color = JsosPalette.BorderSubtle,
    contentPadding: PaddingValues = PaddingValues(JsosConversationTokens.PanelPadding),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(JsosConversationTokens.PanelRadius),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun JsosConversationBubble(
    label: String,
    text: String,
    side: JsosConversationSide,
    accentColor: Color,
    modifier: Modifier = Modifier,
    bodyStyle: JsosConversationBodyStyle = JsosConversationBodyStyle.STANDARD,
    bodyColor: Color = JsosPalette.Text,
    bodyFontWeight: FontWeight = FontWeight.Medium,
    isError: Boolean = false,
    bodyContent: (@Composable () -> Unit)? = null,
) {
    val containerColor = when {
        isError -> JsosPalette.Red.copy(alpha = 0.09f)
        side == JsosConversationSide.USER -> JsosPalette.Cyan.copy(alpha = 0.11f)
        side == JsosConversationSide.SYSTEM -> JsosPalette.CardDark
        else -> accentColor.copy(alpha = 0.10f)
    }
    val borderColor = when {
        isError -> JsosPalette.Red.copy(alpha = 0.48f)
        side == JsosConversationSide.USER -> JsosPalette.Cyan.copy(alpha = 0.40f)
        side == JsosConversationSide.SYSTEM -> JsosPalette.BorderSubtle
        else -> accentColor.copy(alpha = 0.52f)
    }
    val bodyFontFamily = when (bodyStyle) {
        JsosConversationBodyStyle.STANDARD -> FontFamily.Default
        JsosConversationBodyStyle.MONOSPACE -> FontFamily.Monospace
    }
    val bubbleModifier = if (side == JsosConversationSide.SYSTEM) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.widthIn(max = JsosConversationTokens.BubbleMaxWidth)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (side == JsosConversationSide.USER) {
                    JsosConversationTokens.BubbleSideInset
                } else {
                    0.dp
                },
                end = if (side == JsosConversationSide.PEER) {
                    JsosConversationTokens.BubbleSideInset
                } else {
                    0.dp
                },
            ),
        horizontalArrangement = if (side == JsosConversationSide.USER) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        Surface(
            modifier = bubbleModifier,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(JsosConversationTokens.BubbleRadius),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = JsosConversationTokens.BubbleHorizontalPadding,
                    vertical = JsosConversationTokens.BubbleVerticalPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        color = accentColor,
                        fontSize = JsosConversationTokens.SenderFontSize,
                        fontWeight = FontWeight.Bold,
                    )
                }
                SelectionContainer {
                    if (bodyContent != null) {
                        bodyContent()
                    } else {
                        Text(
                            text = text,
                            color = bodyColor,
                            fontFamily = bodyFontFamily,
                            fontSize = JsosConversationTokens.BodyFontSize,
                            lineHeight = JsosConversationTokens.BodyLineHeight,
                            fontWeight = bodyFontWeight,
                        )
                    }
                }
            }
        }
    }
}
