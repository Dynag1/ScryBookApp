package co.dynag.scrybook.data.repository

import android.content.Context
import co.dynag.scrybook.data.db.ScryBookDatabase
import co.dynag.scrybook.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ScryBookRepository(private val context: Context) {

    private var dbHelper: ScryBookDatabase? = null
    private var currentPath: String = ""
    private var originalUri: String? = null

    private val _currentParam = kotlinx.coroutines.flow.MutableStateFlow(Param())
    val currentParam: kotlinx.coroutines.flow.StateFlow<Param> = _currentParam

    fun openProject(path: String, uri: String? = null) {
        if (currentPath != path) {
            dbHelper?.close()
            dbHelper = ScryBookDatabase.open(context, path)
            currentPath = path
            
            val prefs = context.getSharedPreferences("scrybook_recent", Context.MODE_PRIVATE)
            
            // If uri is provided, save it immediately
            if (uri != null) {
                originalUri = uri
                prefs.edit().putString("uri_$path", uri).apply()
            } else {
                // Try to get URI from prefs if not provided
                originalUri = prefs.getString("uri_$path", null)
            }
            
            // Refresh params
            _currentParam.value = dbHelper?.getParam() ?: Param()
        } else if (uri != null) {
            // Update URI even if path is same (refreshed intent)
            originalUri = uri
            context.getSharedPreferences("scrybook_recent", Context.MODE_PRIVATE)
                .edit().putString("uri_$path", uri).apply()
        }
    }

    /** Export the local database file back to the original URI (SAF) */
    fun syncBack() {
        val uriStr = originalUri ?: return
        try {
            android.util.Log.d("ScryBookRepo", "Syncing back to: $uriStr")
            dbHelper?.checkpoint()
            val uri = android.net.Uri.parse(uriStr)
            val file = File(currentPath)
            if (!file.exists()) return

            context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScryBookRepo", "Sync failed: ${e.message}")
        }
    }

    fun closeProject() {
        dbHelper?.close()
        dbHelper = null
        currentPath = ""
    }

    fun createProject(path: String, isEtude: Boolean = false) {
        dbHelper?.close()
        dbHelper = ScryBookDatabase.open(context, path, isEtude)
        currentPath = path
        dbHelper!!.writableDatabase // triggers onCreate
    }

    // ─── Chapitres ────────────────────────────────────────────────────────────

    suspend fun getChapitres(): List<Chapitre> = withContext(Dispatchers.IO) {
        dbHelper?.getChapitres() ?: emptyList()
    }

    suspend fun getChapitre(id: Long): Chapitre? = withContext(Dispatchers.IO) {
        dbHelper?.getChapitre(id)
    }

    suspend fun insertChapitre(nom: String, numero: String, resume: String): Long = withContext(Dispatchers.IO) {
        val id = dbHelper?.insertChapitre(nom, numero, resume) ?: -1L
        if (id != -1L) syncBack()
        id
    }

    suspend fun updateChapitre(id: Long, nom: String, numero: String, resume: String) = withContext(Dispatchers.IO) {
        dbHelper?.updateChapitre(id, nom, numero, resume)
        syncBack()
    }

    suspend fun saveChapitreContenu(id: Long, html: String) = withContext(Dispatchers.IO) {
        dbHelper?.saveChapitreContenu(id, html)
        syncBack()
    }

    suspend fun deleteChapitre(id: Long) = withContext(Dispatchers.IO) {
        dbHelper?.deleteChapitre(id)
        syncBack()
    }

    // ─── Personnages ──────────────────────────────────────────────────────────

    suspend fun getPersonnages(): List<Personnage> = withContext(Dispatchers.IO) {
        dbHelper?.getPersonnages() ?: emptyList()
    }

    suspend fun insertPersonnage(p: Personnage): Long = withContext(Dispatchers.IO) {
        val id = dbHelper?.insertPersonnage(p) ?: -1L
        if (id != -1L) syncBack()
        id
    }

    suspend fun updatePersonnage(p: Personnage) = withContext(Dispatchers.IO) {
        dbHelper?.updatePersonnage(p)
        syncBack()
    }

    suspend fun deletePersonnage(id: Long) = withContext(Dispatchers.IO) {
        dbHelper?.deletePersonnage(id)
        syncBack()
    }

    // ─── Lieux ────────────────────────────────────────────────────────────────

    suspend fun getLieux(): List<Lieu> = withContext(Dispatchers.IO) {
        dbHelper?.getLieux() ?: emptyList()
    }

    suspend fun insertLieu(nom: String, desc: String): Long = withContext(Dispatchers.IO) {
        val id = dbHelper?.insertLieu(nom, desc) ?: -1L
        if (id != -1L) syncBack()
        id
    }

    suspend fun updateLieu(id: Long, nom: String, desc: String) = withContext(Dispatchers.IO) {
        dbHelper?.updateLieu(id, nom, desc)
        syncBack()
    }

    suspend fun deleteLieu(id: Long) = withContext(Dispatchers.IO) {
        dbHelper?.deleteLieu(id)
        syncBack()
    }

    // ─── Info ─────────────────────────────────────────────────────────────────

    suspend fun getInfo(): Info = withContext(Dispatchers.IO) {
        dbHelper?.getInfo() ?: Info()
    }

    suspend fun saveInfo(info: Info) = withContext(Dispatchers.IO) {
        dbHelper?.saveInfo(info)
        syncBack()
    }

    // ─── Param ────────────────────────────────────────────────────────────────

    suspend fun getParam(): Param = withContext(Dispatchers.IO) {
        dbHelper?.getParam() ?: Param()
    }

    suspend fun saveParam(param: Param) = withContext(Dispatchers.IO) {
        dbHelper?.saveParam(param)
        syncBack()
        _currentParam.value = param
    }

    suspend fun saveAsTemplate() = withContext(Dispatchers.IO) {
        val json = dbHelper?.getChaptersAsJson() ?: return@withContext
        context.getSharedPreferences("scrybook_recent", Context.MODE_PRIVATE)
            .edit().putString("chapitres_auto", json).apply()
    }
}
