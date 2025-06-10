package com.example.test2.ui.dashboard

import android.graphics.Paint
import android.util.Log
// --- Ajoute ces imports ---
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi    // ← import manquant
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
// --- fin des imports animation ---
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test2.ui.content.ContentScreen
import com.example.test2.ui.model.Bubble

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    vm: DashboardViewModel = viewModel()
) {
    val bubbles by vm.bubbles.collectAsState()
    val links by vm.links.collectAsState()
    val scale by vm.scale.collectAsState()
    val offset by vm.offset.collectAsState()
    val bubbleMenuState by vm.bubbleContextMenuState.collectAsState()
    val backgroundMenuState by vm.backgroundContextMenuState.collectAsState()
    val renameDialogState by vm.renameDialogState.collectAsState()
    val selectedId by vm.selectedContentBubbleId.collectAsState()
    val selectedBubble = bubbles.find { it.id == selectedId }

    val density = LocalDensity.current
    val textPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.BLACK
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE3F2FD))
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { pressOffset ->
                        val worldPos = (pressOffset - offset) / scale
                        vm.openBackgroundContextMenu(worldPos, pressOffset)
                    },
                    onTap = {
                        if (bubbleMenuState.visible) vm.closeBubbleContextMenu()
                        else if (backgroundMenuState.visible) vm.closeBackgroundContextMenu()
                        else vm.tryFinishLink("")
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    vm.onZoom(zoom, centroid)
                    vm.onPan(pan)
                }
            }
    ) {
        // Statistiques en haut
        Text(
            text = "Bubbles: ${bubbles.size}  Links: ${links.size}  Scale: ${"%.2f".format(scale)}",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                .padding(8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Color.DarkGray
        )

        // Canvas principal
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                canvas.translate(offset.x, offset.y)
                canvas.scale(scale, scale)
                // Liaisons
                links.forEach { link ->
                    val a = bubbles.find { it.id == link.fromId }
                    val b = bubbles.find { it.id == link.toId }
                    if (a != null && b != null) {
                        drawLine(
                            Color.DarkGray,
                            a.position,
                            b.position,
                            strokeWidth = (3f / scale).coerceIn(0.5f, 5f)
                        )
                    }
                }
                // Bulles
                bubbles.forEach { bubble ->
                    val isOpen = bubbleMenuState.visible && bubbleMenuState.bubbleId == bubble.id
                    drawCircle(
                        if (isOpen) Color.Magenta else Color(0xFF1976D2),
                        bubble.radius,
                        bubble.position
                    )
                    textPaint.textSize = (30f / scale).coerceIn(12f, 40f)
                    val ty = bubble.position.y + bubble.radius + (25f / scale).coerceAtLeast(10f)
                    canvas.nativeCanvas.drawText(bubble.name, bubble.position.x, ty, textPaint)
                }
            }
        }

        // Zones clic/drag pour chaque bulle
        bubbles.forEach { bubble ->
            val cx = bubble.position.x * scale + offset.x
            val cy = bubble.position.y * scale + offset.y
            val touchR = (bubble.radius * scale).coerceAtLeast(48f)
            Box(
                Modifier
                    .offset { IntOffset((cx - touchR).toInt(), (cy - touchR).toInt()) }
                    .size(((touchR * 2) / density.density).dp)
                    .pointerInput(bubble.id) {
                        detectTapGestures(
                            onLongPress = { off ->
                                vm.openBubbleContextMenu(
                                    bubble.id,
                                    Offset(cx - touchR + off.x, cy - touchR + off.y)
                                )
                            },
                            onTap = {
                                vm.tryFinishLink(bubble.id)
                                if (bubbleMenuState.visible && bubbleMenuState.bubbleId != bubble.id) vm.closeBubbleContextMenu()
                                if (backgroundMenuState.visible) vm.closeBackgroundContextMenu()
                            }
                        )
                    }
                    .pointerInput(bubble.id) {
                        detectDragGestures { _, drag ->
                            if (bubbleMenuState.bubbleId != bubble.id) {
                                vm.onBubbleDrag(bubble.id, drag / scale)
                            }
                        }
                    }
            )
        }

        // Menu contextuel BULLE
        if (bubbleMenuState.visible && bubbleMenuState.bubbleId != null) {
            val off = with(density) {
                DpOffset(
                    bubbleMenuState.screenPosition.x.toDp(),
                    bubbleMenuState.screenPosition.y.toDp()
                )
            }
            DropdownMenu(
                expanded = true,
                onDismissRequest = { vm.closeBubbleContextMenu() },
                offset = off,
                properties = PopupProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                DropdownMenuItem(
                    text = { Text("Link") },
                    onClick = { vm.onMenuOptionLink() },
                    leadingIcon = { Icon(Icons.Filled.Link, null) }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { vm.onMenuOptionRename() },
                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) }
                )
                DropdownMenuItem(
                    text = { Text("Unlink All") },
                    onClick = { vm.onMenuOptionUnlink() },
                    leadingIcon = { Icon(Icons.Filled.Clear, null) }
                )
                DropdownMenuItem(
                    text = { Text("View Content") },
                    onClick = { vm.onMenuOptionViewContent() },
                    leadingIcon = { Icon(Icons.Filled.Visibility, null) }
                )
            }
        }

        // Menu contextuel FOND
        if (backgroundMenuState.visible) {
            val off = with(density) {
                DpOffset(
                    backgroundMenuState.screenPosition.x.toDp(),
                    backgroundMenuState.screenPosition.y.toDp()
                )
            }
            DropdownMenu(
                expanded = true,
                onDismissRequest = { vm.closeBackgroundContextMenu() },
                offset = off,
                properties = PopupProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                DropdownMenuItem(
                    text = { Text("Add Bubble Here") },
                    onClick = { vm.onMenuOptionAddBubble() },
                    leadingIcon = { Icon(Icons.Filled.AddCircle, null) }
                )
            }
        }

        // Dialogue de renommage
        if (renameDialogState.visible) {
            var textField by remember { mutableStateOf(TextFieldValue(renameDialogState.currentName)) }
            AlertDialog(
                onDismissRequest = { vm.onRenameDialogDismiss() },
                title = { Text("Rename Bubble") },
                text = {
                    OutlinedTextField(
                        value = textField,
                        onValueChange = { textField = it },
                        label = { Text("New name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.onRenameDialogConfirm(textField.text) }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.onRenameDialogDismiss() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Overlay de contenu
        AnimatedVisibility(
            visible = (selectedBubble != null),
            enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                    scaleIn(initialScale = 0.8f, animationSpec = tween(durationMillis = 300)),
            exit  = fadeOut(animationSpec = tween(durationMillis = 200)) +
                    scaleOut(targetScale = 0.8f, animationSpec = tween(durationMillis = 300))
        ) {
            selectedBubble?.let {
                ContentScreen(it) { vm.clearContentSelection() }
            }
        }
    }
}
