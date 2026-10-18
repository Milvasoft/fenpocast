# Kararlar

Fenpo Cast için alınmış kararlar. Bir kararla çelişen bir şey yapmadan önce Buğra'ya sor.

| # | Tarih | Karar | Neden |
|---|---|---|---|
| 1 | 19 Ekim 2026 | Fenpo Cast, Fenpo'dan ayrı bir uygulama ve ayrı bir repo (`fenpocast-tvapp`). Fenpo'nun koduna, protokolüne ve sürüm akışına dokunmaz | Fenpo'nun işleyişine bulaşmasın; lisansı farklı |
| 2 | 19 Ekim 2026 | Lisans GPLv3 | Alıcı çekirdeği UxPlay (GPLv3). Fenpo MIT kalır, kod paylaşılmaz |
| 3 | 19 Ekim 2026 | Ad "Fenpo Cast". Marka ve tasarım dili Fenpo'nunki: Çekirdek logosu, Fenpo renk rolleri, sistem fontu, Gabarito yalnız banner'da | Ayrı marka aramaya gerek yok |
| 4 | 19 Ekim 2026 | Hedef Android TV / Google TV. minSdk 28 (Android TV 9), targetSdk 37. ABI: armeabi-v7a, arm64-v8a, x86_64 | Kutuların çoğu hâlâ Android 9–12; birçoğu 64 bit çipte 32 bit kullanıcı alanı çalıştırıyor |
| 5 | 19 Ekim 2026 | Alıcı protokolü UxPlay'in `lib/` klasöründen, NDK ile derlenir. GStreamer yerine Android'in kendi çözücüleri: görüntü MediaCodec, ses MediaCodec (AAC) ve Apple ALAC çözücüsü, video akışı Media3 | Sıfırdan protokol yazmak aylar sürer; UxPlay bakımda ve iOS 26'yı destekliyor |
| 6 | 19 Ekim 2026 | OpenSSL yerine BoringSSL, statik | CMake ile NDK'da doğrudan derleniyor, ayrı `.so` yok, 16 KB sayfa hizasıyla uyumlu |
| 7 | 19 Ekim 2026 | Hizmet duyurusu UxPlay'in kendi mDNS yanıtlayıcısıyla değil, Android'in NsdManager'ı ile (`receiver/dnssd_android.c`) | Sistemde zaten bir mDNS yanıtlayıcı var; 5353 portu için yarışmak yerine onu kullanmak daha sağlam |
| 8 | 19 Ekim 2026 | Cihaz kimliği (MAC biçiminde) ilk açılışta rastgele üretilir ve saklanır; Ed25519 anahtarı `files/receiver_key.pem` | Android gerçek MAC adresini vermiyor; iPhone TV'yi bu kimlikle tanıyor |
| 9 | 19 Ekim 2026 | Eşleşme olarak AirPlay 2 istemci eşleşmesi (özellik biti 27 kapalı). "Kod iste" açıksa her bağlantıda rastgele 4 haneli kod (UxPlay `-pw` kipi) | UxPlay'in varsayılanı; kod kipi ek eşleşme kaydı gerektirmiyor |
| 10 | 19 Ekim 2026 | 4K ve H.265 yalnız 4K ekranda ve donanım HEVC çözücüsü 3840×2160'ı destekliyorsa; yoksa 1080p H.264. Ayarlardan 1080p ve 720p seçilebilir | UxPlay da H.265'i 4K'ya bağlıyor; zayıf kutularda 1080p daha akıcı |
| 11 | 19 Ekim 2026 | Yeni bir iPhone bağlanınca eskisini düşürür (UxPlay `nohold`) | TV ortak bir ekran; sırayla kullanılıyor |
| 12 | 19 Ekim 2026 | Arka planda `connectedDevice` ön plan servisiyle hazır bekler (ayardan kapatılabilir), açılışta başlar. Bağlantı gelince uygulamayı öne getirmek "Diğer uygulamaların üzerinde göster" iznine bağlı | Android 10+ arka plandan aktivite başlatmayı yalnız bu izinle serbest bırakıyor |
| 13 | 19 Ekim 2026 | Yansıtılan ve akıtılan video iki temada da siyah zemin üzerinde. Uygulamanın kendi ekranları (ana ekran, müzik, ayarlar) Açık/Koyu temaya uyar | Video iPhone'un görüntüsü; açık temada beyaz çerçeve göz alır |
| 14 | 19 Ekim 2026 | DI çatısı yok: `FenpoCastApplication` iki nesne tutar (ayarlar, alıcı denetleyicisi) | Tek modüllü küçük uygulama; Hilt'in getirisi yok |
