# Mimari

## Akış

```
iPhone ──mDNS──▶ NsdManager (_airplay._tcp, _raop._tcp)          ServiceAdvertiser
   │
   ├─ TCP 7000-benzeri dinamik port ─▶ UxPlay httpd / raop        libfenpocast.so
   │     eşleşme, FairPlay, /info, SETUP, /feedback, HLS uçları
   ├─ yansıtma akışı (TCP) ─▶ raop_rtp_mirror: AES-CTR çöz → Annex B
   ├─ ses (UDP RTP) ─▶ raop_rtp + raop_buffer: çöz, sırala
   └─ zaman (UDP NTP) ─▶ raop_ntp
                │
         receiver/receiver.c (JNI)
                │ raop geri çağrıları → NativeReceiver.kt
                ▼
         ReceiverController ──▶ VideoDecoder (MediaCodec → SurfaceView)
                            ──▶ AudioRenderer (MediaCodec AAC / PCM → AudioTrack)
                            ──▶ StreamPlayer (Media3 HLS, localhost üzerinden)
                            ──▶ CastState (StateFlow) → Compose ekranları
```

## Yerel katman (`app/src/main/cpp`)

- `CMakeLists.txt`: dört statik kütüphane (BoringSSL `crypto`, `plist`, `alac`, `airplay`) ve paylaşılan `libfenpocast.so`. `airplay`, UxPlay'in `lib/` klasörünün `dns_sd` ve `mdnsd` dışındaki her şeyi artı `receiver/dnssd_android.c`. UxPlay'in assert'leri `NDEBUG` ile kapalı (bkz. CMake yorumu).
- `receiver/receiver.c`: `uxplay.cpp`'nin karşılığı. raop geri çağrılarını `NativeReceiver`'ın özel metotlarına bağlar, `uxplay.cpp`'nin GLib ana döngüsünün raop'a dokunan kısmını küçük bir denetim iş parçacığıyla yapar (geri bildirim bekçisi 15 sn, oturum sıfırlama, HTTP sunucusunu yeniden başlatma), HLS oynatma durumunu Kotlin'den alıp `/playback-info`'ya verir.
- `receiver/dnssd_android.c`: UxPlay'in `dnssd` arka ucu. TXT kayıtlarını `lib/mdnsd/dnssd_mdnsd.c` ile aynı kurar, kaydı JNI ile `ServiceAdvertiser`'a (NsdManager) verir. `/info` cevabındaki ham TXT baytları da buradan gelir.
- `receiver/alac_decoder.cpp`: Apple ALAC çözücüsünü AirPlay'in sabit "magic cookie"siyle (352 kare, 16 bit, 2 kanal, 44,1 kHz) sarar. ALAC Kotlin'e çözülmüş PCM olarak gider (ct = 1).

JNI yöntem adları ve imzaları `receiver.c` içindeki `cache_methods()` ile `NativeReceiver.kt` arasında birebir eşleşmeli; R8 kuralları `app/proguard-rules.pro` içinde.

## Kotlin katmanı

- `receiver/NativeReceiver.kt`: JNI sınırı. Geri çağrılar raop'un kendi iş parçacıklarında gelir.
- `receiver/ReceiverController.kt`: süreç boyunca tek nesne. Ayarlardaki iPhone'un gördüğü alanlar (ad, kod, kalite, video) değişince alıcıyı yeniden başlatır; oturum durumunu (`Session.Mirroring`, `Music`, `Stream`) üretir; ağ değişikliklerini izler; Android 17'de yerel ağ iznini kontrol eder.
- `media/VideoDecoder.kt`: asenkron MediaCodec, her çıkış karesi geldiği anda gösterilir (`KEY_LOW_LATENCY`). Yüzey yokken (uygulama açılırken) son anahtar kareden beri gelen kareleri tutar ve yüzey gelince yeniden oynatır; böylece duran bir ekranda bir sonraki anahtar kareyi beklemeye gerek kalmaz. Parametre setleri değişirse (iPhone döndü) çözücüyü yeniden kurar.
- `media/AudioRenderer.kt`: tek iş parçacığı, `AudioTrack`'in bloklayan yazması akışı hızlandırır (UxPlay'deki GStreamer saatinin karşılığı). Ses odağı alır.
- `media/StreamPlayer.kt`: Media3 ExoPlayer. iPhone oynatma, duraklatma, sarma komutlarını verir; saniyede iki kez durum geri yollanır (iPhone'daki sürgü bundan çizilir).
- `service/ReceiverService.kt`: `connectedDevice` ön plan servisi. Uygulama görünür değilken bir oturum başlarsa uygulamayı öne getirmeyi dener.

## Tema ve dil

- Renk rolleri Fenpo for Android'in Material 3 rolleriyle aynı, TV Material şemasına aktarılmış (`ui/theme/Color.kt`). Yaprak "Hazır" durumu için ayrı rol. Video zemini ve üstündeki mesaj renkleri de orada tanımlı.
- Görünüm (Sistem, Açık, Koyu) DataStore'da; Dil (Sistem dili, Türkçe, English) AppCompat'ın uygulama dili API'siyle.
- Metinler `res/values/strings.xml` (English) ve `res/values-tr/strings.xml` (Türkçe).
