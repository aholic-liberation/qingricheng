package com.qingricheng.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class AttachmentStore(private val context: Context) {
    init {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    fun import(uri: Uri, kindHint: String): Attachment {
        val resolver = context.contentResolver
        var name = ""
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIndex >= 0) name = c.getString(nameIndex) ?: ""
                if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
            }
        }
        if (name.isBlank()) name = "attachment"
        val mime = resolver.getType(uri) ?: guessMime(name)
        val kind = if (kindHint == "image" || mime.startsWith("image/")) "image" else "file"
        val ext = name.substringAfterLast('.', "").lowercase().ifBlank { extFromMime(mime) }
        if (kind == "image" && ext !in IMAGE_EXTENSIONS) throw IllegalArgumentException("不支持的图片格式：$name")
        if (kind == "file" && ext == "doc") throw IllegalArgumentException("旧版 .doc 暂不支持，请另存为 .docx 或 .pdf")
        if (kind == "file" && ext !in DOC_EXTENSIONS) throw IllegalArgumentException("暂不支持这个文件类型：$name")
        if (size > MAX_FILE_BYTES) throw IllegalArgumentException("文件太大（上限 25MB）：$name")
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val safe = safeName(name).ifBlank { "attachment.$ext" }
        val target = File(dir, UUID.randomUUID().toString().take(8) + "-" + safe)
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取这个文件")
        if (target.length() > MAX_FILE_BYTES) {
            target.delete()
            throw IllegalArgumentException("文件太大（上限 25MB）：$name")
        }
        return Attachment(
            id = UUID.randomUUID().toString(),
            kind = kind,
            name = name,
            mime = mime,
            path = target.absolutePath,
            size = target.length()
        )
    }

    /** Load the model-facing payload (base64 image or extracted document text). */
    fun prepare(attachment: Attachment): Attachment {
        return if (attachment.kind == "image") {
            attachment.copy(imageDataUrl = imageDataUrl(File(attachment.path)))
        } else {
            attachment.copy(extractedText = extractText(File(attachment.path), attachment.name, attachment.mime))
        }
    }

    private fun imageDataUrl(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("无法解码图片")
        var sample = 1
        while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalArgumentException("无法解码图片")
        val max = 1600
        val scale = minOf(1f, max.toFloat() / maxOf(decoded.width, decoded.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        if (base64.length > 8_000_000) throw IllegalArgumentException("图片压缩后仍然太大，请换一张小图")
        return "data:image/jpeg;base64,$base64"
    }

    private fun extractText(file: File, name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase().ifBlank { extFromMime(mime) }
        val text = when (ext) {
            "pdf" -> extractPdf(file)
            "docx" -> extractDocx(file)
            "md", "markdown", "txt", "csv", "json" -> file.readText(Charsets.UTF_8)
            "html", "htm" -> Jsoup.parse(file.readText(Charsets.UTF_8)).text()
            else -> throw IllegalArgumentException("暂不支持读取 .$ext 文件，请转成 PDF/DOCX/MD/TXT")
        }.trim()
        if (text.isBlank()) throw IllegalArgumentException("没有从 $name 里提取到文字")
        return if (text.length > MAX_TEXT_CHARS) text.take(MAX_TEXT_CHARS) + "\n…（内容过长，已截断）" else text
    }

    private fun extractPdf(file: File): String {
        PDDocument.load(file).use { doc ->
            return PDFTextStripper().getText(doc)
        }
    }

    private fun extractDocx(file: File): String {
        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val bytes = zip.readBytes()
                    return parseDocxXml(bytes)
                }
                entry = zip.nextEntry
            }
        }
        throw IllegalArgumentException("DOCX 里找不到 word/document.xml")
    }

    private fun parseDocxXml(bytes: ByteArray): String {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "t") {
                val text = parser.nextText()
                if (text.isNotEmpty()) sb.append(text)
            } else if (event == XmlPullParser.END_TAG && parser.name.substringAfter(':') == "p") {
                sb.append('\n')
            }
            event = parser.next()
        }
        return sb.toString()
    }

    private fun safeName(name: String): String = name
        .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
        .take(80)

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "md", "markdown" -> "text/markdown"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        else -> "application/octet-stream"
    }

    private fun extFromMime(mime: String): String = when {
        mime == "image/jpeg" -> "jpg"
        mime == "image/png" -> "png"
        mime == "image/webp" -> "webp"
        mime == "image/gif" -> "gif"
        mime == "application/pdf" -> "pdf"
        mime.contains("wordprocessingml") -> "docx"
        mime == "text/markdown" -> "md"
        mime.startsWith("text/") -> "txt"
        else -> "bin"
    }

    companion object {
        private const val MAX_FILE_BYTES = 25L * 1024 * 1024
        private const val MAX_TEXT_CHARS = 30_000
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        private val DOC_EXTENSIONS = setOf("pdf", "docx", "md", "markdown", "txt", "csv", "json", "html", "htm", "doc")
    }
}
