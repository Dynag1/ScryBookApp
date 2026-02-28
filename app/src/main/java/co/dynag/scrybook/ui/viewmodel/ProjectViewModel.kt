package co.dynag.scrybook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.dynag.scrybook.data.model.Chapitre
import co.dynag.scrybook.data.model.Info
import co.dynag.scrybook.data.repository.ScryBookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val repository: ScryBookRepository
) : ViewModel() {

    private val _chapitres = MutableStateFlow<List<Chapitre>>(emptyList())
    val chapitres: StateFlow<List<Chapitre>> = _chapitres

    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _showNewChapterDialog = MutableStateFlow(false)
    val showNewChapterDialog: StateFlow<Boolean> = _showNewChapterDialog

    fun loadProject(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.openProject(path)
            _chapitres.value = repository.getChapitres()
            _info.value = repository.getInfo()
            _isLoading.value = false
        }
    }

    fun refresh(path: String) {
        viewModelScope.launch {
            _chapitres.value = repository.getChapitres()
            _info.value = repository.getInfo()
        }
    }

    fun addChapitre(nom: String, numero: String, resume: String) {
        viewModelScope.launch {
            repository.insertChapitre(nom, numero, resume)
            repository.syncBack()
            _chapitres.value = repository.getChapitres()
            _showNewChapterDialog.value = false
        }
    }

    fun deleteChapitre(id: Long) {
        viewModelScope.launch {
            repository.deleteChapitre(id)
            repository.syncBack()
            _chapitres.value = repository.getChapitres()
        }
    }

    fun showNewChapterDialog() { _showNewChapterDialog.value = true }
    fun dismissNewChapterDialog() { _showNewChapterDialog.value = false }

    fun updateChapitreInfo(id: Long, nom: String, numero: String, resume: String) {
        viewModelScope.launch {
            repository.updateChapitre(id, nom, numero, resume)
            repository.syncBack()
            _chapitres.value = repository.getChapitres()
        }
    }
}
