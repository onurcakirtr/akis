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
- **İki tema:** Material You (duvar kâğıdına göre renk alır) ve **Nothing** (siyah-beyaz, kırmızı vurgu, nokta ızgarası, mono yazı); açık / koyu / sistem modu

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
ui/…                   Compose + Material 3 arayüzü (Yayın / Kaynaklar / Ayarlar)
```
