# Durum

Son güncelleme: 14.10.2026, Sprint 3 bitti (K-01'in son adımı kullanıcının Release'ini bekliyor).

## Şu an

- Uygulama: Kitap Bulucu, paket `com.hesaplarimdijital.kitapbulucu`. Depo: `hesaplarimdijital-coder/kitapbulucu` (herkese açık). Kit deposu `hesaplarimdijital-coder/desktop-kit` bu oturuma eklendi (push yetkisi var; etiket gönderilemez).
- Sprint 0–3 bitti. Uygulama: kurulum, arama (satır yerinde açılır, klavye), kitap ekleme (Ctrl+N, Enter kaydeder, aynı kitap ve öteki liste için onay), güvenli yazma (yeniden oku, uygula, doğrula, yedek son 10, yaz, geri oku; başarısızsa bekler ve F5/öne gelişte yeniden dener).
- APK: `ci/apk` dalında `kitapbulucu.apk`; `ornek/` altında örnek dosyalar. Ekran görüntüleri `ci/screenshots`; başvuru görüntüleri `app/src/test/screenshots/` (23 ekran).
- Kod haritası: `xlsx/`, `model/` (Book, Owner, BookColumn, BookTable, ColumnMapping), `text/`, `search/` (SearchIndex, EditDistance, BookGroups), `store/` (AppSettings, FileLinks, Documents, LibraryCache, LibraryStore, LibraryState, Change, ChangeApplier, PendingChanges, Backups, RelativeTime), `ui/` (AppShell, AppShortcuts, Controls, FilePicker, LibraryTexts, setup/, search/, add/, theme/AppPalette, theme/AppFonts), `App.kt`.
- Yazı: kitap adları Literata (OFL, `res/font`, `licenses/Literata-OFL.txt`, üretici `tools/fonts/make_literata.py`). Ara ekranında raf kodu 20 sp (CLAUDE.md güncellendi).
- K-01: kit 0.1.1 kit deposunda (`DeskFonts.heading`, `TableColumn.textStyle`), kit CI yeşil. Kullanıcı GitHub'da `desktop-kit` için 0.1.1 Release'ini oluşturunca: `gradle/libs.versions.toml` → 0.1.1, `DesktopKitRoot(fonts = DeskFonts.Plex.copy(heading = AppFonts.Literata))`, ekran görüntülerine bak.
- Sıradaki: Sprint 4 (S-18 silme, S-19 düzenleme, S-20 kütüphane, S-21 raflar, S-22 raf doldurma), hepsi [O] → Opus. K-01'in son adımı Sprint 4'ün başında.

## Kullanıcıdan beklenenler

1. GitHub'a iki gizli değer: `KB_KEYSTORE_BASE64` ve `KB_KEYSTORE_PASSWORD` (12.10.2026'da dosya ve parola verildi). Eklenene kadar APK'lar her seferinde başka anahtarla imzalanır; yeni APK eskisinin üstüne kurulamaz (önce kaldır).
2. S-06 kalanları: seçilen Drive klasörü boş muydu; yeniden başlatma sonrası izin; dosyanın öteki cihazda görünmesi ve değişikliğin ulaşma süresi; tablet denemesi (kullanıcı "yarın söyleyeceğim" dedi).
3. Sprint 2 tablet kontrol listesi (aşağıda devam mesajında) ve `ornek/Ortak.xlsx` dosyasının Excel, Office NX, Google E-Tablolar'da açılması.
4. S-30 yapıldı (raf 20 sp, satır yerinde açılır, Babam/raf alt alta, adet yalnızca ayrıntıda, "İmzalı" küçük etiket); kullanıcının telefonda yeniden bakması bekleniyor.
5. GitHub'da `desktop-kit` deposunda 0.1.1 Release'i (etiket `0.1.1`, `main` üzerindeki son commit).

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
- Güvenli yazma yalnızca testlerde ve uydurma dosyalarla denendi; Drive'daki gerçek dosyada ilk yazma S-17 ile gelecek, önce `Benim.xlsx` ile denenmeli.

## Sprint 1 retro

Her yapımda CI'ı beklemek yerine birkaç hikâyeyi birlikte göndermek zaman kazandırır; beklentileri yazmadan önce katlama kurallarını bir kez daha okumak CI turlarını azaltır.

## Sprint 2 retro

Ekran görüntülerine bakmak iki gerçek hatayı yakaladı; her ekran hikâyesinde görüntüye bakmadan "bitti" demeyelim.

## Sprint 3 retro

Fable işini (S-15) öne alıp Opus işlerini arkaya toplamak model değişikliğini ikiye indirdi; kit deposuna erişim baştan oturuma eklenseydi K-01 beklemeden biterdi.

## Devam mesajı

`CLAUDE.md, docs/STATE.md ve docs/BACKLOG.md dosyalarını oku; önce kit 0.1.1 Release'ini ve tablet deneme sonuçlarını sor, K-01'in son adımını bitir, sonra Sprint 4'ü planla ve onayımı al.`
