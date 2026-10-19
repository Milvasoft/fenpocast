# Fenpo Cast: Google Play gönderimi

Play Console'a girilecek her şey bu belgeden kopyalanır. Hesap: Milvasoft (kuruluş hesabı; kapalı test zorunluluğu yok). Fenpo for Android ile aynı düzen (`fenpo-android/docs/android/PLAY-LISTING.md`).

## 1. Uygulama

| Alan | Değer |
|---|---|
| Uygulama adı | Fenpo Cast |
| Paket | `com.fenpo.cast` |
| Varsayılan dil | Türkçe (tr-TR); ikinci dil English (en-US) |
| Tür | Uygulama, ücretsiz |
| Kategori | Araçlar (Tools) |
| Cihazlar | Yalnız Android TV (`android.software.leanback` zorunlu); telefon ve tablete çıkmaz |
| Sürüm | `versionName` 1.0.0, `versionCode` 1. Her yüklemede `versionCode` artar |
| Paket dosyası | `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| İmza | Play App Signing açık; yükleme anahtarı Fenpo for Android'inkiyle aynı (`FENPO_UPLOAD_*`, `~/.gradle/gradle.properties`) |
| Gizlilik politikası | https://fenpo.milvasoft.com/cast/privacy (`fenpo-website/cast/privacy.html`) |
| Kaynak kodu | https://github.com/Milvasoft/fenpocast (GPLv3 gereği açık, bkz. §5) |

## 2. Mağaza metinleri

Kurallar: Apple'ın özellik adları (AirPlay, Apple TV) yok; başka uygulamaların adları (YouTube, Netflix) yok. "iPhone" uyumluluğu anlatmak için geçer. iOS'un menü adları (Denetim Merkezi, Ekran Yansıtma) yönerge olarak geçer.

### Türkçe (tr-TR)

**Başlık** (≤ 30): `Fenpo Cast` (10)

**Kısa açıklama** (≤ 80): `iPhone ekranını, müziğini ve videolarını TV'ne gönder. iPhone'a kurulum yok.` (76)

**Tam açıklama:**

```
Fenpo Cast, Android TV'ni iPhone'un için bir ekrana çevirir. iPhone'a hiçbir şey kurman gerekmez: TV, iPhone'un kendi Ekran Yansıtma listesinde görünür.

Ekranını yansıt
• iPhone'da Denetim Merkezi'ni aç, Ekran Yansıtma'ya dokun ve TV'ni seç.
• Görüntü TV'nin donanım çözücüsüyle, düşük gecikmeyle gelir; ses görüntüyle uyumlu.
• iPhone'u çevirdiğinde görüntü de döner.

Müzik
• Müzik ve podcast uygulamalarında ses çıkışı düğmesinden TV'ni seç.
• Albüm kapağı, parça, sanatçı ve ilerleme TV'de görünür; ses seviyesini iPhone'dan ayarla.

Video
• Video uygulamalarında videoyu TV'ye gönder; TV kendi başına oynatır, iPhone'u kilitleyebilirsin.
• Oynatma, duraklatma ve sarma iPhone'dan.

Kontrol sende
• İstersen her bağlantıda TV'de görünen 4 haneli kodu iste.
• TV açıldığında arka planda hazır bekler.
• Açık ve koyu tema, Türkçe ve İngilizce.

Gizlilik
Fenpo Cast hiçbir veri toplamaz. Hesap yok, sunucu yok, reklam ve takip yok; her şey iPhone'un ile TV'n arasında, yerel ağda olur.

iPhone ile TV aynı Wi-Fi ağında olmalı. DRM ile korunan film ve dizi içerikleri gönderilemez.

Fenpo Cast açık kaynaktır (GNU GPL v3, github.com/Milvasoft/fenpocast) ve UxPlay üzerine kuruludur. Apple ile bağlantılı değildir; iPhone, Apple Inc.'in ticari markasıdır.
```

### English (en-US)

**Title** (≤ 30): `Fenpo Cast` (10)

**Short description** (≤ 80): `Show your iPhone's screen, music and videos on your TV. Nothing to install.` (75)

**Full description:**

```
Fenpo Cast turns your Android TV into a screen for your iPhone. There is nothing to install on the iPhone: the TV appears in the iPhone's own Screen Mirroring list.

Mirror your screen
• Open Control Center on your iPhone, tap Screen Mirroring and choose your TV.
• The picture comes through the TV's hardware decoder with low delay; sound stays in sync.
• Turn the iPhone and the picture turns with it.

Music
• In music and podcast apps, choose your TV with the audio output button.
• Cover art, track, artist and progress show on the TV; set the volume from the iPhone.

Video
• Send a video from video apps to the TV; the TV plays it by itself, so you can lock your iPhone.
• Play, pause and seek from the iPhone.

You decide
• Ask for a 4-digit code shown on the TV every time an iPhone connects, if you like.
• Stays ready in the background when the TV starts.
• Light and dark theme, Turkish and English.

Privacy
Fenpo Cast collects no data. No accounts, no servers, no ads or tracking; everything happens between your iPhone and your TV on your local network.

Your iPhone and TV need to be on the same Wi-Fi network. DRM-protected films and series cannot be sent.

Fenpo Cast is open source (GNU GPL v3, github.com/Milvasoft/fenpocast) and built on UxPlay. It is not affiliated with Apple; iPhone is a trademark of Apple Inc.
```

## 3. Grafikler

`python store/build.py` (önce `./gradlew :app:recordRoborazziDebug`) → `store/out/`:

| Play alanı | Dosya |
|---|---|
| Uygulama simgesi 512×512 | `icon-512.png` |
| Öne çıkan grafik 1024×500 | `feature-graphic-tr.png`, `feature-graphic-en.png` |
| Android TV banner'ı 1280×720 | `tv-banner-1280x720.png` |
| Android TV ekran görüntüleri 1920×1080 | `tv-screenshots/{tr,en}/1-…5-*.png` (uygulamanın kendi Roborazzi çizimleri, koyu tema, örnek veriler) |

## 4. App content (beyanlar)

| Soru | Yanıt |
|---|---|
| Gizlilik politikası | https://fenpo.milvasoft.com/cast/privacy |
| Uygulama erişimi | Tüm işlevler kısıtlamasız (hesap yok). Not: bağlanmak için TV ile aynı ağda bir iPhone gerekir; Denetim Merkezi > Ekran Yansıtma > TV'nin adı |
| Reklam | Yok |
| İçerik derecelendirmesi | Kategori: Diğer / araç. Şiddet, cinsellik, kumar, kullanıcılar arası iletişim, konum paylaşımı: hayır |
| Hedef kitle | 13 yaş ve üzeri; çocuklara yönelik değil |
| Data safety | Veri toplanmıyor ve paylaşılmıyor. iPhone'dan gelen görüntü ve ses TV'den dışarı çıkmaz; geliştiriciye hiçbir şey gelmez |
| Haber uygulaması / kamu / finans / sağlık | Hayır |
| Ön plan servisi (`connectedDevice`) | Aşağıdaki metin |

**connectedDevice (EN):** Fenpo Cast is a receiver for the user's iPhone on the local network. The foreground service keeps the receiver listening and advertised (mDNS) while the app is in the background and after the TV starts, so the TV stays in the iPhone's Screen Mirroring list; when the user's iPhone connects, the service receives the screen and audio stream and brings the app forward. The service shows an ongoing notification and the user can turn it off in Settings ("Stay ready in the background").

## 5. Lisans

UxPlay GPLv3 olduğu için APK'yı dağıtmak, kaynak kodunu (bu repo, alt modülleriyle) herkese açık sunmayı gerektirir. Play'e göndermeden önce repo GitHub'da açık olmalı; README ve uygulamadaki Lisans satırı oraya işaret eder.
