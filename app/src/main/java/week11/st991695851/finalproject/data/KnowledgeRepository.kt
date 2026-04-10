package week11.st991695851.finalproject.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose

class KnowledgeRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Authentication Functions
    fun getCurrentUser() = auth.currentUser

    fun logout() = auth.signOut()

    // Firestore Functions for the Knowledge Vault
    suspend fun saveNote(title: String, content: String, category: String) {
        val userId = auth.currentUser?.uid ?: return
        val note = hashMapOf(
            "title" to title,
            "content" to content,
            "category" to category,
            "ownerId" to userId,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("knowledge_vault").add(note).await()
    }


    fun getNotesFlow(): Flow<List<Map<String, Any>>> = callbackFlow {
        // 1. Get the current user ID safely
        val userId = auth.currentUser?.uid

        // 2. If no user, close the flow immediately
        if (userId == null) {
           trySend(emptyList())
            return@callbackFlow
        }

        // 3. Set up the real-time listener (Section 5a requirement)
        val subscription = db.collection("knowledge_vault")
            .whereEqualTo("ownerId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // You could optionally log the error here
                    return@addSnapshotListener
                }

                val notes = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data?.toMutableMap()
                    data?.set("id", doc.id)
                    data
                } ?: emptyList()
                trySend(notes)
            }

        // 4. Clean up the listener when the flow is closed
        awaitClose { subscription.remove() }
    }

    suspend fun fetchNotes(): List<Map<String, Any>> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("knowledge_vault")
                .whereEqualTo("ownerId", userId)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            emptyList()
        }


    }

}

