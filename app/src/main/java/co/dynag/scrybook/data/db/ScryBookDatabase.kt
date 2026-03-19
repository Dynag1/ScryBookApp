package co.dynag.scrybook.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import co.dynag.scrybook.data.model.*

/**
 * ScryBookDatabase — raw SQLite helper.
 * Keeps 100% compatibility with the .sb file format from the desktop app.
 * The .sb file IS the SQLite database file.
 */
class ScryBookDatabase(private val context: Context, dbPath: String, private val isEtude: Boolean = false) :
    SQLiteOpenHelper(context, dbPath, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 3

        // Table names
        const val TABLE_CHAPITRE = "chapitre"
        const val TABLE_PERSO = "perso"
        const val TABLE_LIEUX = "lieux"
        const val TABLE_INFO = "info"
        const val TABLE_PARAM = "param"

        fun open(context: Context, dbPath: String, isEtude: Boolean = false): ScryBookDatabase {
            return ScryBookDatabase(context, dbPath, isEtude)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            // Disable WAL mode to ensure all data is in the main .sb file for sync-back
            db.disableWriteAheadLogging()
            if (!db.isReadOnly) {
                db.execSQL("PRAGMA journal_mode=DELETE")
                db.execSQL("PRAGMA synchronous=FULL")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            try { db.execSQL("PRAGMA journal_mode=DELETE") } catch (e: Exception) {}
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CHAPITRE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nom TEXT NOT NULL DEFAULT '',
                numero TEXT NOT NULL DEFAULT '',
                resume TEXT NOT NULL DEFAULT '',
                contenu_html TEXT DEFAULT ''
            )""")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_PERSO (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                alias TEXT, nom TEXT, prenom TEXT, sexe TEXT,
                age INTEGER, desc_phys TEXT, desc_global TEXT, skill TEXT
            )""")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_LIEUX (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nom TEXT, desc TEXT
            )""")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_INFO (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                titre TEXT, stitre TEXT, auteur TEXT, date TEXT, resume TEXT, couverture TEXT
            )""")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_PARAM (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                police TEXT, taille TEXT, save_time TEXT, langue TEXT, theme TEXT, lic TEXT, format TEXT DEFAULT 'A4'
            )""")

        // Insert default rows
        db.execSQL("INSERT OR IGNORE INTO $TABLE_INFO (id, titre, auteur, date, resume) VALUES (1,'','','','')")
        db.execSQL("INSERT OR IGNORE INTO $TABLE_PARAM (id, police, taille, save_time, langue, theme) VALUES (1,'serif','16','30','fr','dark')")

        if (isEtude) {
            val prefs = context.getSharedPreferences("scrybook_recent", Context.MODE_PRIVATE)
            val templateJson = prefs.getString("chapitres_auto", null)

            if (!templateJson.isNullOrBlank()) {
                try {
                    val array = org.json.JSONArray(templateJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val nom = obj.optString("nom", "")
                        val numero = obj.optString("numero", "")
                        val resume = obj.optString("resume", "")
                        val contenuHtml = obj.optString("contenu_html", "")
                        db.execSQL("INSERT INTO $TABLE_CHAPITRE (nom, numero, resume, contenu_html) VALUES (?, ?, ?, ?)", arrayOf(nom, numero, resume, contenuHtml))
                    }
                    return // Modèle correctement chargé, on sort !
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PARAM ADD COLUMN format TEXT DEFAULT 'A4'")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE $TABLE_INFO ADD COLUMN couverture TEXT")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ─── Chapitres ──────────────────────────────────────────────────────────

    fun getChapitres(): List<Chapitre> {
        val list = mutableListOf<Chapitre>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, nom, numero, resume, COALESCE(contenu_html,'') FROM $TABLE_CHAPITRE ORDER BY CAST(numero AS INTEGER)", null)
        while (cursor.moveToNext()) {
            list.add(Chapitre(
                id = cursor.getLong(0),
                nom = cursor.getString(1) ?: "",
                numero = cursor.getString(2) ?: "",
                resume = cursor.getString(3) ?: "",
                contenuHtml = cursor.getString(4) ?: ""
            ))
        }
        cursor.close()
        return list
    }

    fun getChapitre(id: Long): Chapitre? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, nom, numero, resume, COALESCE(contenu_html,'') FROM $TABLE_CHAPITRE WHERE id=?", arrayOf(id.toString()))
        return if (cursor.moveToFirst()) {
            Chapitre(cursor.getLong(0), cursor.getString(1)?:"", cursor.getString(2)?:"", cursor.getString(3)?:"", cursor.getString(4)?:"").also { cursor.close() }
        } else { cursor.close(); null }
    }

    fun insertChapitre(nom: String, numero: String, resume: String): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("nom", nom); put("numero", numero)
            put("resume", resume); put("contenu_html", "")
        }
        return db.insert(TABLE_CHAPITRE, null, cv)
    }

    fun updateChapitre(id: Long, nom: String, numero: String, resume: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("nom", nom); put("numero", numero); put("resume", resume) }
        db.update(TABLE_CHAPITRE, cv, "id=?", arrayOf(id.toString()))
    }

    fun saveChapitreContenu(id: Long, html: String) {
        android.util.Log.d("ScryBookDB", "Saving chapter $id, content length: ${html.length}")
        val db = writableDatabase
        val cv = ContentValues().apply { put("contenu_html", html) }
        val count = db.update(TABLE_CHAPITRE, cv, "id=?", arrayOf(id.toString()))
        android.util.Log.d("ScryBookDB", "Update result: $count rows affected")
    }

    fun deleteChapitre(id: Long) {
        writableDatabase.delete(TABLE_CHAPITRE, "id=?", arrayOf(id.toString()))
    }

    // ─── Personnages ─────────────────────────────────────────────────────────

    fun getPersonnages(): List<Personnage> {
        val list = mutableListOf<Personnage>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, COALESCE(alias,''), COALESCE(nom,''), COALESCE(prenom,''), COALESCE(sexe,''), COALESCE(age,0), COALESCE(desc_phys,''), COALESCE(desc_global,''), COALESCE(skill,'') FROM $TABLE_PERSO", null)
        while (cursor.moveToNext()) {
            list.add(Personnage(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5), cursor.getString(6), cursor.getString(7), cursor.getString(8)))
        }
        cursor.close()
        return list
    }

    fun insertPersonnage(p: Personnage): Long {
        val cv = ContentValues().apply {
            put("alias", p.alias); put("nom", p.nom); put("prenom", p.prenom)
            put("sexe", p.sexe); put("age", p.age); put("desc_phys", p.descPhys)
            put("desc_global", p.descGlobal); put("skill", p.skill)
        }
        return writableDatabase.insert(TABLE_PERSO, null, cv)
    }

    fun updatePersonnage(p: Personnage) {
        val cv = ContentValues().apply {
            put("alias", p.alias); put("nom", p.nom); put("prenom", p.prenom)
            put("sexe", p.sexe); put("age", p.age); put("desc_phys", p.descPhys)
            put("desc_global", p.descGlobal); put("skill", p.skill)
        }
        writableDatabase.update(TABLE_PERSO, cv, "id=?", arrayOf(p.id.toString()))
    }

    fun deletePersonnage(id: Long) { writableDatabase.delete(TABLE_PERSO, "id=?", arrayOf(id.toString())) }

    // ─── Lieux ───────────────────────────────────────────────────────────────

    fun getLieux(): List<Lieu> {
        val list = mutableListOf<Lieu>()
        val cursor = readableDatabase.rawQuery("SELECT id, COALESCE(nom,''), COALESCE(desc,'') FROM $TABLE_LIEUX", null)
        while (cursor.moveToNext()) list.add(Lieu(cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
        cursor.close()
        return list
    }

    fun insertLieu(nom: String, desc: String): Long {
        val cv = ContentValues().apply { put("nom", nom); put("desc", desc) }
        return writableDatabase.insert(TABLE_LIEUX, null, cv)
    }

    fun updateLieu(id: Long, nom: String, desc: String) {
        val cv = ContentValues().apply { put("nom", nom); put("desc", desc) }
        writableDatabase.update(TABLE_LIEUX, cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteLieu(id: Long) { writableDatabase.delete(TABLE_LIEUX, "id=?", arrayOf(id.toString())) }

    // ─── Info ─────────────────────────────────────────────────────────────────

    fun getInfo(): Info {
        val cursor = readableDatabase.rawQuery("SELECT id, COALESCE(titre,''), COALESCE(stitre,''), COALESCE(auteur,''), COALESCE(date,''), COALESCE(resume,''), COALESCE(couverture,'') FROM $TABLE_INFO WHERE id=1", null)
        return if (cursor.moveToFirst()) {
            Info(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6)).also { cursor.close() }
        } else { cursor.close(); Info() }
    }

    fun saveInfo(info: Info) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("titre", info.titre); put("stitre", info.stitre)
            put("auteur", info.auteur); put("date", info.date); put("resume", info.resume)
            put("couverture", info.couverture)
        }
        val rows = db.update(TABLE_INFO, cv, "id=1", null)
        if (rows == 0) { cv.put("id", 1L); db.insert(TABLE_INFO, null, cv) }
    }

    // ─── Param ────────────────────────────────────────────────────────────────

    fun getParam(): Param {
        val cursor = readableDatabase.rawQuery(
            "SELECT id, COALESCE(police,'serif'), COALESCE(taille,'16'), COALESCE(save_time,'30'), COALESCE(langue,'fr'), COALESCE(theme,'dark'), COALESCE(format, 'A4') FROM $TABLE_PARAM WHERE id=1", null)
        return if (cursor.moveToFirst()) {
            Param(
                cursor.getLong(0),
                cursor.getString(1) ?: "serif",
                cursor.getString(2) ?: "16",
                cursor.getString(3) ?: "30",
                cursor.getString(4) ?: "fr",
                cursor.getString(5) ?: "dark",
                cursor.getString(6) ?: "A4"
            ).also { cursor.close() }
        } else { cursor.close(); Param() }
    }

    fun saveParam(param: Param) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("police", param.police); put("taille", param.taille)
            put("save_time", param.saveTime); put("langue", param.langue)
            put("theme", param.theme); put("format", param.format)
        }
        val rows = db.update(TABLE_PARAM, cv, "id=1", null)
        if (rows == 0) { cv.put("id", 1L); db.insert(TABLE_PARAM, null, cv) }
    }

    /** Force a checkpoint to ensure all data is in the main file */
    fun checkpoint() {
        try {
            readableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        } catch (e: Exception) {
            // Might fail if not in WAL mode, but it's okay.
        }
    }

    fun getChaptersAsJson(): String {
        val array = org.json.JSONArray()
        val cursor = readableDatabase.rawQuery("SELECT nom, numero, resume, contenu_html FROM $TABLE_CHAPITRE", null)
        while (cursor.moveToNext()) {
             val obj = org.json.JSONObject()
             obj.put("nom", cursor.getString(0) ?: "")
             obj.put("numero", cursor.getString(1) ?: "")
             obj.put("resume", cursor.getString(2) ?: "")
             obj.put("contenu_html", cursor.getString(3) ?: "")
             array.put(obj)
        }
        cursor.close()
        return array.toString()
    }
}
