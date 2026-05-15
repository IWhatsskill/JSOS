package com.jsos.phone.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsos.phone.ui.theme.JsosPalette

@Composable
fun SoftwareUpdateSection(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            "HUD Installation",
            color = JsosPalette.Cyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            "Install the glasses APK through Hi Rokid / APK Manager.",
            color = JsosPalette.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = JsosPalette.CardDark.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.34f)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.InstallMobile,
                    contentDescription = null,
                    tint = JsosPalette.Cyan,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Manual install",
                    color = JsosPalette.Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Text(
                    "Build JSOS HUD as a separate APK, then install it on the glasses through Hi Rokid / CXR-L APK Manager.",
                    color = JsosPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
