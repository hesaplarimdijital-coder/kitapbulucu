# Durum

Son güncelleme: 12.10.2026, Sprint 0 bitti.

## Şu an

- Uygulama: Kitap Bulucu, paket `com.hesaplarimdijital.kitapbulucu`. Depo: `hesaplarimdijital-coder/kitapbulucu` (herkese açık).
- Sprint 0 bitti (S-01 … S-05). Uygulamanın boş iskeleti var: Ara, Kütüphane, Raflar, Ayarlar; CI yeşil; APK Actions çıktısı `kitapbulucu-apk`.
- Seçilen tasarım: Taslak A "Fiş". Henüz kitin varsayılan renkleri kullanılıyor; Taslak A renkleri S-11'de.
- Sıradaki: Sprint 1 (S-06 spike, S-07 okuyucu, S-08 yazıcı, S-09 arama 1), hepsi [F] → Fable.

## Kullanıcıdan beklenenler

1. GitHub'a iki gizli değer: `KB_KEYSTORE_BASE64` ve `KB_KEYSTORE_PASSWORD` (12.10.2026'da dosya ve parola verildi). Eklenene kadar APK'lar her seferinde başka anahtarla imzalanır.
2. Sprint 0 tablet kontrol listesinin sonuçları (Sprint 1 başında sor).
3. Kit 0.1.1 onayı: Sprint 3'te (K-01) sorulacak.

## Veri hakkında bilinenler

- Kaynak `Kütüphane.xlsx` babanın listesi: 7.325 satır, 2 sütun (kitap adı, yazar), başlık satırı listenin içinde (6856. satır), hepsi büyük harf, raf bilgisi yok. Kullanıcının kendi listesi henüz yok.
- Aynı satırın tekrarı o kitaptan birden çok kopya demek (kullanıcı söyledi).
- Temiz `Babam.xlsx` (Kitap Adı, Yazar, Raf, Adet, İmzalı, Yayınevi, Yıl, ISBN, Not): 6.775 satır, 7.324 adet; 183 satır imzalı; 164 benzer çift birleştirilmedi (raporun "Benzerler" sayfası).
- Yazar sütununda yer yer yayınevi ya da kurum adı var (dokunulmadı). 273 satırda yazar yok.
- Kaynak ve temiz dosyalar depoda değil. Betikler `tools/temizleme/`; listeye özgü kararlar ayrı bir `kararlar.json` dosyasında (depoda değil). Yeniden üretmek için kaynak dosyayı kullanıcıdan iste.

## Kararlar

- Tekrar eden satırlar tek satır; sayı `Adet` sütununda. İmzalı kopya ayrı satır; `İmzalı` sütununda `Evet`, ithaf Not'ta.
- Excel biçimi genel olarak onaylı ama kesin değil; kullanıcı sonradan değişiklik isteyebilir.
- Büyük harfler, yazar adı yazımları ve yazar sütunundaki yayınevi adları olduğu gibi kalır.
- Güvenli yazmada "geçici dosya + yerine koy" yerine "üret, doğrula, yedekle, yaz, geri oku" (ARCHITECTURE 7).
- Sprint planı 6 kısa sprint; her sprint başında onay alınır.
- Telefonda yalnızca dikey; tablet ve DeX serbest.
- Opus işlerinde effort: high (kullanıcıya önerildi).

## Riskler

- Google Drive klasör seçmeyi desteklemeyebilir; babanın telefonunda paylaşılan dosyanın görünmesi belirsiz. Sprint 1'deki spike ölçer.
- Raf bilgisi hiç yok; uygulamanın asıl faydası raflar girilince ortaya çıkar (S-22).
- Baba eski 2 sütunlu dosyasını kullanmayı sürdürürse birleştirme her seferinde aynı kuralları uygulamalı (ARCHITECTURE 10). Öneri: baba temiz `Babam.xlsx` ile devam etsin.

## Sprint 0 retro

Bir dahaki sefere: CI'a bağlı dış servisleri (JitPack, gizli değerler) ilk gün bir deneme derlemesiyle sınayıp sürprizleri erken görmek.

## Devam mesajı

`CLAUDE.md, docs/STATE.md ve docs/BACKLOG.md dosyalarını oku; Sprint 1'i planla ve onayımı al.`
