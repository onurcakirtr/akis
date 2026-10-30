# Akış

Telefonundaki videoları, müzikleri ve fotoğrafları yerel ağdaki **VLC, akıllı TV, Kodi, Windows** gibi cihazlara UPnP/DLNA ile yayınlar. Reklam yok, internet yok, hesap yok.

## Özellikler
- **Otomatik keşif (SSDP):** VLC'de *Yerel Ağ → Universal Plug'n'Play* altında kendiliğinden görünür
- **Kitaplık:** Videolar (klasör klasör), Müzik (tümü / albümler / sanatçılar / klasörler), Fotoğraflar, Son eklenenler
- **Klasörlerim:** İstediğin klasörü (İndirilenler, SD kart, USB OTG) olduğu gibi paylaşırsın; videoyla aynı adı taşıyan `.srt` altyazılar otomatik eklenir
- **İleri/geri sarma** (HTTP Range), küçük resimler / albüm kapakları
- **Web arayüzü:** `http://telefon-ip:8200` adresinden herhangi bir tarayıcıda izleyebilirsin, QR kodla açılır
- **Canlı panel:** bağlı cihazlar, şu an ne oynatıldığı (ilerleme çubuğuyla), gönderilen veri, çalışma süresi, etkinlik akışı
- **Hızlı ayar kutucuğu:** bildirim panelinden tek dokunuşla aç/kapa
- Ekran kapalıyken de çalışır (foreground service + Wi-Fi/multicast kilidi)
- Telefonun hotspot'u açıkken de çalışır
- **Arama:** TV/VLC'den ve web arayüzünden başlık, sanatçı, albüm araması (Türkçe karakter duyarsız)
- **İzleme geçmişi:** *Devam et* ve *İzlenmemiş* klasörleri, izlenenlere ✓, kaldığın yerden devam
- **Gelişmiş altyazı:** .srt/.vtt/.ass/.sub, `film.tr.srt` gibi dilli adlar, Türkçe karakter düzeltme
- **Çift ses ve altyazı:** dublaj + orijinal ses ve gömülü (MKV) altyazılar; web'de ses/altyazı seçimi ses diline göre hatırlanır, gömülü altyazılar TV'lere de gider
- **Kendi web oynatıcısı:** telefon dosyayı tarayıcıya uygun parçalara çevirir (AC3/DTS ses → AAC); dokunmatik hareketler, klavye kısayolları, altyazı görünümü ve senkronu
- **Özellikler:** web'de her öğe için bilgi paneli (EXIF, video/ses parçaları, etiketler); klasörü ZIP olarak indirme
- **Cihaz yönetimi:** bağlı cihazı anında kesme, süreli yasaklama ya da kalıcı engelleme
- **Hızlı paylaşım:** Paylaş → Akış ile herhangi bir dosya için süreli (ya da tek seferlik) indirme bağlantısı ve QR kod
- **Anında yenileme:** yeni dosyalar TV/VLC'ye (UPnP olay bildirimi) ve açık web sayfasına kendiliğinden yansır
- **Web'de çoklu seçim:** seçilenleri ZIP indirme, müzik sırasına ekleme
- **Web'den yönetim:** ayrı yönetici şifresi + telefonda onay, tek ve süreli oturum; güvenlik ayarları yalnızca telefondan
- **Web'den yükleme:** bilgisayarın tarayıcısından telefona sürükle-bırak dosya gönder
- **Güvenlik:** yeni cihazlar için *İzin ver / Engelle* onayı, web PIN'i
- **Gizli klasörler:** `.nomedia` içerenler ve elle gizlediklerin paylaşılmaz
- **Widget'lar ve bildirim:** aç/kapat widget'ı, durum widget'ı, bildirimde ve kutucukta ilerleme ile oynatılan dosya
- Her özellik *Ayarlar → Özellikler*'den açılıp kapatılabilir
- **12 tema:** Material You, Nothing, Samsung, Cyberpunk, iOS, Terminal, Dracula, Okyanus, Gün batımı, Sakura, Orman, Kağıt; açık / koyu / sistem modu (web arayüzü de temayı izler)
- **Dil:** Türkçe ve İngilizce; uygulama içinden ya da Android 13+ sistem dil menüsünden seçilir

## Derleme
### Android Studio
1. Bu klasörü **File → Open** ile aç
2. Gradle senkronizasyonu bitince ▶ Run (telefon USB'den bağlı olsun) ya da **Build → Build APK(s)**
3. Android Studio sürüm yükseltme önerirse (AGP/Kotlin/Compose) kabul edebilirsin

### GitHub (bilgisayarsız)
Projeyi bir GitHub reposuna yükle. `Actions` sekmesinde **APK derle** iş akışı kendiliğinden çalışır; bitince *Artifacts* altından `Akis-apk` dosyasını indirip telefona kurarsın.

## Kullanım
1. Telefonla diğer cihazı aynı Wi-Fi ağına bağla
2. Akış'ı aç ve büyük düğmeye bas
3. VLC → *Yerel Ağ* → *Universal Plug'n'Play* → **Akış • (telefon adı)**

### Görünmüyorsa
- Misafir ağlarında ve bazı modemlerde **AP/istemci izolasyonu** açık olur, cihazlar birbirini göremez. Modem ayarlarından kapat ya da telefonun hotspot'unu kullan.
- VLC'de listeyi yenile, olmazsa Akış'ı kapatıp tekrar aç.
- Nothing OS'ta pil optimizasyonu: *Ayarlar → Uygulamalar → Akış → Pil → Kısıtlanmamış*

## Yeni dil eklemek
1. `app/src/main/res/values/strings.xml` (İngilizce) dosyasını `values-<kod>/strings.xml` olarak kopyalayıp çevir
2. `Lang.kt` içindeki `SUPPORTED` listesine bir satır ekle

## Yeni tema eklemek
`ui/theme/Theme.kt` içindeki `Themes.ALL` listesine bir `AppTheme` kaydı, `strings.xml` dosyalarına da `theme_<id>` adını ekle.

## Kod yapısı
```
net/HttpServer.kt      Bağımlılıksız HTTP/1.1 sunucu (keep-alive, Range)
net/Ssdp.kt            Ağ duyurusu ve M-SEARCH cevapları
net/NetUtil.kt         Wi-Fi / hotspot IP bulma
dlna/MediaLibrary.kt   MediaStore + klasör (SAF) içerik ağacı, küçük resimler
dlna/Xml.kt            UPnP cihaz tanımı, SCPD, DIDL-Lite
dlna/DlnaServer.kt     Yönlendirme, SOAP Browse, dosya akışı
dlna/WebUi.kt          Tarayıcı arayüzü
service/DlnaService.kt Ön plan servisi, kilitler, bildirim, ağ değişimi
service/ServerTileService.kt  Hızlı ayar kutucuğu
dlna/Subtitles.kt      Altyazı eşleştirme ve srt/vtt dönüştürme
dlna/Uploads.kt        Web'den gelen dosyaları kaydetme
WatchHistory.kt        İzleme geçmişi
DeviceAccess.kt        Cihaz onayı ve web PIN'i
widget/Widgets.kt      Ana ekran widget'ları
ui/…                   Compose + Material 3 arayüzü (Yayın / Kaynaklar / Ayarlar)
```
