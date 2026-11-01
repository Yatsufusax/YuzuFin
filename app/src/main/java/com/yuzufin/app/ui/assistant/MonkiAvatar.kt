package com.yuzufin.app.ui.assistant

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.assistant.MonkiRegion

/** Carita de Monki en un círculo: camiseta de Paraguay, de Argentina o neutral según la moneda. */
@Composable
fun MonkiAvatar(currencyCode: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val res = when (MonkiRegion.fromCurrency(currencyCode)) {
        MonkiRegion.PARAGUAY -> R.drawable.monki_avatar_pyg
        MonkiRegion.ARGENTINA -> R.drawable.monki_avatar_ars
        MonkiRegion.NEUTRAL -> R.drawable.monki_avatar_neutral
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size),
        )
    }
}
