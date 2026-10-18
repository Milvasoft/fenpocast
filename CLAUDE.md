# Fenpo Cast

iPhone'dan Android TV'ye ekran yansıtma, müzik ve video. iPhone'a bir şey kurulmaz; TV, iPhone'un yerleşik Ekran Yansıtma ve ses çıkışı listesinde görünür. Fenpo ailesinden ama Fenpo'dan bağımsız bir uygulama ve repo (Fenpo: `Milvasoft/fenpo`). Açık kaynak (GPLv3), sunucusuz, her şey yerel ağda.

## Önce bunları oku

1. `docs/DECISIONS.md`: alınmış kararlar. Bir kararla çelişen bir şey yapmadan önce Buğra'ya sor.
2. `docs/ARCHITECTURE.md`: yerel katman, JNI köprüsü, Kotlin katmanı.
3. `THIRD_PARTY.md`: UxPlay, BoringSSL, libplist, ALAC; sürümler ve yerelde yapılan değişiklikler.
4. `docs/TESTING.md`: TV'ye kurulum ve cihazda denenecekler.

## Çalışma kuralları

- **Yarım iş teslim etme.** Bir özellik ancak raop geri çağrısı → `NativeReceiver` → `ReceiverController` → ekran → ayar → iki tema → iki dil zincirinde uçtan uca bittiğinde bitmiştir.
- **Gerçek cihaz gerektiren şeyi uydurma.** Yansıtma, ses, video akışı ve mDNS görünürlüğü emülatörde doğrulanamaz (emülatör NAT arkasında). Cihazda denenmemiş bir şeyi "çalışıyor" diye raporlama; "derleniyor, cihazda denenmedi" de. Sonuçlar `docs/TESTING.md` tablosuna yazılır.
- **Derle ve test et.** Her değişiklikten sonra `./gradlew :app:testDebugUnitTest :app:lintDebug spotlessCheck :app:assembleDebug`.
- **Üçüncü taraf kod.** `app/src/main/cpp/third_party/` upstream kopyasıdır. Değiştirmen gerekiyorsa en küçük değişikliği yap, `// Fenpo Cast:` yorumuyla işaretle ve `THIRD_PARTY.md` listesine ekle. Kendi yerel kodumuz `app/src/main/cpp/receiver/` altında.
- **JNI sözleşmesi.** `receiver.c` içindeki `cache_methods()` ile `NativeReceiver.kt`'deki özel metotların adları ve imzaları birebir aynı kalır; yeni bir geri çağrı üçüne birden eklenir (C, Kotlin, `proguard-rules.pro` gerekirse).
- **Tema ve dil her ekranda.** Görünüm (Sistem, Açık, Koyu) ve Dil (Sistem dili, Türkçe, English). Renkler `ui/theme/` içinden, kodda sabit renk yok; metinler `res/values/strings.xml` ve `res/values-tr/strings.xml`, ikisi birlikte güncellenir.
- **Apple markaları arayüzde yok.** "AirPlay", "Apple TV" arayüzde ve mağaza metninde geçmez. iOS'un menü adları ("Denetim Merkezi", "Ekran Yansıtma") yönerge olarak geçebilir.
- **TV kuralları.** Her şey D-pad ile erişilebilir; her ekranda ilk odak belli; yanlarda en az 48 dp, üstte ve altta en az 27 dp boşluk (TV taşma payı); oturum sırasında ekran uyumaz.

## Kotlin kod stili

Fenpo for Android ile aynı: resmi Kotlin kuralları, `ktlint_official` (`.editorconfig`, Spotless ile zorlanır), satır sonu LF, girinti 4, satır 120. Public API'lerde İngilizce KDoc; kod, log ve yorumlar İngilizce. Kodda Java'nın `toLowerCase()`/`toUpperCase()`'i ve yerel ayara bağlı `format` yok (Türkçe "I" sorunu); `Locale.ROOT` kullan.

## Tasarım sözlüğü

Fenpo'nunki: Mürekkep `#1B1F3B` (metin), Çiçek `#E8577E` (marka; açık zeminde metin için `#C93A63`), Yaprak `#2F8F6B` (hazır/başarı), Alacakaranlık `#4E54C8`, Cam `#DCE6F5`, Pervaz `#F6F7FB`. Logo Çekirdek. Uygulama sistem fontunu kullanır, Gabarito yalnız banner'da. Kırmızı yalnız hata için. Metinler: düz fiiller, cümle düzeni büyük harf, düğme ne yapacaksa onu söyler, hata mesajı özür dilemez, nasıl düzeltileceğini söyler.

## Ortam

- JDK 21 (`~/.jdks/temurin-21.0.12`), Android SDK platform 37, NDK `30.0.16248370`, CMake `4.1.2`.
- BoringSSL git alt modülü: klonlarken `--recurse-submodules`.
- Deneme: Buğra'nın Android TV'si ve iPhone'u, `adb` ile yan yükleme (`docs/TESTING.md`). Emülatör (`fenpocast_tv` AVD) yalnız arayüz ve başlangıç için.
