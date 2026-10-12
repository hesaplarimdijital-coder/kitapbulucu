# Mimari

Uygulama: Kitap Bulucu. Bu dosya kalıcı teknik kararları tutar. Gerekince ilgili bölümü Grep ile bul, tamamını okuma.

## 1. Tek cümleyle

İki Excel dosyasını (Babam.xlsx, Benim.xlsx) okuyup bellekte arayan, değişiklikleri güvenli biçimde aynı dosyalara geri yazan, internet izni olmayan tek modüllü bir Android uygulaması.

## 2. Modül ve paketler

Tek Gradle modülü: `app`. Ek modül yok (bakım az olsun diye).

| Paket | İş | Android'e bağımlı mı |
| --- | --- | --- |
| `model` | `Book`, `Owner`, `Library` | Hayır |
| `text` | Türkçe küçültme, katlama (fold), sıralama anahtarı | Hayır |
| `search` | Dizin, eşleştirme, sıralama | Hayır |
| `xlsx` | `XlsxReader`, `XlsxWriter`, `CsvReader` | Yalnızca `XmlPullParser` |
| `importing` | Sütun eşleme, içe aktarma kuralları, birleştirme planı | Hayır |
| `store` | SAF okuma/yazma, önbellek, yedek, bekleyen değişiklikler | Evet |
| `ui` | Ekranlar (kit ile), tema, kısayollar | Evet |

Kural: `model`, `text`, `search`, `importing` içinde `android.*` içe aktarılmaz; testleri düz JUnit ile hızlı çalışır. `xlsx` ve `store` testleri Robolectric (sdk=34) ile çalışır.

## 3. Veri modeli

```kotlin
enum class Owner { DAD, ME }          // ekranda "Babam" / "Benim"
data class Book(
    val title: String, val author: String, val shelf: String,
    val copies: Int = 1, val signed: Boolean = false,
    val publisher: String, val year: String, val isbn: String, val note: String,
    val extra: List<String> = emptyList(),   // tanımadığımız sütunlar, aynen geri yazılır
)
```

- Excel'de satırın kalıcı kimliği yok. Bir satır, alanlarının tamamıyla tanınır.
- Aynı kitabın birden çok kopyası tek satırdır; kaç tane olduğu `Adet` sütunundadır.
- İmzalı kopya ayrı satırdır; `İmzalı` sütununda `Evet` yazar. "Bülent Ecevit'e imzalı" gibi ithaflar ayrıca Not'ta durur.

## 4. Dosya biçimi (kullanıcı onayı bekliyor)

- Biçim kullanıcı tarafından genel olarak onaylandı ama kesin değil; sonradan değişebilir. Okuyucu sütunları sıraya göre değil başlık adına göre bulur.
- Kişi dosyası: tek sayfa. Başlık satırı: `Kitap Adı, Yazar, Raf, Adet, İmzalı, Yayınevi, Yıl, ISBN, Not`. Başlık kalın ve dondurulmuş, sütun genişlikleri ayarlı.
- `Adet` sayı olarak yazılır; boşsa 1 sayılır. `İmzalı` sütununda `Evet` ya da boş; okurken `evet`, `e`, `x`, `var`, `imzalı`, `1` da imzalı sayılır.
- `Adet` dışındaki bütün hücreler metin biçiminde (`@`). Neden: Excel "3-4" gibi raf kodlarını ve "Nis.53" gibi yazıları tarihe çevirmesin (kaynak dosyada 3 hücre böyle bozulmuştu), ISBN bilimsel gösterime dönmesin.
- Okurken sayfa adı önemli değil; ilk dolu sayfa okunur. Birden çok dolu sayfa varsa kullanıcı seçer.
- Tanınmayan sütunlar silinmez: okunur, `extra` içinde taşınır, standart sütunlardan sonra aynı başlıkla geri yazılır.
- Dışa aktarılan ortak dosya: `Babam` ve `Benim` adlı iki sayfa. Uygulama bu dosyadan okumaz.

## 5. Excel okuyucu (`xlsx`)

- `java.util.zip` + `XmlPullParser`. Apache POI yok.
- Okunan parçalar: `xl/workbook.xml` (sayfalar, 1904 tarih sistemi), `xl/_rels/workbook.xml.rels`, `xl/sharedStrings.xml` (zengin metin parçaları birleştirilir, `rPh` atlanır), `xl/styles.xml` (yalnızca tarih biçimini anlamak için), `xl/worksheets/sheetN.xml`.
- Hücre türleri: `s`, `inlineStr`, `str`, `b`, `e`, sayı, boş. Hücre adresi (`r="C7"`) esas alınır; seyrek satır ve sütunlar doğru yere oturur.
- Sayılar: tam sayı `.0` olmadan, uzun sayılar (ISBN) bilimsel gösterim olmadan metne çevrilir.
- Tarih biçimli sayı hücresi metne çevrilir: biçimde gün varsa `15 MART 1998`, yoksa `NİSAN 1953`. Bu kural ilk temizlikte de uygulandı; aynı kalmalı.
- `.csv`: ayraç `;` ya da `,` (ilk satırlardan anlaşılır), kodlama UTF-8 (BOM'lu ya da BOM'suz), olmazsa Windows-1254.
- `.xls`: uzantı ya da ilk baytlardan tanınır; "Excel'de .xlsx olarak kaydedip tekrar deneyin" denir.

## 6. Excel yazıcı (`xlsx`)

- Parçalar: `[Content_Types].xml`, `_rels/.rels`, `xl/workbook.xml`, `xl/_rels/workbook.xml.rels`, `xl/styles.xml`, `xl/worksheets/sheet1.xml` (dışa aktarmada `sheet2.xml` de).
- Metinler satır içi (`inlineStr`) yazılır: tek geçişte yazılır, kod kısa kalır. Temizlenmiş Babam.xlsx de bu biçimde; tablette Excel, Office NX ve Google E-Tablolar'da açılması bu kararın ilk sınavı.
- XML'de geçersiz denetim karakterleri atılır, özel karakterler kaçırılır.
- Dosya her yazışta baştan oluşturulur; elle verilmiş renk ve biçim korunmaz. Uygulama bunu ilk yazmadan önce bir kez söyler.

## 7. Saklama ve güvenli yazma (`store`)

Asıl kaynak Drive'daki iki dosyadır. Uygulama klasöründe yalnızca yardımcı dosyalar durur: önbellek, yedekler, bekleyen değişiklikler.

Değişiklikler anlık görüntü olarak değil **işlem** olarak tutulur:

```kotlin
sealed interface Change
data class Add(val owner: Owner, val book: Book) : Change
data class Edit(val owner: Owner, val before: Book, val after: Book) : Change
data class Remove(val owner: Owner, val before: Book) : Change
// Taşıma = hedefe Add, başarılı olunca kaynaktan Remove. En kötü durumda kitap iki dosyada da kalır, kaybolmaz.
```

Bir dosyaya yazma sırası (tek iş parçacığı, `Mutex`):

1. Dosyayı SAF'tan **yeniden oku** (her yazmadan önce; son değişme zamanı ve boyut da kaydedilir).
2. Bekleyen işlemleri sırayla taze içeriğe uygula.
3. Yeni dosyayı uygulama klasöründe geçici dosyaya üret ve kendi okuyucumuzla geri okuyup karşılaştır.
4. 1. adımda okunan baytları yedekle (dosya başına son 10 yedek).
5. Baytları belgeye yaz (`"wt"`), kapat.
6. Belgeyi geri oku, özetini (hash) karşılaştır.
7. Başarılıysa işlemleri bekleyenlerden düş, önbelleği güncelle. Başarısızsa hata açıkça gösterilir, işlemler bekleyenlerde kalır ve sonraki açılışta, öne gelişte ya da F5'te yeniden denenir.

Çakışma kuralları (2. adım):

- `Edit`: `before` satırı aynen bulunamazsa aynı ad+yazar+İmzalı değerli satır aranır; bulunursa yalnızca kullanıcının değiştirdiği alanlar uygulanır, bulunamazsa `after` yeni satır olarak eklenir.
- `Remove`: satır aynen ya da ad+yazar+İmzalı değeriyle bulunamazsa atlanır ve "zaten yok" diye bildirilir.
- `Add`: aynı ad+yazar+İmzalı değeri varsa yeni satır eklenmez, `Adet` artırılır.

Zorunlu gereksinimden bilinçli sapma: SAF tek belge izniyle "geçici dosyaya yaz, sonra yerine koy" yapamaz (kardeş dosya oluşturma ve yeniden adlandırma yok). Yerine 3–6. adımlar uygulanır: hedefe yalnızca eksiksiz ve doğrulanmış içerik yazılır, öncesinde yedek alınır, sonrasında geri okunur. Klasör izni çalışırsa (spike) gerçek geçici dosya yöntemi de denenir.

Öteki kişinin dosyasına yazmadan önce onay sorulur; yazma yolu aynıdır (zaten her seferinde yeniden okur).

Açılışta önbellek gösterilir, ardından iki dosya arka planda okunur; fark varsa ekran sessizce güncellenir. `onResume` ve F5 aynı okumayı tetikler.

Ayarlar `SharedPreferences` içinde: kim olduğum, dosya URI'leri, sütun eşlemeleri, bir kerelik uyarı bayrakları. Bekleyen işlemler `org.json` ile dosyada (Android'de yerleşik, yeni kütüphane değil).

## 8. Dosya erişimi (SAF) ve Sprint 1 spike'ı

- Birincil yol: iki dosyayı tek tek seçmek (`ACTION_OPEN_DOCUMENT`, kalıcı okuma+yazma izni). Dosya yoksa `ACTION_CREATE_DOCUMENT` ile oluşturulur.
- İkincil yol: klasör seçmek (`ACTION_OPEN_DOCUMENT_TREE`). Varsayım, doğrulanacak: Google Drive klasör seçmeyi desteklemiyor olabilir.
- `~$` ile başlayan ve "çakışan kopya" dosyalarını görmek yalnızca klasör izni varsa mümkündür.
- Spike'ta ölçülecekler: Drive'da okuma, geri yazma, yeniden başlatma sonrası kalıcı izin; babanın telefonunda paylaşılan dosyanın görünmesi; değişikliğin öteki cihaza ulaşma süresi; `COLUMN_LAST_MODIFIED` değerinin güvenilirliği.
- INTERNET izni yok. CI, APK izinlerinde INTERNET görürse kırılır.

## 9. Arama (`text`, `search`)

Katlama iki düzeylidir:

- Düzey 1 (Türkçe): `I→ı`, `İ→i` ile küçült; sonra `ı→i`; şapkayı kaldır (`â→a`, `î→i`, `û→u`). "islik" → "Islık", "hala" → "hâlâ".
- Düzey 2 (ASCII): ayrıca `ç→c, ğ→g, ö→o, ş→s, ü→u`. Türkçe harfsiz yazılan sorgular için; düzey 1 eşleşmesinin altında sıralanır.

Sözcüklere ayırma: harf ve rakam dışı her şey ayraçtır. Kesme işaretli sözcük hem ayrık hem bitişik dizinlenir ("İSTANBUL'UN" → `istanbul`, `un`, `istanbulun`).

Eşleşme: her sorgu sözcüğü, kitabın ad ya da yazar sözcüklerinden biriyle eşleşmeli; sıra önemsiz. Sözcük eşleşme türleri: tam, önek, içerme (3+ harf), yakın (düzenleme uzaklığı: 4–7 harfte 1, 8+ harfte 2; yer değiştirme 1 sayılır).

Sıralama katmanları: (1) tüm ad ya da yazar sorguyla aynı, (2) ad sorguyla başlıyor, (3) bütün sözcükler tam ya da önek eşleşiyor, (4) içerme, (5) yakın eşleşme. Katman içinde: ad eşleşmesi yazardan önce, düzey 1 düzey 2'den önce, kısa ad önce, sonra Türkçe alfabetik.

Hız: sözcük dağarcığı sıralı dizi (önek için ikili arama), sözcük → kitap listeleri, yakın eşleşme için uzunluk süzgeci + bit paralel Levenshtein. Yakın eşleşme yalnızca gerekince çalışır. Hedef 10.000 kitapta tuş başına 50 ms altı; üretilmiş 10.000 kitaplık veriyle ölçen bir test yazılır.

"Yok" durumu: en yakın 3 öneri ve aranan adla dolu "Ekle" düğmesi.

## 10. İçe aktarma ve birleştirme (`importing`)

İlk temizlikte uygulanan kurallar gelen her dosyaya aynen uygulanır (babanın dosyası 2 sütunlu, başlıksız ve büyük harfli gelmeye devam edebilir):

1. Baştaki, sondaki, çift boşluk silinir.
2. Listenin içine karışmış başlık satırı (`KİTAP | YAZAR`) atılır.
3. Tarih hücresi metne çevrilir (bkz. 5).
4. `İMZALI` işareti addan ya da yazardan çıkarılır, `İmzalı` sütununa `Evet` yazılır; "… 'E İMZALI" gibi ithaflar ayrıca Not'a taşınır; `ŞİİR` türü adda kalır; `İMZASIZ` sözcüğüne dokunulmaz.
5. Birebir aynı satırlar tek satır olur, sayıları `Adet` sütununa yazılır.

Karşılaştırma büyük/küçük harfe ve boşluğa duyarsızdır. Gelen dosyadaki boş alan dolu alanı asla silmez (özellikle Raf). Belirsiz eşleşmelerde verilen kararlar yerelde hatırlanır ki her birleştirmede yeniden sorulmasın.

Başlıksız dosyada eşleme ekranı ilk satırları gösterip "1. sütun Kitap Adı, 2. sütun Yazar" önerir.

## 11. Arayüz

- Kit: `desktop-kit` 0.1.0. Kabuk `DesktopShell` + `ShellSpec`; ekranlar: Ara, Kütüphane, Raflar, Ayarlar.
- Kabuğun üst çubuğundaki arama kutusu bir düğmedir, metin kutusu değildir. Gerçek arama kutusu Ara ekranının içindedir; Ctrl+K oraya götürüp odaklar.
- Kabuğun alt çubuk yuvası durum satırı olarak kullanılır: eşitleme zamanı, bekleyen yazma, "Geri al".
- Telefon: yalnızca dikey. En dar kenarı 600 dp'den küçük cihazda etkinlik dikeye kilitlenir (`requestedOrientation`); tablet ve DeX serbest kalır. Dar pencerede kit üst çubuk + alt gezinme çubuğu gösterir (Ara, Kütüphane, Raflar, Ayarlar). Seçilen tasarım: Taslak A "Fiş" (`docs/design/taslak-a-*.png`); renkler `docs/design/tokens.json` içindeki `A/...` paletleri, başlık serifi Literata. Telefonda da aynı tek sütun düzeni.
- Tablolar `DenseTable`; hücreler yalnızca metin. Sıralamayı uygulama yapar (`Collator`, Türkçe).
- Kısayollar tek `ShortcutRegistry` içinde. Etkinlik düzeyinde yalnızca Ctrl/Alt/Meta'lı ve F tuşları (`matchNative`); F2, Del gibi tek tuşlar ekranın içinde yakalanır.
- Tema: kitin renkleri `colors = { it.copy(...) }` ile ısıtılır, vurgular `Accent.custom`. Uygulamada kitin `PaletteContrastTest` benzeri bir test bütün tema × vurgu çiftlerini 4.5:1 için denetler.
- Kitte eksik (kit deposunda düzeltilecek, uygulamada yamanmayacak): `DeskFonts` yalnızca `sans` ve `mono` alıyor, başlıklar için serif aile yok; `TableColumn` hücre yazı stilini değiştiremiyor; `DenseTable` tek satır seçiyor.

## 12. Açık kararlar

| Konu | Durum |
| --- | --- |
| Excel biçimi | Genel olarak onaylı, kesin değil; kullanıcı sonradan değişiklik isteyebilir |
| Kit 0.1.1 (serif başlık ailesi, tablo sütun stili) | Kullanıcı onayı ve Release gerekir |

## 13. Paket ve imza

- Paket adı: `com.hesaplarimdijital.kitapbulucu` (uygulama kimliği ve Kotlin paketi).
- CI release APK'sı üretir (küçültme kapalı). `versionCode` = CI çalıştırma numarası, böylece her yeni APK eskisinin üstüne kurulur.
- İmza: GitHub gizli değerleri `KB_KEYSTORE_BASE64` (anahtar dosyası, base64) ve `KB_KEYSTORE_PASSWORD`; takma ad `kitapbulucu`. Yoksa CI uyarı verir ve atılık bir hata ayıklama anahtarıyla imzalar (o APK sonrakilerin üstüne kurulamaz).
- JitPack bir kit sürümünü ilk istekte derler; CI derlemeden önce kitin JitPack'te hazır olmasını bekler.

## 14. Sürümler

Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, JDK 17, compileSdk/targetSdk 35, minSdk 26, Compose BOM 2024.12.01 (kit üzerinden), activity-compose 1.9.3, Robolectric 4.14.1 (sdk=34), JUnit 4.13.2.

Bağımlılıklar (hepsi desktop-kit'te zaten kullanılanlar): `desktop-kit` (arayüz), `activity-compose` (Compose'u etkinliğe bağlar); testte `junit`, `robolectric`, `androidx.test:core`, `compose-ui-test-junit4`. Yeni her kütüphane için kullanıcıdan onay alınır.
