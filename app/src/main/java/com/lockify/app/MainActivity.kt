package com.lockify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF101116)
private val Panel = Color(0xFF1B1D26)
private val Accent = Color(0xFF9B8CFF)
private val Muted = Color(0xFF9A9BA8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LockifyApp() }
    }
}

private data class VaultItem(val title: String, val username: String, val category: String)

@Composable
private fun LockifyApp() {
    var isUnlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf(
        VaultItem("Google", "your.email@gmail.com", "Personal"),
        VaultItem("Instagram", "your_username", "Social"),
        VaultItem("GitHub", "developer", "Work")
    ) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Panel, primary = Accent,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            if (!isUnlocked) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(82.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Security, null, tint = Accent, modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("LOCKIFY", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Your passwords. Your privacy.", color = Muted)
                    Spacer(Modifier.height(34.dp))
                    OutlinedTextField(
                        value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                        label = { Text("Enter PIN") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { if (pin.length >= 4) isUnlocked = true },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    ) { Text("Unlock vault") }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { /* Biometric flow is added with the secure vault milestone. */ }) {
                        Icon(Icons.Default.Fingerprint, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use fingerprint")
                    }
                    Text("UI prototype — authentication is not active yet", color = Muted, fontSize = 11.sp)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("LOCKIFY", color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("Your private vault", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { isUnlocked = false; pin = "" }) { Text("Lock") }
                    }
                    Spacer(Modifier.height(22.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("SAVED LOGINS", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                                Text(items.size.toString(), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Default.Security, null, tint = Accent, modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Search accounts") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your accounts", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        FilledIconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null) }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items.filter { it.title.contains(search, true) || it.username.contains(search, true) }) { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(42.dp).background(Color(0xFF302D46), CircleShape), contentAlignment = Alignment.Center) {
                                        Text(item.title.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(item.title, fontWeight = FontWeight.SemiBold)
                                        Text(item.username, color = Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                if (showAdd) {
                    AlertDialog(
                        onDismissRequest = { showAdd = false },
                        title = { Text("Add login") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(title, { title = it }, label = { Text("Website / app") }, singleLine = true)
                                OutlinedTextField(username, { username = it }, label = { Text("Username / email") }, singleLine = true)
                                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (title.isNotBlank() && username.isNotBlank()) {
                                    items.add(VaultItem(title.trim(), username.trim(), "Other"))
                                }
                                title = ""; username = ""; password = ""; showAdd = false
                            }) { Text("Add") }
                        },
                        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
}
