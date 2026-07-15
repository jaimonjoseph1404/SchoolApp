package org.familytools.educationtracker.services

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import org.familytools.educationtracker.data.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup/Restore module. Unlike the Kivy build (where the `cryptography`
 * package needed a Rust toolchain to cross-compile for Android and had to
 * be made optional), AES-256-GCM here uses javax.crypto — part of the
 * Android platform, always available, no extra dependency at all.
 */
object BackupService {
    // Parents before children, so a restore can insert in this order with
    // foreign keys off.
    private val TABLE_ORDER = listOf(
        "children", "academic_years", "classes", "terms", "subjects", "exams", "marks",
        "teachers", "teacher_assignments", "expense_categories", "expenses", "fee_receipts",
        "ocr_history", "backups",
    )

    private const val MAGIC = "EDU1"
    private const val PBKDF2_ITERATIONS = 200_000

    private fun db(context: Context): SupportSQLiteDatabase =
        AppDatabase.getInstance(context).openHelper.writableDatabase

    private fun exportAllToJson(context: Context): JSONObject {
        val db = db(context)
        val root = JSONObject()
        for (table in TABLE_ORDER) {
            val array = JSONArray()
            db.query("SELECT * FROM $table").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until cursor.columnCount) {
                        val name = cursor.getColumnName(i)
                        when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                            android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(name, cursor.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(name, cursor.getDouble(i))
                            else -> row.put(name, cursor.getString(i))
                        }
                    }
                    array.put(row)
                }
            }
            root.put(table, array)
        }
        return root
    }

    private fun restoreFromJson(context: Context, json: JSONObject) {
        val db = db(context)
        db.execSQL("PRAGMA foreign_keys = OFF")
        db.beginTransaction()
        try {
            for (table in TABLE_ORDER.reversed()) db.execSQL("DELETE FROM $table")
            for (table in TABLE_ORDER) {
                val array = json.optJSONArray(table) ?: continue
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    val columns = row.keys().asSequence().toList()
                    val placeholders = columns.joinToString(",") { "?" }
                    val values = columns.map { row.get(it).takeIf { v -> v != JSONObject.NULL } }.toTypedArray()
                    db.execSQL(
                        "INSERT INTO $table (${columns.joinToString(",")}) VALUES ($placeholders)",
                        values,
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    private fun timestampedName(prefix: String, ext: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$ts.$ext"
    }

    private fun backupsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }

    private fun recordBackup(context: Context, path: String, type: String) {
        db(context).execSQL(
            "INSERT INTO backups (filePath, backupType, createdAt) VALUES (?, ?, ?)",
            arrayOf(path, type, System.currentTimeMillis()),
        )
    }

    fun lastBackupPath(context: Context): Pair<String, Long>? {
        db(context).query("SELECT filePath, createdAt FROM backups ORDER BY createdAt DESC LIMIT 1").use {
            return if (it.moveToFirst()) it.getString(0) to it.getLong(1) else null
        }
    }

    fun exportJson(context: Context): File {
        val file = File(backupsDir(context), timestampedName("backup", "json"))
        file.writeText(exportAllToJson(context).toString(2))
        recordBackup(context, file.absolutePath, "json")
        return file
    }

    fun importJson(context: Context, file: File) {
        restoreFromJson(context, JSONObject(file.readText()))
    }

    fun exportZip(context: Context): File {
        val json = exportAllToJson(context)
        val file = File(backupsDir(context), timestampedName("backup", "zip"))
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.toString(2).toByteArray())
            zip.closeEntry()
        }
        recordBackup(context, file.absolutePath, "zip")
        return file
    }

    fun importZip(context: Context, file: File) {
        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "data.json") {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    restoreFromJson(context, JSONObject(text))
                    return
                }
                entry = zip.nextEntry
            }
        }
        throw IllegalArgumentException("data.json not found in archive")
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun exportEncrypted(context: Context, password: String): File {
        val payload = exportAllToJson(context).toString().toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(payload)

        val file = File(backupsDir(context), timestampedName("backup_encrypted", "bak"))
        file.outputStream().use { out ->
            out.write(MAGIC.toByteArray())
            out.write(salt)
            out.write(nonce)
            out.write(ciphertext)
        }
        recordBackup(context, file.absolutePath, "encrypted")
        return file
    }

    fun importEncrypted(context: Context, file: File, password: String) {
        val bytes = file.readBytes()
        val magic = bytes.copyOfRange(0, 4).toString(Charsets.UTF_8)
        require(magic == MAGIC) { "Not a recognized encrypted backup file" }
        val salt = bytes.copyOfRange(4, 20)
        val nonce = bytes.copyOfRange(20, 32)
        val ciphertext = bytes.copyOfRange(32, bytes.size)
        val key = deriveKey(password, salt)

        val payload = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw IllegalArgumentException("Incorrect password or corrupted backup file", e)
        }
        restoreFromJson(context, JSONObject(payload.toString(Charsets.UTF_8)))
    }
}
