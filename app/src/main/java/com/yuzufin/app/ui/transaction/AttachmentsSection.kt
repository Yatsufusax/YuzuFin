package com.yuzufin.app.ui.transaction

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzufin.app.R
import com.yuzufin.app.data.AttachmentFileStore
import com.yuzufin.app.domain.MAX_ATTACHMENTS

/** Fotos del movimiento (tickets, facturas) y botón del comprobante PDF. */
@Composable
fun AttachmentsSection(
    state: TransactionFormState,
    files: AttachmentFileStore,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemove: (PhotoItem) -> Unit,
    onVoucher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoItem?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.photos_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.photos_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.photos, key = { it.fileName }) { item ->
                Box {
                    Thumbnail(files, item.fileName, Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).clickable { viewing = item })
                    IconButton(
                        onClick = { onRemove(item) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.photos_remove),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (!state.photoLimitReached) {
                item {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.photoBusy) { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.photoBusy) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Rounded.AddAPhoto,
                                contentDescription = stringResource(R.string.photos_add),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.photos_camera)) },
                                onClick = { menuOpen = false; onTakePhoto() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.photos_gallery)) },
                                onClick = { menuOpen = false; onPickPhoto() },
                            )
                        }
                    }
                }
            }
        }
        if (state.photoLimitReached) {
            Text(
                stringResource(R.string.photos_limit, MAX_ATTACHMENTS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.photoError) {
            Text(stringResource(R.string.photos_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (state.isEdit) {
            OutlinedButton(
                onClick = onVoucher,
                enabled = !state.photosDirty && !state.voucherBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.voucherBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    stringResource(if (state.voucherBusy) R.string.voucher_button_busy else R.string.voucher_button),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.photosDirty) {
                Text(
                    stringResource(R.string.voucher_save_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.voucherError) {
                Text(stringResource(R.string.voucher_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    viewing?.let { item ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewing = null }) {
                val bitmap by produceState<Bitmap?>(null, item.fileName) { value = files.decode(item.fileName, 1600) }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.photos_view),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                IconButton(onClick = { viewing = null }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.photos_close), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(files: AttachmentFileStore, name: String, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, name) { value = files.decode(name, 300) }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded.asImageBitmap(), contentDescription = stringResource(R.string.photos_view), contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
