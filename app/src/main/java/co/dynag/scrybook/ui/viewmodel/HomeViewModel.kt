package co.dynag.scrybook.ui.viewmodel

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.dynag.scrybook.data.model.ProjectFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _projects = MutableStateFlow<List<ProjectFile>>(emptyList())
    val projects: StateFlow<List<ProjectFile>> = _projects

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val prefs = context.getSharedPreferences("scrybook_recent", Context.MODE_PRIVATE)

    init {
        scanForProjects()
    }

    private fun getRecentPaths(): Set<String> {
        return prefs.getStringSet("recent_paths", emptySet()) ?: emptySet()
    }

    private fun getUriForPath(path: String): String? {
        return prefs.getString("uri_$path", null)
    }

    fun addToRecent(path: String, uri: String? = null) {
        val current = getRecentPaths().toMutableSet()
        current.add(path)
        val editor = prefs.edit().putStringSet("recent_paths", current)
        if (uri != null) editor.putString("uri_$path", uri)
        editor.apply()
        scanForProjects()
    }

    /** Dossier par défaut: espace privé de l'appli — accessible sans aucune permission */
    fun defaultProjectDir(): String {
        val dir = context.getExternalFilesDir("ScryBook")
            ?: File(context.filesDir, "ScryBook")
        dir.mkdirs()
        return dir.absolutePath
    }

    fun scanForProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            _projects.value = withContext(Dispatchers.IO) {
                val found = mutableListOf<ProjectFile>()
                
                // 1. D'abord les dossiers connus (scan automatique)
                val searchDirs = buildList {
                    context.getExternalFilesDir("ScryBook")?.let { add(it) }
                    add(File(context.filesDir, "ScryBook"))
                    try {
                        val extRoot = Environment.getExternalStorageDirectory()
                        if (extRoot.canRead()) {
                            add(extRoot)
                            add(File(extRoot, "ScryBook"))
                            add(File(extRoot, "Cloud"))
                            add(File(extRoot, "Nextcloud"))
                            add(File(extRoot, "Syncthing"))
                        }
                    } catch (_: Exception) {}
                }

                for (dir in searchDirs) {
                    if (dir.exists() && dir.canRead()) {
                        try {
                            dir.walkTopDown()
                                .maxDepth(8)
                                .filter { it.isFile && it.extension == "sb" }
                                .forEach { file ->
                                    if (found.none { it.path == file.absolutePath }) {
                                        found.add(ProjectFile(file.nameWithoutExtension, file.absolutePath, file.lastModified()))
                                    }
                                }
                        } catch (_: Exception) {}
                    }
                }

                // 2. Ajouter les fichiers ouverts manuellement qui ne sont pas dans les dossiers scannés
                val recentPaths = getRecentPaths()
                val validRecents = mutableSetOf<String>()
                
                recentPaths.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        validRecents.add(path)
                        if (found.none { it.path == file.absolutePath }) {
                            found.add(ProjectFile(file.nameWithoutExtension, file.absolutePath, file.lastModified()))
                        }
                    }
                }

                // Sauvegarder la liste nettoyée
                if (validRecents.size != recentPaths.size) {
                    prefs.edit().putStringSet("recent_paths", validRecents).apply()
                }

                found.sortedByDescending { it.lastModified }
            }
            _isLoading.value = false
        }
    }

    fun createProject(dirPath: String, name: String): String? {
        return try {
            val dir = File(dirPath)
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    _error.value = "Impossible de créer le dossier :\n$dirPath"
                    return null
                }
            }
            val file = File(dir, "$name.sb")
            if (!file.exists()) file.createNewFile()
            addToRecent(file.absolutePath)
            file.absolutePath
        } catch (e: Exception) {
            _error.value = "Erreur : ${e.message}"
            null
        }
    }

    fun clearError() { _error.value = null }

    fun formatDate(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}
