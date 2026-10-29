package com.notes.purenoteit.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.notes.purenoteit.R
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.showToastLong
import com.notes.purenoteit.util.showToastShort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSettingScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val hasPassword = remember { SecurityUtil.isPasswordSet(context) }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }

    var clickCount by remember { mutableIntStateOf(0) }

    fun handleEasterEgg() {
        clickCount++
        when (clickCount) {
            10 -> context.showToastLong("Neden Tıkladın")
            20 -> context.showToastLong("Verdiğiniz Desteklerden Dolayı Hamur Size Teşekkür Eder\n Fatih Yavuzyiğitoğlu <3")
        }
    }

    fun savePassword() {
        val curr = currentPassword.trim()
        val newP = newPassword.trim()
        val repP = repeatPassword.trim()

        if (newP.isBlank() || repP.isBlank() || (hasPassword && curr.isBlank())) {
            context.showToastShort("Lütfen Boş Alanları Doldurun")
            return
        }

        if (newP.length != 6 || !newP.all { it.isDigit() }) {
            context.showToastShort("Şifre 6 Haneli Rakamlardan Oluşmalıdır")
            return
        }

        if (hasPassword) {
            if (!SecurityUtil.verifyPassword(context, curr)) {
                context.showToastShort(context.getString(R.string.you_entered_the_current_password_incorrectly))
                return
            }
            if (curr == newP) {
                context.showToastShort("Yeni Şifre Mevcut Şifreyle Aynı Olamaz")
                return
            }
        }

        if (newP != repP) {
            context.showToastShort("Yeni Şifreler Eşleşmiyor")
            return
        }

        SecurityUtil.savePassword(context, newP)
        context.showToastShort("Şifre Başarıyla Değiştirildi")
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = if (hasPassword) "Güvenlik Şifresi Değiştir" else "Güvenlik Şifresi Belirle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Animated illustration with Easter Egg click listener
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(SoftOrangeLight.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        handleEasterEgg()
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = R.drawable.rolling_pin_unscreen,
                    contentDescription = "Güvenlik İkonu",
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Bitwarden Düzeyinde Kasa Güvenliği",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Kilitli notlarınız ve kategorileriniz AES-256-GCM ile şifrelenir. Uygulamadan çıktığınızda kasanız otomatik olarak kilitlenir.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Inputs card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f)),
                shadowElevation = 0.5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (hasPassword) {
                        Column {
                            Text(
                                text = "Mevcut PIN Kodu",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) currentPassword = it
                                },
                                placeholder = { Text("••••••", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VibrantOrange,
                                    unfocusedBorderColor = BorderLight
                                )
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (hasPassword) "Yeni PIN Kodu (6 Haneli)" else "Yeni PIN Kodu (6 Haneli)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) newPassword = it
                            },
                            placeholder = { Text("6 haneli PIN", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VibrantOrange,
                                unfocusedBorderColor = BorderLight
                            )
                        )
                    }

                    Column {
                        Text(
                            text = "Yeni PIN Kodunu Doğrulayın",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = repeatPassword,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) repeatPassword = it
                            },
                            placeholder = { Text("6 haneli PIN tekrar", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VibrantOrange,
                                unfocusedBorderColor = BorderLight
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save PIN Button
            Button(
                onClick = { savePassword() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibrantOrange,
                    contentColor = PureWhite
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.lock),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasPassword) "Şifreyi Güncelle" else "Şifreyi Kaydet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Security note info box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SoftOrangeLight.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.fingerprint),
                        contentDescription = null,
                        tint = VibrantOrange,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "İpucu: Şifrenizi kaydettikten sonra kilitli notlarınıza ana ekrandan parmak izi veya biyometrik kimlik doğrulama ile de kolayca erişebilirsiniz.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
