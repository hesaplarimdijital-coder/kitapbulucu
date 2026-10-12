# Durum

Son güncelleme: 12.10.2026, Sprint 1 bitti (S-06 kullanıcı denemesini bekliyor).

## Şu an

- Uygulama: Kitap Bulucu, paket `com.hesaplarimdijital.kitapbulucu`. Depo: `hesaplarimdijital-coder/kitapbulucu` (herkese açık).
- Sprint 0 bitti (S-01 … S-05). Sprint 1 bitti: S-07 okuyucu, S-08 yazıcı, S-09 arama 1 kodda ve testte; S-06 deneme ekranı APK'da, kullanıcı sonuçları bekleniyor.
- APK: `ci/apk` dalında `kitapbulucu.apk` (https://github.com/hesaplarimdijital-coder/kitapbulucu/raw/ci/apk/kitapbulucu.apk); aynı dalda `ornek/Babam.xlsx`, `ornek/Benim.xlsx`, `ornek/Ortak.xlsx` (yazıcının ürettiği örnekler). Ekran görüntüleri `ci/screenshots` dalında.
- Kod haritası: `xlsx/` (XlsxReader, XlsxWriter, CsvReader, DateFormats, SpreadsheetReader), `model/` (Book, Owner, BookColumn, BookTable), `text/` (TurkishText, Fold), `search/` (SearchIndex), `trial/` (S-06 deneme ekranı; S-08'den sonra `MiniXlsx` gereksiz, S-12'de kaldırılacak), `ui/` (kabuk, yer tutucu ekranlar).
- Seçilen tasarım: Taslak A "Fiş". Henüz kitin varsayılan renkleri kullanılıyor; Taslak A renkleri S-11'de.
- Sıradaki: Sprint 2 (S-10 arama 2, S-11 tema temeli, S-12 yeniden okuma, S-13 kurulum, S-14 ara ekranı). S-10, S-11, S-12 [F] → Fable; S-13, S-14 [O] → Opus.

## Kullanıcıdan beklenenler

1. GitHub'a iki gizli değer: `KB_KEYSTORE_BASE64` ve `KB_KEYSTORE_PASSWORD` (12.10.2026'da dosya ve parola verildi). Eklenene kadar APK'lar her seferinde başka anahtarla imzalanır; yeni APK eskisinin üstüne kurulamaz (önce kaldır).
2. S-06 deneme ekranı sonuçları (tablet + babanın telefonu): Drive'da dosya oluştur / seç / oku / yaz / klasör seç; "Sonuçları kopyala" çıktısı. Sonuç ARCHITECTURE 8'e işlenecek.
3. `ornek/Ortak.xlsx` dosyasının tablette Excel, Office NX ve Google E-Tablolar'da açılıp açılmadığı (S-08'in son kriteri).
4. Sprint 0 tablet kontrol listesi henüz denenmedi (kullanıcı söyledi); Sprint 1 listesiyle birlikte sorulacak.
5. Kit 0.1.1 onayı: Sprint 3'te (K-01) sorulacak.

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
- CI her yapımda örnek çalışma kitaplarını openpyxl ve LibreOffice ile açar; bu kontrol ~30 sn sürer.

## Riskler

- Google Drive klasör seçmeyi desteklemeyebilir; babanın telefonunda paylaşılan dosyanın görünmesi belirsiz. S-06 denemesi henüz yapılmadı.
- Raf bilgisi hiç yok; uygulamanın asıl faydası raflar girilince ortaya çıkar (S-22).
- Baba eski 2 sütunlu dosyasını kullanmayı sürdürürse birleştirme her seferinde aynı kuralları uygulamalı (ARCHITECTURE 10). Öneri: baba temiz `Babam.xlsx` ile devam etsin.
- Arama hızı 10.000 kitapla henüz ölçülmedi (S-10'da test yazılacak); içerme araması bütün sözcük dağarcığını tarıyor, gerekirse daraltılır.

## Sprint 1 retro

Her yapımda CI'ı beklemek yerine birkaç hikâyeyi birlikte göndermek zaman kazandırır; ama bu sprintte üç hatanın üçü de test beklentisindeki yazım hatasıydı, kod değil: beklentileri yazmadan önce katlama kurallarını bir kez daha okumak CI turlarını azaltır.

## Devam mesajı

`CLAUDE.md, docs/STATE.md ve docs/BACKLOG.md dosyalarını oku; önce tablet kontrol listesi ve S-06 deneme sonuçlarını sor, sonra Sprint 2'yi planla ve onayımı al.`
