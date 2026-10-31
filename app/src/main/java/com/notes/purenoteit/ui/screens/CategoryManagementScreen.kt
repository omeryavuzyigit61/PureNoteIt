package com.notes.purenoteit.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel

private val DEFAULT_CATEGORIES = setOf("Not", "Önemli", "Günlük")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    categoryViewModel: CategoryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val categoryList by categoryViewModel.categoryList.observeAsState(emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryData?>(null) }
    var categoryInputName by remember { mutableStateOf("") }

    var showPinDialog by remember { mutableStateOf(false) }
    var targetCategoryForPin by remember { mutableStateOf<CategoryData?>(null) }
    var enteredPin by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<CategoryData?>(null) }

    LaunchedEffect(Unit) {
        categoryViewModel.getAllCategories()
    }

    val filteredList = remember(categoryList, searchQuery) {
        if (searchQuery.isBlank()) {
            categoryList
        } else {
            categoryList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = stringResource(R.string.cat_mgmt_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editingCategory = null
                            categoryInputName = ""
                            showAddEditDialog = true
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.apps_add),
                            contentDescription = stringResource(R.string.cat_add_btn),
                            tint = VibrantOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCategory = null
                    categoryInputName = ""
                    showAddEditDialog = true
                },
                containerColor = VibrantOrange,
                contentColor = PureWhite,
                shape = CircleShape,
                modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp, end = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.apps_add),
                    contentDescription = stringResource(R.string.cat_add_btn),
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Soft Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.search),
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.cat_search_placeholder),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            }

            // Categories list
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) stringResource(R.string.cat_no_categories) else stringResource(R.string.cat_no_results),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { category ->
                        val isDefault = category.name in DEFAULT_CATEGORIES
                        val isLocked = category.isPassword == true

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f)),
                            shadowElevation = 0.5.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category Icon Badge
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(SoftOrangeLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.category),
                                        contentDescription = null,
                                        tint = VibrantOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Category Name & Default badge
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = category.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isDefault) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(BorderLight.copy(alpha = 0.4f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.cat_default_badge),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }

                                // Lock / Unlock Toggle
                                IconButton(
                                    onClick = {
                                        if (isLocked) {
                                            // Unlock category
                                            category.isPassword = false
                                            categoryViewModel.update(category)
                                            context.showToastShort(context.getString(R.string.cat_unlocked_toast))
                                        } else {
                                            // Prompt for PIN to lock
                                            targetCategoryForPin = category
                                            enteredPin = ""
                                            showPinDialog = true
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isLocked) R.drawable.lock else R.drawable.unlock
                                        ),
                                        contentDescription = null,
                                        tint = if (isLocked) VibrantOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Edit Category (Non-default only)
                                if (!isDefault) {
                                    IconButton(
                                        onClick = {
                                            editingCategory = category
                                            categoryInputName = category.name
                                            showAddEditDialog = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.file_edit),
                                            contentDescription = stringResource(R.string.edit),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Delete Category
                                    IconButton(
                                        onClick = {
                                            deletingCategory = category
                                            showDeleteConfirmDialog = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.delete),
                                            contentDescription = stringResource(R.string.delete),
                                            tint = Color(0xFFE53935).copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Add / Edit Category
    if (showAddEditDialog) {
        val isEditing = editingCategory != null
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = {
                Text(
                    text = if (isEditing) stringResource(R.string.cat_dialog_edit_title) else stringResource(R.string.cat_dialog_add_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.cat_dialog_name_prompt),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = categoryInputName,
                        onValueChange = { categoryInputName = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.cat_dialog_name_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = categoryInputName.trim()
                        if (trimmed.isBlank()) {
                            context.showToastShort(context.getString(R.string.please_enter_category))
                            return@TextButton
                        }
                        if (isEditing) {
                            editingCategory?.let { cat ->
                                cat.name = trimmed
                                categoryViewModel.update(cat)
                                context.showToastShort(context.getString(R.string.cat_updated_toast))
                            }
                        } else {
                            categoryViewModel.upsert(CategoryData(name = trimmed, isPassword = false))
                            context.showToastShort(context.getString(R.string.cat_added_toast))
                        }
                        showAddEditDialog = false
                    }
                ) {
                    Text(
                        text = if (isEditing) stringResource(R.string.cat_update_btn) else stringResource(R.string.cat_add_btn_action),
                        color = VibrantOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Dialog: Lock Category (PIN Input)
    if (showPinDialog) {
        val isPasswordSet = SecurityUtil.isPasswordSet(context)
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Text(
                    text = if (isPasswordSet) stringResource(R.string.cat_lock_title) else stringResource(R.string.cat_set_lock_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isPasswordSet) {
                            stringResource(R.string.cat_lock_prompt_with_pwd)
                        } else {
                            stringResource(R.string.cat_lock_prompt_no_pwd)
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) enteredPin = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        placeholder = { Text("••••••") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pin = enteredPin.trim()
                        if (pin.length != 6) {
                            context.showToastShort(context.getString(R.string.pin_must_be_6_digits))
                            return@TextButton
                        }

                        targetCategoryForPin?.let { cat ->
                            if (!isPasswordSet) {
                                SecurityUtil.savePassword(context, pin)
                                cat.isPassword = true
                                categoryViewModel.update(cat)
                                context.showToastShort(context.getString(R.string.cat_pwd_saved_and_locked_toast))
                            } else if (SecurityUtil.verifyPassword(context, pin)) {
                                cat.isPassword = true
                                categoryViewModel.update(cat)
                                context.showToastShort(context.getString(R.string.cat_locked_toast))
                            } else {
                                context.showToastShort(context.getString(R.string.wrong_password))
                                return@TextButton
                            }
                        }
                        showPinDialog = false
                    }
                ) {
                    Text(stringResource(R.string.cat_lock_btn), color = VibrantOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Dialog: Delete Confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(stringResource(R.string.cat_delete_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = stringResource(R.string.cat_delete_confirm_msg, deletingCategory?.name ?: ""),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingCategory?.let { cat ->
                            categoryViewModel.deleteCategoryById(cat.id)
                            context.showToastShort(context.getString(R.string.cat_deleted_toast))
                        }
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
