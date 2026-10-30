package com.akis.app.dlna

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import com.akis.app.Prefs
import com.akis.app.Lang
import com.akis.app.R
import com.akis.app.WatchHistory
import java.io.ByteArrayOutputStream

enum class Kind { FOLDER, VIDEO, AUDIO, IMAGE, SUBTITLE }

data class BucketInfo(val id: String, val name: String, val count: Int, val kinds: Set<Kind>)

data class Entry(
    val id: String,
    val parentId: String,
    val title: String,
    val kind: Kind,
    val mime: String = "",
    val size: Long = 0,
    val uri: Uri? = null,
    val durationMs: Long = 0,
    val dateSec: Long = 0,
    val artist: String? = null,
    val album: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val childCount: Int = -1,
    val subtitles: List<Sub> = emptyList(),
    val ext: String = "",
)

/**
 * İçerik ağacı:
 *  0 ─┬─ r  Son eklenenler
 *     ├─ v  Videolar ── v:all, v:b:<klasör>
 *     ├─ a  Müzik ───── a:all, a:albums → a:al:<id>, a:artists → a:ar:<id>, a:b:<klasör>
 *     ├─ i  Fotoğraflar ─ i:all, i:b:<klasör>
 *     └─ f  Klasörlerim ─ t:<n> → d:<n>:<docId> … dosyalar fd:<n>:<docId>
 *  Medya öğeleri: m:<v|a|i>:<MediaStore id>
 */
class MediaLibrary(private val ctx: Context) {
    private val cr: ContentResolver = ctx.contentResolver
    private val s get() = Prefs.current

    private val thumbCache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    private val durationCache = LruCache<String, Long>(512)

    /** Klasörlerim'deki videoların süresi MediaStore'da yok; gerektiğinde dosyadan okunur. */
    fun durationOf(e: Entry): Long {
        if (e.durationMs > 0) return e.durationMs
        val uri = e.uri ?: return 0
        durationCache.get(e.id)?.let { return it }
        val d = try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            } finally {
                r.release()
            }
        } catch (_: Exception) {
            0L
        }
        durationCache.put(e.id, d)
        return d
    }

    /** Arama için Klasörlerim'in dosya listesi önbelleği (klasör sırası → (zaman, dosyalar)). */
    private val treeIndex = HashMap<Int, Pair<Long, List<Entry>>>()

    /** SAF klasörü için ".nomedia" var mı önbelleği (doküman URI → sonuç). */
    private val nomediaCache = LruCache<String, Boolean>(512)

    /** Klasör listelenirken bulunan altyazılar (video ID → altyazılar); tekil aramada (web oynatıcı) kullanılır. */
    private val subCache = LruCache<String, List<Sub>>(256)

    private val hiding get() = s.hideFolders

    private fun folder(id: String, parent: String, title: String, count: Int = -1) =
        Entry(id, parent, title, Kind.FOLDER, childCount = count)

    fun root(): List<Entry> = buildList {
        if (s.history) {
            add(folder("h:c", "0", Lang.s(R.string.continue_watching)))
            add(folder("h:u", "0", Lang.s(R.string.unwatched)))
        }
        if (s.showRecent && (s.shareVideo || s.shareImage)) add(folder("r", "0", Lang.s(R.string.recent)))
        if (s.shareVideo) add(folder("v", "0", Lang.s(R.string.videos)))
        if (s.shareAudio) add(folder("a", "0", Lang.s(R.string.music)))
        if (s.shareImage) add(folder("i", "0", Lang.s(R.string.photos)))
        if (s.folders.isNotEmpty()) add(folder("f", "0", Lang.s(R.string.my_folders), s.folders.size))
    }

    fun children(id: String): List<Entry> = try {
        when {
            id == "0" -> root()
            id == "r" -> recent()
            id == "v" && s.shareVideo -> listOf(folder("v:all", "v", Lang.s(R.string.all_videos))) + buckets(VIDEO, "v")
            id == "a" && s.shareAudio -> listOf(
                folder("a:all", "a", Lang.s(R.string.all_songs)),
                folder("a:albums", "a", Lang.s(R.string.albums)),
                folder("a:artists", "a", Lang.s(R.string.artists)),
            ) + buckets(AUDIO, "a")
            id == "i" && s.shareImage -> listOf(folder("i:all", "i", Lang.s(R.string.all_photos))) + buckets(IMAGE, "i")
            id == "f" -> trees()
            id == "h:c" && s.history -> WatchHistory.inProgress().take(60).mapNotNull { r ->
                lookup(r.id)?.copy(parentId = id)
            }
            id == "h:u" && s.history -> {
                val videos = (if (s.shareVideo) queryMedia(Kind.VIDEO, null, null, id) else emptyList()) +
                    s.folders.indices.flatMap { i -> indexTree(i).filter { it.kind == Kind.VIDEO }.map { it.copy(parentId = id) } }
                videos.filter { !WatchHistory.isWatched(it.id) }
            }

            id == "v:all" && s.shareVideo -> queryMedia(Kind.VIDEO, null, null, id)
            id.startsWith("v:b:") && s.shareVideo -> queryMedia(Kind.VIDEO, "${MediaStore.MediaColumns.BUCKET_ID}=?", arrayOf(id.substring(4)), id)

            id == "a:all" && s.shareAudio -> queryMedia(Kind.AUDIO, "${MediaStore.Audio.Media.IS_MUSIC}!=0", null, id)
            id == "a:albums" && s.shareAudio -> albums()
            id == "a:artists" && s.shareAudio -> artists()
            id.startsWith("a:al:") && s.shareAudio -> queryMedia(
                Kind.AUDIO, "${MediaStore.Audio.Media.ALBUM_ID}=?", arrayOf(id.substring(5)), id,
                sort = "${MediaStore.Audio.Media.TRACK} ASC",
            )
            id.startsWith("a:ar:") && s.shareAudio -> queryMedia(Kind.AUDIO, "${MediaStore.Audio.Media.ARTIST_ID}=?", arrayOf(id.substring(5)), id)
            id.startsWith("a:b:") && s.shareAudio -> queryMedia(Kind.AUDIO, "${MediaStore.MediaColumns.BUCKET_ID}=?", arrayOf(id.substring(4)), id)

            id == "i:all" && s.shareImage -> queryMedia(Kind.IMAGE, null, null, id)
            id.startsWith("i:b:") && s.shareImage -> queryMedia(Kind.IMAGE, "${MediaStore.MediaColumns.BUCKET_ID}=?", arrayOf(id.substring(4)), id)

            id.startsWith("t:") -> treeChildren(id.substring(2).toInt(), null, id)
            id.startsWith("d:") -> {
                val rest = id.substring(2)
                treeChildren(rest.substringBefore(':').toInt(), rest.substringAfter(':'), id)
            }
            else -> emptyList()
        }
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun metadata(id: String): Entry? = when (id) {
        "0" -> folder("0", "-1", s.name, root().size)
        "r", "v", "a", "i", "f", "h:c", "h:u" -> root().firstOrNull { it.id == id }
        else -> lookup(id) ?: folder(id, parentOf(id), id)
    }

    fun parentOf(id: String): String = when {
        id.startsWith("m:v:") -> "v:all"
        id.startsWith("m:a:") -> "a:all"
        id.startsWith("m:i:") -> "i:all"
        id.startsWith("a:al:") -> "a:albums"
        id.startsWith("a:ar:") -> "a:artists"
        id.startsWith("v:") -> "v"
        id.startsWith("a:") -> "a"
        id.startsWith("i:") -> "i"
        id.startsWith("t:") -> "f"
        id.startsWith("h:") -> "0"
        id.startsWith("d:") || id.startsWith("fd:") -> "t:" + id.substringAfter(':').substringBefore(':')
        else -> "0"
    }

    /** Tek bir öğeyi ID'den bulur (dosya sunarken kullanılır). */
    fun lookup(id: String): Entry? = try {
        when {
            id.startsWith("m:") -> {
                val parts = id.split(':')
                val kind = when (parts[1]) {
                    "v" -> Kind.VIDEO
                    "a" -> Kind.AUDIO
                    else -> Kind.IMAGE
                }
                val allowed = when (kind) {
                    Kind.VIDEO -> s.shareVideo
                    Kind.AUDIO -> s.shareAudio
                    else -> s.shareImage
                }
                if (!allowed) null
                else queryMedia(kind, "${MediaStore.MediaColumns._ID}=?", arrayOf(parts[2]), parentOf(id)).firstOrNull()
            }
            id.startsWith("fd:") -> lookupDocument(id)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Başlık / sanatçı / albümde arar (Türkçe karakterler katlanır: "sarki" → "şarkı").
     * @param kinds boşsa tüm türler
     */
    fun search(query: String, kinds: Set<Kind> = emptySet(), limit: Int = 300): List<Entry> {
        val q = fold(query.trim())
        val want = kinds.ifEmpty { setOf(Kind.VIDEO, Kind.AUDIO, Kind.IMAGE) }
        fun hit(e: Entry) = q.isEmpty() || listOfNotNull(e.title, e.artist, e.album).any { q in fold(it) }
        val out = ArrayList<Entry>()
        try {
            if (Kind.VIDEO in want && s.shareVideo) out += queryMedia(Kind.VIDEO, null, null, "0").filter(::hit)
            if (Kind.AUDIO in want && s.shareAudio) {
                out += queryMedia(Kind.AUDIO, "${MediaStore.Audio.Media.IS_MUSIC}!=0", null, "0").filter(::hit)
            }
            if (Kind.IMAGE in want && s.shareImage) out += queryMedia(Kind.IMAGE, null, null, "0").filter(::hit)
        } catch (_: Exception) {
        }
        for (i in s.folders.indices) {
            if (out.size >= limit) break
            out += indexTree(i).filter { it.kind in want && hit(it) }
        }
        return out.take(limit)
    }

    /** Klasörlerim'deki dosyaları (en fazla 5 seviye, 5000 dosya) tarar; 5 dk önbellekte tutar. */
    private fun indexTree(idx: Int): List<Entry> {
        val now = System.currentTimeMillis()
        synchronized(treeIndex) {
            treeIndex[idx]?.let { (t, list) -> if (now - t < 5 * 60_000) return list }
        }
        val out = ArrayList<Entry>()
        fun walk(docId: String?, parentId: String, depth: Int) {
            if (depth > 5 || out.size >= 5000) return
            val children = try { treeChildren(idx, docId, parentId) } catch (_: Exception) { emptyList() }
            for (c in children) {
                if (c.kind == Kind.FOLDER) walk(c.id.substringAfter(':').substringAfter(':'), c.id, depth + 1)
                else out += c
            }
        }
        walk(null, "t:$idx", 0)
        synchronized(treeIndex) { treeIndex[idx] = now to out }
        return out
    }

    fun clearSearchCache() = synchronized(treeIndex) { treeIndex.clear() }

    fun count(kind: Kind): Int = try {
        val (uri, sel) = when (kind) {
            Kind.VIDEO -> VIDEO to null
            Kind.AUDIO -> AUDIO to "${MediaStore.Audio.Media.IS_MUSIC}!=0"
            else -> IMAGE to null
        }
        cr.query(uri, arrayOf(MediaStore.MediaColumns._ID), sel, null, null)?.use { it.count } ?: 0
    } catch (e: Exception) {
        0
    }

    fun thumbnail(id: String): ByteArray? {
        thumbCache.get(id)?.let { return it }
        val e = lookup(id) ?: return null
        val uri = e.uri ?: return null
        val bmp: Bitmap = try {
            if (id.startsWith("fd:")) DocumentsContract.getDocumentThumbnail(cr, uri, Point(320, 320), null)
            else cr.loadThumbnail(uri, Size(320, 320), null)
        } catch (ex: Exception) {
            null
        } ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        bmp.recycle()
        return out.toByteArray().also { thumbCache.put(id, it) }
    }

    // ---------------- MediaStore ----------------

    private fun recent(): List<Entry> {
        val list = ArrayList<Entry>()
        if (s.shareVideo) list += queryMedia(Kind.VIDEO, null, null, "r", limit = 40)
        if (s.shareImage) list += queryMedia(Kind.IMAGE, null, null, "r", limit = 40)
        return list.sortedByDescending { it.dateSec }.take(60)
    }

    private fun buckets(uri: Uri, prefix: String): List<Entry> {
        val map = LinkedHashMap<String, Pair<String, Int>>()
        cr.query(
            uri,
            arrayOf(MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME),
            null, null,
            "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val bid = c.getString(0) ?: continue
                val name = c.getString(1) ?: Lang.s(R.string.other)
                val prev = map[bid]
                map[bid] = (prev?.first ?: name) to ((prev?.second ?: 0) + 1)
            }
        }
        return map.filterKeys { !(hiding && it in s.hiddenBuckets) }
            .map { (bid, v) -> folder("$prefix:b:$bid", prefix, v.first, v.second) }
    }

    /** Kaynaklar ekranındaki "Gizli klasörler" listesi için: gizlilerle birlikte tüm klasörler. */
    fun allBuckets(): List<BucketInfo> {
        val map = LinkedHashMap<String, BucketInfo>()
        for ((uri, kind) in listOf(VIDEO to Kind.VIDEO, AUDIO to Kind.AUDIO, IMAGE to Kind.IMAGE)) {
            try {
                cr.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val bid = c.getString(0) ?: continue
                        val prev = map[bid]
                        map[bid] = if (prev == null) BucketInfo(bid, c.getString(1) ?: Lang.s(R.string.other), 1, setOf(kind))
                        else prev.copy(count = prev.count + 1, kinds = prev.kinds + kind)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return map.values.sortedBy { it.name.lowercase() }
    }

    private fun albums(): List<Entry> {
        val out = ArrayList<Entry>()
        cr.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.NUMBER_OF_SONGS),
            null, null, "${MediaStore.Audio.Albums.ALBUM} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += folder("a:al:${c.getLong(0)}", "a:albums", c.getString(1) ?: Lang.s(R.string.unknown_album), c.getInt(2))
            }
        }
        return out
    }

    private fun artists(): List<Entry> {
        val out = ArrayList<Entry>()
        cr.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Artists.NUMBER_OF_TRACKS),
            null, null, "${MediaStore.Audio.Artists.ARTIST} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += folder("a:ar:${c.getLong(0)}", "a:artists", c.getString(1) ?: Lang.s(R.string.unknown_artist), c.getInt(2))
            }
        }
        return out
    }

    private fun queryMedia(
        kind: Kind,
        sel: String?,
        args: Array<String>?,
        parent: String,
        sort: String? = null,
        limit: Int = Int.MAX_VALUE,
    ): List<Entry> {
        val base = when (kind) {
            Kind.VIDEO -> VIDEO
            Kind.AUDIO -> AUDIO
            else -> IMAGE
        }
        val proj = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
        if (kind != Kind.IMAGE) proj.add(MediaStore.MediaColumns.DURATION)
        if (kind == Kind.AUDIO) proj.addAll(
            listOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM),
        )
        val order = sort ?: if (kind == Kind.AUDIO) "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        else "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val p = when (kind) {
            Kind.VIDEO -> "v"
            Kind.AUDIO -> "a"
            else -> "i"
        }
        var where = sel
        var whereArgs = args
        val hidden = if (hiding) s.hiddenBuckets.toList() else emptyList()
        if (hidden.isNotEmpty()) {
            val clause = "${MediaStore.MediaColumns.BUCKET_ID} NOT IN (${hidden.joinToString(",") { "?" }})"
            where = if (sel == null) clause else "($sel) AND $clause"
            whereArgs = (args?.toList() ?: emptyList<String>()).plus(hidden).toTypedArray()
        }
        val out = ArrayList<Entry>()
        cr.query(base, proj.toTypedArray(), where, whereArgs, order)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iDur = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            while (c.moveToNext() && out.size < limit) {
                val rowId = c.getLong(iId)
                val name = c.getString(iName) ?: "dosya"
                val title = if (iTitle >= 0) c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                else name.substringBeforeLast('.')
                out += Entry(
                    id = "m:$p:$rowId",
                    parentId = parent,
                    title = title,
                    kind = kind,
                    mime = mimeFor(name, c.getString(iMime)),
                    size = c.getLong(iSize),
                    uri = ContentUris.withAppendedId(base, rowId),
                    durationMs = if (iDur >= 0) c.getLong(iDur) else 0,
                    dateSec = c.getLong(iDate),
                    artist = if (iArtist >= 0) c.getString(iArtist)?.takeIf { it != MediaStore.UNKNOWN_STRING } else null,
                    album = if (iAlbum >= 0) c.getString(iAlbum) else null,
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    ext = name.substringAfterLast('.', "bin").lowercase(),
                )
            }
        }
        return out
    }

    // ---------------- Klasörlerim (SAF) ----------------

    private fun trees(): List<Entry> =
        s.folders.mapIndexed { i, u -> folder("t:$i", "f", treeName(ctx, u)) }

    private data class Row(val doc: String, val name: String, val mime: String?, val size: Long, val mod: Long)

    private fun treeChildren(idx: Int, docId: String?, parentId: String): List<Entry> {
        val tree = Uri.parse(s.folders.getOrNull(idx) ?: return emptyList())
        val parentDoc = docId ?: DocumentsContract.getTreeDocumentId(tree)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDoc)
        val rows = ArrayList<Row>()
        cr.query(
            uri,
            arrayOf(
                Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                rows += Row(
                    doc = c.getString(0),
                    name = c.getString(1) ?: "?",
                    mime = c.getString(2),
                    size = if (c.isNull(3)) 0 else c.getLong(3),
                    mod = if (c.isNull(4)) 0 else c.getLong(4) / 1000,
                )
            }
        }
        if (hiding && rows.any { it.name == ".nomedia" }) return emptyList()
        val visible = rows.filter { r ->
            !r.name.startsWith(".") &&
                !(hiding && r.mime == Document.MIME_TYPE_DIR && hasNomedia(tree, r.doc))
        }
        val subs = subtitlesFor(idx, visible.filter { kindOf(it.name, it.mime) == Kind.SUBTITLE }, visible)
        val dirs = visible.filter { it.mime == Document.MIME_TYPE_DIR }
            .sortedBy { it.name.lowercase() }
            .map { folder("d:$idx:${it.doc}", parentId, it.name) }
        val files = visible.mapNotNull { r ->
            if (r.mime == Document.MIME_TYPE_DIR) return@mapNotNull null
            val k = kindOf(r.name, r.mime) ?: return@mapNotNull null
            if (k == Kind.SUBTITLE) return@mapNotNull null
            val base = r.name.substringBeforeLast('.')
            Entry(
                id = "fd:$idx:${r.doc}",
                parentId = parentId,
                title = base,
                kind = k,
                mime = mimeFor(r.name, r.mime),
                size = r.size,
                uri = DocumentsContract.buildDocumentUriUsingTree(tree, r.doc),
                dateSec = r.mod,
                subtitles = if (k == Kind.VIDEO) subs[base.lowercase()].orEmpty().also { subCache.put("fd:$idx:${r.doc}", it) } else emptyList(),
                ext = r.name.substringAfterLast('.', "bin").lowercase(),
            )
        }.sortedBy { it.title.lowercase() }
        return dirs + files
    }

    /** Aynı klasördeki altyazıları videolara bağlar (videonun küçük harfli adı → altyazılar). */
    private fun subtitlesFor(idx: Int, subRows: List<Row>, all: List<Row>): Map<String, List<Sub>> {
        if (subRows.isEmpty()) return emptyMap()
        if (!s.subtitles) {
            // Basit mod: yalnızca aynı adlı .srt
            return subRows.filter { it.name.endsWith(".srt", true) }
                .associate { subtitleBase(it.name) to listOf(Sub("fd:$idx:${it.doc}", "", "srt")) }
        }
        val videos = all.filter { kindOf(it.name, it.mime) == Kind.VIDEO }.map { it.name.substringBeforeLast('.').lowercase() }
        val out = HashMap<String, MutableList<Sub>>()
        for (r in subRows) {
            val (video, lang) = Subtitles.match(r.name.substringBeforeLast('.').lowercase(), videos) ?: continue
            out.getOrPut(video) { ArrayList() } += Sub("fd:$idx:${r.doc}", lang, r.name.substringAfterLast('.').lowercase())
        }
        return out.mapValues { Subtitles.sort(it.value, s.subLang) }
    }

    /** Tek bir video için altyazılar: önce önbellek, yoksa (yerel depolamada) üst klasörü tarar. */
    fun subtitlesOf(e: Entry): List<Sub> {
        if (e.kind != Kind.VIDEO || !e.id.startsWith("fd:")) return e.subtitles
        subCache.get(e.id)?.let { return it }
        val rest = e.id.substring(3)
        val idx = rest.substringBefore(':').toIntOrNull() ?: return emptyList()
        val doc = rest.substringAfter(':')
        if ('/' !in doc) return emptyList()
        return try {
            treeChildren(idx, doc.substringBeforeLast('/'), "")
            subCache.get(e.id) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun hasNomedia(tree: Uri, doc: String): Boolean {
        val key = "$tree|$doc"
        nomediaCache.get(key)?.let { return it }
        val found = try {
            cr.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc),
                arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null,
            )?.use { c ->
                var f = false
                while (!f && c.moveToNext()) f = c.getString(0) == ".nomedia"
                f
            } ?: false
        } catch (_: Exception) {
            false
        }
        nomediaCache.put(key, found)
        return found
    }

    private fun lookupDocument(id: String): Entry? {
        val rest = id.substring(3)
        val idx = rest.substringBefore(':').toInt()
        val doc = rest.substringAfter(':')
        val tree = Uri.parse(s.folders.getOrNull(idx) ?: return null)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, doc)
        cr.query(
            uri,
            arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val name = c.getString(0) ?: "dosya"
            val kind = kindOf(name, c.getString(1)) ?: return null
            return Entry(
                id = id,
                parentId = parentOf(id),
                title = name.substringBeforeLast('.'),
                kind = kind,
                mime = mimeFor(name, c.getString(1)),
                size = if (c.isNull(2)) 0 else c.getLong(2),
                uri = uri,
                dateSec = if (c.isNull(3)) 0 else c.getLong(3) / 1000,
                ext = name.substringAfterLast('.', "bin").lowercase(),
            )
        }
        return null
    }

    companion object {
        val VIDEO: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val AUDIO: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val IMAGE: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "m2ts", "mpg", "mpeg", "wmv", "flv", "3gp")
        private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac", "amr")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        private val SUB_EXT = Subtitles.EXT
        private val EXTRA_MIME = mapOf(
            "mkv" to "video/x-matroska", "ts" to "video/mp2t", "m2ts" to "video/mp2t", "flac" to "audio/flac",
            "opus" to "audio/ogg", "m4a" to "audio/mp4", "srt" to "application/x-subrip", "vtt" to "text/vtt",
            "ass" to "text/x-ssa", "ssa" to "text/x-ssa", "heic" to "image/heic", "webp" to "image/webp",
        )

        fun kindOf(name: String, mime: String?): Kind? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                ext in SUB_EXT -> Kind.SUBTITLE
                mime?.startsWith("video/") == true || ext in VIDEO_EXT -> Kind.VIDEO
                mime?.startsWith("audio/") == true || ext in AUDIO_EXT -> Kind.AUDIO
                mime?.startsWith("image/") == true || ext in IMAGE_EXT -> Kind.IMAGE
                else -> null
            }
        }

        fun mimeFor(name: String, given: String?): String {
            if (given != null && given.contains('/') && given != "application/octet-stream") return given
            val ext = name.substringAfterLast('.', "").lowercase()
            return EXTRA_MIME[ext] ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }

        /** Aramada karşılaştırma için: küçük harf + Türkçe karakter katlama. */
        fun fold(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s) sb.append(
                when (ch) {
                    'ç', 'Ç' -> 'c'; 'ğ', 'Ğ' -> 'g'; 'ı', 'I', 'İ' -> 'i'; 'ö', 'Ö' -> 'o'; 'ş', 'Ş' -> 's'; 'ü', 'Ü' -> 'u'
                    else -> ch.lowercaseChar()
                },
            )
            return sb.toString()
        }

        private fun subtitleBase(name: String): String {
            var b = name.substringBeforeLast('.').lowercase()
            for (lang in listOf(".tr", ".tur", ".turkish", ".en", ".eng")) b = b.removeSuffix(lang)
            return b
        }

        fun treeName(ctx: Context, uriStr: String): String {
            return try {
                val tree = Uri.parse(uriStr)
                val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                ctx.contentResolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: DocumentsContract.getTreeDocumentId(tree).substringAfter(':')
            } catch (e: Exception) {
                Lang.s(R.string.folder)
            }
        }
    }
}
