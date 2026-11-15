package com.yuguang.lumo.ui.translate

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yuguang.lumo.R
import com.yuguang.lumo.ui.components.ErrorCard
import com.yuguang.lumo.ui.components.GlassCard
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LocalLumoPalette

/**
 * 图片文字翻译：选图、发起、逐行对照。
 *
 * 这里刻意不做相机实时取景（需要 CameraX 新依赖），只复用系统照片选择器，
 * 因此不需要任何存储或相机权限。
 */
@Composable
internal fun PhotoStage(
    state: TranslateUiState,
    message: String?,
    onPick: () -> Unit,
    onTranslate: () -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoPickerCard(state.photoUri, onPick = onPick)
        // 提示与错误都收进卡片，不再以裸字铺在画心上
        if (message != null) {
            ErrorCard(message = message, modifier = Modifier.fillMaxWidth())
        }
        Button(
            onClick = onTranslate,
            enabled = state.photoUri != null && !state.photoRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.photoRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                stringResource(
                    if (state.photoRunning) R.string.photo_running else R.string.photo_run,
                ),
            )
        }
        // 模型名 / 回退提示合成一条玻璃小条：labelSmall 直接压背景在浅色下不达标
        if (state.photoModel.isNotBlank() || state.photoFallback) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(shape = RoundedCornerShape(14.dp), level = 0.9f)
                    .border(1.dp, LocalLumoPalette.current.glassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.photoModel.isNotBlank()) {
                    Text(
                        stringResource(R.string.photo_model, state.photoModel, state.photoElapsed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.photoFallback) {
                    Text(
                        stringResource(R.string.photo_fallback),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        when {
            state.photoLines.isEmpty() -> {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    level = 0.9f,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        stringResource(R.string.photo_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.photo_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.photoLines) { line ->
                        PhotoLineCard(
                            source = line.source,
                            target = line.target,
                            onCopy = onCopy,
                            onSpeak = onSpeak,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPickerCard(uri: Uri?, onPick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .glass(shape = shape, level = 0.9f)
            .border(1.dp, LocalLumoPalette.current.glassBorder, shape)
            .clip(shape)
            .clickable(onClick = onPick),
    ) {
        if (uri == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    LumoIcons.Picture,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.photo_pick), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Box {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    contentScale = ContentScale.Fit,
                )
                OutlinedButton(
                    onClick = onPick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Text(stringResource(R.string.photo_retake))
                }
            }
        }
    }
}

@Composable
private fun PhotoLineCard(
    source: String,
    target: String,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = shape, level = 0.9f)
            .border(1.dp, LocalLumoPalette.current.glassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (source.isNotBlank()) {
            Text(
                source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            target,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (target.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { onCopy(target) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        LumoIcons.Copy,
                        contentDescription = stringResource(R.string.photo_copy),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onSpeak(target) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        LumoIcons.Speaker,
                        contentDescription = stringResource(R.string.photo_read),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
