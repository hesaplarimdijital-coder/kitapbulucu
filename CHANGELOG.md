# Değişiklikler

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
