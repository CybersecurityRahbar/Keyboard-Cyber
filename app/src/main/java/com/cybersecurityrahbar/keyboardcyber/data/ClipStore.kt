package com.cybersecurityrahbar.keyboardcyber.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

data class ClipRecord(
    val id: Long,
    val content: String,
    val copiedAt: Long,
    val isFavorite: Boolean
)

class ClipStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "cyber_clip_archive.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var instance: ClipStore? = null

        fun get(context: Context): ClipStore =
            instance ?: synchronized(this) {
                instance ?: ClipStore(context).also { instance = it }
            }
    }

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE clips (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                copied_at INTEGER NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                content_hash TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_clips_copied_at ON clips(copied_at DESC)")
        db.execSQL("CREATE INDEX idx_clips_favorite ON clips(is_favorite)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future database migrations must preserve existing clipboard history.
    }

    fun insert(content: String, copiedAt: Long = System.currentTimeMillis()): Long {
        if (content.isEmpty()) return -1L
        val values = ContentValues().apply {
            put("content", content)
            put("copied_at", copiedAt)
            put("is_favorite", 0)
            put("content_hash", sha256(content))
        }
        return writableDatabase.insertOrThrow("clips", null, values)
    }

    fun search(query: String, limit: Int = 300): List<ClipRecord> {
        val safeLimit = limit.coerceIn(1, 2000)
        val selection = if (query.isBlank()) null else "content LIKE ? ESCAPE '\\'"
        val args = if (query.isBlank()) null else arrayOf("%" + escapeLike(query) + "%")
        val cursor = readableDatabase.query(
            "clips",
            arrayOf("_id", "content", "copied_at", "is_favorite"),
            selection,
            args,
            null,
            null,
            "copied_at DESC",
            safeLimit.toString()
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        ClipRecord(
                            id = it.getLong(0),
                            content = it.getString(1),
                            copiedAt = it.getLong(2),
                            isFavorite = it.getInt(3) == 1
                        )
                    )
                }
            }
        }
    }

    fun recent(limit: Int = 4): List<ClipRecord> = search("", limit)

    fun count(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM clips", null).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun favoriteCount(): Long {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM clips WHERE is_favorite = 1",
            null
        ).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        val values = ContentValues().apply { put("is_favorite", if (favorite) 1 else 0) }
        writableDatabase.update("clips", values, "_id = ?", arrayOf(id.toString()))
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
