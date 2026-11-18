package com.tryonwardrobe.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tryonwardrobe.app.data.local.ApiUsageRecord
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.local.PersonPhoto
import com.tryonwardrobe.app.data.local.SafeImageStorage
import com.tryonwardrobe.app.data.local.TryOnCache
import com.tryonwardrobe.app.data.local.WardrobeDao
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

enum class TopStylingMode(val label: String, val promptDirective: String) {
    AUTO("AI 自動", "Natural styling appropriate for the top and bottom"),
    TUCK_IN("紮進褲頭 (Tuck-in)", "STRICT TUCK-IN: The top hem must be neatly tucked inside the waistband of the bottom garment, clearly exposing the waist and beltline"),
    UNTUCKED("自然垂放 (Untucked)", "STRICT UNTUCKED: The top hem must hang naturally and loosely outside over the waistband of the bottom garment")
}

enum class OuterStylingMode(val label: String, val promptDirective: String) {
    AUTO("AI 自動", "Natural outerwear layering"),
    OPEN("敞開內搭 (Open)", "STRICT OPEN FRONT: The outerwear/jacket/coat must be worn completely open in the front, clearly showcasing the inner top beneath"),
    CLOSED("拉鍊/扣子全扣 (Closed)", "STRICT CLOSED FRONT: The outerwear/jacket/coat must be fully buttoned up or zipped closed in the front")
}

class WardrobeRepository(
    private val context: Context,
    private val dao: WardrobeDao,
    val settings: SettingsDataStore,
    val imageStorage: SafeImageStorage
) {

    val allClothingFlow: Flow<List<ClothingItem>> = dao.observeAllClothing()
    val allPersonPhotosFlow: Flow<List<PersonPhoto>> = dao.observeAllPersonPhotos()
    val recentTryOnCachesFlow: Flow<List<TryOnCache>> = dao.observeRecentTryOnCaches()
    val allApiUsageFlow: Flow<List<ApiUsageRecord>> = dao.observeAllApiUsage()

    fun buildTryOnCacheKey(
        personPhotoId: Long,
        clothingIds: List<Long>,
        topMode: TopStylingMode = TopStylingMode.AUTO,
        outerMode: OuterStylingMode = OuterStylingMode.AUTO,
        mallItemIds: List<String> = emptyList()
    ): String {
        val sortedIds = clothingIds.distinct().sorted()
        val sortedMall = mallItemIds.distinct().sorted()
        val raw = "$personPhotoId:${sortedIds.joinToString(",")}:${topMode.name}:${outerMode.name}:${sortedMall.joinToString(",")}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun saveClothingBitmapToInternal(bitmap: Bitmap, preferPng: Boolean = true): String =
        imageStorage.saveWardrobeBitmap(bitmap, preferPng = preferPng)

    suspend fun savePersonBitmapToInternal(bitmap: Bitmap): String =
        imageStorage.saveFullBodyBitmap(bitmap)

    suspend fun insertClothingItem(item: ClothingItem): Long = withContext(Dispatchers.IO) {
        dao.insertClothing(item)
    }

    suspend fun updateClothingItem(item: ClothingItem) = withContext(Dispatchers.IO) {
        dao.updateClothing(item)
    }

    suspend fun incrementWearCount(ids: List<Long>, wornAt: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            if (ids.isNotEmpty()) {
                dao.incrementWearCountForIds(ids.distinct(), wornAt)
            }
        }

    suspend fun deleteClothingItem(item: ClothingItem) = withContext(Dispatchers.IO) {
        dao.deleteClothing(item)
        runCatching { File(item.imagePath).delete() }
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

    suspend fun getAllPersonPhotosSnapshot(): List<PersonPhoto> = withContext(Dispatchers.IO) {
        dao.getAllPersonPhotosSnapshot()
    }

    suspend fun insertPersonPhoto(photo: PersonPhoto): PersonPhoto = withContext(Dispatchers.IO) {
        val newId = dao.insertPersonPhoto(photo)
        photo.copy(id = newId)
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

    suspend fun clearAllWardrobeAndPersonPhotos() = withContext(Dispatchers.IO) {
        val allClothes = dao.getAllClothingSnapshot()
        allClothes.forEach { runCatching { File(it.imagePath).delete() } }
        val allPersons = dao.getAllPersonPhotosSnapshot()
        allPersons.forEach { runCatching { File(it.imagePath).delete() } }
        val allCaches = dao.observeRecentTryOnCaches().first()
        allCaches.forEach { runCatching { File(it.resultImagePath).delete() } }

        dao.deleteAllTryOnCaches()
        dao.deleteAllClothing()
        dao.deleteAllPersonPhotos()
        settings.setActivePersonPhotoId(null)
    }

    suspend fun recordApiUsage(record: ApiUsageRecord) = withContext(Dispatchers.IO) {
        dao.insertApiUsage(record)
    }

    suspend fun clearAllApiUsage() = withContext(Dispatchers.IO) {
        dao.clearAllApiUsage()
    }

    suspend fun findValidTryOnCache(
        personPhotoId: Long,
        clothingIds: List<Long>,
        topMode: TopStylingMode = TopStylingMode.AUTO,
        outerMode: OuterStylingMode = OuterStylingMode.AUTO,
        mallItemIds: List<String> = emptyList()
    ): TryOnCache? = withContext(Dispatchers.IO) {
        val key = buildTryOnCacheKey(personPhotoId, clothingIds, topMode, outerMode, mallItemIds)
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
        topMode: TopStylingMode = TopStylingMode.AUTO,
        outerMode: OuterStylingMode = OuterStylingMode.AUTO,
        mallItemIds: List<String> = emptyList(),
        resultBitmap: Bitmap,
        modelUsed: String
    ): TryOnCache = withContext(Dispatchers.IO) {
        val sortedIds = clothingIds.distinct().sorted()
        val cacheKey = buildTryOnCacheKey(personPhoto.id, sortedIds, topMode, outerMode, mallItemIds)
        dao.getTryOnCacheByKey(cacheKey)?.let { previous ->
            runCatching { File(previous.resultImagePath).delete() }
        }
        val resultPath = imageStorage.saveTryOnResultBitmap(cacheKey, resultBitmap)
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
