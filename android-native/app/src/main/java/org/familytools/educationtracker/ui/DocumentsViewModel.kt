package org.familytools.educationtracker.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.familytools.educationtracker.data.Child
import org.familytools.educationtracker.data.ChildDao
import org.familytools.educationtracker.data.ChildDocument
import org.familytools.educationtracker.data.MiscDao
import java.io.File

class DocumentsViewModel(
    private val miscDao: MiscDao,
    private val childDao: ChildDao,
) : ViewModel() {
    val children: StateFlow<List<Child>> = childDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChildId = MutableStateFlow<Long?>(null)
    val selectedChildId: StateFlow<Long?> = _selectedChildId

    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<ChildDocument>> = _selectedChildId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else miscDao.observeDocumentsForChild(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectChild(id: Long) { _selectedChildId.value = id }

    /** Copies [sourceUri]'s bytes into this app's own storage before
     * recording it — a gallery-picked or camera-cache URI can go stale
     * (revoked permission, cache cleared), but a "saved certificate" needs
     * to survive that. */
    fun addDocument(
        context: Context, childId: Long, academicYear: String, category: String, title: String,
        sourceUri: Uri, onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        if (academicYear.isBlank()) { onError("Academic year is required"); return }
        viewModelScope.launch {
            try {
                val savedPath = withContext(Dispatchers.IO) { copyIntoAppStorage(context, sourceUri, category) }
                miscDao.insertChildDocument(
                    ChildDocument(
                        childId = childId, academicYear = academicYear.trim(), category = category,
                        title = title.trim(), imagePath = savedPath,
                    ),
                )
                onDone()
            } catch (e: Exception) {
                onError("Couldn't save: ${e.message}")
            }
        }
    }

    fun deleteDocument(document: ChildDocument) {
        viewModelScope.launch {
            miscDao.deleteChildDocument(document.id)
            withContext(Dispatchers.IO) { runCatching { File(document.imagePath).delete() } }
        }
    }

    private fun copyIntoAppStorage(context: Context, sourceUri: Uri, category: String): String {
        val dir = File(context.getExternalFilesDir(null), "documents").apply { mkdirs() }
        val prefix = if (category == "CLASS_PHOTO") "classphoto" else "certificate"
        val dest = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Couldn't read the selected image")
        return dest.absolutePath
    }

    companion object {
        fun factory(miscDao: MiscDao, childDao: ChildDao) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return DocumentsViewModel(miscDao, childDao) as T
                }
            }
    }
}
