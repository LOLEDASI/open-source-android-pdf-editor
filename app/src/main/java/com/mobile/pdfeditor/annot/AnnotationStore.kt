package com.mobile.pdfeditor.annot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnnotationSnapshot(
    val strokes: List<InkStroke> = emptyList(),
    val stamps: List<SignatureStamp> = emptyList(),
    val texts: List<TextBox> = emptyList(),
    val forms: Map<String, String> = emptyMap(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Changed since the last load or save. */
    val dirty: Boolean = false,
)

/**
 * The document's ink strokes and signatures with undo/redo. Each edit stores the whole
 * (immutable) [Annotations] value, which stays cheap for hand-made markup.
 */
class AnnotationStore {
    private val undoStack = ArrayDeque<Annotations>()
    private val redoStack = ArrayDeque<Annotations>()
    private var saved = Annotations()
    private var nextId = 1L

    var current: Annotations = Annotations()
        private set

    private val _state = MutableStateFlow(AnnotationSnapshot())
    val state: StateFlow<AnnotationSnapshot> = _state.asStateFlow()

    val strokes: List<InkStroke> get() = current.strokes
    val stamps: List<SignatureStamp> get() = current.stamps
    val texts: List<TextBox> get() = current.texts

    fun newId(): Long = nextId++

    /** Replace everything with what was read from the file; clears history. */
    fun load(
        strokes: List<InkStroke>,
        stamps: List<SignatureStamp> = emptyList(),
        texts: List<TextBox> = emptyList(),
        forms: Map<String, String> = emptyMap(),
    ) {
        undoStack.clear()
        redoStack.clear()
        current = Annotations(strokes, stamps, texts, forms)
        saved = current
        nextId = ((strokes.map { it.id } + stamps.map { it.id } + texts.map { it.id }).maxOrNull() ?: 0L) + 1
        publish()
    }

    fun add(stroke: InkStroke) = commit(current.copy(strokes = current.strokes + stroke))

    fun addStamp(stamp: SignatureStamp) = commit(current.copy(stamps = current.stamps + stamp))

    fun moveStamp(id: Long, rect: PageRect) {
        val stamp = current.stamps.firstOrNull { it.id == id } ?: return
        if (stamp.rect == rect) return
        commit(current.copy(stamps = current.stamps.map { if (it.id == id) it.copy(rect = rect) else it }))
    }

    /** Sets one form field's value as one undo step. */
    fun setForm(name: String, value: String) {
        if (current.forms[name] == value) return
        commit(current.copy(forms = current.forms + (name to value)))
    }

    /** Adds [box], or replaces the box with the same id, as one undo step. */
    fun putText(box: TextBox) {
        val exists = current.texts.any { it.id == box.id }
        val next = if (exists) current.texts.map { if (it.id == box.id) box else it } else current.texts + box
        if (next != current.texts) commit(current.copy(texts = next))
    }

    /** Removes strokes, stamps and/or text boxes with these ids as one undo step. */
    fun remove(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val next = Annotations(
            strokes = current.strokes.filterNot { it.id in ids },
            stamps = current.stamps.filterNot { it.id in ids },
            texts = current.texts.filterNot { it.id in ids },
            forms = current.forms,
        )
        if (next != current) commit(next)
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        current = prev
        publish()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        current = next
        publish()
    }

    fun markSaved() {
        saved = current
        publish()
    }

    private fun commit(next: Annotations) {
        undoStack.addLast(current)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        current = next
        publish()
    }

    private fun publish() {
        _state.value = AnnotationSnapshot(
            strokes = current.strokes,
            stamps = current.stamps,
            texts = current.texts,
            forms = current.forms,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            dirty = current != saved,
        )
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}
