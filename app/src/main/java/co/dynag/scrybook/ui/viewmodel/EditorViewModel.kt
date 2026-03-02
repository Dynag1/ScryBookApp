package co.dynag.scrybook.ui.viewmodel

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.dynag.scrybook.data.model.Chapitre
import co.dynag.scrybook.data.repository.ScryBookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: ScryBookRepository,
    @ApplicationContext private val context: Context
) : ViewModel(), TextToSpeech.OnInitListener {

    private val _chapitre = MutableStateFlow<Chapitre?>(null)
    val chapitre: StateFlow<Chapitre?> = _chapitre

    private val _chapitres = MutableStateFlow<List<Chapitre>>(emptyList())
    val chapitres: StateFlow<List<Chapitre>> = _chapitres

    private val _param = MutableStateFlow(co.dynag.scrybook.data.model.Param())
    val param: StateFlow<co.dynag.scrybook.data.model.Param> = _param

    private val _htmlContent = MutableStateFlow("")
    val htmlContent: StateFlow<String> = _htmlContent

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _isTtsPlaying = MutableStateFlow(false)
    val isTtsPlaying: StateFlow<Boolean> = _isTtsPlaying

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady

    private var tts: TextToSpeech? = null
    private var currentPath = ""
    private var currentChapterId = 0L

    // Autosave timer
    private var lastContent = ""
    private val saveMutex = Mutex()

    init {
        tts = TextToSpeech(context, this)
        startAutoSave()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.FRENCH
            _ttsReady.value = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _isTtsPlaying.value = true }
                override fun onDone(utteranceId: String?) { _isTtsPlaying.value = false }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { _isTtsPlaying.value = false }
            })
        }
    }

    fun loadChapitre(projectPath: String, chapterId: Long) {
        // Save previous content if needed before switching
        if (currentChapterId != 0L && _htmlContent.value != lastContent) {
            saveNow()
        }

        currentPath = projectPath
        currentChapterId = chapterId
        viewModelScope.launch {
            try {
                repository.openProject(projectPath)
                _param.value = repository.getParam()

                val ch = repository.getChapitre(chapterId)
                _chapitre.value = ch
                _htmlContent.value = ch?.contenuHtml ?: ""
                lastContent = _htmlContent.value
                
                // Also load all chapters for the drawer
                _chapitres.value = repository.getChapitres()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateContent(html: String) {
        _htmlContent.value = html
    }

    fun saveNow() {
        val idToSave = currentChapterId
        if (idToSave == 0L) return
        val contentToSave = _htmlContent.value
        if (contentToSave == lastContent) return

        viewModelScope.launch {
            withContext(Dispatchers.IO + NonCancellable) {
                saveMutex.withLock {
                    // Re-check after acquiring lock
                    if (contentToSave != lastContent) {
                        _isSaving.value = true
                        android.util.Log.d("EditorVM", "Triggering save for chapter $idToSave")
                        repository.saveChapitreContenu(idToSave, contentToSave)
                        repository.syncBack() // Propagation vers le fichier d'origine
                        lastContent = contentToSave
                        _isSaving.value = false
                        android.util.Log.d("EditorVM", "Save and sync complete")
                    } else {
                        android.util.Log.d("EditorVM", "Content unchanged, skipping save")
                    }
                }
            }
        }
    }

    fun addChapitre(nom: String, numero: String, resume: String) {
        viewModelScope.launch {
            repository.insertChapitre(nom, numero, resume)
            _chapitres.value = repository.getChapitres()
        }
    }

    fun updateChapitreInfo(id: Long, nom: String, numero: String, resume: String) {
        viewModelScope.launch {
            repository.updateChapitre(id, nom, numero, resume)
            // Refresh
            val ch = repository.getChapitre(id)
            if (currentChapterId == id) {
                _chapitre.value = ch
            }
            _chapitres.value = repository.getChapitres()
        }
    }

    private fun startAutoSave() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                if (_htmlContent.value != lastContent) saveNow()
            }
        }
    }

    // ─── TTS ─────────────────────────────────────────────────────────────────

    fun toggleTts(text: String) {
        if (_isTtsPlaying.value) {
            tts?.stop()
            _isTtsPlaying.value = false
        } else {
            val cleanText = text.replace(Regex("<[^>]*>"), " ").trim()
            if (cleanText.isNotBlank()) {
                tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "scrybook_tts")
            }
        }
    }

    override fun onCleared() {
        saveNow()
        tts?.shutdown()
        super.onCleared()
    }
}
