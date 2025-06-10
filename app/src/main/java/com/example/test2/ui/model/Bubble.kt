package com.example.test2.ui.model

import androidx.compose.ui.geometry.Offset

data class Bubble(
    val id: String,
    val position: Offset,
    val name: String,
    val radius: Float,
    val velocity: Offset = Offset.Zero,
    val documentId: String?,
    val content: String = ""   // ← nouveau champ "content"
)
