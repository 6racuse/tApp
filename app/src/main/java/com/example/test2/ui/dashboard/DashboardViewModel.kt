package com.example.test2.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.ui.model.Bubble
import com.example.test2.ui.model.Link
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// État pour le menu contextuel sur une bulle
data class BubbleContextMenuState(
    val visible: Boolean = false,
    val bubbleId: String? = null,
    val screenPosition: Offset = Offset.Zero
)

// État pour le menu contextuel sur le fond (pour ajouter une bulle)
data class BackgroundContextMenuState(
    val visible: Boolean = false,
    val worldPosition: Offset = Offset.Zero,
    val screenPosition: Offset = Offset.Zero
)

// Nouvel état pour le dialogue de renommage
data class RenameDialogState(
    val visible: Boolean = false,
    val bubbleIdToRename: String? = null,
    val currentName: String = ""
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DashboardViewModel"

    private val _bubbles = MutableStateFlow<List<Bubble>>(emptyList())
    val bubbles: StateFlow<List<Bubble>> = _bubbles.asStateFlow()

    private val _links = MutableStateFlow<List<Link>>(emptyList())
    val links: StateFlow<List<Link>> = _links.asStateFlow()

    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _offset = MutableStateFlow(Offset.Zero)
    val offset: StateFlow<Offset> = _offset.asStateFlow()

    // --- Gestion du menu contextuel sur une BULLE ---
    private val _bubbleContextMenuState = MutableStateFlow(BubbleContextMenuState())
    val bubbleContextMenuState: StateFlow<BubbleContextMenuState> = _bubbleContextMenuState.asStateFlow()

    // --- Gestion du menu contextuel sur le FOND ---
    private val _backgroundContextMenuState = MutableStateFlow(BackgroundContextMenuState())
    val backgroundContextMenuState: StateFlow<BackgroundContextMenuState> = _backgroundContextMenuState.asStateFlow()

    // --- État pour le dialogue de renommage ---
    private val _renameDialogState = MutableStateFlow(RenameDialogState())
    val renameDialogState: StateFlow<RenameDialogState> = _renameDialogState.asStateFlow()

    private val _selectedContentBubbleId = MutableStateFlow<String?>(null)
    val selectedContentBubbleId: StateFlow<String?> = _selectedContentBubbleId.asStateFlow()

    private var isLinkingModeActive = false
    private var pendingLinkFromBubbleIdByMenu: String? = null
    private var simulationJob: Job? = null


    // Paramètres de simulation
    private val repulsionStrength = 2000f
    private val minDistanceFactor = 1.1f
    private val dampingFactor = 0.85f
    private val maxSpeed = 10f
    private val floatStrength = 0.5f

    private val gson = Gson()
    private val dataFileName = "bubbles_data.json"

    private data class PersistBubble(
        val id: String,
        val name: String,
        val radius: Float,
        val velocityX: Float,
        val velocityY: Float,
        val positionX: Float,
        val positionY: Float,
        val documentId: String?,
        val content: String
    )
    private data class PersistLink(val fromId: String, val toId: String)
    private data class PersistData(val bubbles: List<PersistBubble>, val links: List<PersistLink>)

    init {
        loadData()
        startContinuousSimulation()
        Log.d(TAG, "ViewModel initialized with ${bubbles.value.size} bubbles.")
    }

    private fun loadData() {
        val file = File(getApplication<Application>().filesDir, dataFileName)
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<PersistData>() {}.type
                val data: PersistData = gson.fromJson(json, type)
                _bubbles.value = data.bubbles.map { pb ->
                    Bubble(
                        id         = pb.id,
                        name       = pb.name,
                        radius     = pb.radius,
                        velocity   = Offset(pb.velocityX, pb.velocityY),
                        position   = Offset(pb.positionX, pb.positionY),
                        documentId = pb.documentId
                    )
                }
                _links.value = data.links.map { pl ->
                    Link(fromId = pl.fromId, toId = pl.toId)
                }
                Log.d(TAG, "Loaded persisted data")
            } catch (e: Exception) {
                Log.e(TAG, "Load error – using defaults", e)
                setDefaultData()
            }
        } else {
            setDefaultData()
        }
    }

    private fun setDefaultData() {
        _bubbles.value = listOf(
            Bubble(id = "bubble1", position = Offset(200f,200f), name = "Alpha", radius = 50f, velocity = Offset(0.2f,0.2f) ,documentId = "doc_alpha",content="Contenu"),
            Bubble(id = "bubble2", position = Offset(300f,250f), name = "Beta",  radius = 60f, velocity = Offset(0.2f,0.2f) ,documentId = "doc_beta" ,content="Contenu"),
            Bubble(id = "bubble3", position = Offset(250f,400f), name = "Gamma", radius = 40f, velocity = Offset(0.2f,0.2f) ,documentId = "doc_gamma",content="Contenu")
        )
        _links.value = emptyList()
        saveData()
    }

    private fun saveData() {
        val data = PersistData(
            bubbles = _bubbles.value.map { b ->
                PersistBubble(
                    id         = b.id,
                    name       = b.name,
                    radius     = b.radius,
                    velocityX  = b.velocity.x,
                    velocityY  = b.velocity.y,
                    positionX  = b.position.x,
                    positionY  = b.position.y,
                    documentId = b.documentId,
                    content    = b.content
                )
            },
            links = _links.value.map { l ->
                PersistLink(fromId = l.fromId, toId = l.toId)
            }
        )
        val json = gson.toJson(data)
        File(getApplication<Application>().filesDir, dataFileName).writeText(json)
        Log.d(TAG, "Saved data")
    }
    // --- Fonctions du menu contextuel sur une BULLE ---
    fun openBubbleContextMenu(bubbleId: String, screenPos: Offset) {
        closeBackgroundContextMenu()
        _bubbleContextMenuState.value = BubbleContextMenuState(visible = true, bubbleId = bubbleId, screenPosition = screenPos)
        isLinkingModeActive = false
        pendingLinkFromBubbleIdByMenu = null
        Log.d(TAG, "Bubble context menu opened for $bubbleId")
    }

    fun closeBubbleContextMenu() {
        if (_bubbleContextMenuState.value.visible) {
            _bubbleContextMenuState.value = BubbleContextMenuState(visible = false)
            Log.d(TAG, "Bubble context menu closed.")
        }
    }

    fun onMenuOptionLink() {
        pendingLinkFromBubbleIdByMenu = _bubbleContextMenuState.value.bubbleId
        if (pendingLinkFromBubbleIdByMenu != null) {
            isLinkingModeActive = true
            Log.d(TAG, "Link mode activated from bubble: $pendingLinkFromBubbleIdByMenu")
        } else {
            Log.w(TAG, "onMenuOptionLink called but no bubbleId in context menu state.")
        }
        closeBubbleContextMenu()
        saveData()
    }

    fun onMenuOptionUnlink() {
        val bubbleIdToUnlink = _bubbleContextMenuState.value.bubbleId
        if (bubbleIdToUnlink != null) {
            val initialLinkCount = _links.value.size
            _links.value = _links.value.filterNot { it.fromId == bubbleIdToUnlink || it.toId == bubbleIdToUnlink }
            Log.d(TAG, "Unlinked all from $bubbleIdToUnlink. Links removed: ${initialLinkCount - _links.value.size}")
        }
        closeBubbleContextMenu()
    }

    fun onMenuOptionView() {
        val bubbleIdToView = _bubbleContextMenuState.value.bubbleId
        val bubble = _bubbles.value.find { it.id == bubbleIdToView }
        if (bubble?.documentId != null) {
            Log.i(TAG, "View document: ${bubble.documentId} for bubble: ${bubble.name}")
            // TODO: Implémenter la logique de navigation ou d'affichage du document réel.
        } else {
            Log.w(TAG, "View option selected, but no documentId for bubble $bubbleIdToView")
        }
        closeBubbleContextMenu()
    }

    fun onMenuOptionViewContent() {
        val id = _bubbleContextMenuState.value.bubbleId
        if (id != null) _selectedContentBubbleId.value = id
        closeBubbleContextMenu()
    }
    fun clearContentSelection() {
        _selectedContentBubbleId.value = null
    }

    fun onMenuOptionRename() {
        val bubbleId = _bubbleContextMenuState.value.bubbleId
        val bubble = _bubbles.value.find { it.id == bubbleId }
        if (bubble != null) {
            _renameDialogState.value = RenameDialogState(
                visible = true,
                bubbleIdToRename = bubble.id,
                currentName = bubble.name
            )
            Log.d(TAG, "Rename dialog opened for bubble: ${bubble.name} (ID: ${bubble.id})")
        } else {
            Log.w(TAG, "Rename option selected, but bubble not found: $bubbleId")
        }
        closeBubbleContextMenu()
    }

    // --- Fonctions pour le dialogue de renommage ---
    fun onRenameDialogDismiss() {
        _renameDialogState.value = RenameDialogState(visible = false)
        Log.d(TAG, "Rename dialog dismissed.")
    }

    fun onRenameDialogConfirm(newName: String) {
        val bubbleId = _renameDialogState.value.bubbleIdToRename
        if (bubbleId != null && newName.isNotBlank()) {
            _bubbles.value = _bubbles.value.map { bubble ->
                if (bubble.id == bubbleId) {
                    bubble.copy(name = newName)
                } else {
                    bubble
                }
            }
            Log.i(TAG, "Bubble $bubbleId renamed to: $newName")
        } else {
            Log.w(TAG, "Rename confirmation failed. BubbleId: $bubbleId, NewName: '$newName'")
        }
        onRenameDialogDismiss() // Fermer le dialogue après confirmation
        saveData()
    }

    // --- Fonctions du menu contextuel sur le FOND ---
    fun openBackgroundContextMenu(worldPos: Offset, screenPos: Offset) {
        closeBubbleContextMenu()
        _backgroundContextMenuState.value = BackgroundContextMenuState(visible = true, worldPosition = worldPos, screenPosition = screenPos)
        Log.d(TAG, "Background context menu opened at world: $worldPos, screen: $screenPos")
    }

    fun closeBackgroundContextMenu() {
        if (_backgroundContextMenuState.value.visible) {
            _backgroundContextMenuState.value = BackgroundContextMenuState(visible = false)
            Log.d(TAG, "Background context menu closed.")
        }
    }

    fun onMenuOptionAddBubble() {
        val newBubblePosition = _backgroundContextMenuState.value.worldPosition
        val newBubbleName = "Bubble ${_bubbles.value.size + 1}"
        val newBubbleId = UUID.randomUUID().toString()

        val newBubble = Bubble(
            id = newBubbleId,
            position = newBubblePosition,
            name = newBubbleName,
            radius = 45f,
            documentId = "doc_for_$newBubbleId" // Assigner un documentId par défaut
        )
        _bubbles.value = _bubbles.value + newBubble
        Log.i(TAG, "Added new bubble: $newBubbleName at $newBubblePosition with docId: ${newBubble.documentId}")
        closeBackgroundContextMenu()
        viewModelScope.launch {
            applyRepulsionAndFloat()
        }
        saveData()
    }

    // --- Gestion des liens ---
    fun tryFinishLink(targetBubbleId: String) {
        Log.d(TAG, "tryFinishLink called with target: $targetBubbleId. Linking mode: ${isLinkingModeActive}, Source: $pendingLinkFromBubbleIdByMenu")
        if (isLinkingModeActive) {
            val fromId = pendingLinkFromBubbleIdByMenu
            if (fromId != null && targetBubbleId.isNotBlank() && fromId != targetBubbleId) {
                val linkExists = _links.value.any {
                    (it.fromId == fromId && it.toId == targetBubbleId) ||
                            (it.fromId == targetBubbleId && it.toId == fromId)
                }
                if (!linkExists) {
                    val newLink = Link(fromId = fromId, toId = targetBubbleId)
                    _links.value = _links.value + newLink
                    Log.i(TAG, "Link created: $newLink. Total links: ${_links.value.size}")
                } else {
                    Log.d(TAG, "Link between $fromId and $targetBubbleId already exists.")
                }
            } else if (fromId == null) {
                Log.w(TAG, "Cannot finish link: Source bubble ID is null.")
            } else if (targetBubbleId.isBlank()){
                Log.d(TAG, "Link cancelled by tapping on background.")
            } else if (fromId == targetBubbleId) {
                Log.d(TAG, "Cannot link a bubble to itself.")
            }
            pendingLinkFromBubbleIdByMenu = null
            isLinkingModeActive = false
            Log.d(TAG, "Linking mode deactivated.")
        }
        saveData()
    }

    // --- Simulation et Mouvement ---
    private fun startContinuousSimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (true) {
                applyRepulsionAndFloat()
                delay(32) // ~30 FPS
            }
        }
        Log.d(TAG, "Continuous simulation started.")
    }

    fun onBubbleDrag(bubbleId: String, dragAmount: Offset) {
        if (bubbleContextMenuState.value.visible && bubbleContextMenuState.value.bubbleId == bubbleId) {
            Log.d(TAG, "Drag ignored for $bubbleId as context menu is open.")
            return
        }
        simulationJob?.cancel()
        _bubbles.value = _bubbles.value.map { bubble ->
            if (bubble.id == bubbleId) {
                bubble.copy(position = bubble.position + dragAmount, velocity = Offset.Zero)
            } else {
                bubble
            }
        }
        viewModelScope.launch {
            applyRepulsionAndFloat()
            startContinuousSimulation()
        }
        saveData()
    }

    fun onPan(dragAmount: Offset) {
        _offset.value += dragAmount
    }

    fun onZoom(zoomFactor: Float, centroid: Offset) {
        val currentScale = _scale.value
        val newScale = (currentScale * zoomFactor).coerceIn(0.1f, 10f)
        val worldPointUnderCentroid = (centroid - _offset.value) / currentScale
        val newOffset = centroid - (worldPointUnderCentroid * newScale)
        _scale.value = newScale
        _offset.value = newOffset
    }

    private fun applyRepulsionAndFloat() {
        val currentBubbles = _bubbles.value.toList()
        if (currentBubbles.isEmpty()) return

        val forces = MutableList(currentBubbles.size) { Offset.Zero }

        for (i in currentBubbles.indices) {
            for (j in i + 1 until currentBubbles.size) {
                val bubbleA = currentBubbles[i]
                val bubbleB = currentBubbles[j]
                val delta = bubbleB.position - bubbleA.position
                var distance = delta.getDistance()
                if (distance == 0f) distance = 0.1f

                val minAllowedDistance = (bubbleA.radius + bubbleB.radius) * minDistanceFactor
                if (distance < minAllowedDistance) {
                    val overlap = minAllowedDistance - distance
                    val strength = (repulsionStrength * overlap) / distance
                    val forceDirection = delta.normalized()
                    forces[i] -= forceDirection * strength
                    forces[j] += forceDirection * strength
                }
            }
        }

        for (i in currentBubbles.indices) {
            val randomAngle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val floatForceX = cos(randomAngle) * floatStrength
            val floatForceY = sin(randomAngle) * floatStrength
            forces[i] += Offset(floatForceX, floatForceY)
        }

        val newBubbles = currentBubbles.mapIndexed { index, bubble ->
            var newVelocity = (bubble.velocity + forces[index]) * dampingFactor
            if (newVelocity.getDistanceSquared() > maxSpeed * maxSpeed) {
                newVelocity = newVelocity.normalized() * maxSpeed
            }
            bubble.copy(
                position = bubble.position + newVelocity,
                velocity = newVelocity
            )
        }
        if (_bubbles.value != newBubbles) {
            _bubbles.value = newBubbles
        }
    }

    private fun Offset.normalized(): Offset {
        val mag = getDistance()
        return if (mag == 0f) Offset.Zero else this / mag
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        saveData()
        Log.d(TAG, "ViewModel cleared, simulation job cancelled.")
    }
}