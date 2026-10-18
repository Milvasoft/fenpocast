# Üçüncü taraf bileşenler

Fenpo Cast'in alıcı çekirdeği aşağıdaki açık kaynak projelerden gelir. Kaynakları `app/src/main/cpp/third_party/` altında, kendi lisans dosyalarıyla birlikte durur. Uygulamanın tamamı GPLv3 ile dağıtılır (`LICENSE`).

| Bileşen | Sürüm | Lisans | Ne için |
|---|---|---|---|
| [UxPlay](https://github.com/FDH2/UxPlay) `lib/` | `4e95a9ec` (16 Ekim 2026) | GPLv3 (çoğu dosya LGPL 2.1+, `playfair/` GPLv3, `llhttp/` MIT) | Alıcı protokolü: RAOP/AirPlay oturumu, eşleşme, FairPlay el sıkışması, yansıtma akışının şifresini çözme, HLS video aktarımı |
| [BoringSSL](https://boringssl.googlesource.com/boringssl) | `890a85a2` (git alt modülü) | Apache 2.0 / ISC / OpenSSL | UxPlay'in kullandığı libcrypto API'si (EVP, BN, AES, SHA). Statik bağlanır |
| [libplist](https://github.com/libimobiledevice/libplist) | `fe3dc34d` | LGPL 2.1+ | İkili ve XML property list okuma/yazma |
| [Apple ALAC](https://github.com/macosforge/alac) `codec/` (yalnız çözücü) | `c38887c5` | Apache 2.0 | Müzik akışındaki ALAC sesini çözme |

Java/Kotlin tarafındaki AndroidX, Compose for TV ve Media3 kütüphaneleri Apache 2.0 lisanslıdır ve Gradle ile gelir.

## Yerelde yapılan değişiklikler

Bunların dışındaki dosyalar upstream ile aynıdır; güncellerken bu liste yeniden uygulanır.

- **UxPlay `lib/`**
  - `CMakeLists.txt`, `dns_sd/`, `mdnsd/`, `playfair/CMakeLists.txt`, `llhttp/CMakeLists.txt` kaldırıldı. Derleme `app/src/main/cpp/CMakeLists.txt` ile yapılıyor; hizmet duyurusu `receiver/dnssd_android.c` üzerinden Android'in NsdManager'ına gidiyor.
  - `crypto.c` `pk_to_base64()`: BoringSSL'de `BIO_f_base64()` olmadığı için `EVP_EncodeBlock()` kullanılıyor (çıktı aynı).
- **libplist**: yalnız C kaynakları (`src/*.c`, `libcnary/`) ve `include/plist/plist.h` alındı; C++ sarmalayıcısı, araçlar ve testler alınmadı.
- **ALAC**: yalnız çözücü dosyaları alındı, kodlayıcı ve `convert-utility` alınmadı.

## Hukuki not

UxPlay'in `playfair/` klasörü (FairPlay el sıkışması) tersine mühendislikle yazılmıştır; UxPlay'in README'si hukuki durumunun belirsiz olduğunu söyler. Play Store'da aynı yöntemi kullanan alıcılar (AirScreen ve benzerleri) yıllardır yayında, ama bu bir güvence değildir.

"AirPlay" Apple'ın ticari markasıdır. Fenpo Cast'in arayüzünde ve mağaza metninde geçmez; kaynak kodda ve geliştirici dokümanlarında yalnız protokolü adlandırmak için geçer.
