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

/**
 * A local, persistent clipboard archive.
 *
 * clipboard_timestamp is the timestamp supplied by ClipDescription for the
 * copy event. Android may notify listeners more than once for the same clip
 * (for example, when text classification status changes), so a unique
 * (content_hash, clipboard_timestamp) index makes event ingestion idempotent.
 */
class ClipStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "cyber_clip_archive.db"
        private const val DATABASE_VERSION = 2
        private const val FALLBACK_DUPLICATE_WINDOW_MS = 2_500L

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
                content_hash TEXT NOT NULL,
                clipboard_timestamp INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_clips_copied_at ON clips(copied_at DESC)")
        db.execSQL("CREATE INDEX idx_clips_favorite ON clips(is_favorite)")
        db.execSQL(
            """
            CREATE UNIQUE INDEX idx_unique_clipboard_event
            ON clips(content_hash, clipboard_timestamp)
            WHERE clipboard_timestamp > 0
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE clips ADD COLUMN clipboard_timestamp INTEGER NOT NULL DEFAULT 0"
            )

            // Collapse rapid duplicate rows created by repeated callbacks in v1.
            // Keep the newest row so its displayed timestamp is retained.
            db.execSQL(
                """
                DELETE FROM clips
                WHERE _id IN (
                    SELECT older._id
                    FROM clips AS older
                    INNER JOIN clips AS newer
                        ON newer.content_hash = older.content_hash
                       AND newer._id > older._id
                       AND newer.copied_at >= older.copied_at
                       AND newer.copied_at - older.copied_at <= $FALLBACK_DUPLICATE_WINDOW_MS
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_clipboard_event
                ON clips(content_hash, clipboard_timestamp)
                WHERE clipboard_timestamp > 0
                """.trimIndent()
            )
        }
    }

    /**
     * Saves one clipboard event. Pass ClipDescription.timestamp when available.
     * Repeated callbacks for the same source clip are ignored by the unique index.
     */
    fun insertClipboard(content: String, clipboardTimestamp: Long): Long {
        if (content.isBlank()) return -1L

        val now = System.currentTimeMillis()
        val eventTimestamp = clipboardTimestamp.takeIf { it > 0L } ?: now
        val hash = sha256(content)
        val db = writableDatabase

        db.beginTransaction()
        try {
            // Defensive fallback for sources/devices that return timestamp=0.
            if (clipboardTimestamp <= 0L) {
                val cursor = db.rawQuery(
                    "SELECT copied_at FROM clips WHERE content_hash = ? ORDER BY copied_at DESC LIMIT 1",
                    arrayOf(hash)
                )
                val lastCopiedAt = cursor.use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }
                if (lastCopiedAt != null &&
                    now >= lastCopiedAt &&
                    now - lastCopiedAt <= FALLBACK_DUPLICATE_WINDOW_MS
                ) {
                    return -1L
                }
            }

            val values = ContentValues().apply {
                put("content", content)
                put("copied_at", eventTimestamp)
                put("is_favorite", 0)
                put("content_hash", hash)
                put("clipboard_timestamp", clipboardTimestamp.coerceAtLeast(0L))
            }

            val conflictAlgorithm = if (clipboardTimestamp > 0L) {
                SQLiteDatabase.CONFLICT_IGNORE
            } else {
                SQLiteDatabase.CONFLICT_ABORT
            }

            val insertedId = db.insertWithOnConflict(
                "clips",
                null,
                values,
                conflictAlgorithm
            )
            if (insertedId > 0L) db.setTransactionSuccessful()
            return insertedId
        } finally {
            db.endTransaction()
        }
    }

    fun search(query: String, limit: Int = 300): List<ClipRecord> {
        val safeLimit = limit.coerceIn(1, 2_000)
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
        val values = ContentValues().apply {
            put("is_favorite", if (favorite) 1 else 0)
        }
        writableDatabase.update("clips", values, "_id = ?", arrayOf(id.toString()))
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
