package com.lockify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val Ink=Color(0xFF101116)
private val Panel=Color(0xFF1B1D26)
private val Accent=Color(0xFF9B8CFF)
private val Muted=Color(0xFF9A9BA8)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContent { LockifyApp(SecureVault(applicationContext)) }
    }
}

@Composable
private fun LockifyApp(vault:SecureVault){
    var configured by remember { mutableStateOf(vault.configured()) }
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var logins by remember { mutableStateOf<List<LoginEntry>>(emptyList()) }
    var adding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    val scope= rememberCoroutineScope()

    MaterialTheme(colorScheme=darkColorScheme(background=Ink,surface=Panel,primary=Accent,onSurface=Color.White)){
        Surface(Modifier.fillMaxSize(),color=Ink){
            if(!unlocked){
                Column(Modifier.fillMaxSize().padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                    Box(Modifier.size(76.dp).background(Panel,CircleShape),contentAlignment=Alignment.Center){Image(painterResource(R.drawable.lockify_logo),contentDescription="Lockify logo",modifier=Modifier.size(54.dp),contentScale=ContentScale.Fit)}
                    Spacer(Modifier.height(20.dp))
                    Text("LOCKIFY",fontSize=27.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp)
                    Text(if(configured)"Unlock your private vault" else "Create your private vault",color=Muted)
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(pin,{v->if(v.length<=12&&v.all(Char::isDigit))pin=v},label={Text("6–12 digit PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                    if(!configured){
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(confirm,{v->if(v.length<=12&&v.all(Char::isDigit))confirm=v},label={Text("Confirm PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                    }
                    if(message.isNotBlank()){Spacer(Modifier.height(8.dp));Text(message,color=Color(0xFFFF8A8A))}
                    Spacer(Modifier.height(16.dp))
                    Button(onClick={
                        message=""
                        if(pin.length<6) message="PIN must contain at least 6 digits."
                        else if(!configured&&pin!=confirm) message="PINs do not match."
                        else {
                            val chars=pin.toCharArray()
                            scope.launch {
                                try{
                                    withContext(Dispatchers.IO){if(configured)vault.unlock(chars) else vault.create(chars)}
                                    configured=true
                                    logins=withContext(Dispatchers.IO){vault.entries()}
                                    unlocked=true;pin="";confirm=""
                                }catch(e:Exception){message=if(configured)"Incorrect PIN or vault data is damaged." else "Could not create vault. Try again."}
                                finally{chars.fill('\u0000')}
                            }
                        }
                    },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text(if(configured)"Unlock vault" else "Create secure vault")}
                    Spacer(Modifier.height(12.dp))
                    Text("Your PIN is not stored directly. There is no PIN recovery.",color=Muted,fontSize=11.sp)
                }
            }else{
                Column(Modifier.fillMaxSize().padding(20.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Image(painterResource(R.drawable.lockify_logo),contentDescription="Lockify logo",modifier=Modifier.size(42.dp),contentScale=ContentScale.Fit)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)){Text("LOCKIFY",color=Accent,fontWeight=FontWeight.Bold,letterSpacing=2.sp);Text("Your private vault",fontSize=24.sp,fontWeight=FontWeight.Bold)}
                        TextButton(onClick={showAbout=true}){Text("About")}
                        TextButton(onClick={vault.lock();logins=emptyList();unlocked=false;reveal=null}){Icon(Icons.Default.Lock,null);Text(" Lock")}
                    }
                    Spacer(Modifier.height(18.dp))
                    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
                        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){Text("ENCRYPTED LOGINS",color=Muted,fontSize=11.sp);Text("${logins.size}",fontSize=30.sp,fontWeight=FontWeight.Bold)}
                            Icon(Icons.Default.Security,null,tint=Accent,modifier=Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Your accounts",fontSize=18.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));FilledIconButton(onClick={adding=true}){Icon(Icons.Default.Add,null)}}
                    if(logins.isEmpty())Text("Your vault is empty. Tap + to add a login.",color=Muted)
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
                        items(logins,key={it.id}){item->
                            Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)){
                                Column(Modifier.fillMaxWidth().padding(15.dp)){
                                    Text(item.title,fontWeight=FontWeight.SemiBold);Text(item.username,color=Muted,fontSize=12.sp)
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        TextButton(onClick={reveal=if(reveal==item.id)null else item.id}){Text(if(reveal==item.id)item.password else "Show password")}
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick={
                                            val next=logins.filterNot{it.id==item.id}
                                            scope.launch{withContext(Dispatchers.IO){vault.save(next)};logins=next}
                                        }){Text("Delete",color=Color(0xFFFF8A8A))}
                                    }
                                }
                            }
                        }
                    }
                }
                if(adding)AlertDialog(
                    onDismissRequest={adding=false},
                    title={Text("Add login")},
                    text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
                        OutlinedTextField(title,{title=it},label={Text("Website / app")},singleLine=true)
                        OutlinedTextField(user,{user=it},label={Text("Username / email")},singleLine=true)
                        OutlinedTextField(pass,{pass=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation())
                    }},
                    confirmButton={TextButton(onClick={
                        if(title.isNotBlank()&&user.isNotBlank()&&pass.isNotBlank()){
                            val next=logins+LoginEntry(UUID.randomUUID().toString(),title.trim(),user.trim(),pass)
                            scope.launch{
                                try{withContext(Dispatchers.IO){vault.save(next)};logins=next;title="";user="";pass="";adding=false}
                                catch(_:Exception){message="Could not save encrypted login."}
                            }
                        }
                    }){Text("Save encrypted")}},
                    dismissButton={TextButton(onClick={adding=false}){Text("Cancel")}}
                )
            }
            if(showAbout) AlertDialog(
                onDismissRequest={showAbout=false},
                icon={Image(painterResource(R.drawable.lockify_logo),contentDescription="Lockify logo",modifier=Modifier.size(56.dp))},
                title={Text("About Lockify")},
                text={Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()){
                    Text("Offline Password Manager",color=Muted)
                    Spacer(Modifier.height(12.dp))
                    Text("Made by Shadowking",fontWeight=FontWeight.SemiBold,color=Accent)
                    Spacer(Modifier.height(6.dp))
                    Text("Your vault is stored encrypted on this device.",fontSize=12.sp,color=Muted)
                }},
                confirmButton={TextButton(onClick={showAbout=false}){Text("Close")}}
            )
        }
    }
}
