# Backlog

Başlık biçimi: `kimlik ad · puan · model · durum`. Puan: 1, 2, 3, 5, 8. Model: [F] Fable (düşünme isteyen), [O] Opus (tanımı net). Durum: bekliyor, sürüyor, bitti, onay bekliyor.

Bir sprint bitince hikâyeleri `docs/DONE.md` dosyasına taşınır; burada sprint tablosundaki satırı kalır.

## Sprint planı

| Sprint | Hedef | Hikâyeler | Puan | Model |
| --- | --- | --- | --- | --- |
| 0 | Kod yazmaya hazır olmak | S-01 … S-05 | 15 | F, sonda O |
| 1 | Dosya okunuyor, yazılıyor; arama motorunun temeli | S-06 … S-09 | 16 | F |
| 2 | İlk kullanılabilir sürüm: dosyalara bağlan ve ara (salt okunur) | S-10 … S-14 | 21 | F, sonra O |
| 3 | Değişiklikler güvenle yazılıyor; kitap ekleme | S-15, S-16, S-17, K-01 | 19 | F, sonra O |
| 4 | Sil, düzelt, kütüphane, raflar | S-18 … S-22 | 19 | O |
| 5 | Dışarıdan gelen Excel; ayarlar | S-23 … S-26 | 18 | F, sonra O |
| 6 | Cilalama ve 1.0.0 | S-27 … S-29 | 10 | F, sonra O |

İlk önerilen plandan farkı: 4 yerine 6 kısa sprint (ilk planda 1. ve 3. sprint 27'şer puandı, bir oturuma sığmaz) ve arama öne alındı; böylece 2. sprintin sonunda tablette denenebilen bir sürüm olur. Her sprintin başında onay alınır.

## Sprint 0: Hazırlık

### S-01 Excel'i temizleme · 3 · [F] · bitti
- [x] Rapor verildi: 3 sayfa (biri dolu), 7.325 satır, 2 sütun, başlık satırı listenin içinde, birleştirilmiş hücre yok.
- [x] Temizleme planı onaylandı.
- [x] `Babam.xlsx` (6.775 satır, 7.324 adet), `Benim.xlsx` (boş) ve `Temizleme raporu.xlsx` üretildi; bağımsız denetim hiçbir satırın kaybolmadığını doğruladı.
- [x] Kullanıcı dosyaları genel olarak onayladı; isteği üzerine `Adet` ve `İmzalı` ayrı sütun oldu (dosyalar yeniden gönderildi).
- Not: biçim kesin değil; kullanıcı sonradan değişiklik isteyebilir. Betik `tools/temizleme/` içinde, yeniden çalıştırılabilir.

### S-02 Mimari, CLAUDE.md, STATE.md · 3 · [F] · bitti
- [x] `desktop-kit` README'si okundu; kitin sınırları not edildi.
- [x] `docs/ARCHITECTURE.md`, `CLAUDE.md` (60 satırın altında), `docs/STATE.md` yazıldı.

### S-03 Backlog · 3 · [F] · bitti
- [x] Bütün özellikler hikâye olarak yazıldı; kabul kriteri, puan ve model etiketi var.
- [x] Sprint planı gözden geçirildi.

### S-04 Ana ekran için 2 tasarım taslağı · 3 · [F] · bitti
- [x] İki farklı taslak (`docs/design/`): A "Fiş" ve B "İki bölme"; her biri açık ("Kâğıt") ve koyu ("Gece okuması") temada, DeX genişliğinde.
- [x] Taslaklardaki her metin/arka plan çifti en az 4.5:1 (`docs/design/PALETTE.md`, 8 vurgu × 2 tema × 2 taslak); raf yazısı en az 32 sp.
- [x] Telefon (yalnızca dikey) taslakları: `taslak-a-telefon.png`, `taslak-b-telefon.png`.
- [x] Kullanıcı Taslak A "Fiş"i seçti.

### S-05 Depo iskeleti, CI, örnek test Excel'i · 3 · [O] · bitti
- [x] `app` modülü, kit bağımlılığı; boş kabuk açılıyor (Ara, Kütüphane, Raflar, Ayarlar); Ayarlar kitin bölümleriyle çalışıyor.
- [x] Telefonda (en dar kenar 600 dp'den küçük) ekran dikeye kilitli; tablette ve DeX'te serbest.
- [x] `.gitignore` içinde `*.xlsx` (yalnızca test dosyaları klasörü hariç); uydurma kitaplı iki örnek test Excel'i var (`tools/fixtures/make_fixtures.py`).
- [x] CI: derleme, birim testleri, ekran görüntüleri `ci/screenshots` dalında, APK `kitapbulucu-apk` olarak indirilebilir.
- [x] CI, APK'da INTERNET izni görürse kırılır; manifest izni ayrıca siler.
- [x] APK her seferinde aynı anahtarla imzalanır: anahtar GitHub gizli değerlerinde (`KB_KEYSTORE_BASE64`, `KB_KEYSTORE_PASSWORD`).
- [ ] Kullanıcı iki gizli değeri GitHub'a ekledi (oturum ekleyemiyor, proxy izin vermiyor).
- [x] Robolectric `sdk=34`; ekran görüntüsü klasörü test çıktısı olarak tanımlı; telefon ve DeX görüntüleri `app/src/test/screenshots` içinde referans.

## Sprint 1: Dosya okunuyor, yazılıyor; arama motorunun temeli

### S-06 Spike: Drive ve SAF denemesi · 3 · [F] · sürüyor (kullanıcının deneme sonuçları bekleniyor)
Gerçek listelere dokunmadan, uygulamanın kendi `Kitap Bulucu deneme.xlsx` dosyasıyla yapılır. Geçici "Deneme" ekranı (`trial/`), kurulum ekranı gelince kaldırılır.
- [x] Deneme ekranı: dosya oluştur ya da seç, oku, içine saat yazıp geri yaz, kalıcı izni göster, klasör seç ve listele, sonuçları kopyala.
- [x] Kullanıcıya adım adım deneme listesi verildi (tablet ve babanın telefonu). APK: `ci/apk` dalında `kitapbulucu.apk`.
- [ ] Ölçüldü: Drive'da okuma ve geri yazma (✓ telefonda: okuma ~150 ms, yazma 71 ms); klasör seçimi (✓ izin alındı, listede 0 öğe – boş muydu?); son değişme zamanının doğruluğu (✗ yazmadan sonra eski kaldı → içerik özetiyle karşılaştırılacak). Bekleyen: yeniden başlatma sonrası izin; paylaşılan dosyanın babanın telefonunda görünmesi; değişikliğin öteki cihaza ulaşma süresi; tablet sonuçları.
- [ ] Çalışmayan yol için alternatif seçildi; sonuçlar `STATE.md` ve ARCHITECTURE 8'e yazıldı (ilk ölçüm işlendi, kalanlar yarın).

### S-07 Excel okuyucu · 5 · [F] · bitti
- [x] `.xlsx`: paylaşılan metin, satır içi metin, sayı, boş hücre, zengin metin; seyrek satır ve sütunlar doğru yerde.
- [x] Tam sayılar `.0` olmadan, uzun sayılar (ISBN) olduğu gibi okunur.
- [x] Tarih biçimli hücre metne çevrilir (`NİSAN 1953`, `15 MART 1998`).
- [x] Birden çok dolu sayfa varsa sayfa listesi döner.
- [x] `.csv`: `;` ve `,`; UTF-8 (BOM'lu, BOM'suz) ve Windows-1254.
- [x] `.xls` ve bozuk dosya anlaşılır hata verir; uygulama çökmez.
- [x] Test dosyaları uydurma kitaplarla: openpyxl ve LibreOffice çıktısı, elle yazılmış uç durumlar. 10.000 satır 1 saniyenin altında okunur.

### S-08 Excel yazıcı · 3 · [F] · bitti (tablet denemesi bekleniyor)
- [x] Tek sayfalı kişi dosyası ve iki sayfalı (`Babam`, `Benim`) ortak dosya üretir.
- [x] Başlık kalın ve dondurulmuş; sütun genişlikleri ayarlı; bütün hücreler metin biçiminde.
- [x] Tanınmayan sütunlar aynı başlıkla geri yazılır.
- [x] Yaz → oku testi: içerik birebir aynı (Türkçe harfler, `&`, `<`, tırnak, baştaki `=` dahil).
- [x] CI örnek çıktıları saklar; bu dosyalar LibreOffice ve openpyxl ile hatasız açıldı.
- [ ] Kullanıcı örnek dosyayı tablette Excel, Office NX ve Google E-Tablolar'da açtı. (Dosyalar: `ci/apk` dalında `ornek/Babam.xlsx`, `ornek/Benim.xlsx`, `ornek/Ortak.xlsx`.)

### S-09 Arama motoru 1: katlama, dizin, sıralama · 5 · [F] · bitti
- [x] "islik" → "Islık", "sefiller" → "Sefiller", "hala" → "hâlâ" bulunur.
- [x] Sözcük sırası önemsiz: "madonna kürk" → "Kürk Mantolu Madonna".
- [x] Arama ad ve yazarda yapılır; "orhan pamuk kar" gibi karışık sorgu çalışır.
- [x] Sıralama: tam eşleşme > başlangıç eşleşmesi > içerme; katman içi kurallar ARCHITECTURE 9'daki gibi.
- [x] Kesme işaretli adlar hem ayrık hem bitişik bulunur ("istanbulun", "istanbul'un").
- [x] İki kütüphanede birden arar; "Hepsi / Babam / Benim" süzgeci.
- [x] Türkçe harfsiz sorgu da bulur ("kurk"), ama Türkçe harfli eşleşmenin altında sıralanır.

## Sprint 2: İlk kullanılabilir sürüm

### S-10 Arama motoru 2: yazım hatası, öneri, hız · 5 · [F] · bitti
- [x] "kurk mantlu" → "Kürk Mantolu Madonna" bulunur; bir iki harf hatası tolere edilir, yer değiştirme tek hata sayılır.
- [x] Yakın eşleşmeler her zaman tam, başlangıç ve içerme eşleşmelerinin altında.
- [x] Sonuç yoksa en yakın 3 öneri döner.
- [x] Üretilmiş 10.000 kitapta tuş başına 50 ms'nin altında; test süreyi ölçer ve yazar.
- [x] Kapsamlı test: Türkçe harfler, tek harfli sorgu, rakam, boş sorgu, çok uzun sorgu.

### S-11 Tema temeli · 3 · [F] · bitti
- [x] Taslak A'ya göre "Kâğıt" ve "Gece okuması" renkleri `colors = { it.copy(...) }` ile tanımlı; OLED siyah da okunur.
- [x] Vurgular: bordo, mürekkep laciverti, zeytin yeşili, eskitilmiş altın; kitin dört hazır rengi de seçilebilir.
- [x] Kontrast testi bütün tema × vurgu çiftlerinde her metin/arka plan çifti için en az 4.5:1 ister; geçmeyen renk kullanılmaz.

### S-12 Açılışta ve öne gelişte yeniden okuma · 3 · [F] · bitti
- [x] Açılışta önbellekten anında liste gelir; ardından iki dosya okunur, fark varsa ekran sessizce güncellenir.
- [x] Uygulama her öne gelişinde ve F5'te iki dosya yeniden okunur.
- [x] "Babam'ın listesi 3 dk önce güncellendi" bilgisi üretilir.
- [x] Klasör izni varsa `~$` ile başlayan ve "çakışan kopya" dosyaları yok sayılır; görülürse bir kez uyarılır. (Uyarı `LibraryStore.warning` olarak hazır; pencere S-13'te bağlanır.)
- [x] Dosya okunamazsa (silinmiş, izin gitmiş) anlaşılır mesaj ve "Başka dosya seç" yolu. (Mesajlar `LibraryTexts`; düğme S-13 kurulum ekranında.)

### S-13 Kurulum ekranı · 5 · [O] · bitti (tablet denemesi bekleniyor)
- [x] İlk açılışta "Ben kimim? Babam / Ben" seçilir; Ayarlar'dan değiştirilebilir.
- [x] İki dosya seçilir (klasör isteğe bağlı, Ayarlar'da); kalıcı izin alınır; yeniden başlatmada sorulmaz. (Yeniden başlatma tablette denenecek.)
- [x] Dosyalardan biri yoksa "Oluştur" boş, başlıklı dosya oluşturur.
- [x] Sütun başlıkları farklıysa ("Ad", "Eser", "Konum") ya da başlık yoksa bir kerelik eşleme ekranı çıkar; ilk satırlar önizlenir; eşleme saklanır.
- [x] Birden çok dolu sayfa varsa sayfa seçtirir.
- [x] `.xls` seçilirse "Excel'de .xlsx olarak kaydedip tekrar deneyin" der.
- [x] Klavye ve fareyle tamamlanabilir; açık ve koyu ekran görüntüsü.

### S-14 Ara ekranı ve sonuç görünümü · 5 · [O] · bitti
- [x] Yazdıkça sonuç gelir; üstte "Hepsi / Babam / Benim" süzgeci.
- [x] Kitap varsa kimde ve hangi rafta olduğu büyük ve net: "Babam · Raf B3"; raf yazısı en az 32 sp (36 sp), raf kodu mono. Başlıklar şimdilik sistemin serif yazısıyla; Literata K-01 (kit 0.1.1) ile gelir.
- [x] Raf boşsa "Raf yazılmamış" görünür (şu an bütün raflar boş).
- [x] İki kütüphanede de varsa ikisi de gösterilir; birden çok kopya ve imzalı kopya belirtilir.
- [x] Yoksa "Bu kitap iki kütüphanede de yok", en yakın 3 öneri ve "Ekle" düğmesi (form S-17'de bağlanır).
- [x] DeX genişliğinde ve telefon genişliğinde (dikey), açık ve koyu ekran görüntüsü.

## Sprint 3: Güvenli yazma ve kitap ekleme

### S-15 Güvenli yazma, yedek, bekleyen değişiklikler · 8 · [F] · bekliyor
- [ ] Değişiklikler işlem olarak tutulur (ekle, düzenle, sil); taşıma önce hedefe ekler, sonra kaynaktan siler.
- [ ] Her yazmadan önce dosya yeniden okunur; dışarıda yapılan değişiklik kaybolmaz (testte araya girilerek dosya değiştirilir).
- [ ] Çakışma kuralları ARCHITECTURE 7'deki gibi çalışır ve testlidir.
- [ ] Yazmadan önce yedek alınır; dosya başına son 10 yedek kalır.
- [ ] İçerik önce uygulama klasöründe üretilip geri okunarak doğrulanır; yazdıktan sonra belge geri okunup karşılaştırılır.
- [ ] Yazma başarısız olursa hata açıkça görünür; değişiklik bekleyenlerde kalır; uygulama kapanıp açılsa da öne gelişte ve F5'te yeniden denenir.
- [ ] İlk yazmadan önce bir kez "Excel'deki renk ve biçimler korunmaz" bilgisi gösterilir.
- [ ] Dosyada başka dolu sayfa varsa yazmadan önce uyarır.

### S-16 Ara ekranında klavye · 3 · [O] · bekliyor
- [ ] Uygulama açılınca imleç arama kutusunda; herhangi bir harfe basınca aramaya yazılır.
- [ ] Ctrl+K her ekrandan aramaya götürür ve odaklar.
- [ ] Ok tuşları sonuçlarda gezer, Enter kitabın detayını açar, Esc aramayı temizler.
- [ ] Dokunmatikte de rahat: hedefler parmak için yeterli.

### S-17 Kitap ekleme · 5 · [O] · bekliyor
- [ ] Alanlar: Kitap adı, Yazar, Kimin (varsayılan: kullanıcının kendisi), Raf; isteğe bağlı Adet (varsayılan 1), İmzalı, Yayınevi, Yıl, ISBN, Not.
- [ ] Kayıt doğru dosyaya eklenir.
- [ ] Aynı ad ve yazar o kütüphanede varsa uyarır: "Bu kitap zaten Babam'ın kütüphanesinde, Raf A2". "Bir kopya daha" adedi artırır, "Vazgeç" eklemez.
- [ ] Ctrl+N formu açar; Tab alanlar arasında gezer; Enter kaydeder; form açık kalır, imleç kitap adına döner.
- [ ] Ara ekranındaki "Ekle" formu aranan adla açar.
- [ ] Öteki kişinin listesine eklerken onay sorar.

### K-01 Kit 0.1.1: başlık yazı tipi ve tablo sütun stili · 3 · [O] · bekliyor (kit deposunda)
- [ ] `DeskFonts` başlıklar için ayrı bir aile alır (varsayılanı `sans`); `display`, `headline`, `title` onu kullanır.
- [ ] `TableColumn` hücre yazı stilini seçebilir (kitap adı serif olabilsin).
- [ ] Kitin testleri ve ekran görüntüleri güncel; CHANGELOG yazıldı.
- [ ] Kullanıcı GitHub'da 0.1.1 Release'ini oluşturdu; uygulama 0.1.1'e geçti.

## Sprint 4: Sil, düzelt, kütüphane, raflar

### S-18 Silme ve geri alma · 3 · [O] · bekliyor
- [ ] Silmeden önce onay sorulur.
- [ ] Sildikten sonra birkaç saniye "Geri al" görünür; Ctrl+Z de geri alır; kitap bütün alanlarıyla geri gelir.
- [ ] Adet'i 1'den büyük kitapta "bir kopyayı sil" (Adet bir azalır) ile "hepsini sil" ayrı.
- [ ] Öteki kişinin kitabı için: "Bu Babam'ın listesi, yine de değiştirilsin mi?"

### S-19 Düzenleme ve taşıma · 3 · [O] · bekliyor
- [ ] Her alan değiştirilebilir.
- [ ] "Kimin" değişirse kitap öteki dosyaya taşınır; kitap hiçbir anda iki dosyadan birden eksik kalmaz.
- [ ] Öteki kişinin kitabı için onay sorulur; yazmadan hemen önce o dosya yeniden okunur.
- [ ] Ctrl+Z son düzenlemeyi geri alır.

### S-20 Kütüphane görünümü · 5 · [O] · bekliyor
- [ ] Babam ve Benim sekmeleri; bütün kitaplar `DenseTable` içinde; 10.000 satırda akıcı.
- [ ] Sütuna tıklayınca Türkçe sıraya göre sıralanır.
- [ ] Sağ tık menüsü: Düzenle, Sil, Öteki kütüphaneye taşı, Raftaki diğer kitaplar, Kopyala.
- [ ] Üstte son eşitleme zamanı; F5 iki dosyayı hemen yeniden okur.
- [ ] F2 düzenler, Del siler (yalnızca tablo odaktayken).

### S-21 Raflar görünümü · 3 · [O] · bekliyor
- [ ] Raf raf gruplanmış liste; her rafta kaç kitap olduğu.
- [ ] "Rafı yazılmamış" grubu ayrı görünür.
- [ ] Rafa tıklayınca o raftaki kitaplar listelenir.

### S-22 Raf doldurma · 5 · [O] · bekliyor (öneri: listede hiç raf bilgisi yok)
- [ ] "Aktif raf" seçilir (örnek: B3); kitap aranıp Enter'a basılınca o rafa atanır, kutu temizlenir, sıradaki kitap yazılır.
- [ ] Arama sonuçlarının tamamına tek adımda raf atanabilir; onay sorar.
- [ ] Rafı yazılmamış kitap sayısı görünür ve azaldıkça güncellenir.
- [ ] Her atama Ctrl+Z ile geri alınır.

## Sprint 5: Dışarıdan gelen Excel ve ayarlar

### S-23 Excel birleştirme · 8 · [F] · bekliyor
- [ ] Ayarlar'da ve "Dosya" menüsünde "Excel birleştir…" (Ctrl+Shift+O); dosya seçilir, hangi listeye (Babam / Benim) birleştirileceği sorulur.
- [ ] Sütunlar farklıysa ya da başlık yoksa eşleme ekranı çıkar.
- [ ] İlk temizlikteki kurallar gelen dosyaya aynen uygulanır (ARCHITECTURE 10); eski 2 sütunlu dosya birleştirildiğinde sahte "yeni" ya da "değişen" çıkmaz.
- [ ] Karşılaştırma Türkçe duyarlı ve hataya toleranslı; aynı kitap küçük yazım farkıyla iki kez sayılmaz.
- [ ] Özet ekranı: yeni kitaplar (varsayılan: eklenir); değişenler eski ve yeni değer yan yana (varsayılan: yeni değer); gelen dosyada olmayanlar (varsayılan: silinmez, tek tek işaretlenirse silinir); belirsiz eşleşmeler ("Suç ve Ceza" ile "Suç ve Ceza 1. Cilt") kullanıcıya sorulur.
- [ ] Gelen dosyadaki boş alan dolu alanı silmez (özellikle Raf). Adet ve İmzalı farkları da "değişen" olarak gösterilir.
- [ ] Her satır işaretlenip kaldırılabilir; "Uygula" denmeden dosyaya hiçbir şey yazılmaz; uygulamadan önce yedek alınır.
- [ ] Belirsiz eşleşmelerde verilen kararlar hatırlanır; sonraki birleştirmede yeniden sorulmaz.
- [ ] Sonuç tek satır: "47 kitap eklendi, 3 kitabın rafı güncellendi, 0 kitap silindi."

### S-24 Ayarlar ve yedekten geri yükleme · 5 · [O] · bekliyor
- [ ] Kitin `AppearanceSection`, `InputSection`, `ShortcutListSection` bölümleri.
- [ ] "Ben kimim", bağlı dosyalar (ya da klasör), "Başka dosya seç".
- [ ] "Yedekten geri yükle": son 10 yedek tarih ve kitap sayısıyla listelenir; geri yüklemeden önce şimdiki dosyanın da yedeği alınır; onay sorulur.
- [ ] Bekleyen yazma varsa burada görünür; "Şimdi dene" düğmesi vardır.

### S-25 Tek Excel olarak dışa aktar · 2 · [O] · bekliyor
- [ ] Düğme `Babam` ve `Benim` sayfalı tek dosya üretir; nereye kaydedileceğini kullanıcı seçer.
- [ ] Uygulama bu dosyadan okumaz; dosya Excel, Office NX ve Google E-Tablolar'da açılır.

### S-26 Kısayollar ve menü çubuğu · 3 · [O] · bekliyor
- [ ] Ctrl+K ara, Ctrl+N kitap ekle, Ctrl+O dosya seç, Ctrl+Shift+O Excel birleştir, Ctrl+Z geri al, F5 yenile, Ctrl+, ayarlar, Ctrl+/ kısayol listesi.
- [ ] Hepsi tek `ShortcutRegistry` içinde; menü çubuğu, ipuçları ve Ayarlar'daki liste oradan üretilir; çakışma testi geçer.
- [ ] Tek tuşlu kısayollar (F2, Del) metin kutusuna yazmayı bozmaz.

## Sprint 6: Cilalama ve 1.0.0

### S-27 Tema son hâli · 3 · [F] · bekliyor
- [ ] Başlıklar ve kitap adları serif (Literata ya da Source Serif 4; OFL lisans dosyası depoda); arayüz IBM Plex Sans; raf kodları mono.
- [ ] Kitap, raf ve içe aktar simgeleri `DeskIcons.build` ile kitin çizgi stilinde.
- [ ] Gerçek ekranlarda açık ve koyu temada son kontrast denetimi; bütün ekranların DeX genişliğinde ekran görüntüsü.

### S-28 Türkçe ve İngilizce metinler · 2 · [O] · bekliyor
- [ ] Bütün metinler `strings.xml` içinde; Türkçe ve İngilizce tam.
- [ ] Mesajlar kısa ve sade; hata mesajları ne yapılacağını söyler.

### S-29 Cilalama ve 1.0.0 · 5 · [O] · bekliyor
- [ ] README (kurulum, Drive klasörü, babanın telefonu), CHANGELOG, sürüm 1.0.0.
- [ ] 1.0.0 APK'sı önceki sürümlerin üstüne kurulur; ayarlar ve yedekler kalır.
- [ ] Tablette ve babanın telefonunda baştan sona deneme listesi geçti.

## Sonraki sürüm (şimdi yapılmayacak)

- ISBN barkod okutma (çevrimdışı ZXing; ML Kit yok).
- "Ödünç verdim" işareti.
- İkiden fazla kütüphane.
