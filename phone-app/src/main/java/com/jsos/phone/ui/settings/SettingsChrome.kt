package com.jsos.phone.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jsos.phone.ui.theme.JsosPalette

internal fun jsosPanelBorder(alpha: Float = 0.34f): BorderStroke =
    BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = alpha))

@Composable
internal fun jsosTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = JsosPalette.Text,
    unfocusedTextColor = JsosPalette.Text,
    disabledTextColor = JsosPalette.Disabled,
    focusedContainerColor = JsosPalette.CardDark.copy(alpha = 0.58f),
    unfocusedContainerColor = JsosPalette.CardDark.copy(alpha = 0.58f),
    disabledContainerColor = JsosPalette.CardDark.copy(alpha = 0.38f),
    cursorColor = JsosPalette.Cyan,
    focusedBorderColor = JsosPalette.Cyan,
    unfocusedBorderColor = JsosPalette.Cyan.copy(alpha = 0.48f),
    disabledBorderColor = JsosPalette.BorderSubtle,
    focusedLabelColor = JsosPalette.Cyan,
    unfocusedLabelColor = JsosPalette.Muted,
    disabledLabelColor = JsosPalette.Disabled,
    focusedPlaceholderColor = JsosPalette.Muted,
    unfocusedPlaceholderColor = JsosPalette.Muted,
    disabledPlaceholderColor = JsosPalette.Disabled,
)

@Composable
internal fun jsosSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.Black,
    checkedTrackColor = JsosPalette.Green,
    checkedBorderColor = JsosPalette.Green,
    uncheckedThumbColor = JsosPalette.Cyan,
    uncheckedTrackColor = JsosPalette.CardDark,
    uncheckedBorderColor = JsosPalette.Cyan.copy(alpha = 0.55f),
    disabledCheckedThumbColor = JsosPalette.Disabled,
    disabledCheckedTrackColor = JsosPalette.Green.copy(alpha = 0.24f),
    disabledCheckedBorderColor = JsosPalette.Green.copy(alpha = 0.22f),
    disabledUncheckedThumbColor = JsosPalette.Disabled,
    disabledUncheckedTrackColor = JsosPalette.CardDark.copy(alpha = 0.5f),
    disabledUncheckedBorderColor = JsosPalette.BorderSubtle,
)

@Composable
internal fun jsosPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = JsosPalette.Green,
    contentColor = Color.Black,
    disabledContainerColor = JsosPalette.CardDark,
    disabledContentColor = JsosPalette.Disabled,
)

@Composable
internal fun jsosOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = JsosPalette.CardDark.copy(alpha = 0.38f),
    contentColor = JsosPalette.Cyan,
    disabledContainerColor = JsosPalette.CardDark.copy(alpha = 0.24f),
    disabledContentColor = JsosPalette.Disabled,
)

@Composable
internal fun jsosTextButtonColors(contentColor: Color = JsosPalette.Cyan) =
    ButtonDefaults.textButtonColors(
        contentColor = contentColor,
        disabledContentColor = JsosPalette.Disabled,
    )
