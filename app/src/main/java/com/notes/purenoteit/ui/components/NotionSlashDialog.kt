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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.VibrantOrange

data class SlashItem(
    val type: BlockType?,
    val title: String,
    val subtitle: String,
    val iconRes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotionSlashDialog(
    onDismiss: () -> Unit,
    onSelectBlock: (BlockType) -> Unit,
    onOpenTemplateHub: (() -> Unit)? = null
) {
    val items = buildList {
        add(SlashItem(BlockType.PARAGRAPH, stringResource(R.string.slash_text_title), stringResource(R.string.slash_text_desc), R.drawable.file_edit))
        if (onOpenTemplateHub != null) {
            add(SlashItem(null, stringResource(R.string.slash_template_title), stringResource(R.string.slash_template_desc), R.drawable.calendar))
        }
        add(SlashItem(BlockType.HEADING_1, stringResource(R.string.slash_h1_title), stringResource(R.string.slash_h1_desc), R.drawable.ic_format_h1))
        add(SlashItem(BlockType.HEADING_2, stringResource(R.string.slash_h2_title), stringResource(R.string.slash_h2_desc), R.drawable.ic_format_h2))
        add(SlashItem(BlockType.TODO, stringResource(R.string.slash_todo_title), stringResource(R.string.slash_todo_desc), R.drawable.ic_checklist))
        add(SlashItem(BlockType.BULLET_LIST, stringResource(R.string.slash_bullet_title), stringResource(R.string.slash_bullet_desc), R.drawable.ic_format_bullet))
        add(SlashItem(BlockType.CALLOUT, stringResource(R.string.slash_callout_title), stringResource(R.string.slash_callout_desc), R.drawable.ic_format_quote))
        add(SlashItem(BlockType.TOGGLE, stringResource(R.string.slash_toggle_title), stringResource(R.string.slash_toggle_desc), R.drawable.arrow_right))
        add(SlashItem(BlockType.TABLE, stringResource(R.string.slash_table_title), stringResource(R.string.slash_table_desc), R.drawable.ic_table))
        add(SlashItem(BlockType.WEB_BOOKMARK, stringResource(R.string.slash_bookmark_title), stringResource(R.string.slash_bookmark_desc), R.drawable.category))
        add(SlashItem(BlockType.SUB_PAGE, stringResource(R.string.slash_subpage_title), stringResource(R.string.slash_subpage_desc), R.drawable.ic_subpage))
        add(SlashItem(BlockType.DRAWING, stringResource(R.string.slash_drawing_title), stringResource(R.string.slash_drawing_desc), R.drawable.ic_brush))
        add(SlashItem(BlockType.DIVIDER, stringResource(R.string.slash_divider_title), stringResource(R.string.slash_divider_desc), R.drawable.cancel))
    }

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
                text = stringResource(R.string.slash_title),
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
                                onDismiss()
                                if (item.type != null) {
                                    onSelectBlock(item.type)
                                } else {
                                    onOpenTemplateHub?.invoke()
                                }
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
