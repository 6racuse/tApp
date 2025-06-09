package com.example.test2.ui.dashboard

import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DriveFileRenameOutline // Icône pour Rename
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
import androidx.compose.ui.text.input.TextFieldValue // Pour le TextField
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class) // Nécessaire pour AlertDialog et OutlinedTextField
@Composable
fun DashboardScreen(modifier: Modifier = Modifier, vm: DashboardViewModel = viewModel()) {
    val bubbles by vm.bubbles.collectAsState()
    val links by vm.links.collectAsState()
    val scale by vm.scale.collectAsState()
    val offset by vm.offset.collectAsState()
    val bubbleMenuState by vm.bubbleContextMenuState.collectAsState()
    val backgroundMenuState by vm.backgroundContextMenuState.collectAsState()
    val renameDialogState by vm.renameDialogState.collectAsState() // État pour le dialogue de renommage

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
                        val worldPosition = (pressOffset - offset) / scale
                        vm.openBackgroundContextMenu(worldPosition, pressOffset)
                        Log.d("DashboardScreen", "Background long press at screen: $pressOffset, world: $worldPosition")
                    },
                    onTap = {
                        Log.d("DashboardScreen", "Background tap.")
                        if (bubbleMenuState.visible) {
                            vm.closeBubbleContextMenu()
                        } else if (backgroundMenuState.visible) {
                            vm.closeBackgroundContextMenu()
                        } else {
                            vm.tryFinishLink("")
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panAmount, zoomFactor, _ ->
                    vm.onZoom(zoomFactor, centroid)
                    vm.onPan(panAmount)
                }
            }
    ) {
        Text(
            text = "Bubbles: ${bubbles.size}, Links: ${links.size}, Scale: ${String.format("%.2f", scale)}",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Color.DarkGray
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                canvas.translate(offset.x, offset.y)
                canvas.scale(scale, scale)

                links.forEach { link ->
                    val bubbleA = bubbles.find { it.id == link.fromId }
                    val bubbleB = bubbles.find { it.id == link.toId }
                    if (bubbleA != null && bubbleB != null) {
                        drawLine(
                            color = Color.DarkGray,
                            start = bubbleA.position,
                            end = bubbleB.position,
                            strokeWidth = (3f / scale).coerceAtLeast(0.5f).coerceAtMost(5f)
                        )
                    } else {
                        Log.w("CanvasDraw", "Could not draw link: ${link.fromId} -> ${link.toId}. Bubble(s) not found.")
                    }
                }

                bubbles.forEach { bubble ->
                    val isMenuOpenForThisBubble = bubbleMenuState.bubbleId == bubble.id && bubbleMenuState.visible
                    drawCircle(
                        color = if (isMenuOpenForThisBubble) Color.Magenta else Color(0xFF1976D2),
                        radius = bubble.radius,
                        center = bubble.position
                    )
                    textPaint.textSize = (30f / scale).coerceIn(12f, 40f)
                    val textYPosition = bubble.position.y + bubble.radius + (25f / scale).coerceAtLeast(10f)
                    canvas.nativeCanvas.drawText(
                        bubble.name,
                        bubble.position.x,
                        textYPosition,
                        textPaint
                    )
                }
            }
        }

        bubbles.forEach { bubble ->
            val centerOnScreenX = bubble.position.x * scale + offset.x
            val centerOnScreenY = bubble.position.y * scale + offset.y
            val touchRadiusPx = (bubble.radius * scale).coerceAtLeast(48f)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (centerOnScreenX - touchRadiusPx).toInt(),
                            (centerOnScreenY - touchRadiusPx).toInt()
                        )
                    }
                    .size(((touchRadiusPx * 2) / density.density).dp)
                    .pointerInput(bubble.id) {
                        detectTapGestures(
                            onLongPress = { pressOffsetInBox ->
                                val menuScreenPosition = Offset(
                                    x = centerOnScreenX - touchRadiusPx + pressOffsetInBox.x,
                                    y = centerOnScreenY - touchRadiusPx + pressOffsetInBox.y
                                )
                                vm.openBubbleContextMenu(bubble.id, menuScreenPosition)
                                Log.d("BubbleInput", "Long press on ${bubble.name} at screen: $menuScreenPosition")
                            },
                            onTap = {
                                Log.d("BubbleInput", "Tap on ${bubble.name}")
                                vm.tryFinishLink(bubble.id)
                                if (bubbleMenuState.visible && bubbleMenuState.bubbleId != bubble.id) vm.closeBubbleContextMenu()
                                if (backgroundMenuState.visible) vm.closeBackgroundContextMenu()
                            }
                        )
                    }
                    .pointerInput(bubble.id + "_drag") {
                        detectDragGestures { change, dragAmount ->
                            if (!bubbleMenuState.visible || bubbleMenuState.bubbleId != bubble.id) {
                                change.consume()
                                vm.onBubbleDrag(bubble.id, dragAmount / scale)
                            }
                        }
                    }
            )
        }

        if (bubbleMenuState.visible && bubbleMenuState.bubbleId != null) {
            val menuPositionDpOffset = with(density) {
                DpOffset(bubbleMenuState.screenPosition.x.toDp(), bubbleMenuState.screenPosition.y.toDp())
            }
            MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = ShapeDefaults.ExtraSmall)) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { vm.closeBubbleContextMenu() },
                    offset = menuPositionDpOffset,
                    properties = PopupProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
                ) {
                    DropdownMenuItem(
                        text = { Text("Link") },
                        onClick = { vm.onMenuOptionLink() },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = "Link bubbles") }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") }, // Option de renommage
                        onClick = { vm.onMenuOptionRename() },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename bubble") }
                    )
                    DropdownMenuItem(
                        text = { Text("Unlink All") },
                        onClick = { vm.onMenuOptionUnlink() },
                        leadingIcon = { Icon(Icons.Filled.Clear, contentDescription = "Unlink all from this bubble") }
                    )
                    val currentBubbleForMenu = bubbles.find { it.id == bubbleMenuState.bubbleId }
                    if (currentBubbleForMenu?.documentId != null) {
                        DropdownMenuItem(
                            text = { Text("View Content") },
                            onClick = { vm.onMenuOptionView() },
                            leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = "View bubble content") }
                        )
                    }
                }
            }
        }

        if (backgroundMenuState.visible) {
            val menuPositionDpOffset = with(density) {
                DpOffset(backgroundMenuState.screenPosition.x.toDp(), backgroundMenuState.screenPosition.y.toDp())
            }
            MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = ShapeDefaults.ExtraSmall)) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { vm.closeBackgroundContextMenu() },
                    offset = menuPositionDpOffset,
                    properties = PopupProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Bubble Here") },
                        onClick = { vm.onMenuOptionAddBubble() },
                        leadingIcon = { Icon(Icons.Filled.AddCircle, contentDescription = "Add new bubble") }
                    )
                }
            }
        }

        // Dialogue de renommage
        if (renameDialogState.visible) {
            var textFieldValue by remember(renameDialogState.currentName) {
                mutableStateOf(TextFieldValue(renameDialogState.currentName))
            }
            AlertDialog(
                onDismissRequest = { vm.onRenameDialogDismiss() },
                title = { Text("Rename Bubble") },
                text = {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        label = { Text("New name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.onRenameDialogConfirm(textFieldValue.text)
                    }) {
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
    }
}