# Durum

Karar (22.10.2026): arayüzde listeler MKE ve Barış adıyla görünür; dosyalar (`Babam.xlsx`, `Benim.xlsx`), sayfa adı ve iç adlar Babam/Benim olarak kalır.

Son güncelleme: 22.10.2026, sürüm 1.1.0 (son sürüm): MKE/Barış adları, otomatik yeniden okuma; telefon testleri kullanıcıda tamam.

## Şu an

- Uygulama: Kitap Bulucu, paket `com.hesaplarimdijital.kitapbulucu`, sürüm 1.0.0. Depo: `hesaplarimdijital-coder/kitapbulucu` (herkese açık). Kit deposu `hesaplarimdijital-coder/desktop-kit` (push yetkisi var; etiket gönderilemez).
- Sprint 0–6 bitti (kullanıcı "bitene kadar durma, ara mesaj verme" dedi; [F] işler S-23 ve S-27 de Opus ile yapıldı). Açık kalanlar yalnızca kullanıcı denemeleri.
- Uygulama: kurulum, arama, kitap ekleme (Kütüphane'de "+" ve Ctrl+N), silme/düzenleme/taşıma, geri alma, kütüphane tablosu, raflar ve raf doldurma, Excel birleştirme (Ctrl+Shift+O, Ayarlar), yedekten geri yükleme, bekleyen değişiklikler, tek Excel olarak dışa aktarma, Ctrl+O.
- APK: `ci/apk` dalında `kitapbulucu.apk`; `ornek/` altında örnek dosyalar. Ekran görüntüleri `ci/screenshots`; başvuru görüntüleri `app/src/test/screenshots/` (33 ekran).
- Kod haritası: `xlsx/`, `model/`, `text/` (TurkishText, Fold, Cleaning), `search/`, `importing/MergePlan`, `store/` (… LibraryStore: restore, exportTo, backupBookCount, mergeDecisions), `ui/` (AppShell, AppShortcuts, Controls + CheckRow, DataSection, BookActions, FilePicker, LibraryTexts, setup/, search/, add/, library/, shelves/, importing/, theme/), `App.kt`.
- K-01 bitti; K-02 bitti (kit 0.1.2: `type` kancası, klavye açıkken alt çubuk gizli, `DenseTable` çoklu seçim). Uygulama `AppFonts.type` ile klavye-fare modunda 1 sp büyük yazı kullanıyor.
- Raflar (S-33…S-35): `store/ShelfCatalog` (cihazda `files/library/shelves.json`, kişi başına), Raflar ekranında Yeni raf / Adını değiştir / Rafı sil / Kitap ekle; Kütüphane'de "Rafa koy…" bir ya da birçok kitap (Ctrl/Shift, dokunmatikte "Seç").
- Çalışma biçimi: sprint yok; Opus orkestratör, işler alt ajanlarla (CLAUDE.md). Aynı depoda paralel ajanlar ayrı klonlarda çalışır (`git clone /home/claude/kitapbulucu …`), yeni metinler ayrı `strings_*.xml` dosyalarına yazılır.
- Kit 0.1.3: ekran geçişleri, `CompactTopBar`, `currentWidthClass()`. JitPack 0.1.3 etiketini derleyemedi (Maven indirme hatası, önbellekte kaldı); uygulama `desktopKit = "ce87c9ae8e"` (aynı kod). Sonraki kit sürümünde etikete dönülür.
- Telefon arayüzü (S-36, S-37): `ui/TouchControls.kt` (liste, FAB, alt sayfa, seçim) ve `ui/PhoneLayout.kt` (alt eylem çubuğu, telefon başlığı, boş durumlar). İki dosyada telefon algılama ayrı (`isCompactLayout()` ve `isPhone()`); ileride birleştirilebilir. DeX görüntüleri değişmedi.
- S-38 (telefon geri bildirimi 2): alt sayfalar `SheetHost` ile uygulamanın penceresinde çizilir (AppShell sarar; kökün safeDrawing boşluğu sayesinde gezinme çubuğu ve klavyenin üstünde); `ShelfFormDialog` imleci sayfa içeriğinde ister. `PhoneForm` + `isKeyboardOpen()`: alt düğme çubuğu klavye açıkken formun sonuna geçer. Ara'da satır yerinde açılır (`LazyListState.reveal`, `revealDelta`: en az kaydırma, başa taşımaz).

## Kullanıcıdan beklenenler

1. GitHub'a iki gizli değer: `KB_KEYSTORE_BASE64` ve `KB_KEYSTORE_PASSWORD` (12.10.2026'da dosya ve parola verildi). Eklenene kadar APK'lar her seferinde başka anahtarla imzalanır; yeni APK eskisinin üstüne kurulamaz (önce kaldır). S-29'un "üstüne kurulur" maddesi buna bağlı.
2. Telefonda S-38 denemesi: kitap sayfasında "Sil" görünüyor mu, "Yeni raf" klavyenin üstünde mi, Ekle/Düzenle'de klavye açıkken alt çubuk klavyeye yapışmıyor mu (yalnızca gerçek telefonda görülebilir).
3. Tablette ve babanın telefonunda baştan sona deneme (kontrol listesi son mesajda); S-06 kalanları (yeniden başlatma sonrası izin, dosyanın öteki cihazda görünmesi, değişikliğin ulaşma süresi).
4. `ornek/Ortak.xlsx` ve dışa aktarılan `Kitaplar.xlsx` dosyasının Excel, Office NX ve Google E-Tablolar'da açılması.

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
- Güvenli yazma yalnızca testlerde ve uydurma dosyalarla denendi; Drive'daki gerçek dosyada ilk yazma önce `Benim.xlsx` ile denenmeli.
- Excel birleştirme gerçek 7.000 satırlık dosyayla tablette denenmedi; testte 7.000 ekleme 2 sn'nin altında.

## Sprint 1 retro

Her yapımda CI'ı beklemek yerine birkaç hikâyeyi birlikte göndermek zaman kazandırır; beklentileri yazmadan önce katlama kurallarını bir kez daha okumak CI turlarını azaltır.

## Sprint 2 retro

Ekran görüntülerine bakmak iki gerçek hatayı yakaladı; her ekran hikâyesinde görüntüye bakmadan "bitti" demeyelim.

## Sprint 3 retro

Fable işini (S-15) öne alıp Opus işlerini arkaya toplamak model değişikliğini ikiye indirdi; kit deposuna erişim baştan oturuma eklenseydi K-01 beklemeden biterdi.

## Sprint 4–6 retro

Kullanıcı durmadan bitirmemi isteyince hikâyeleri ikişer üçer toplayıp CI'a göndermek iyi işledi (üç gönderim, üçü de ilk seferde yeşil); büyük birleştirmenin hızını en baştan testle ölçmek kare zamanlı bir döngüyü yakalattı.

## Devam mesajı

`CLAUDE.md, docs/STATE.md ve docs/BACKLOG.md dosyalarını oku; önce tablet ve telefon deneme sonuçlarını ve iki gizli değeri sor, sonra gelen geri bildirimlerden bir sprint planla ve onayımı al.`
