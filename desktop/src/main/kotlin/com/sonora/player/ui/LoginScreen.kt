package com.sonora.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sonora.player.AppState
import kotlin.math.roundToInt

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SonoraLogo(Modifier.size(88.dp))
    }
}

@Composable
fun LoginScreen(vm: AppState) {
    var password by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val accent = MaterialTheme.colorScheme.primary
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) { appeared = true }
    LaunchedEffect(vm.loginErrorTick) {
        if (vm.loginErrorTick > 0) {
            shake.snapTo(0f)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    14f at 60 using Motion.Standard
                    -12f at 140 using Motion.Standard
                    9f at 220 using Motion.Standard
                    -5f at 300 using Motion.Standard
                    2f at 370
                },
            )
        }
    }

    val canLogin = vm.loginServer.isNotBlank() && vm.loginAccount.isNotBlank() && password.isNotEmpty() && !vm.loginLoading
    val submit = {
        focus.clearFocus()
        if (canLogin) vm.login(password, otp)
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(listOf(accent, Violet, Teal), Modifier.fillMaxSize(), intensity = 0.45f)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            AnimatedVisibility(
                appeared,
                enter = fadeIn(tween(700, easing = Motion.Standard)) + scaleIn(tween(900, easing = Motion.Decelerate), initialScale = 0.6f),
            ) {
                SonoraLogo(Modifier.size(96.dp))
            }
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(appeared, enter = fadeIn(tween(700, 150, Motion.Standard)) + slideInVertically(tween(800, 150, Motion.Decelerate)) { it / 2 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sonora", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Ваша музыка с Synology NAS",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            AnimatedVisibility(appeared, enter = fadeIn(tween(700, 300, Motion.Standard)) + slideInVertically(tween(900, 300, Motion.Decelerate)) { it / 6 }) {
                GlassCard(
                    Modifier
                        .widthIn(max = 480.dp)
                        .offset { IntOffset(shake.value.roundToInt(), 0) },
                ) {
                    Field(
                        vm.loginServer, { vm.loginServer = it }, "Адрес NAS", Icons.Rounded.Dns,
                        placeholder = "192.168.1.10 или nas.example.com",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    SwitchRow("HTTPS", "Порт по умолчанию 5001 вместо 5000", vm.loginHttps) { vm.loginHttps = it }
                    AnimatedVisibility(vm.loginHttps, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        SwitchRow(
                            "Самоподписанный сертификат",
                            "Доверять сертификату NAS без проверки",
                            vm.loginTrustAll,
                        ) { vm.loginTrustAll = it }
                    }
                    Field(
                        vm.loginAccount, { vm.loginAccount = it }, "Пользователь", Icons.Rounded.Person,
                        keyboard = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    Field(
                        password, { password = it }, "Пароль", Icons.Rounded.Lock,
                        keyboard = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (vm.needOtp) ImeAction.Next else ImeAction.Done,
                        ),
                        actions = KeyboardActions(onDone = { submit() }),
                        visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                AnimatedContent(showPassword, label = "eye") { shown ->
                                    Icon(if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать пароль")
                                }
                            }
                        },
                    )
                    AnimatedVisibility(vm.needOtp, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Field(
                            otp, { otp = it.filter(Char::isDigit).take(8) }, "Код 2FA", Icons.Rounded.Key,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            actions = KeyboardActions(onDone = { submit() }),
                        )
                    }
                    AnimatedVisibility(
                        vm.loginError != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Text(vm.loginError.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(
                        onClick = submit,
                        enabled = canLogin,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    ) {
                        AnimatedContent(
                            vm.loginLoading,
                            transitionSpec = { (fadeIn() + scaleIn()) togetherWith fadeOut() },
                            label = "loginButton",
                        ) { loading ->
                            if (loading) {
                                CircularProgressIndicator(
                                    Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp,
                                )
                            } else {
                                Text("Войти", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            AnimatedVisibility(appeared, enter = fadeIn(tween(700, 600))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = vm::startDemo) {
                        Icon(Icons.Rounded.PlayCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Попробовать демо-режим")
                    }
                    Text(
                        "Без NAS — несколько свободных треков из интернета",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (IsDark) Glass.copy(alpha = 0.06f) else Surface1.copy(alpha = 0.75f))
            .border(1.dp, Glass.copy(alpha = 0.1f), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    placeholder: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else null,
        leadingIcon = { Icon(icon, null) },
        trailingIcon = trailing,
        singleLine = true,
        keyboardOptions = keyboard,
        keyboardActions = actions,
        visualTransformation = visual,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Glass.copy(alpha = 0.16f),
            unfocusedContainerColor = Glass.copy(alpha = 0.04f),
            focusedContainerColor = Glass.copy(alpha = 0.06f),
        ),
        modifier = Modifier.fillMaxWidth().trackTextFocus(),
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
