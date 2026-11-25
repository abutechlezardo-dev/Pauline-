package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.ui.AvatarBackdrop
import com.example.ui.AvatarMood
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PaulineRepository(
  private val dbProvider: suspend () -> FirebaseFirestore,
  private val authProvider: suspend () -> FirebaseAuth = {
    withContext(Dispatchers.IO) { Firebase.auth }
  },
) {
  private val dbMutex = Mutex()
  @Volatile private var cachedDb: FirebaseFirestore? = null

  private val authMutex = Mutex()
  @Volatile private var cachedAuth: FirebaseAuth? = null

  constructor(db: FirebaseFirestore, auth: FirebaseAuth = Firebase.auth) : this(
    dbProvider = { db },
    authProvider = { auth },
  )

  constructor(context: Context) : this(
    dbProvider = createFirestoreProvider(context.applicationContext),
  )

  private suspend fun getDb(): FirebaseFirestore {
    cachedDb?.let { return it }
    return dbMutex.withLock {
      cachedDb ?: dbProvider().also { cachedDb = it }
    }
  }

  private suspend fun getAuth(): FirebaseAuth {
    cachedAuth?.let { return it }
    return authMutex.withLock {
      cachedAuth ?: authProvider().also { cachedAuth = it }
    }
  }

  private suspend fun requireMatchingUid(userId: String): String {
    val uid =
      getAuth().currentUser?.uid
        ?: throw IllegalStateException("L'utilisateur doit être connecté avec Google.")
    check(uid == userId) { "Session modifiée ; écriture refusée pour un autre utilisateur." }
    return uid
  }

  fun observePreferences(userId: String): Flow<UserAvatarPreferences?> = flow {
    val db = getDb()
    emitAll(
      db.collection("users")
        .document(userId)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              UserAvatarPreferences::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
    )
  }

  suspend fun savePreferences(
    userId: String,
    mood: AvatarMood,
    backdrop: AvatarBackdrop,
  ): Result<Unit> {
    val docPath = "users/$userId"
    return try {
      val uid = requireMatchingUid(userId)
      val db = getDb()
      val docRef = db.collection("users").document(uid)
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef
          .update(
            mapOf(
              "mood" to mood.name,
              "backdrop" to backdrop.name,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "mood" to mood.name,
              "backdrop" to backdrop.name,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  fun observeMessages(userId: String): Flow<List<ChatMessage>> = flow {
    val db = getDb()
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("messages")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            ChatMessage::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
    )
  }

  suspend fun addMessage(
    userId: String,
    sender: String,
    text: String,
    moodTag: AvatarMood,
  ): Result<Unit> {
    var docPath = "users/$userId/messages"
    return try {
      val uid = requireMatchingUid(userId)
      val db = getDb()
      val cleanText = text.trim()
      require(cleanText.isNotEmpty() && cleanText.length <= 4000) {
        "Le message doit contenir entre 1 et 4000 caractères."
      }
      require(sender == "user" || sender == "pauline") { "Expéditeur invalide." }

      val docRef = db.collection("users").document(uid).collection("messages").document()
      docPath = docRef.path
      docRef
        .set(
          mapOf(
            "userId" to uid,
            "sender" to sender,
            "text" to cleanText,
            "moodTag" to moodTag.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun clearMessages(userId: String): Result<Unit> {
    val collectionPath = "users/$userId/messages"
    return try {
      val uid = requireMatchingUid(userId)
      val db = getDb()
      val snapshot = db.collection("users").document(uid).collection("messages").get().await()
      if (!snapshot.isEmpty) {
        val docs = snapshot.documents
        docs.chunked(400).forEach { chunk ->
          val batch = db.batch()
          chunk.forEach { doc -> batch.delete(doc.reference) }
          batch.commit().await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, collectionPath)
      Result.failure(e)
    }
  }

  fun observeMemories(userId: String): Flow<List<MemoryItem>> = flow {
    val db = getDb()
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("memories")
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            MemoryItem::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
    )
  }

  suspend fun addMemory(
    userId: String,
    title: String,
    detail: String,
    source: String = "manual",
  ): Result<Unit> {
    var docPath = "users/$userId/memories"
    return try {
      val uid = requireMatchingUid(userId)
      val db = getDb()
      val cleanTitle = title.trim()
      val cleanDetail = detail.trim()
      require(cleanTitle.isNotEmpty() && cleanTitle.length <= 120) {
        "Le titre doit contenir entre 1 et 120 caractères."
      }
      require(cleanDetail.isNotEmpty() && cleanDetail.length <= 1000) {
        "Le souvenir doit contenir entre 1 et 1000 caractères."
      }
      require(source == "auto" || source == "manual") { "Source de souvenir invalide." }

      val docRef = db.collection("users").document(uid).collection("memories").document()
      docPath = docRef.path
      docRef
        .set(
          mapOf(
            "userId" to uid,
            "title" to cleanTitle,
            "detail" to cleanDetail,
            "source" to source,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateMemory(
    userId: String,
    memoryId: String,
    title: String,
    detail: String,
  ): Result<Unit> {
    val docPath = "users/$userId/memories/$memoryId"
    return try {
      val uid = requireMatchingUid(userId)
      val db = getDb()
      require(memoryId.isNotBlank()) { "ID de souvenir manquant." }
      val cleanTitle = title.trim()
      val cleanDetail = detail.trim()
      require(cleanTitle.isNotEmpty() && cleanTitle.length <= 120) {
        "Le titre doit contenir entre 1 et 120 caractères."
      }
      require(cleanDetail.isNotEmpty() && cleanDetail.length <= 1000) {
        "Le souvenir doit contenir entre 1 et 1000 caractères."
      }

      db.collection("users")
        .document(uid)
        .collection("memories")
        .document(memoryId)
        .update(
          mapOf(
            "title" to cleanTitle,
            "detail" to cleanDetail,
            "source" to "manual",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteMemory(userId: String, memoryId: String): Result<Unit> {
    val docPath = "users/$userId/memories/$memoryId"
    return try {
      val uid = requireMatchingUid(userId)
      val db = getDb()
      require(memoryId.isNotBlank()) { "ID de souvenir manquant." }
      db.collection("users")
        .document(uid)
        .collection("memories")
        .document(memoryId)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  private companion object {
    fun createFirestoreProvider(appContext: Context): suspend () -> FirebaseFirestore = {
      withContext(Dispatchers.IO) {
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
      }
    }
  }
}
