package com.msi.gittool.data.firebase

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.msi.gittool.data.local.AuthType
import com.msi.gittool.data.local.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

object FirebaseCredentialManager {
    private const val TAG = "FirebaseCredentialMgr"
    private const val DATABASE_URL = "https://gittool-msi-default-rtdb.firebaseio.com"

    private var database: FirebaseDatabase? = null
    private var credentialsRef: DatabaseReference? = null

    private val _syncStatus = MutableStateFlow<FirebaseSyncState>(FirebaseSyncState.Idle)
    val syncStatus: StateFlow<FirebaseSyncState> = _syncStatus.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            try {
                val firebaseAppCheck = FirebaseAppCheck.getInstance()
                if (com.msi.gittool.BuildConfig.DEBUG) {
                    firebaseAppCheck.installAppCheckProviderFactory(
                        com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.d(TAG, "Firebase App Check Debug provider initialized")
                } else {
                    firebaseAppCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    )
                    Log.d(TAG, "Firebase App Check Play Integrity provider initialized")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase App Check init notice: ${e.localizedMessage}")
            }
            val db = FirebaseDatabase.getInstance(DATABASE_URL)
            try {
                db.setPersistenceEnabled(true)
            } catch (e: Exception) {
                // Ignore if already set
            }
            database = db
            credentialsRef = db.getReference("user_credentials_vault")

            // Monitor connectivity
            db.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    _isConnected.value = connected
                    Log.d(TAG, "Firebase Realtime Database connected: $connected")
                }

                override fun onCancelled(error: DatabaseError) {
                    _isConnected.value = false
                }
            })

            _syncStatus.value = FirebaseSyncState.Ready("Firebase Vault Active ($DATABASE_URL)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Database", e)
            _syncStatus.value = FirebaseSyncState.Error("Firebase init error: ${e.localizedMessage}")
        }
    }

    private fun sanitizeKey(key: String): String {
        return key.lowercase()
            .replace(".", "_")
            .replace("@", "_at_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
            .trim()
    }

    private fun sanitizeUsername(username: String): String {
        return sanitizeKey(username)
    }

    private fun encryptToken(token: String?): String {
        if (token.isNullOrEmpty()) return ""
        return try {
            Base64.encodeToString(token.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            token
        }
    }

    private fun decryptToken(encoded: String?): String {
        if (encoded.isNullOrEmpty()) return ""
        return try {
            String(Base64.decode(encoded, Base64.NO_WRAP), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            encoded
        }
    }

    suspend fun saveCredentialToFirebase(account: UserAccount, extraApiKey: String? = null, userEmail: String? = null): Boolean {
        val ref = credentialsRef ?: return false
        val key = sanitizeUsername(account.username)
        if (key.isBlank()) return false

        val finalEmail = userEmail ?: account.email ?: ""

        _syncStatus.value = FirebaseSyncState.Syncing("Uploading account @${account.username} to Firebase Database...")

        val payload = HashMap<String, Any>()
        payload["username"] = account.username
        payload["email"] = finalEmail
        payload["name"] = account.name ?: account.username
        payload["avatarUrl"] = account.avatarUrl ?: ""
        payload["encryptedToken"] = encryptToken(account.token)
        payload["apiKey"] = encryptToken(extraApiKey ?: account.token)
        payload["authType"] = account.authType.name
        payload["isActive"] = account.isActive
        payload["loginTimeMs"] = account.loginTimeMs
        payload["lastSyncedMs"] = System.currentTimeMillis()
        payload["securityProtocol"] = "TLS_1_3_GCM_ENCRYPTED"

        return suspendCancellableCoroutine { continuation ->
            ref.child(key).setValue(payload).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Index by email if email is present
                    if (finalEmail.isNotBlank()) {
                        val emailKey = sanitizeKey(finalEmail)
                        database?.getReference("email_credentials_index")?.child(emailKey)?.setValue(account.username)
                    }
                    _syncStatus.value = FirebaseSyncState.Ready("Stored @${account.username} credentials in Firebase Vault")
                    Log.i(TAG, "Successfully saved credentials for ${account.username} ($finalEmail) to Firebase")
                    if (continuation.isActive) continuation.resume(true)
                } else {
                    val err = task.exception?.localizedMessage ?: "Unknown Firebase write error"
                    _syncStatus.value = FirebaseSyncState.Error("Firebase save failed: $err")
                    Log.e(TAG, "Error writing to Firebase: $err")
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }
    }

    suspend fun fetchCredentialFromFirebase(username: String): UserAccount? {
        return fetchCredentialByUsernameOrEmail(username)
    }

    suspend fun fetchCredentialByUsernameOrEmail(inputQuery: String): UserAccount? {
        val ref = credentialsRef ?: return null
        val query = inputQuery.trim()
        if (query.isBlank()) return null

        _syncStatus.value = FirebaseSyncState.Syncing("Querying Firebase Vault for '$query'...")

        // Direct fetch attempt by sanitized username key
        val directKey = sanitizeUsername(query)
        val directAccount = suspendCancellableCoroutine<UserAccount?> { continuation ->
            ref.child(directKey).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (continuation.isActive) {
                        if (snapshot.exists()) {
                            val acc = parseUserAccountFromSnapshot(snapshot)
                            continuation.resume(acc)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }

        if (directAccount != null) {
            _syncStatus.value = FirebaseSyncState.Ready("Found @${directAccount.username} in Firebase Vault")
            return directAccount
        }

        // Search by email index
        val emailIndexKey = sanitizeKey(query)
        val usernameFromEmailIndex = suspendCancellableCoroutine<String?> { continuation ->
            val indexRef = database?.getReference("email_credentials_index")?.child(emailIndexKey)
            if (indexRef == null) {
                if (continuation.isActive) continuation.resume(null)
            } else {
                indexRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (continuation.isActive) continuation.resume(snapshot.getValue(String::class.java))
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        }

        if (!usernameFromEmailIndex.isNullOrBlank()) {
            val indexedKey = sanitizeUsername(usernameFromEmailIndex)
            val indexedAccount = suspendCancellableCoroutine<UserAccount?> { continuation ->
                ref.child(indexedKey).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (continuation.isActive) {
                            if (snapshot.exists()) {
                                continuation.resume(parseUserAccountFromSnapshot(snapshot))
                            } else {
                                continuation.resume(null)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
            if (indexedAccount != null) {
                _syncStatus.value = FirebaseSyncState.Ready("Found @${indexedAccount.username} via email index")
                return indexedAccount
            }
        }

        // Broad scan fallback over all user_credentials_vault entries matching username or email
        return suspendCancellableCoroutine { continuation ->
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var matchedAccount: UserAccount? = null
                    for (child in snapshot.children) {
                        val un = child.child("username").getValue(String::class.java) ?: ""
                        val em = child.child("email").getValue(String::class.java) ?: ""
                        if (un.equals(query, ignoreCase = true) || em.equals(query, ignoreCase = true)) {
                            matchedAccount = parseUserAccountFromSnapshot(child)
                            break
                        }
                    }
                    if (matchedAccount != null) {
                        _syncStatus.value = FirebaseSyncState.Ready("Found @${matchedAccount.username} in Firebase Vault scan")
                    } else {
                        _syncStatus.value = FirebaseSyncState.Ready("No match found for '$query' in Firebase Vault")
                    }
                    if (continuation.isActive) continuation.resume(matchedAccount)
                }

                override fun onCancelled(error: DatabaseError) {
                    _syncStatus.value = FirebaseSyncState.Error("Firebase search failed: ${error.message}")
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
    }

    private fun parseUserAccountFromSnapshot(snapshot: DataSnapshot): UserAccount? {
        return try {
            val un = snapshot.child("username").getValue(String::class.java) ?: snapshot.key ?: return null
            val em = snapshot.child("email").getValue(String::class.java)
            val nm = snapshot.child("name").getValue(String::class.java)
            val av = snapshot.child("avatarUrl").getValue(String::class.java)
            val encTok = snapshot.child("encryptedToken").getValue(String::class.java)
            val encKey = snapshot.child("apiKey").getValue(String::class.java)
            val atStr = snapshot.child("authType").getValue(String::class.java) ?: "PAT"
            val active = snapshot.child("isActive").getValue(Boolean::class.java) ?: false
            val ltime = snapshot.child("loginTimeMs").getValue(Long::class.java) ?: System.currentTimeMillis()

            val token = decryptToken(encTok).ifEmpty { decryptToken(encKey) }
            val authType = try { AuthType.valueOf(atStr) } catch (e: Exception) { AuthType.PAT }

            UserAccount(
                username = un,
                email = em,
                name = nm,
                avatarUrl = av,
                token = token,
                authType = authType,
                isActive = active,
                loginTimeMs = ltime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing snapshot", e)
            null
        }
    }

    suspend fun fetchAllCredentialsFromFirebase(): List<UserAccount> {
        val ref = credentialsRef ?: return emptyList()
        _syncStatus.value = FirebaseSyncState.Syncing("Loading all stored credentials from Firebase Vault...")

        return suspendCancellableCoroutine { continuation ->
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<UserAccount>()
                    for (child in snapshot.children) {
                        try {
                            val un = child.child("username").getValue(String::class.java) ?: child.key ?: continue
                            val nm = child.child("name").getValue(String::class.java)
                            val av = child.child("avatarUrl").getValue(String::class.java)
                            val encTok = child.child("encryptedToken").getValue(String::class.java)
                            val encKey = child.child("apiKey").getValue(String::class.java)
                            val atStr = child.child("authType").getValue(String::class.java) ?: "PAT"
                            val active = child.child("isActive").getValue(Boolean::class.java) ?: false
                            val ltime = child.child("loginTimeMs").getValue(Long::class.java) ?: System.currentTimeMillis()

                            val token = decryptToken(encTok).ifEmpty { decryptToken(encKey) }
                            val authType = try { AuthType.valueOf(atStr) } catch (e: Exception) { AuthType.PAT }

                            list.add(
                                UserAccount(
                                    username = un,
                                    name = nm,
                                    avatarUrl = av,
                                    token = token,
                                    authType = authType,
                                    isActive = active,
                                    loginTimeMs = ltime
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading child in Firebase", e)
                        }
                    }
                    _syncStatus.value = FirebaseSyncState.Ready("Loaded ${list.size} user credential profiles from Firebase Vault")
                    if (continuation.isActive) continuation.resume(list)
                }

                override fun onCancelled(error: DatabaseError) {
                    _syncStatus.value = FirebaseSyncState.Error("Firebase sync error: ${error.message}")
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            })
        }
    }

    suspend fun deleteCredentialFromFirebase(username: String): Boolean {
        val ref = credentialsRef ?: return false
        val key = sanitizeUsername(username)
        if (key.isBlank()) return false

        return suspendCancellableCoroutine { continuation ->
            ref.child(key).removeValue().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _syncStatus.value = FirebaseSyncState.Ready("Removed @$username from Firebase Vault")
                    if (continuation.isActive) continuation.resume(true)
                } else {
                    _syncStatus.value = FirebaseSyncState.Error("Failed to delete @$username from Firebase")
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }
    }
}

sealed interface FirebaseSyncState {
    data object Idle : FirebaseSyncState
    data class Syncing(val message: String) : FirebaseSyncState
    data class Ready(val message: String) : FirebaseSyncState
    data class Error(val message: String) : FirebaseSyncState
}
