package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    // Sanitize diagnostic info: never leak email addresses, UIDs, or tenant IDs into logs
    val authInfoJson = JSONObject().apply {
        put("isAuthenticated", currentUser != null)
        put("authProvider", currentUser?.providerData?.firstOrNull()?.providerId ?: "none")
        put("isEmailVerified", currentUser?.isEmailVerified ?: false)
    }

    val errorInfoJson = JSONObject().apply {
        put("errorType", exception.javaClass.simpleName)
        put("message", exception.localizedMessage ?: exception.message ?: "Unknown Firestore error")
        put("operationType", operationType.value)
        put("path", path)
        put("authStatus", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore error: $jsonString")
    return jsonString
}
