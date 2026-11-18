package com.tryonwardrobe.app.service.backup

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.local.PersonPhoto
import com.tryonwardrobe.app.data.preferences.PersonalStyleProfile
import com.tryonwardrobe.app.data.repository.WardrobeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupClothingDto(
    val zipEntryName: String,
    val category: String,
    val subCategory: String,
    val color: String,
    val fit: String,
    val styleTags: List<String>,
    val season: List<String>,
    val tagStatus: String,
    val tagError: String?,
    val wearCount: Int,
    val lastWornTimestamp: Long?,
    val createdAt: Long
)

data class BackupPersonPhotoDto(
    val zipEntryName: String,
    val label: String,
    val isActive: Boolean,
    val createdAt: Long
)

data class WardrobeBackupManifest(
    val schemaVersion: Int = 3,
    val exportedAt: Long = System.currentTimeMillis(),
    val appName: String = "FitStudio AI",
    val personalProfile: PersonalStyleProfile,
    val clothingItems: List<BackupClothingDto>,
    val personPhotos: List<BackupPersonPhotoDto>
)

data class BackupResultSummary(
    val clothingCount: Int,
    val personPhotoCount: Int
)

class WardrobeBackupManager(
    private val context: Context,
    private val repository: WardrobeRepository
) {
    companion object {
        private const val MANIFEST_ENTRY = "backup_manifest.json"
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportBackupToZipUri(targetUri: Uri): Result<BackupResultSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val allClothing = repository.getAllClothingSnapshot()
                val allPersons = repository.getAllPersonPhotosSnapshot()
                val activePersonId = repository.settings.activePersonPhotoIdFlow.first()
                val profile = repository.settings.personalStyleProfileFlow.first()

                val clothingDtos = mutableListOf<BackupClothingDto>()
                val personDtos = mutableListOf<BackupPersonPhotoDto>()
                val filesToPack = mutableListOf<Pair<String, File>>()

                allClothing.forEachIndexed { idx, item ->
                    val file = File(item.imagePath)
                    if (file.exists() && file.length() > 0L) {
                        val ext = file.extension.ifBlank { "png" }
                        val entryName = "wardrobe/garment_${idx}_${file.nameWithoutExtension}.$ext"
                        filesToPack.add(entryName to file)
                        clothingDtos.add(
                            BackupClothingDto(
                                zipEntryName = entryName,
                                category = item.category,
                                subCategory = item.subCategory,
                                color = item.color,
                                fit = item.fit,
                                styleTags = item.styleTags,
                                season = item.season,
                                tagStatus = item.tagStatus,
                                tagError = item.tagError,
                                wearCount = item.wearCount,
                                lastWornTimestamp = item.lastWornTimestamp,
                                createdAt = item.createdAt
                            )
                        )
                    }
                }

                allPersons.forEachIndexed { idx, person ->
                    val file = File(person.imagePath)
                    if (file.exists() && file.length() > 0L) {
                        val ext = file.extension.ifBlank { "jpg" }
                        val entryName = "full_body/person_${idx}_${file.nameWithoutExtension}.$ext"
                        filesToPack.add(entryName to file)
                        personDtos.add(
                            BackupPersonPhotoDto(
                                zipEntryName = entryName,
                                label = person.label,
                                isActive = person.id == activePersonId,
                                createdAt = person.createdAt
                            )
                        )
                    }
                }

                val manifest = WardrobeBackupManifest(
                    personalProfile = profile,
                    clothingItems = clothingDtos,
                    personPhotos = personDtos
                )

                val outStream = context.contentResolver.openOutputStream(targetUri)
                    ?: error("無法建立備份 ZIP 寫入串流")

                ZipOutputStream(BufferedOutputStream(outStream)).use { zos ->
                    // 1. Write backup_manifest.json
                    val manifestBytes = gson.toJson(manifest).toByteArray(Charsets.UTF_8)
                    zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zos.write(manifestBytes)
                    zos.closeEntry()

                    // 2. Write image files
                    val buffer = ByteArray(16 * 1024)
                    filesToPack.forEach { (entryName, srcFile) ->
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(srcFile).use { fis ->
                            var read: Int
                            while (fis.read(buffer).also { read = it } != -1) {
                                zos.write(buffer, 0, read)
                            }
                        }
                        zos.closeEntry()
                    }
                }

                BackupResultSummary(
                    clothingCount = clothingDtos.size,
                    personPhotoCount = personDtos.size
                )
            }
        }

    suspend fun importBackupFromZipUri(
        sourceUri: Uri,
        overwriteExisting: Boolean
    ): Result<BackupResultSummary> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "backup_restore_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            runCatching {
                val inStream = context.contentResolver.openInputStream(sourceUri)
                    ?: error("無法讀取所選的備份 ZIP 檔案")

                var manifestJson: String? = null
                val extractedFiles = mutableMapOf<String, File>()

                ZipInputStream(BufferedInputStream(inStream)).use { zis ->
                    val buffer = ByteArray(16 * 1024)
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && !name.contains("..")) {
                            if (name == MANIFEST_ENTRY) {
                                manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                            } else if (name.startsWith("wardrobe/") || name.startsWith("full_body/")) {
                                val outFile = File(stagingDir, name)
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } != -1) {
                                        fos.write(buffer, 0, len)
                                    }
                                }
                                extractedFiles[name] = outFile
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                val rawManifest = manifestJson
                    ?: error("備份包內缺少 backup_manifest.json，請確認檔案是否為 FitStudio AI 備份檔")

                val manifest = runCatching {
                    gson.fromJson(rawManifest, WardrobeBackupManifest::class.java)
                }.getOrNull() ?: error("backup_manifest.json 格式解析失敗")

                if (overwriteExisting) {
                    repository.clearAllWardrobeAndPersonPhotos()
                }

                // Restore Personal Color & Body Shape profile if present
                if (manifest.personalProfile.isConfigured || overwriteExisting) {
                    repository.settings.savePersonalStyleProfile(manifest.personalProfile)
                }

                val wardrobeDestDir = File(context.filesDir, "wardrobe").apply { mkdirs() }
                val fullBodyDestDir = File(context.filesDir, "full_body").apply { mkdirs() }

                var restoredClothingCount = 0
                manifest.clothingItems.forEach { dto ->
                    val staged = extractedFiles[dto.zipEntryName]
                    if (staged != null && staged.exists() && staged.length() > 0L) {
                        val ext = staged.extension.ifBlank { "png" }
                        val destFile = File(wardrobeDestDir, "garment_${UUID.randomUUID()}.$ext")
                        staged.copyTo(destFile, overwrite = true)
                        repository.insertClothingItem(
                            ClothingItem(
                                imagePath = destFile.absolutePath,
                                category = dto.category,
                                subCategory = dto.subCategory,
                                color = dto.color,
                                fit = dto.fit,
                                styleTags = dto.styleTags,
                                season = dto.season,
                                tagStatus = dto.tagStatus,
                                tagError = dto.tagError,
                                wearCount = dto.wearCount,
                                lastWornTimestamp = dto.lastWornTimestamp,
                                createdAt = dto.createdAt
                            )
                        )
                        restoredClothingCount++
                    }
                }

                var restoredPersonCount = 0
                var newActivePersonId: Long? = null
                manifest.personPhotos.forEach { dto ->
                    val staged = extractedFiles[dto.zipEntryName]
                    if (staged != null && staged.exists() && staged.length() > 0L) {
                        val ext = staged.extension.ifBlank { "jpg" }
                        val destFile = File(fullBodyDestDir, "person_${UUID.randomUUID()}.$ext")
                        staged.copyTo(destFile, overwrite = true)
                        val inserted = repository.insertPersonPhoto(
                            PersonPhoto(
                                label = dto.label,
                                imagePath = destFile.absolutePath,
                                createdAt = dto.createdAt
                            )
                        )
                        if (dto.isActive || newActivePersonId == null) {
                            newActivePersonId = inserted.id
                        }
                        restoredPersonCount++
                    }
                }

                if (newActivePersonId != null) {
                    repository.settings.setActivePersonPhotoId(newActivePersonId)
                }

                BackupResultSummary(
                    clothingCount = restoredClothingCount,
                    personPhotoCount = restoredPersonCount
                )
            }
        } finally {
            runCatching { stagingDir.deleteRecursively() }
        }
    }
}
