package org.familytools.educationtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.familytools.educationtracker.data.Child
import org.familytools.educationtracker.data.ChildRepository

class ChildViewModel(private val repository: ChildRepository) : ViewModel() {

    val children: StateFlow<List<Child>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editing = MutableStateFlow<Child?>(null)
    val editing: StateFlow<Child?> = _editing

    fun startNewChild() {
        _editing.value = Child()
    }

    fun startEditingChild(child: Child) {
        _editing.value = child
    }

    fun stopEditing() {
        _editing.value = null
    }

    fun save(child: Child, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.save(child)
            onSaved()
        }
    }

    fun delete(child: Child, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(child)
            onDeleted()
        }
    }

    companion object {
        fun factory(repository: ChildRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ChildViewModel(repository) as T
            }
        }
    }
}
