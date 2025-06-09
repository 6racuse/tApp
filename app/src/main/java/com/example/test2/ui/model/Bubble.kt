package com.example.test2.model

import androidx.compose.ui.geometry.Offset

data class Bubble(
    val id: String,
    var position: Offset,
    val name: String,
    val radius: Float = 40f,
    val velocity: Offset = Offset.Zero,
    val documentId: String? = null // Peut être null, mais on s'assure que les nouvelles en ont un
)