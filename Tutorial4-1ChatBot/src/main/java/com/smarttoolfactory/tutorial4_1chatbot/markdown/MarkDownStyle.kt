package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.ListStyle
import com.halilibo.richtext.ui.RichTextStyle

object MarkDownStyle {

    val DefaultTextStyle = RichTextStyle.Companion.Default.copy(
        paragraphSpacing = 16.sp,
        listStyle = ListStyle(
            itemSpacing = 16.sp
        ),
        headingStyle = { index, textStyle ->
            when (index) {
                1 -> textStyle.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Companion.SemiBold,
                    lineHeight = 32.sp,
                    color = Color.Companion.Red
                )

                2 -> textStyle.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Companion.SemiBold,
                    lineHeight = 28.sp,
                    color = Color.Companion.Red
                )

                3 -> textStyle.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Companion.Medium,
                    lineHeight = 26.sp,
                    color = Color.Companion.Red
                )

                4 -> textStyle.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Companion.Medium,
                    lineHeight = 24.sp,
                    color = Color.Companion.Red
                )

                5 -> textStyle.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Companion.Medium,
                    lineHeight = 22.sp,
                    color = Color.Companion.Red
                )

                else -> textStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Companion.Medium,
                    lineHeight = 20.sp,
                    color = Color.Companion.Red
                )
            }
        }
    )
}