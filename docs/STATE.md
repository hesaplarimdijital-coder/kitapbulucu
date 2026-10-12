# Durum

Son güncelleme: 12.10.2026, Sprint 0 sürüyor.

## Şu an

- Uygulama adı: Kitap Bulucu. Depo: `hesaplarimdijital-coder/kitapbulucu` (herkese açık).
- Sprint 0: S-01 onay bekliyor; S-02 ve S-03 bitti; S-04 taslakları gönderildi, seçim bekliyor; S-05 bekliyor (Opus).
- Henüz kod yok. Depoda belgeler, tasarım taslakları ve temizleme betikleri var.

## Kullanıcıdan beklenenler

1. `Babam.xlsx` için onay (dosyalar 12.10.2026'da gönderildi).
2. Adet nerede dursun: Not içinde `N adet` mi, ayrı `Adet` sütunu mu. Öneri: ayrı sütun.
3. Tasarım seçimi: Taslak A (Fiş) ya da Taslak B (İki bölme). `docs/design/`.
4. Kit 0.1.1 için onay (başlık yazı tipi ailesi, tablo sütun stili). Onaylarsa Release'i kullanıcı oluşturur.
5. S-05 için Opus'a geçiş.

## Veri hakkında bilinenler

- Kaynak `Kütüphane.xlsx` babanın listesi: 7.325 satır, 2 sütun (kitap adı, yazar), başlık satırı listenin içinde (6856. satır), hepsi büyük harf, raf bilgisi yok. Kullanıcının kendi listesi henüz yok.
- Aynı satırın tekrarı o kitaptan birden çok kopya demek (kullanıcı söyledi).
- Temiz `Babam.xlsx`: 6.775 satır, 7.324 adet. 499 kitabın birden çok kopyası var; 185 imzalı kopya; 164 benzer çift birleştirilmedi (raporun "Benzerler" sayfası, karar kullanıcıda).
- Yazar sütununda yer yer yayınevi ya da kurum adı var (dokunulmadı). 273 satırda yazar yok.
- Kaynak dosya ve temiz dosyalar depoda değil. Betikler `tools/temizleme/` içinde; yeniden üretmek için kaynak dosyayı kullanıcıdan iste.

## Kararlar

- Tekrar eden satırlar tek satır + adet; imzalı kopya ayrı satır, Not `İmzalı`.
- Büyük harfler, yazar adı yazımları ve yazar sütunundaki yayınevi adları olduğu gibi kalır.
- Güvenli yazmada "geçici dosya + yerine koy" yerine "üret, doğrula, yedekle, yaz, geri oku" uygulanır (ARCHITECTURE 7). Kullanıcıya söylendi.
- Sprint planı 6 kısa sprint olarak önerildi; her sprint başında onay alınır.
- Telefonda yalnızca dikey kullanım olacak (kullanıcı söyledi); telefon taslakları `docs/design/` içinde.

## Riskler

- Google Drive klasör seçmeyi desteklemeyebilir; babanın telefonunda paylaşılan dosyanın görünmesi belirsiz. Sprint 1'deki spike bunları ölçer.
- Raf bilgisi hiç yok; uygulamanın asıl faydası raflar girilince ortaya çıkar (S-22 Raf doldurma).
- Baba eski 2 sütunlu dosyasını kullanmayı sürdürürse birleştirme her seferinde aynı kuralları uygulamak zorunda (ARCHITECTURE 10). Öneri: baba bundan sonra temiz `Babam.xlsx` ile devam etsin.

## Sıradaki adım

Kullanıcı yanıt verince: gerekirse `Babam.xlsx`'i `Adet` sütunuyla yeniden üret, seçilen taslağı `docs/design/` içinde işaretle, sonra Opus ile S-05.

## Devam mesajı

`CLAUDE.md, docs/STATE.md ve docs/BACKLOG.md dosyalarını oku; Sprint 0'da S-05'ten devam et.`
