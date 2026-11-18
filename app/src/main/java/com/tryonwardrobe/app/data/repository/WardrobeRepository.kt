package com.tryonwardrobe.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.local.PersonPhoto
import com.tryonwardrobe.app.data.local.TryOnCache
import com.tryonwardrobe.app.data.local.WardrobeDao
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class WardrobeRepository(
    private val context: Context,
    private val dao: WardrobeDao,
    val settings: SettingsDataStore
) {

    val allClothingFlow: Flow<List<ClothingItem>> = dao.observeAllClothing()
    val allPersonPhotosFlow: Flow<List<PersonPhoto>> = dao.observeAllPersonPhotos()
    val recentTryOnCachesFlow: Flow<List<TryOnCache>> = dao.observeRecentTryOnCaches()

    private val wardrobeDir: File by lazy {
        File(context.filesDir, "wardrobe_cutouts").apply { mkdirs() }
    }

    private val basePhotosDir: File by lazy {
        File(context.filesDir, "base_person_photos").apply { mkdirs() }
    }

    private val tryOnCacheDir: File by lazy {
        File(context.filesDir, "try_on_cache").apply { mkdirs() }
    }

    /**
     * Deterministic SHA-256 key using (personPhotoId + sorted clothingIds)
     * so repeated identical try-on combinations hit local cache without consuming Gemini quota.
     */
    fun buildTryOnCacheKey(personPhotoId: Long, clothingIds: List<Long>): String {
        val sortedIds = clothingIds.distinct().sorted()
        val raw = "$personPhotoId:${sortedIds.joinToString(",")}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun saveClothingBitmapToInternal(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val file = File(wardrobeDir, "garment_${UUID.randomUUID()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    }

    suspend fun savePersonBitmapToInternal(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val file = File(basePhotosDir, "person_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }
        file.absolutePath
    }

    suspend fun saveTryOnResultToInternal(cacheKey: String, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val file = File(tryOnCacheDir, "tryon_${cacheKey.take(16)}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            file.absolutePath
        }

    suspend fun insertClothingItem(item: ClothingItem): Long = withContext(Dispatchers.IO) {
        dao.insertClothing(item)
    }

    suspend fun deleteClothingItem(item: ClothingItem) = withContext(Dispatchers.IO) {
        dao.deleteClothing(item)
        runCatching { File(item.imagePath).delete() }
        // Clean up any cached try-on results referencing this deleted clothing item
        val allCaches = dao.observeRecentTryOnCaches().first()
        allCaches.filter { item.id in it.clothingIds }.forEach { cache ->
            dao.deleteTryOnCache(cache)
            runCatching { File(cache.resultImagePath).delete() }
        }
    }

    suspend fun getClothingByIds(ids: List<Long>): List<ClothingItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else dao.getClothingByIds(ids)
    }

    suspend fun getAllClothingSnapshot(): List<ClothingItem> = withContext(Dispatchers.IO) {
        dao.getAllClothingSnapshot()
    }

    suspend fun insertPersonPhotoAndSetActive(label: String, imagePath: String): PersonPhoto =
        withContext(Dispatchers.IO) {
            val photo = PersonPhoto(label = label, imagePath = imagePath)
            val newId = dao.insertPersonPhoto(photo)
            settings.setActivePersonPhotoId(newId)
            photo.copy(id = newId)
        }

    suspend fun deletePersonPhoto(photo: PersonPhoto) = withContext(Dispatchers.IO) {
        val relatedCaches = dao.getCachesByPersonPhotoId(photo.id)
        relatedCaches.forEach { cache ->
            runCatching { File(cache.resultImagePath).delete() }
        }
        dao.deleteCachesByPersonPhotoId(photo.id)
        dao.deletePersonPhoto(photo)
        runCatching { File(photo.imagePath).delete() }

        val activeId = settings.activePersonPhotoIdFlow.first()
        if (activeId == photo.id) {
            val fallback = dao.getLatestPersonPhoto()
            settings.setActivePersonPhotoId(fallback?.id)
        }
    }

    suspend fun findValidTryOnCache(personPhotoId: Long, clothingIds: List<Long>): TryOnCache? =
        withContext(Dispatchers.IO) {
            val key = buildTryOnCacheKey(personPhotoId, clothingIds)
            val cached = dao.getTryOnCacheByKey(key) ?: return@withContext null
            val resultFile = File(cached.resultImagePath)
            if (resultFile.exists() && resultFile.length() > 0L) {
                cached
            } else {
                dao.deleteTryOnCache(cached)
                null
            }
        }

    suspend fun storeTryOnCache(
        personPhoto: PersonPhoto,
        clothingIds: List<Long>,
        resultBitmap: Bitmap,
        modelUsed: String
    ): TryOnCache = withContext(Dispatchers.IO) {
        val sortedIds = clothingIds.distinct().sorted()
        val cacheKey = buildTryOnCacheKey(personPhoto.id, sortedIds)
        dao.getTryOnCacheByKey(cacheKey)?.let { previous ->
            runCatching { File(previous.resultImagePath).delete() }
        }
        val resultPath = saveTryOnResultToInternal(cacheKey, resultBitmap)
        val entry = TryOnCache(
            cacheKey = cacheKey,
            personPhotoId = personPhoto.id,
            personPhotoPath = personPhoto.imagePath,
            clothingIds = sortedIds,
            resultImagePath = resultPath,
            modelUsed = modelUsed,
            createdAt = System.currentTimeMillis()
        )
        dao.upsertTryOnCache(entry)
        entry
    }

    suspend fun saveBitmapToGallery(bitmap: Bitmap, fileNamePrefix: String = "FitStudio_TryOn"): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val filename = "${fileNamePrefix}_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/FitStudioAI"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(collection, contentValues)
                    ?: error("無法建立系統相簿檔案（MediaStore insert 失敗）")

                resolver.openOutputStream(itemUri)?.use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, outputStream)) {
                        error("影像壓縮儲存失敗")
                    }
                } ?: error("無法開啟相簿檔案寫入串流")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                }

                itemUri
            }
        }
}
