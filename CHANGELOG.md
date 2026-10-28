# Değişiklikler

## 1.2.0 — 29.10.2026

- **Telefon**: alttan açılan sayfalar parmakla aşağı çekilip kapanır; kitaplığın 0.2.0 sürümüyle daha akıcı geçişler, kaydırınca gizlenen alt çubuk, aramada "Hepsi / MKE / Barış" seçim düğmeleri, açık rafta kayan geçiş ve Android'in geri hareketi.
- **Öneriler**: kitap eklerken ve düzenlerken Yazar ve Yayınevi yazdıkça iki listeden öneri çıkar.
- **Ad ve yazarları düzelt** (Ayarlar): tamamı büyük harfle yazılmış adlar ve yazarlar bir kez "Fyodor Dostoyevski" biçimine çevrilir; yedek alınır, tek adımda geri alınır.
- **Kurulum**: her APK aynı anahtarla imzalanır, güncellemeler eskisinin üstüne kurulur (ilk seferde bir kez kaldırıp kurmak gerekir).

## 1.1.0 — 22.10.2026

- **Adlar**: listeler artık "MKE" ve "Barış" olarak görünüyor (Drive'daki dosya adları ve ayarlar değişmedi). Dışa aktarılan Excel'in sayfaları da MKE ve Barış.
- **Otomatik güncelleme**: uygulama açılınca, öne gelince, DeX'te pencereye dönünce ve açık kaldıkça dakikada bir iki dosyayı yeniden okur; dışarıda değişen liste için "… listesi güncellendi" notu çıkar.
- **Raflar**: raflar cihazda oluşturulur ("Yeni raf"), rafa arayarak kitap eklenir, Kütüphane'den bir ya da birçok kitap "Rafa koy…" ile rafa konur; raf adı değiştirilir, boş raf silinir.
- **Telefon**: iki satırlı listeler, yüzen "Kitap ekle"/"Yeni raf" düğmeleri, alttan açılan ayrıntı sayfaları, uzun basarak çoklu seçim, üst çubukta geri ve eylemler; formlarda düğmeler klavyeye yapışmaz; klavye açıkken alt menü gizlenir.
- **Animasyonlar**: ekran geçişleri, açılan paneller, pencereler, geri al çubuğu ve seçimler yumuşak geçişli.
- **DeX**: klavye-fare modunda yazılar 1 punto büyük; aramada satır olduğu yerde açılır; Ctrl/Shift ile çoklu seçim.

## 1.0.0 — 14.10.2026

İlk tam sürüm.

- **Arama**: Türkçe harf ve şapka duyarlı, yazım hatasına toleranslı, sözcük sırası önemsiz; iki kütüphanede birden. Sonuç yoksa en yakın öneriler ve "Kitap ekle". 10.000 kitapta tuş başına 50 ms'nin altında.
- **Sonuç satırı**: kitap adı, yazar; sağda kütüphane ve raf (20 sp, kalın). Satır yerinde açılır: Düzenle, Taşı, Sil.
- **Kütüphane**: bütün liste sıralanabilir tabloda; sağ tık menüsü, F2 düzenle, Del sil; "+" ile kitap ekleme.
- **Raflar**: raf listesi, raftaki kitaplar ve "Raf doldur".
- **Kitap ekleme ve düzenleme**: aynı kitap varsa sorar; öteki kişinin listesine yazmadan önce onay ister.
- **Güvenli yazma**: her yazmadan önce yeniden okuma, yedek (son 10), uygulama klasöründe üretip doğrulama, geri okuma. Yazılamayan değişiklik bekler, kaybolmaz.
- **Geri al**: Ctrl+Z ya da 8 saniyelik "Geri al" çubuğu; birleştirme tek adımda geri alınır.
- **Excel birleştirme**: başka bir Excel ya da CSV; ilk temizliğin kuralları (İMZALI işareti, ithaf, tekrar eden satırlar) uygulanır. Yeni, değişen, dosyada olmayan kitaplar ve belirsiz eşleşmeler tek tek seçilir; yanıtlar hatırlanır.
- **Ayarlar**: ben kimim, dosyalar, klasör, görünüm, giriş, kısayollar; yedekten geri yükleme, bekleyen değişiklikler ("Şimdi dene"), iki listeyi tek Excel olarak dışa aktarma.
- **Kısayollar**: Ctrl+K ara, Ctrl+N ekle, Ctrl+O dosya seç, Ctrl+Shift+O birleştir, Ctrl+Z geri al, F5 yenile, Ctrl+, ayarlar, Ctrl+/ kısayol listesi.
- **Görünüm**: Taslak A "Fiş"; "Kâğıt" ve "Gece okuması" temaları, dört sıcak vurgu rengi; her metin en az 4.5:1 kontrastta. Başlıklar ve kitap adları Literata, arayüz IBM Plex Sans, raf kodları mono.
- **Excel okuyucu ve yazıcı**: kendi kodumuz; `.xlsx` ve `.csv` (UTF-8 ya da Windows-1254). Tanınmayan sütunlar korunur, tarihe dönmüş hücreler metne çevrilir.
