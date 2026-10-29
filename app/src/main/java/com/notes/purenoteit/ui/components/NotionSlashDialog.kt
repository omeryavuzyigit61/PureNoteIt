package com.notes.purenoteit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.VibrantOrange

data class SlashItem(
    val type: BlockType,
    val title: String,
    val subtitle: String,
    val iconRes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotionSlashDialog(
    onDismiss: () -> Unit,
    onSelectBlock: (BlockType) -> Unit
) {
    val items = listOf(
        SlashItem(BlockType.PARAGRAPH, "Düz Metin", "Yazmaya başlamak için basit bir paragraf", R.drawable.file_edit),
        SlashItem(BlockType.HEADING_1, "Başlık 1 (H1)", "Büyük bölüm başlığı", R.drawable.ic_format_h1),
        SlashItem(BlockType.HEADING_2, "Başlık 2 (H2)", "Orta boy alt başlık", R.drawable.ic_format_h1),
        SlashItem(BlockType.TODO, "Yapılacaklar Listesi", "Tamamlanabilir görev kutucuğu", R.drawable.ic_checklist),
        SlashItem(BlockType.BULLET_LIST, "Madde İmleri", "Basit bir liste oluşturun", R.drawable.ic_format_bullet),
        SlashItem(BlockType.CALLOUT, "Callout (Vurgu Kutusu)", "Önemli fikir ve notları kutu içine alın", R.drawable.ic_format_quote),
        SlashItem(BlockType.DIVIDER, "Ayırıcı Çizgi", "Blokları birbirinden ayıran yatay çizgi", R.drawable.cancel)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Temel Bloklar (Notion Tarzı)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn {
                items(items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectBlock(item.type)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = null,
                            tint = VibrantOrange,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.subtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
