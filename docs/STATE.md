# Durum

Son güncelleme: 12.10.2026, Sprint 2 bitti.

## Şu an

- Uygulama: Kitap Bulucu, paket `com.hesaplarimdijital.kitapbulucu`. Depo: `hesaplarimdijital-coder/kitapbulucu` (herkese açık).
- Sprint 0, 1, 2 bitti. Uygulama artık salt okunur kullanılabilir: ilk açılışta kurulum (bu cihaz kimin, iki dosya seç ya da oluştur, gerekirse sayfa seç ve sütun eşle), sonra Ara ekranı (yazdıkça sonuç, Hepsi/Babam/Benim, "Babam · Raf C2" kartı, bulunamazsa 3 öneri). Açılışta önbellekten anında liste, öne gelişte ve F5'te yeniden okuma.
- APK: `ci/apk` dalında `kitapbulucu.apk` (https://github.com/hesaplarimdijital-coder/kitapbulucu/raw/ci/apk/kitapbulucu.apk); aynı dalda `ornek/` altında yazıcının örnek dosyaları. Ekran görüntüleri `ci/screenshots` dalında; başvuru görüntüleri `app/src/test/screenshots/` (19 ekran).
- Kod haritası: `xlsx/` (okuyucu, yazıcı, csv, tarih), `model/` (Book, Owner, BookColumn, BookTable, ColumnMapping), `text/` (TurkishText, Fold), `search/` (SearchIndex, EditDistance, BookGroups), `store/` (AppSettings, FileLinks, Documents/SafDocuments, LibraryCache, LibraryStore, LibraryState, RelativeTime), `ui/` (AppShell, AppShortcuts, Controls, FilePicker, LibraryTexts, setup/, search/, theme/AppPalette), `App.kt`. Deneme ekranı (`trial/`) kaldırıldı.
- Tasarım: Taslak A "Fiş" renkleri uygulandı (Kâğıt / Gece okuması / OLED, 8 vurgu). Başlıklar şimdilik sistemin serif yazısıyla; Literata için kit 0.1.1 (K-01) gerekiyor.
- Sıradaki: Sprint 3 (S-15 güvenli yazma [F], S-16 klavye [O], S-17 kitap ekleme [O], K-01 kit 0.1.1 [O]). Başlarken Fable önerilir (S-15).

## Kullanıcıdan beklenenler

1. GitHub'a iki gizli değer: `KB_KEYSTORE_BASE64` ve `KB_KEYSTORE_PASSWORD` (12.10.2026'da dosya ve parola verildi). Eklenene kadar APK'lar her seferinde başka anahtarla imzalanır; yeni APK eskisinin üstüne kurulamaz (önce kaldır).
2. S-06 kalanları: seçilen Drive klasörü boş muydu; yeniden başlatma sonrası izin; dosyanın öteki cihazda görünmesi ve değişikliğin ulaşma süresi; tablet denemesi (kullanıcı "yarın söyleyeceğim" dedi).
3. Sprint 2 tablet kontrol listesi (aşağıda devam mesajında) ve `ornek/Ortak.xlsx` dosyasının Excel, Office NX, Google E-Tablolar'da açılması.
4. Ara ekranı geri bildirimi (12.10.2026, telefon): yazılar çok büyük, basılan sonuç yerinde açılmalı, "Babam" ve raf üst üste, "2 adet" aramada gereksiz. Backlog'a S-30 olarak alındı; kullanıcı "şimdilik değişiklik yapma" dedi. Sprint 3 planlanırken sıraya konacak; raf yazısı ölçüsü (CLAUDE.md'deki 32 sp kuralı) o zaman yeniden kararlaştırılacak.
5. Kit 0.1.1 onayı: Sprint 3'te (K-01) sorulacak; serif yazı yuvası ve Literata.

## Veri hakkında bilinenler

- Kaynak `Kütüphane.xlsx` babanın listesi: 7.325 satır, 2 sütun (kitap adı, yazar), başlık satırı listenin içinde (6856. satır), hepsi büyük harf, raf bilgisi yok. Kullanıcının kendi listesi henüz yok.
- Aynı satırın tekrarı o kitaptan birden çok kopya demek (kullanıcı söyledi).
- Temiz `Babam.xlsx` (Kitap Adı, Yazar, Raf, Adet, İmzalı, Yayınevi, Yıl, ISBN, Not): 6.775 satır, 7.324 adet; 183 satır imzalı; 164 benzer çift birleştirilmedi (raporun "Benzerler" sayfası).
- Yazar sütununda yer yer yayınevi ya da kurum adı var (dokunulmadı). 273 satırda yazar yok.
- Kaynak ve temiz dosyalar depoda değil. Betikler `tools/temizleme/`; listeye özgü kararlar ayrı bir `kararlar.json` dosyasında (depoda değil). Yeniden üretmek için kaynak dosyayı kullanıcıdan iste.
- Test dosyaları (`app/src/test/resources/fixtures/`): openpyxl, LibreOffice ve elle yazılmış uç durumlar; hepsi uydurma kitaplar. Üretici betikler `tools/fixtures/`.

## Kararlar

- Tekrar eden satırlar tek satır; sayı `Adet` sütununda. İmzalı kopya ayrı satır; `İmzalı` sütununda `Evet`, ithaf Not'ta.
- Excel biçimi genel olarak onaylı ama kesin değil; kullanıcı sonradan değişiklik isteyebilir.
- Büyük harfler, yazar adı yazımları ve yazar sütunundaki yayınevi adları olduğu gibi kalır.
- Güvenli yazmada "geçici dosya + yerine koy" yerine "üret, doğrula, yedekle, yaz, geri oku" (ARCHITECTURE 7).
- Sprint planı 6 kısa sprint; her sprint başında onay alınır.
- Telefonda yalnızca dikey; tablet ve DeX serbest.
- Opus işlerinde effort: high (kullanıcıya önerildi).
- Yazıcı boş hücreleri de metin biçemiyle yazar ve sütunlara `<col style>` verir; başlıklar takma adlarla tanınır; tarih biçimli hücre `15 MART 1998` / `NİSAN 1953` olur (ARCHITECTURE 5, 6).
- Arama: tam sözcük önekten önce sıralanır (katman içi); sorgudaki kesme işaretli sözcük yalnızca bitişik aranır (ARCHITECTURE 9).
- CI her yapımda örnek çalışma kitaplarını openpyxl ve LibreOffice ile açar; adımın süre sınırı var (bir kez 10 dk takılmıştı).
- Kurulum "Bitir" bu cihazın listesi okununca açılır; öteki liste Ayarlar'dan sonra eklenebilir. Klasör seçimi isteğe bağlı, yalnızca Ayarlar'da.
- Sütun eşleme dosya başına saklanır; başlık satırı değişirse ya da uygulamanın başlıkları belirirse kendiliğinden devre dışı kalır. Üstünde 3'ten fazla dolu satır olan başlık kabul edilmez.
- Aynı kitap iki listede de varsa Ara ekranında tek kartta iki satır ("Babam · Raf C2", "Benim · Raf 1B").

## Riskler

- Google Drive klasör seçmeyi desteklemeyebilir; babanın telefonunda paylaşılan dosyanın görünmesi belirsiz. S-06 denemesi henüz yapılmadı.
- Raf bilgisi hiç yok; uygulamanın asıl faydası raflar girilince ortaya çıkar (S-22).
- Baba eski 2 sütunlu dosyasını kullanmayı sürdürürse birleştirme her seferinde aynı kuralları uygulamalı (ARCHITECTURE 10). Öneri: baba temiz `Babam.xlsx` ile devam etsin.
- Arama hızı CI'da 10.000 uydurma kitapla ölçülüyor (tuş başına 50 ms altı); gerçek tablette henüz denenmedi.
- Henüz hiçbir şey dosyaya yazılmıyor (yalnızca "Yeni dosya oluştur"); güvenli yazma S-15'te.

## Sprint 1 retro

Her yapımda CI'ı beklemek yerine birkaç hikâyeyi birlikte göndermek zaman kazandırır; beklentileri yazmadan önce katlama kurallarını bir kez daha okumak CI turlarını azaltır.

## Sprint 2 retro

Ekran görüntülerine bakmak iki gerçek hatayı yakaladı (eşleme ekranının yanlış başlık satırı tahmini, yinelenen bölüm adı); her ekran hikâyesinde görüntüye bakmadan "bitti" demeyelim.

## Devam mesajı

`CLAUDE.md, docs/STATE.md ve docs/BACKLOG.md dosyalarını oku; önce S-06 kalanlarını ve Sprint 2 tablet kontrol listesinin sonuçlarını sor, sonra S-30'u da düşünerek Sprint 3'ü planla ve onayımı al.`
