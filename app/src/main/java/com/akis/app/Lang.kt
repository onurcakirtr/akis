package com.akis.app

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Uygulama dili.
 *
 * Yeni dil eklemek için:
 *  1. res/values-<kod>/strings.xml dosyasını oluşturup res/values/strings.xml'i (İngilizce) çevir
 *  2. Aşağıdaki [SUPPORTED] listesine bir satır ekle
 * Android 13+ sistem dil menüsü (Ayarlar → Uygulamalar → Akış → Dil) derleme sırasında kendiliğinden oluşur.
 */
object Lang {
    /** Dil kodu → kendi dilindeki adı. Boş kod = telefonun dili. */
    val SUPPORTED = listOf(
        "tr" to "Türkçe",
        "en" to "English",
    )

    private lateinit var app: Context
    @Volatile private var cached: Pair<String, Context>? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    /** Seçili dil kodu; boşsa sistem dili. */
    val current: String get() = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')

    /** Ekranda ve web arayüzünde kullanılan gerçek dil (ör. "tr"). */
    val locale: Locale get() = current.takeIf { it.isNotEmpty() }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()

    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
        cached = null
        // TV'lerdeki klasör adları da yeni dile geçsin
        ServerState.accessChanged.tryEmit(Unit)
    }

    /** Servis, bildirim, widget ve web arayüzü için seçili dile ayarlanmış bağlam. */
    fun ctx(): Context {
        val tag = current
        cached?.let { (t, c) -> if (t == tag) return c }
        val c = if (tag.isEmpty()) app else {
            val conf = Configuration(app.resources.configuration)
            conf.setLocale(Locale.forLanguageTag(tag))
            app.createConfigurationContext(conf)
        }
        cached = tag to c
        return c
    }

    fun s(@StringRes id: Int): String = ctx().getString(id)

    fun s(@StringRes id: Int, vararg args: Any): String = ctx().getString(id, *args)

    /** Çoğul metin: q(R.plurals.count_devices, 3) → "3 devices" / "3 cihaz". */
    fun q(@PluralsRes id: Int, n: Int): String = ctx().resources.getQuantityString(id, n, n)
}
