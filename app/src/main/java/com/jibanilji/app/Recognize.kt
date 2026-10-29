package com.jibanilji.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

object Photos {
    /** 촬영 원본을 회전 보정 + 최대 1600px로 줄여서 저장 용량 절약 */
    fun normalize(f: File, maxSide: Int = 1600) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var s = 1
        while (bounds.outWidth / (s * 2) >= maxSide || bounds.outHeight / (s * 2) >= maxSide) s *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return
        val rot = when (ExifInterface(f.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = min(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
        val m = Matrix().apply { postRotate(rot); postScale(scale, scale) }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    fun thumb(path: String, size: Int): Bitmap? {
        if (!File(path).exists()) return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        var s = 1
        while (b.outWidth / (s * 2) >= size && b.outHeight / (s * 2) >= size) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** 공유용: 최대 720px · JPEG 70% → 보통 50~100KB (Firestore 무료 저장) */
    fun toBase64(f: File, maxSide: Int = 720): String? {
        val src = thumb(f.path, maxSide) ?: return null
        val scale = min(1f, maxSide.toFloat() / max(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private val cache = LruCache<String, Bitmap>(40)

    fun decode(b64: String?): Bitmap? {
        if (b64.isNullOrEmpty()) return null
        val key = "${b64.length}:${b64.hashCode()}"
        cache.get(key)?.let { return it }
        return runCatching {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { cache.put(key, it) }
    }
}

/**
 * Google ML Kit 기기 내 글자 인식 (무료, 오프라인).
 * - 날짜: 사진 속 날짜들 중 가장 늦은 날짜를 유통기한으로 추정 (제조일보다 늦으므로)
 * - 이름: 포장지에서 가장 큰 글씨를 이름 후보로 제안
 */
object Ocr {
    fun recognize(ctx: Context, uri: Uri, cb: (name: String?, date: LocalDate?) -> Unit) {
        val image = try { InputImage.fromFilePath(ctx, uri) } catch (e: Exception) { cb(null, null); return }
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            .process(image)
            .addOnSuccessListener { r -> cb(guessName(r), findDate(r.text)) }
            .addOnFailureListener { cb(null, null) }
    }

    private val SEP = "\\s*[.\\-/,년]\\s*"
    private val SEP2 = "\\s*[.\\-/,월]\\s*"
    private val patterns = listOf(
        Regex("(20\\d{2})$SEP(\\d{1,2})$SEP2(\\d{1,2})"),              // 2026.11.03 / 2026년 11월 3일
        Regex("(?<!\\d)(\\d{2})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})(?!\\d)"), // 26.11.03
        Regex("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)"),             // 20261103
    )

    fun findDate(text: String): LocalDate? {
        val today = LocalDate.now()
        val found = mutableListOf<LocalDate>()
        for (p in patterns) for (m in p.findAll(text)) {
            var y = m.groupValues[1].toInt()
            if (y < 100) y += 2000
            val d = runCatching {
                LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull() ?: continue
            if (d.isAfter(today.minusYears(1)) && d.isBefore(today.plusYears(5))) found += d
        }
        return found.maxOrNull()
    }

    private val skipWords = listOf("기한", "까지", "제조", "보관", "원재료", "용량", "kcal", "영양")

    private fun guessName(r: Text): String? = r.textBlocks
        .flatMap { it.lines }
        .filter { line ->
            val t = line.text.trim()
            t.count { it.isLetter() } >= 2 && skipWords.none { t.contains(it, true) } &&
                t.count { it.isDigit() } <= t.length / 3
        }
        .maxByOrNull { it.boundingBox?.height() ?: 0 }
        ?.text?.trim()?.take(20)
}
