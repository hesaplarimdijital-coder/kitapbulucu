# Kitap Bulucu

İki kütüphanenin (Babam, Benim) kitaplarını Google Drive'daki iki Excel dosyasında tutan ve "bu kitap var mı, kimde, hangi rafta" sorusunu yanıtlayan Android uygulaması.

- İnternet izni yok; hesap, reklam, analitik yok. Dosyaları buluta Google Drive uygulaması taşır.
- Asıl kaynak Drive'daki `Babam.xlsx` ve `Benim.xlsx`. Herkes kendi dosyasına yazar, ikisini de okur.
- Her yazmadan önce dosya yeniden okunur ve yedeği alınır (son 10 yedek saklanır).
- Tablet (Samsung DeX, klavye ve fare) ve telefon için tasarlandı.

## Kurulum

1. APK'yı indirin: [kitapbulucu.apk](https://github.com/hesaplarimdijital-coder/kitapbulucu/raw/ci/apk/kitapbulucu.apk).
2. Tablette ya da telefonda dosyayı açın. "Bilinmeyen uygulamalar" izni sorulursa o kez izin verin.
3. Uygulama açılınca "Ben kimim?" sorusunu yanıtlayın (Babam ya da Ben).
4. İki dosyayı tek tek seçin: önce `Babam.xlsx`, sonra `Benim.xlsx`. Dosya yoksa "Oluştur" boş, başlıklı bir dosya açar.
5. Sütun adları farklıysa uygulama bir kez hangi sütunda ne olduğunu sorar.

Yeni sürüm aynı yoldan kurulur; ayarlar, dosya bağlantıları ve yedekler kalır.

## Drive klasörü

1. Drive'da `Kitap Bulucu` adlı bir klasör açın, `Babam.xlsx` ve `Benim.xlsx` dosyalarını içine koyun.
2. Klasörü babanızla paylaşın ("Düzenleyen" yetkisiyle).
3. İki cihazda da Google Drive uygulamasında bu iki dosyayı "Çevrimdışı kullanılabilir" yapın. Böylece internet yokken de okunur.
4. İsteğe bağlı: Ayarlar → Kitaplık → "Klasör seç" ile klasörü de gösterin. Uygulama o zaman Excel'de açık kalmış dosyaları ve çakışan kopyaları fark edip uyarır.

## Babanın telefonu

1. Telefona Google Drive uygulaması kurulu ve babanızın hesabı açık olsun; paylaşılan klasör "Benimle paylaşılanlar" altında görünür.
2. APK'yı kurun, "Ben kimim?" sorusuna "Babam" deyin.
3. İki dosyayı Drive'dan seçin. Telefon yalnızca dikey kullanılır.
4. Babanız kendi listesine yazar; sizin listenize bir şey eklemek isterse uygulama önce sorar.

## Kullanım

- **Ara** (Ctrl+K): adın ya da yazarın bir parçasını yazın. Türkçe harfler, şapka ve küçük yazım hataları sorun değil; sözcük sırası önemsiz.
- **Kütüphane**: bütün liste tablo hâlinde; sütun başlığına tıklayınca sıralanır. Sağ tık ya da uzun basış: düzenle, sil, taşı, rafa koy, raftaki öteki kitaplar. Seçili satırda F2 düzenler, Del siler. "+" yeni kitap ekler (Ctrl+N).
- **Raflar**: raf raf kitaplar. "Yeni raf" ile raf oluşturun (raf listesi yalnızca bu cihazda durur); rafı açıp "Kitap ekle" ile kitapları arayıp art arda o rafa koyun. Raf adı değiştirilebilir, boş raf silinebilir.
- **Geri al** (Ctrl+Z): son değişiklikleri birer birer geri alır.
- **Ayarlar → Yedek ve aktarma**: başka bir Excel'i birleştirme (Ctrl+Shift+O), iki listeyi tek Excel olarak dışa aktarma, bekleyen değişiklikler, yedekten geri yükleme.
- F5 iki dosyayı yeniden okur; Ctrl+O kendi listenizin dosyasını yeniden seçtirir; Ctrl+/ bütün kısayolları gösterir.

## Excel dosyası

Sütunlar: Kitap Adı, Yazar, Raf, Adet, İmzalı, Yayınevi, Yıl, ISBN, Not. Sütunlar adlarından bulunur; sırası ve fazladan sütunlar sorun değildir, tanınmayan sütunlar silinmez. `.xlsx` ve `.csv` okunur; eski `.xls` dosyasını Excel'de ".xlsx olarak kaydet" deyip yeniden seçin.

## Geliştirme

- Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, JDK 17, minSdk 26, targetSdk 35, Compose BOM 2024.12.01.
- Arayüz kiti: [desktop-kit](https://github.com/hesaplarimdijital-coder/desktop-kit) (JitPack).
- Excel okuyucu ve yazıcı uygulamanın kendi kodu (`java.util.zip` + `XmlPullParser`); Apache POI kullanılmaz.
- Derleme, testler ve ekran görüntüleri GitHub Actions'ta. APK `ci/apk`, ekran görüntüleri `ci/screenshots` dalında.
- APK'nın hep aynı anahtarla imzalanması için iki gizli değer gerekir: `KB_KEYSTORE_BASE64`, `KB_KEYSTORE_PASSWORD`.
- Kitap listesi depoya girmez (`*.xlsx` yok sayılır); testlerde yalnızca uydurma kitaplar var.
- Mimari: `docs/ARCHITECTURE.md`; iş listesi: `docs/BACKLOG.md`; durum: `docs/STATE.md`.

## Lisanslar

Literata yazı tipi SIL Open Font License 1.1 ile dağıtılır (`licenses/Literata-OFL.txt`).
