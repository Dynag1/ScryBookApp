package co.dynag.scrybook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import co.dynag.scrybook.data.repository.ScryBookRepository
import co.dynag.scrybook.ui.navigation.AppNavigation
import co.dynag.scrybook.ui.theme.ScryBookTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: ScryBookRepository

    /** If set, the app opens this .sb file directly */
    val openFilePath = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val param by repository.currentParam.collectAsState()
            
            val configuration = LocalConfiguration.current
            val locale = when (param.langue) {
                "fr" -> Locale.FRENCH
                "en" -> Locale.ENGLISH
                else -> Locale.getDefault()
            }
            
            val updatedConfig = configuration.apply {
                setLocale(locale)
            }

            CompositionLocalProvider(LocalConfiguration provides updatedConfig) {
                ScryBookTheme(appTheme = param.theme) {
                    AppNavigation(openFilePath = openFilePath.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            
            // Try to resolve a real path before copying
            val realPath = getRealPathFromUri(uri)
            if (realPath != null && File(realPath).exists() && File(realPath).canWrite()) {
                android.util.Log.d("MainActivity", "Using direct path: $realPath")
                repository.openProject(realPath)
                openFilePath.value = realPath
                return
            }

            // Otherwise, use the resolver which might copy it
            val path = resolveUri(uri)
            if (path != null && (path.endsWith(".sb") || path.endsWith(".sbe"))) {
                openFilePath.value = path
            }
        }
    }

    /** Resolve a content: or file: URI to a local persistent path */
    private fun resolveUri(uri: Uri): String? {
        // Already handled file:// in handleIntent calling getRealPathFromUri
        val directPath = getRealPathFromUri(uri)
        if (directPath != null && File(directPath).exists() && File(directPath).canWrite()) {
            repository.openProject(directPath)
            return directPath
        }

        // content:// scheme — copy to persistent storage
        try {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "project.sb"
            val destDir = File(getExternalFilesDir("ScryBook"), "")
            destDir.mkdirs()
            val destFile = File(destDir, fileName)
            
            // Always refresh from original if it's an Intent open
            // This ensures we have the latest version from cloud sync
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Take persistable permission if possible
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { /* Not supported by all providers */ }

            // Link the URI to the path so syncBack will work
            repository.openProject(destFile.absolutePath, uri.toString())
            
            return destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        
        if (uri.scheme == "content") {
            // 1. Try DocumentContract
            if (android.provider.DocumentsContract.isDocumentUri(this, uri)) {
                if ("com.android.externalstorage.documents" == uri.authority) {
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    if (split.size >= 2 && "primary".equals(split[0], ignoreCase = true)) {
                        return android.os.Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    }
                }
            }
            
            // 2. Try _data column
            try {
                val cursor = contentResolver.query(uri, arrayOf("_data"), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex("_data")
                        if (idx != -1) return it.getString(idx)
                    }
                }
            } catch (e: Exception) {}
        }
        return null
    }
}
