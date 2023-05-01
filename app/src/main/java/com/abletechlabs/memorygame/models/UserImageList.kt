package com.abletechlabs.memorygame.models

import com.google.firebase.firestore.PropertyName

data class UserImageList(
    @PropertyName("images") val images: List<String>? = null
)
