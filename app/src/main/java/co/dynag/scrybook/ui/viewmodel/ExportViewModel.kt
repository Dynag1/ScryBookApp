package co.dynag.scrybook.ui.viewmodel

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.Color
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.dynag.scrybook.data.repository.ScryBookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.net.Uri
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ScryBookRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    private val _result = MutableStateFlow<ExportResult?>(null)
    val result: StateFlow<ExportResult?> = _result

    fun clearResult() { _result.value = null }

    /** Export tout le livre en PDF (Titre + Sommaire + Chapitres + Résumé) */
    fun exportBookPdf(projectPath: String, output: Any) {
        viewModelScope.launch {
            _exporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    repository.openProject(projectPath)
                    val info = repository.getInfo()
                    val chapitres = repository.getChapitres()
                    val param = repository.getParam()

                    val selectedTypeface = when (param.police.lowercase()) {
                        "sans" -> Typeface.SANS_SERIF
                        "mono" -> Typeface.MONOSPACE
                        else -> Typeface.SERIF
                    }
                    val baseFontSize = param.taille.toFloatOrNull() ?: 12f
                    val (pageWidth, pageHeight) = when (param.format) {
                        "A5" -> 420 to 595
                        "Poche" -> 312 to 510
                        else -> 595 to 842
                    }
                    val margin = if (param.format == "Poche") 40f else 56f
                    val contentWidth = pageWidth - (2 * margin).toInt()
                    val contentHeight = pageHeight - (2 * margin).toInt()

                    // --- Styles ---
                    val titlePaint = TextPaint().apply {
                        color = Color.BLACK; typeface = Typeface.create(selectedTypeface, Typeface.BOLD)
                        textSize = baseFontSize * 2.3f; isAntiAlias = true
                    }
                    val subtitlePaint = TextPaint().apply {
                        color = Color.DKGRAY; typeface = Typeface.create(selectedTypeface, Typeface.NORMAL)
                        textSize = baseFontSize * 1.5f; isAntiAlias = true
                    }
                    val authorPaint = TextPaint().apply {
                        color = Color.DKGRAY; typeface = Typeface.create(selectedTypeface, Typeface.ITALIC)
                        textSize = baseFontSize * 1.2f; isAntiAlias = true
                    }
                    val bodyPaint = TextPaint().apply {
                        color = Color.BLACK; typeface = selectedTypeface
                        textSize = baseFontSize; isAntiAlias = true
                    }
                    val chapterTitlePaint = TextPaint().apply {
                        color = Color.BLACK; typeface = Typeface.create(selectedTypeface, Typeface.BOLD)
                        textSize = baseFontSize * 1.6f; isAntiAlias = true
                    }
                    val headerPaint = TextPaint().apply {
                        color = Color.GRAY; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                        textSize = baseFontSize * 0.75f; isAntiAlias = true
                    }

                    val isEtude = projectPath.endsWith(".sbe")
                    val logoDecoded = if (param.logoB64.isNotBlank()) {
                        try {
                            val decoded = android.util.Base64.decode(param.logoB64, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                        } catch (e: Exception) { null }
                    } else null

                    val drawHeaderLogo: (android.graphics.Canvas) -> Unit = { canvas ->
                        if (isEtude) {
                            logoDecoded?.let { logo ->
                            val logoWidth = 40f
                            val logoRatio = logo.height.toFloat() / logo.width.toFloat()
                            val logoHeight = logoWidth * logoRatio
                            val rect = android.graphics.RectF(margin, margin - 30f, margin + logoWidth, margin - 30f + logoHeight)
                            canvas.drawBitmap(logo, null, rect, null)
                        }
                        }
                    }

                    // --- Phase 1 : Calcul des numéros de page pour le Sommaire ---
                    // Page 1: Titre
                    // Page 2: Sommaire (1 page reservée)
                    var currentPage = 3 
                    val tocEntries = mutableListOf<Pair<String, Int>>()
                    
                    val chapterLayouts = chapitres.map { chapitre ->
                        val formattedContent = getHtmlContent(chapitre.contenuHtml, contentWidth, contentHeight, baseFontSize)
                        val layout = StaticLayout.Builder.obtain(formattedContent, 0, formattedContent.length, bodyPaint, contentWidth)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1.6f)
                            .build()
                        
                        tocEntries.add(chapitre.nom to currentPage)
                        
                        var yOffset = margin + 40f + 60f // Titre chapitre
                        var pagesInChapter = 1
                        for (i in 0 until layout.lineCount) {
                            val spacing = getHeadingSpacing(layout, i, baseFontSize)
                            yOffset += spacing.first // Margin Top
                            val lh = layout.getLineBottom(i) - layout.getLineTop(i)
                            if (yOffset + lh > pageHeight - margin) {
                                pagesInChapter++
                                yOffset = margin + 20f
                            }
                            yOffset += lh + spacing.second // Margin Bottom
                        }
                        currentPage += pagesInChapter
                        layout
                    }

                    // --- Phase 2 : Rendu ---
                    val document = PdfDocument()
                    var docPageNumber = 1

                    // 1. Page de Titre
                    val titlePageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, docPageNumber).create()
                    val titlePage = document.startPage(titlePageInfo)
                    var yPos = pageHeight * 0.35f
                    drawCenteredText(titlePage.canvas, info.titre.ifBlank { "Sans titre" }, titlePaint, pageWidth.toFloat(), yPos)
                    if (info.stitre.isNotBlank()) {
                        yPos += 30f
                        drawCenteredText(titlePage.canvas, info.stitre, subtitlePaint, pageWidth.toFloat(), yPos)
                    }

                    logoDecoded?.let { logo ->
                        val targetWidth = pageWidth * 0.25f
                        val logoRatio = logo.height.toFloat() / logo.width.toFloat()
                        val targetHeight = targetWidth * logoRatio
                        yPos += 40f
                        val xLogo = (pageWidth - targetWidth) / 2f
                        val rect = android.graphics.RectF(xLogo, yPos, xLogo + targetWidth, yPos + targetHeight)
                        titlePage.canvas.drawBitmap(logo, null, rect, null)
                        yPos += targetHeight + 20f
                    }

                    if (info.couverture.isNotBlank()) {
                        try {
                            val pureBase64 = info.couverture.substringAfter(",")
                            val decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            if (bitmap != null) {
                                val maxWidth = pageWidth * 0.5f
                                val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
                                val targetWidth = if (bitmap.width > maxWidth) maxWidth else bitmap.width.toFloat()
                                val targetHeight = targetWidth * ratio
                                yPos += 40f
                                val xPos = (pageWidth - targetWidth) / 2f
                                val rect = android.graphics.RectF(xPos, yPos, xPos + targetWidth, yPos + targetHeight)
                                titlePage.canvas.drawBitmap(bitmap, null, rect, null)
                                yPos += targetHeight + 20f
                                bitmap.recycle()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    drawCenteredText(titlePage.canvas, "Par " + info.auteur.ifBlank { "Auteur Inconnu" }, authorPaint, pageWidth.toFloat(), pageHeight - margin - 30f)
                    document.finishPage(titlePage)
                    docPageNumber++

                    // 2. Sommaire
                    val tocPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, docPageNumber).create()
                    val tocPage = document.startPage(tocPageInfo)
                    drawHeaderLogo(tocPage.canvas)
                    yPos = margin + 20f
                    drawCenteredText(tocPage.canvas, "Sommaire", titlePaint, pageWidth.toFloat(), yPos)
                    yPos += 60f
                    val tocLinePaint = TextPaint(bodyPaint).apply { textSize = baseFontSize * 1.1f }
                    for ((title, pageNum) in tocEntries) {
                        tocPage.canvas.drawText(title, margin, yPos, tocLinePaint)
                        val pStr = (pageNum - 2).toString() // Adjust to exclude title and TOC from numbering
                        val pWidth = tocLinePaint.measureText(pStr)
                        tocPage.canvas.drawText(pStr, pageWidth - margin - pWidth, yPos, tocLinePaint)
                        // Points
                        val startDots = margin + tocLinePaint.measureText(title) + 10f
                        val endDots = pageWidth - margin - pWidth - 10f
                        var dotX = startDots
                        while (dotX < endDots) {
                            tocPage.canvas.drawText(".", dotX, yPos, tocLinePaint)
                            dotX += 10f
                        }
                        yPos += 30f
                        if (yPos > pageHeight - margin) break
                    }
                    document.finishPage(tocPage)
                    docPageNumber++

                    // 3. Chapitres
                    for (idx in chapitres.indices) {
                        val chapitre = chapitres[idx]
                        val layout = chapterLayouts[idx]
                        
                        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, docPageNumber).create())
                        var canvas = page.canvas
                        drawHeaderLogo(canvas)
                        
                        // Header
                        canvas.drawText(chapitre.nom, pageWidth / 2f - headerPaint.measureText(chapitre.nom) / 2f, margin - 15f, headerPaint)

                        // Titre
                        yPos = margin + 40f
                        drawCenteredText(canvas, chapitre.nom, chapterTitlePaint, pageWidth.toFloat(), yPos)
                        yPos += 60f

                        for (i in 0 until layout.lineCount) {
                            val spacing = getHeadingSpacing(layout, i, baseFontSize)
                            yPos += spacing.first // Margin Top
                            val lh = layout.getLineBottom(i) - layout.getLineTop(i)

                            val lStart = layout.getLineStart(i)
                            val lEnd = layout.getLineEnd(i)
                            val spans = (layout.text as android.text.Spanned).getSpans(lStart, lEnd, android.text.style.ImageSpan::class.java)
                            val hasImg = spans.isNotEmpty()

                            if (hasImg) {
                                val img = spans.first()
                                val trueLh = img.drawable.bounds.height().toFloat()
                                if (yPos + trueLh > pageHeight - margin) {
                                    canvas.drawText((docPageNumber - 2).toString(), pageWidth - margin, pageHeight - margin / 2, headerPaint)
                                    document.finishPage(page)
                                    docPageNumber++
                                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, docPageNumber).create())
                                    canvas = page.canvas
                                    drawHeaderLogo(canvas)
                                    canvas.drawText(chapitre.nom, pageWidth / 2f - headerPaint.measureText(chapitre.nom) / 2f, margin - 15f, headerPaint)
                                    yPos = margin + 20f
                                }
                                canvas.save()
                                val xOff = (pageWidth - img.drawable.bounds.width()) / 2f
                                canvas.translate(xOff, yPos)
                                img.drawable.draw(canvas)
                                canvas.restore()
                                yPos += trueLh + spacing.second
                                continue
                            }

                            if (yPos + lh > pageHeight - margin) {
                                canvas.drawText((docPageNumber - 2).toString(), pageWidth - margin, pageHeight - margin / 2, headerPaint)
                                document.finishPage(page)
                                docPageNumber++
                                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, docPageNumber).create())
                                canvas = page.canvas
                                drawHeaderLogo(canvas)
                                canvas.drawText(chapitre.nom, pageWidth / 2f - headerPaint.measureText(chapitre.nom) / 2f, margin - 15f, headerPaint)
                                yPos = margin + 20f
                            }
                            drawLayoutLine(canvas, layout, i, margin, yPos)
                            yPos += lh + spacing.second // Margin Bottom
                        }
                        canvas.drawText((docPageNumber - 2).toString(), pageWidth - margin, pageHeight - margin / 2, headerPaint)
                        document.finishPage(page)
                        docPageNumber++
                    }

                    // 4. Résumé
                    if (info.resume.isNotBlank()) {
                        val formattedResume = getHtmlContent(info.resume, contentWidth, contentHeight, baseFontSize)
                        val resLayout = StaticLayout.Builder.obtain(formattedResume, 0, formattedResume.length, bodyPaint, contentWidth)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setLineSpacing(0f, 1.6f)
                            .build()

                        // Calcul de la hauteur totale du bloc
                        var totalLayoutHeight = 0f
                        for (i in 0 until resLayout.lineCount) {
                            val spacing = getHeadingSpacing(resLayout, i, baseFontSize)
                            val lh = resLayout.getLineBottom(i) - resLayout.getLineTop(i)
                            totalLayoutHeight += spacing.first + lh + spacing.second
                        }

                        val titleText = "Résumé"
                        val metrics = chapterTitlePaint.fontMetrics
                        val titleHeight = metrics.descent - metrics.ascent
                        val titleSpacing = 40f
                        val totalBlockHeight = titleHeight + titleSpacing + totalLayoutHeight

                        var yPos = (pageHeight - totalBlockHeight) / 2f
                        if (yPos < margin) yPos = margin

                        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, docPageNumber).create())
                        val canvas = page.canvas

                        // Dessin du Titre centré
                        drawCenteredText(canvas, titleText, chapterTitlePaint, pageWidth.toFloat(), yPos - metrics.ascent)
                        yPos += titleHeight + titleSpacing

                        for (i in 0 until resLayout.lineCount) {
                            val spacing = getHeadingSpacing(resLayout, i, baseFontSize)
                            yPos += spacing.first // Margin Top
                            val lh = resLayout.getLineBottom(i) - resLayout.getLineTop(i)
                            if (yPos + lh > pageHeight - margin) break
                            drawLayoutLine(canvas, resLayout, i, margin, yPos)
                            yPos += lh + spacing.second // Margin Bottom
                        }
                        
                        document.finishPage(page)
                        docPageNumber++
                    }

                    // Flush
                    val os: OutputStream? = when (output) {
                        is Uri -> context.contentResolver.openOutputStream(output)
                        is String -> FileOutputStream(File(output))
                        else -> null
                    }
                    logoDecoded?.recycle()
                    os?.use { document.writeTo(it) }
                    document.close()
                }
                _result.value = ExportResult.Success(if (output is String) output else "Livre")
            } catch (e: Exception) {
                _result.value = ExportResult.Error(e.message ?: "Erreur")
            } finally {
                _exporting.value = false
            }
        }
    }

    /** Export un seul chapitre */
    fun exportChapterPdf(projectPath: String, chapterId: Long, output: Any) {
        viewModelScope.launch {
            _exporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    repository.openProject(projectPath)
                    val chapitre = repository.getChapitre(chapterId) ?: throw Exception("Introuvable")
                    val param = repository.getParam()
                    val (pageWidth, pageHeight) = when (param.format) {
                        "A5" -> 420 to 595
                        "Poche" -> 312 to 510
                        else -> 595 to 842
                    }
                    val margin = if (param.format == "Poche") 40f else 56f
                    val contentWidth = pageWidth - (2 * margin).toInt()
                    val contentHeight = pageHeight - (2 * margin).toInt()

                    val selectedTypeface = when (param.police.lowercase()) {
                        "sans" -> Typeface.SANS_SERIF
                        "mono" -> Typeface.MONOSPACE
                        else -> Typeface.SERIF
                    }
                    val baseFontSize = param.taille.toFloatOrNull() ?: 12f
                    val bodyPaint = TextPaint().apply { color = Color.BLACK; typeface = selectedTypeface; textSize = baseFontSize; isAntiAlias = true }
                    val titlePaint = TextPaint().apply { color = Color.BLACK; typeface = Typeface.create(selectedTypeface, Typeface.BOLD); textSize = baseFontSize * 1.6f; isAntiAlias = true }
                    val headerPaint = TextPaint().apply { color = Color.GRAY; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); textSize = baseFontSize * 0.75f; isAntiAlias = true }

                    val isEtude = projectPath.endsWith(".sbe")
                    val logoDecoded = if (param.logoB64.isNotBlank()) {
                        try {
                            val decoded = android.util.Base64.decode(param.logoB64, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                        } catch (e: Exception) { null }
                    } else null

                    val drawHeaderLogo: (android.graphics.Canvas) -> Unit = { canvas ->
                        if (isEtude) {
                            logoDecoded?.let { logo ->
                            val logoWidth = 40f
                            val logoRatio = logo.height.toFloat() / logo.width.toFloat()
                            val logoHeight = logoWidth * logoRatio
                            val rect = android.graphics.RectF(margin, margin - 30f, margin + logoWidth, margin - 30f + logoHeight)
                            canvas.drawBitmap(logo, null, rect, null)
                        }
                        }
                    }

                    val content = getHtmlContent(chapitre.contenuHtml, contentWidth, contentHeight, baseFontSize)
                    val layout = StaticLayout.Builder.obtain(content, 0, content.length, bodyPaint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.6f).build()

                    val document = PdfDocument()
                    var pNum = 1
                    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pNum).create())
                    var canvas = page.canvas
                    drawHeaderLogo(canvas)
                    var yP = margin + 40f
                    drawCenteredText(canvas, chapitre.nom, titlePaint, pageWidth.toFloat(), yP)
                    yP += 60f

                    for (i in 0 until layout.lineCount) {
                        val spacing = getHeadingSpacing(layout, i, baseFontSize)
                        yP += spacing.first // Margin Top
                        val lh = layout.getLineBottom(i) - layout.getLineTop(i)

                        val lStart = layout.getLineStart(i)
                        val lEnd = layout.getLineEnd(i)
                        val spans = (layout.text as android.text.Spanned).getSpans(lStart, lEnd, android.text.style.ImageSpan::class.java)
                        val hasImg = spans.isNotEmpty()

                        if (hasImg) {
                            val img = spans.first()
                            val trueLh = img.drawable.bounds.height().toFloat()
                            if (yP + trueLh > pageHeight - margin) {
                                canvas.drawText(pNum.toString(), pageWidth - margin, pageHeight - margin/2, headerPaint)
                                document.finishPage(page)
                                pNum++
                                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pNum).create())
                                canvas = page.canvas
                                drawHeaderLogo(canvas)
                                yP = margin + 20f
                            }
                            canvas.save()
                            val xOff = (pageWidth - img.drawable.bounds.width()) / 2f
                            canvas.translate(xOff, yP)
                            img.drawable.draw(canvas)
                            canvas.restore()
                            yP += trueLh + spacing.second
                            continue
                        }

                        if (yP + lh > pageHeight - margin) {
                            canvas.drawText(pNum.toString(), pageWidth - margin, pageHeight - margin/2, headerPaint)
                            document.finishPage(page)
                            pNum++
                            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pNum).create())
                            canvas = page.canvas
                            drawHeaderLogo(canvas)
                            yP = margin + 20f
                        }
                        drawLayoutLine(canvas, layout, i, margin, yP)
                        yP += lh + spacing.second // Margin Bottom
                    }
                    canvas.drawText(pNum.toString(), pageWidth - margin, pageHeight - margin/2, headerPaint)
                    document.finishPage(page)

                    val os: OutputStream? = when (output) {
                        is Uri -> context.contentResolver.openOutputStream(output)
                        is String -> FileOutputStream(File(output))
                        else -> null
                    }
                    logoDecoded?.recycle()
                    os?.use { document.writeTo(it) }
                    document.close()
                }
                _result.value = ExportResult.Success("Chapitre")
            } catch (e: Exception) {
                _result.value = ExportResult.Error(e.message ?: "Erreur")
            } finally {
                _exporting.value = false
            }
        }
    }

    private fun getHtmlContent(html: String, contentWidth: Int, contentHeight: Int, baseFontSize: Float): CharSequence {
        val cleaned = cleanHtmlForExport(html)
        val imgAttrsMap = mutableMapOf<String, ImageAttrs>()
        val imgRegex = Regex("<img[^>]*src=\"([^\"]+)\"[^>]*>", RegexOption.IGNORE_CASE)
        
        imgRegex.findAll(cleaned).forEach { match ->
            val tag = match.value
            val src = match.groups[1]?.value ?: return@forEach
            val w = Regex("width=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(tag)?.groups?.get(1)?.value
            val h = Regex("height=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(tag)?.groups?.get(1)?.value
            val s = Regex("style=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(tag)?.groups?.get(1)?.value
            imgAttrsMap[src] = ImageAttrs(w, h, s)
        }

        val spanned = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_COMPACT, { source ->
            try {
                val bitmap = if (source.startsWith("data:image/", ignoreCase = true) && source.contains("base64,")) {
                    val base64Data = source.substringAfter("base64,")
                    val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } else {
                    val imgFile = File(source)
                    if (imgFile.exists()) android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath) else null
                }

                if (bitmap != null) {
                    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                    val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
                    val attrs = imgAttrsMap[source]
                    
                    var targetWidth = bitmap.width
                    var targetHeight = bitmap.height

                    attrs?.let { attr ->
                        attr.width?.let { w ->
                            if (w.endsWith("%")) {
                                val pct = w.dropLast(1).toFloatOrNull() ?: 100f
                                targetWidth = (contentWidth * pct / 100f).toInt()
                                targetHeight = (targetWidth * ratio).toInt()
                            } else {
                                w.toIntOrNull()?.let { px ->
                                    // Conversion 96 DPI (écran) -> 72 DPI (PDF) comme sur Bureau (* 0.75)
                                    targetWidth = (px * 0.75f).toInt()
                                    targetHeight = (targetWidth * ratio).toInt()
                                }
                            }
                        }
                    }

                    if (targetWidth > contentWidth) {
                        targetWidth = contentWidth
                        targetHeight = (targetWidth * ratio).toInt()
                    }
                    if (targetHeight > contentHeight) {
                        targetHeight = contentHeight
                        targetWidth = (targetHeight / ratio).toInt()
                    }

                    drawable.setBounds(0, 0, targetWidth, targetHeight)
                    return@fromHtml drawable
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }, null)

        if (spanned is android.text.Spannable) {
            try {
                val absSpans = spanned.getSpans(0, spanned.length, android.text.style.AbsoluteSizeSpan::class.java)
                for (s in absSpans) {
                    val start = spanned.getSpanStart(s)
                    val end = spanned.getSpanEnd(s)
                    val size = s.size.toFloat()
                    
                    if (size / baseFontSize > 1.3f) { // H1
                        var pStart = start
                        while (pStart > 0 && spanned[pStart - 1] != '\n') pStart--
                        var pEnd = end
                        while (pEnd < spanned.length && spanned[pEnd] != '\n') pEnd++
                        spanned.setSpan(android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER), pStart, pEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (size / baseFontSize > 1.05f) { // H2
                        var pStart = start
                        while (pStart > 0 && spanned[pStart - 1] != '\n') pStart--
                        var pEnd = end
                        while (pEnd < spanned.length && spanned[pEnd] != '\n') pEnd++
                        spanned.setSpan(android.text.style.LeadingMarginSpan.Standard(20, 20), pStart, pEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        return spanned
    }

    private fun cleanHtmlForExport(html: String): String {
        if (html.isBlank()) return ""
        var cleaned = html.replace(Regex("<style.*?>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<script.*?>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<!DOCTYPE.*?>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<html.*?>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</html.*?>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<body.*?>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</body.*?>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<head.*?>.*?</head>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            
        // Conversion CSS -> Balises pour mieux supporter Html.fromHtml
        cleaned = cleaned.replace(Regex("<span style=\"[^\"]*font-weight:700[^\"]*\">(.*?)</span>", RegexOption.IGNORE_CASE), "<b>$1</b>")
        cleaned = cleaned.replace(Regex("<span style=\"[^\"]*font-weight:bold[^\"]*\">(.*?)</span>", RegexOption.IGNORE_CASE), "<b>$1</b>")
        cleaned = cleaned.replace(Regex("<span style=\"[^\"]*font-style:italic[^\"]*\">(.*?)</span>", RegexOption.IGNORE_CASE), "<i>$1</i>")
        cleaned = cleaned.replace(Regex("<span style=\"[^\"]*text-decoration:\\s*underline[^\"]*\">(.*?)</span>", RegexOption.IGNORE_CASE), "<u>$1</u>")
        
        return cleaned
    }

    
    private fun getHeadingSpacing(layout: android.text.StaticLayout, lineIndex: Int, baseFontSize: Float): Pair<Float, Float> {
        val text = layout.text
        if (text !is android.text.Spanned) return 0f to 0f
        val start = layout.getLineStart(lineIndex)
        val isParaStart = start == 0 || text[start - 1] == '\n'
        if (!isParaStart) return 0f to 0f
        val absSpans = text.getSpans(start, start + 1, android.text.style.AbsoluteSizeSpan::class.java)
        if (absSpans.isEmpty()) return 0f to 0f
        
        val maxFontSize = absSpans.maxOfOrNull { it.size.toFloat() } ?: baseFontSize
        val ratio = maxFontSize / baseFontSize
        
        if (ratio > 1.3f) { // H1
            val scale = baseFontSize + 6f
            return (scale * 2.0f) to (scale * 1.0f)
        } else if (ratio > 1.05f) { // H2 / Subtitle
            val scale = baseFontSize + 2f
            return (scale * 2.0f) to (scale * 1.0f)
        }
        return 0f to 0f
    }

    private fun drawLayoutLine(canvas: android.graphics.Canvas, layout: StaticLayout, lineIndex: Int, x: Float, y: Float) {
        canvas.save()
        val lineTop = layout.getLineTop(lineIndex)
        val lineBottom = layout.getLineBottom(lineIndex)
        canvas.translate(x, y - lineTop)
        canvas.clipRect(0, lineTop, layout.width, lineBottom)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawCenteredText(canvas: android.graphics.Canvas, text: String, paint: TextPaint, pageWidth: Float, y: Float) {
        val x = (pageWidth - paint.measureText(text)) / 2f
        canvas.drawText(text, x, y, paint)
    }
}

private data class ImageAttrs(val width: String?, val height: String?, val style: String?)

sealed class ExportResult {
    data class Success(val path: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

class CustomHeadingSpan(val level: Int)