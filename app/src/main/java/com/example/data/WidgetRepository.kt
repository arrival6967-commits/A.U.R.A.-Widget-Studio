package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WidgetRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeWidgets(userId: String = requireUserId()): Flow<List<WidgetDesign>> = flow {
        val path = "users/$userId/widgets"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("widgets")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WidgetDesign::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWorkspace(userId: String = requireUserId()): Flow<UserWorkspace?> = flow {
        val docPath = "users/$userId/settings/workspace"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("settings")
                .document("workspace")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserWorkspace::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.GET, docPath)
                    }
                    throw error
                }
        )
    }

    suspend fun createWidget(design: WidgetDesign): Result<String> {
        val uid = requireUserId()
        val docId = design.id.ifBlank { "w_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection("users").document(uid).collection("widgets").document(docId)
        return try {
            docRef.set(design.toCreatePayload(uid)).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateWidget(design: WidgetDesign): Result<Unit> {
        val uid = requireUserId()
        require(design.id.isNotBlank()) { "Widget ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("widgets").document(design.id)
        return try {
            docRef.update(design.toUpdatePayload()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteWidget(widgetId: String): Result<Unit> {
        val uid = requireUserId()
        require(widgetId.isNotBlank()) { "Widget ID must not be blank for delete" }
        val docRef = db.collection("users").document(uid).collection("widgets").document(widgetId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getWidgetById(targetUserId: String, widgetId: String): Result<WidgetDesign?> {
        val docRef = db.collection("users").document(targetUserId).collection("widgets").document(widgetId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    WidgetDesign::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveWorkspace(workspace: UserWorkspace): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("settings").document("workspace")
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(workspace.toUpdatePayload()).await()
            } else {
                docRef.set(workspace.toCreatePayload(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
