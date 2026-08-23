package com.scrolla.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Cards 20, rows and inner blocks 14, chips and bars fully round.
 * Nothing else.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

val CardShape = RoundedCornerShape(20.dp)
val RowShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(percent = 50)
