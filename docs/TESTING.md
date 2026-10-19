# TV'de deneme

## Kurulum

TV'de Geliştirici seçeneklerini aç (Ayarlar > Cihaz tercihleri > Hakkında > Derleme'ye 7 kez bas), sonra Geliştirici seçenekleri > USB/Ağ hata ayıklama'yı aç. TV'nin IP adresi Ayarlar > Ağ'da ya da Fenpo Cast > Ayarlar > Hakkında'da.

```bash
adb connect <tv-ip>:5555
```

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Günlük:

```bash
adb logcat -s FenpoCastNative UxPlay ReceiverController ServiceAdvertiser VideoDecoder AudioRenderer
```

## Denenecekler

Her madde için sonuç `docs/TESTING.md` sonundaki tabloya yazılır. Cihazda denenmemiş bir şey "çalışıyor" sayılmaz.

1. **Görünürlük**: Fenpo Cast açılınca ana ekranda "Hazır". iPhone'da Denetim Merkezi > Ekran Yansıtma listesinde TV'nin adı çıkıyor.
2. **Yansıtma**: Bağlan; görüntü 1 saniye içinde geliyor, ses dudakla uyumlu. iPhone'u yatay ve dikey çevir: görüntü yeniden boyutlanıyor. iPhone'u kilitle: "yansıtmayı duraklattı" mesajı; aç: devam ediyor.
3. **Uygulamadan çıkınca**: Ana ekrana dön; Fenpo Cast iPhone'un listesinden kayboluyor. Müzik çalarken çıkarsan müzik bitene kadar sürüyor.
4. **Müzik**: Müzik uygulamasında ses çıkışından TV'yi seç. Kapak, parça, sanatçı, ilerleme görünüyor; iPhone'dan ses seviyesi değişiyor; duraklat/devam ve parça değiştir çalışıyor.
5. **Video**: YouTube'da videoyu TV'ye gönder. TV kendi oynatıyor, iPhone'daki sürgü ilerliyor, ileri sarma ve duraklatma çalışıyor. iPhone'u kilitle: video sürüyor.
6. **Kod iste**: Ayarlar > Kod iste'yi aç. Bağlanınca TV'de 4 haneli kod, iPhone kodu soruyor; yanlış kod reddediliyor.
7. **Bağlantıyı kes**: Oturumdayken Geri > Bağlantıyı kes. iPhone'da yansıtma bitiyor; TV ana ekrana dönüyor.
8. **İkinci iPhone**: Biri yansıtırken ikincisi bağlanınca ekranı devralıyor.
9. **Ağ kopması**: Yansıtırken iPhone'un Wi‑Fi'sini kapat: en geç 15 sn'de "Bağlantı koptu", TV ana ekrana dönüyor ve yeniden bağlanılabiliyor.
10. **4K**: 4K TV'de iPhone H.265 gönderiyor (logcat'te `h265`), görüntü net ve akıcı. Ayarlar > Görüntü kalitesi > 1080p sonrası H.264'e dönüyor.
11. **Tema ve dil**: Ayarlar > Görünüm Açık ve Koyu; Dil Türkçe ve English. Ana ekran, Ayarlar, müzik ekranı, kod, bildirimler iki temada ve iki dilde doğru.

## Sonuçlar

| Tarih | Cihaz | Android | iPhone / iOS | Madde | Sonuç |
|---|---|---|---|---|---|
| 19 Ekim 2026 | Philips 2021/22 UHD Android TV (PH1M, armeabi-v7a) | 11 (API 30) | iPhone 13 (iPhone14,5), AirPlay 960.13.1 | 1, 2 | Çalışıyor: listede görünüyor, yansıtma görüntü ve sesle geliyor (Buğra) |
| 19 Ekim 2026 | aynı | 11 | aynı | 5 | İlk sürümde başarısız (ters kanal kapanıyor, liste bulunamıyor); `a005e42` ile çalışıyor (Buğra) |
