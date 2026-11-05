package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange

@Composable
fun PageRelationsHub(
    subPageBlocks: List<NoteBlock>,
    linkedNotes: List<String>,
    backlinks: List<NoteData> = emptyList(),
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenSubPage: (NoteBlock) -> Unit,
    onOpenLinkedNote: (String) -> Unit,
    onOpenBacklink: ((NoteData) -> Unit)? = null,
    onAddSubPage: () -> Unit,
    onAddLink: () -> Unit,
    onRequestRename: ((NoteBlock) -> Unit)? = null,
    onDeleteSubPage: ((NoteBlock) -> Unit)? = null,
    onRemoveLinkedNote: ((String) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onDeleteHub: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "hub_chevron")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and badges (clickable to expand/collapse)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SoftOrangeLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_hub_index),
                            contentDescription = null,
                            tint = VibrantOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = stringResource(R.string.relations_hub_title),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Badges for counts
                    if (subPageBlocks.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(0.5.dp, BorderLight.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_subpage_doc),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${subPageBlocks.size}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (linkedNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(0.5.dp, BorderLight.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_link_hub),
                                    contentDescription = null,
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${linkedNotes.size}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VibrantOrange
                                )
                            }
                        }
                    }

                    if (backlinks.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(0.5.dp, BorderLight.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_link_hub),
                                    contentDescription = null,
                                    tint = Color(0xFF1976D2),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${backlinks.size}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1976D2)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Dedicated Accordion Chevron Icon (Toggles expand/collapse)
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_down),
                            contentDescription = if (isExpanded) "Katla" else "Genişlet",
                            tint = VibrantOrange,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(rotationState)
                        )
                    }
                }

                // Block-Level Controls: Move Up (↑), Move Down (↓), Close/Remove (✕)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onMoveUp != null) {
                        IconButton(
                            onClick = onMoveUp,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_move_up),
                                contentDescription = "Yukarı Taşı",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (onMoveDown != null) {
                        IconButton(
                            onClick = onMoveDown,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_move_down),
                                contentDescription = "Aşağı Taşı",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (onDeleteHub != null) {
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = onDeleteHub,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.cancel),
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            // Quick action buttons row
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAddSubPage() },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.relations_add_subpage),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAddLink() },
                    shape = RoundedCornerShape(8.dp),
                    color = SoftOrangeLight.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.relations_add_link),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange
                        )
                    }
                }
            }

            // Expandable List Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Subpages Section
                    if (subPageBlocks.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.relations_subpages_title) + " (${subPageBlocks.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            subPageBlocks.forEach { block ->
                                val titleText = block.content.ifBlank { stringResource(R.string.new_subpage_default) }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_subpage_doc),
                                            contentDescription = null,
                                            tint = VibrantOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Static readable Title Text
                                        Text(
                                            text = titleText,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onOpenSubPage(block) }
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // "✏️ Düzenle" (Rename) Button
                                        if (onRequestRename != null) {
                                            IconButton(
                                                onClick = { onRequestRename(block) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_pencil_edit),
                                                    contentDescription = stringResource(R.string.rename_subpage_title),
                                                    tint = VibrantOrange,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(2.dp))
                                        }

                                        // "Aç ↗" Navigation Pill
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onOpenSubPage(block) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = SoftOrangeLight.copy(alpha = 0.7f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.block_subpage_open),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = VibrantOrange
                                                )
                                            }
                                        }

                                        if (onDeleteSubPage != null) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            IconButton(
                                                onClick = { onDeleteSubPage(block) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.cancel),
                                                    contentDescription = stringResource(R.string.delete),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Divider between sections if both exist
                    if (subPageBlocks.isNotEmpty() && linkedNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Linked Notes Section
                    if (linkedNotes.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.relations_links_title) + " (${linkedNotes.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            linkedNotes.forEach { targetTitle ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp)),
                                    shape = RoundedCornerShape(10.dp),
                                    color = SoftOrangeLight.copy(alpha = 0.7f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_link_hub),
                                            contentDescription = null,
                                            tint = VibrantOrange,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = targetTitle,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VibrantOrange,
                                            maxLines = 1,
                                            modifier = Modifier.clickable { onOpenLinkedNote(targetTitle) }
                                        )
                                        if (onRemoveLinkedNote != null) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            IconButton(
                                                onClick = { onRemoveLinkedNote(targetTitle) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.cancel),
                                                    contentDescription = "Bağlantıyı Kaldır",
                                                    tint = VibrantOrange.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Divider between sections if subpages or outgoing links exist before backlinks
                    if ((subPageBlocks.isNotEmpty() || linkedNotes.isNotEmpty()) && backlinks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Backlinks Section (Bu Nota Bağlantılar / Üst Not)
                    if (backlinks.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.relations_backlinks_title) + " (${backlinks.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            backlinks.forEach { bNote ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onOpenBacklink?.invoke(bNote) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ModernNoteIconBadge(
                                            iconKey = bNote.icon,
                                            isProtected = bNote.isPassword == true,
                                            size = 20.dp,
                                            iconSize = 11.dp,
                                            shape = RoundedCornerShape(5.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = bNote.title?.ifBlank { null } ?: stringResource(R.string.untitled_note),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_link_hub),
                                            contentDescription = null,
                                            tint = VibrantOrange,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Empty state if user manually opened hub and there are neither subpages nor links nor backlinks
                    if (subPageBlocks.isEmpty() && linkedNotes.isEmpty() && backlinks.isEmpty()) {
                        Text(
                            text = stringResource(R.string.relations_empty_subpages),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
