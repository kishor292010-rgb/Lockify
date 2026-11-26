package com.lockify.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class LoginEntry(val id:String,val title:String,val username:String,val password:String)

class SecureVault(context:Context) {
    private val prefs=context.getSharedPreferences("lockify_secure_meta",Context.MODE_PRIVATE)
    private val file=File(context.filesDir,"vault.enc")
    private val rng=SecureRandom()
    private var key:ByteArray?=null
    fun configured()=prefs.contains("salt") && prefs.contains("nonce") && prefs.contains("wrapped")
    fun create(pin:CharArray) {
        require(pin.size>=6 && pin.all{it.isDigit()})
        check(!configured())
        val salt=bytes(16); val vaultKey=bytes(32); val wrap=derive(pin,salt)
        val (nonce,ciphertext)=encrypt(wrap,vaultKey)
        check(prefs.edit().putString("salt",b64(salt)).putString("nonce",b64(nonce)).putString("wrapped",b64(ciphertext)).commit())
        key=vaultKey
        save(emptyList())
    }
    fun unlock(pin:CharArray) {
        val salt=dec(prefs.getString("salt",null)!!)
        val nonce=dec(prefs.getString("nonce",null)!!)
        val wrapped=dec(prefs.getString("wrapped",null)!!)
        key=decrypt(derive(pin,salt),nonce,wrapped).also { check(it.size==32) }
    }
    fun lock(){ key?.fill(0); key=null }
    fun entries():List<LoginEntry> {
        val k=key?:error("Vault locked")
        if(!file.exists()) return emptyList()
        val obj=JSONObject(file.readText())
        val raw=decrypt(k,dec(obj.getString("n")),dec(obj.getString("c")))
        val a=JSONArray(String(raw,Charsets.UTF_8))
        return (0 until a.length()).map { i -> a.getJSONObject(i).let {
            LoginEntry(it.getString("id"),it.getString("title"),it.getString("username"),it.getString("password"))
        } }
    }
    fun save(items:List<LoginEntry>) {
        val k=key?:error("Vault locked")
        val a=JSONArray()
        items.forEach { a.put(JSONObject().put("id",it.id).put("title",it.title).put("username",it.username).put("password",it.password)) }
        val (n,c)=encrypt(k,a.toString().toByteArray(Charsets.UTF_8))
        val tmp=File(file.parentFile,"vault.tmp")
        tmp.writeText(JSONObject().put("n",b64(n)).put("c",b64(c)).toString())
        if(!tmp.renameTo(file)){ tmp.copyTo(file,true); tmp.delete() }
    }
    private fun derive(pin:CharArray,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(pin,salt,310000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun encrypt(k:ByteArray,p:ByteArray):Pair<ByteArray,ByteArray>{
        val n=bytes(12); val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE,SecretKeySpec(k,"AES"),GCMParameterSpec(128,n))
        return n to c.doFinal(p)
    }
    private fun decrypt(k:ByteArray,n:ByteArray,p:ByteArray):ByteArray{
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE,SecretKeySpec(k,"AES"),GCMParameterSpec(128,n))
        return c.doFinal(p)
    }
    private fun bytes(n:Int)=ByteArray(n).also(rng::nextBytes)
    private fun b64(b:ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP)
    private fun dec(s:String)=Base64.decode(s,Base64.NO_WRAP)
}
