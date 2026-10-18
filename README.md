# Fenpo Cast

iPhone ekranını, müziğini ve videolarını Android TV'ye gönder. iPhone'a hiçbir şey kurmadan: Denetim Merkezi > Ekran Yansıtma'dan ya da çalan şeyin ses çıkışı düğmesinden TV'yi seçmen yeterli.

Fenpo ailesinin bir parçası ([Fenpo](https://github.com/Milvasoft/fenpo): telefon ile Windows arasında köprü), ama ondan bağımsız bir uygulama. Açık kaynak, sunucusuz, her şey yerel ağda.

## Ne yapar

| | |
|---|---|
| Ekran yansıtma | iPhone ekranı H.264 ya da (4K TV'de) H.265 olarak gelir, donanım çözücüyle gecikmesiz gösterilir. Ses AAC-ELD |
| Müzik | Müzik ve podcast uygulamalarından ses (ALAC, AAC). Albüm kapağı, parça, sanatçı, ilerleme |
| Video | YouTube gibi uygulamalar videoyu akış olarak TV'ye devreder; TV kendi başına oynatır, iPhone kilitlenebilir |
| Kod ile bağlanma | İsteğe bağlı: her bağlantıda TV'de 4 haneli kod görünür |
| Arka planda hazır | TV açıldığında iPhone'un listesinde görünür; izin verilirse bağlantı gelince kendiliğinden öne çıkar |

DRM'li içerik (Netflix, Disney+, Apple TV+) gönderilemez; bu, kimsenin aşamadığı FairPlay Streaming kısıtıdır.

## Derleme

Gerekenler: JDK 21, Android SDK (platform 37), NDK `30.0.16248370`, CMake `4.1.2`.

```bash
git clone --recurse-submodules <repo-url>
```

```bash
./gradlew :app:assembleRelease
```

Çıktı: `app/build/outputs/apk/release/app-release.apk` (yan yükleme için debug anahtarıyla imzalı). TV'ye kurulum ve denenecekler: [docs/TESTING.md](docs/TESTING.md).

## Yapı

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt         BoringSSL + libplist + ALAC + UxPlay çekirdeği → libfenpocast.so
│   ├── receiver/              JNI köprüsü, Android mDNS arka ucu, ALAC sarmalayıcısı
│   └── third_party/           UxPlay lib/, libplist, ALAC, BoringSSL (alt modül)
├── kotlin/com/fenpo/cast/
│   ├── receiver/              NativeReceiver (JNI), ReceiverController, NsdManager duyurusu
│   ├── media/                 MediaCodec görüntü ve ses, Media3 akış oynatıcı, DMAP
│   ├── service/               Ön plan servisi, açılış alıcısı
│   ├── settings/              DataStore ayarları, cihaz kimliği
│   └── ui/                    Compose for TV ekranları, tema
└── res/                       Türkçe ve İngilizce metinler, simgeler, banner
```

Ayrıntılar: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Kararlar: [docs/DECISIONS.md](docs/DECISIONS.md).

## Lisans

GNU GPL v3 ([LICENSE](LICENSE)). Alıcı çekirdeği UxPlay'den gelir; bileşenler ve lisansları [THIRD_PARTY.md](THIRD_PARTY.md) içinde.

"AirPlay", "Apple TV" ve "iPhone" Apple Inc.'in ticari markalarıdır. Fenpo Cast Apple ile bağlantılı değildir.
