package com.hextapp.android.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import com.hextapp.android.BuildConfig
import com.hextapp.android.data.local.UpdatePreferences
import com.hextapp.android.data.update.AppUpdateManager
import com.hextapp.android.data.update.UpdateUiState
import com.hextapp.android.ui.components.HextappTopBar
import com.hextapp.android.ui.theme.HextPrimary
import com.hextapp.android.ui.theme.HextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    appUpdateManager: AppUpdateManager,
    updatePreferences: UpdatePreferences
) {
    val state by viewModel.uiState.collectAsState()
    val updateSuccess by viewModel.updateSuccessState.collectAsState()
    val session = state.session
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoUpdateEnabled by updatePreferences.autoUpdateEnabled.collectAsState(initial = false)
    val updateState by appUpdateManager.uiState.collectAsState()
    var cacheCleared by remember { mutableStateOf(false) }

    // Edit profile form state
    var editName by remember(session?.name) { mutableStateOf(session?.name ?: "") }
    var editBio by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }

    // Change password form state
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var showCurrentPw by remember { mutableStateOf(false) }
    var showNewPw by remember { mutableStateOf(false) }

    // Auto-dismiss success banner after 2.5 s
    LaunchedEffect(updateSuccess) {
        if (updateSuccess != null) {
            delay(2500)
            viewModel.clearSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HextappTopBar(
            title = "Perfil y Ajustes",
            subtitle = "Cuenta Hextapp & Apariencia",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme
        )

        // Success banner
        AnimatedVisibility(
            visible = updateSuccess != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(color = HextSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        updateSuccess ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (session != null) {
                // ── Profile card ──────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar: initial letter with gradient background
                        val initial = (session.name.firstOrNull() ?: 'H').uppercaseChar().toString()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(listOf(HextPrimary, Color(0xFF3D1FA3)))
                                )
                        ) {
                            Text(
                                text = initial,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            session.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!session.username.isNullOrBlank()) {
                            Text(
                                "@${session.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HextPrimary
                            )
                        }
                        Text(
                            session.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(16.dp))

                        // Stats row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatBadge(
                                icon = Icons.Outlined.Favorite,
                                label = "Favoritos",
                                value = session.favorites.size.toString(),
                                tint = HextPrimary
                            )
                            StatBadge(
                                icon = Icons.Outlined.Bookmark,
                                label = "Guardados",
                                value = session.bookmarks.size.toString(),
                                tint = HextSecondary
                            )
                            StatBadge(
                                icon = Icons.Outlined.Download,
                                label = "Descargas",
                                value = session.downloadsCount.toString(),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Edit profile toggle button
                        OutlinedButton(
                            onClick = { viewModel.toggleEditing() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isEditing) Icons.Outlined.Close else Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isEditing) "Cancelar edición" else "Editar perfil")
                        }

                        // Edit profile form
                        AnimatedVisibility(
                            visible = state.isEditing,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Nombre") },
                                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editBio,
                                    onValueChange = { editBio = it },
                                    label = { Text("Bio") },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editLocation,
                                    onValueChange = { editLocation = it },
                                    label = { Text("Ubicación") },
                                    leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (state.error != null && state.isEditing) {
                                    Text(
                                        state.error!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = { viewModel.updateProfile(editName, editBio, editLocation) },
                                    enabled = !state.isSubmitting,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (state.isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(Icons.Outlined.Save, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Guardar cambios")
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Change password card ──────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = HextPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Seguridad",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = { viewModel.toggleChangePassword() }) {
                                Text(if (state.showChangePassword) "Cancelar" else "Cambiar contraseña")
                            }
                        }

                        AnimatedVisibility(
                            visible = state.showChangePassword,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = currentPw,
                                    onValueChange = { currentPw = it },
                                    label = { Text("Contraseña actual") },
                                    singleLine = true,
                                    visualTransformation = if (showCurrentPw) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { showCurrentPw = !showCurrentPw }) {
                                            Icon(
                                                if (showCurrentPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newPw,
                                    onValueChange = { newPw = it },
                                    label = { Text("Nueva contraseña") },
                                    singleLine = true,
                                    visualTransformation = if (showNewPw) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { showNewPw = !showNewPw }) {
                                            Icon(
                                                if (showNewPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = confirmPw,
                                    onValueChange = { confirmPw = it },
                                    label = { Text("Confirmar nueva contraseña") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    isError = confirmPw.isNotEmpty() && newPw != confirmPw,
                                    supportingText = if (confirmPw.isNotEmpty() && newPw != confirmPw) {
                                        { Text("Las contraseñas no coinciden") }
                                    } else null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (state.error != null && state.showChangePassword) {
                                    Text(
                                        state.error!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = { viewModel.changePassword(currentPw, newPw, confirmPw) },
                                    enabled = !state.isSubmitting && currentPw.isNotBlank() && newPw.length >= 6,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (state.isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(Icons.Outlined.Lock, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Actualizar contraseña")
                                    }
                                }
                            }
                        }
                    }
                }

            } else {
                // ── Login / Register form ─────────────────────────────────
                var isRegister by remember { mutableStateOf(false) }
                var name by remember { mutableStateOf("") }
                var username by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (isRegister) "Crear cuenta en Hextapp" else "Iniciar sesión",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(14.dp))
                        if (isRegister) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; viewModel.clearError() },
                                label = { Text("Nombre") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Usuario (opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; viewModel.clearError() },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; viewModel.clearError() },
                            label = { Text("Contraseña") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.error != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                state.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (isRegister) viewModel.register(name, username, email, password)
                                else viewModel.login(email, password)
                            },
                            enabled = !state.isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = HextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            } else {
                                Text(if (isRegister) "Registrarme" else "Entrar")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { isRegister = !isRegister; viewModel.clearError() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                if (isRegister) "¿Ya tienes cuenta? Inicia sesión"
                                else "¿No tienes cuenta? Regístrate gratis",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // ── Appearance & Settings card ────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Apariencia y Ajustes", style = MaterialTheme.typography.titleMedium)

                    SettingsRow(
                        icon = Icons.Outlined.DarkMode,
                        title = "Modo Oscuro",
                        subtitle = if (isDarkTheme) "Tema negro activado" else "Tema blanco activado",
                        trailing = { Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() }) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Caché de imágenes",
                        subtitle = if (cacheCleared) "Caché limpiada ✓" else "Iconos y capturas guardadas",
                        trailing = {
                            TextButton(onClick = {
                                context.imageLoader.memoryCache?.clear()
                                context.imageLoader.diskCache?.clear()
                                cacheCleared = true
                            }) { Text("Limpiar") }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        icon = Icons.Outlined.CloudDone,
                        title = "Servidor conectado",
                        subtitle = BuildConfig.API_BASE_URL,
                        iconTint = HextSecondary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "Motor Nexus integrado",
                        subtitle = "NX6R / NXS6 • Verificación SHA-256 • Auto-limpieza 24h",
                        iconTint = HextPrimary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "Auto-actualizar app",
                        subtitle = if (autoUpdateEnabled)
                            "Descarga e instala nuevas versiones automáticamente"
                        else
                            "Preguntar antes de descargar actualizaciones",
                        trailing = {
                            Switch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { updatePreferences.setAutoUpdateEnabled(enabled) }
                                }
                            )
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (updateState) {
                                is UpdateUiState.Checking -> "Buscando actualizaciones..."
                                is UpdateUiState.UpToDate -> "Estás al día (v${BuildConfig.VERSION_NAME})"
                                is UpdateUiState.Error -> (updateState as UpdateUiState.Error).message
                                else -> "Versión actual: v${BuildConfig.VERSION_NAME}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                            onClick = {
                                scope.launch {
                                    appUpdateManager.checkForUpdates(
                                        autoTrigger = false,
                                        activityContextForAutoInstall = context
                                    )
                                }
                            }
                        ) { Text("Buscar") }
                    }
                }
            }

            if (session != null) {
                OutlinedButton(
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Cerrar sesión")
                }
            }
        }
    }
}

@Composable
private fun StatBadge(icon: ImageVector, label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}
