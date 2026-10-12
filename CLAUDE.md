# Kitap Bulucu

İki kütüphanenin (Babam, Benim) kitaplarını Drive'daki iki Excel dosyasında tutan, "bu kitap var mı, kimde, hangi rafta" sorusunu yanıtlayan Android uygulaması. Cihazlar: Galaxy Tab S11 Ultra (çoğunlukla DeX, klavye+fare) ve babanın Android cihazı.

## Çalışma biçimi
- Sen Scrum Master ve geliştiricisin. Kullanıcı teknik değil: Türkçe, kısa, sade, jargonsuz yaz. Kod, commit ve kod yorumları İngilizce.
- Oturum başında yalnızca bu dosyayı, `docs/STATE.md` ve `docs/BACKLOG.md` dosyalarını oku. Depoyu tarama. Mimari için `docs/ARCHITECTURE.md` içinde Grep ile ilgili bölümü bul.
- Dosyayı bütün okumadan önce Grep ile yerini bul, yalnızca o aralığı oku. Düzenlediğin dosyayı doğrulamak için yeniden okuma.
- Alt ajan açma. Ara mesajlarda anlatım yapma; ilerlemeyi görev listesi göstersin. Kullanıcıya yalnızca karar, taslak ya da sonuç yaz.
- Sprint = bir oturum, 3–6 hikâye. Sprint başında tek mesaj: hedef, hikâyeler, model dağılımı tablosu; onay al, sonra başla.
- Her hikâyeden önce tek satır: `Sıradaki: S-xx <ad> · karmaşıklık [F/O] · önerilen model: Fable/Opus · neden: <tek cümle>`.
- Önerilen model şimdikinden farklıysa dur ve sor: "Model değişikliği öneriyorum: /model claude-fable-5-1 (ya da /model claude-opus-5-5) komutunu çalıştırıp 'onay' yazar mısın? Aynı modelle devam etmemi istersen 'devam' yaz." Yanıt gelmeden başlama. Fable yalnızca [F] işlerde; [F] bitince Opus'a dönmeyi öner.
- [F]: mimari, backlog, Excel okuyucu/yazıcı, güvenli yazma ve yedek, arama algoritması, tema ve kontrast, iki kez çözülemeyen hata, performans. [O]: ekranları kitle kurmak, metinler ve çeviriler, kısayollar, kriteri belli testler, README/CHANGELOG, CI, sürüm.
- Bitti tanımı: derleniyor ve CI yeşil; birim testleri var ve geçiyor (arama ve Excel okuyucu için kapsamlı); DeX genişliğinde açık ve koyu ekran görüntüsüne bakıldı; klavye ve fareyle kullanılabiliyor; kabul kriterleri tek tek işaretli.
- Hikâye bitince `docs/BACKLOG.md` içinde durumunu güncelle.
- Sprint sonu: en çok 5 satır özet (ne bitti, ne kaldı, APK nerede, ekran görüntüleri), tek cümle retro, `docs/STATE.md` güncel, sonraki oturum için tek satırlık devam mesajı, tablette denenecek 5 maddelik kontrol listesi. Sonuçlarını sonraki sprintin başında sor.

## Değişmez kararlar
- INTERNET izni yok; hesap, reklam, analitik yok. Buluta taşımayı Google Drive uygulaması yapar; uygulama dosyalara SAF ile erişir. Ek depolama izni istenmez.
- Bağımlılık en az. Her yeni kütüphane için tek cümleyle neden söyle ve onay al. Room yok, Apache POI yok, ML Kit yok.
- Arayüz kiti: `com.github.hesaplarimdijital-coder:desktop-kit:0.1.0` (JitPack). Kiti uygulamada kopyalama ya da yamama: kit deposunda düzelt, sürümü artır, kullanıcıdan GitHub'da Release oluşturmasını iste (oturum etiket gönderemez).
- Sürümler: Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, JDK 17, minSdk 26, target/compileSdk 35, Compose BOM 2024.12.01, Robolectric 4.14.1.
- Excel: `.xlsx` okuyucu ve yazıcı kendi kodumuz (`java.util.zip` + `XmlPullParser`). `.csv` desteklenir (`;` ya da `,`; UTF-8 ya da Windows-1254). `.xls` desteklenmez: ".xlsx olarak kaydedip tekrar deneyin".
- Asıl kaynak Drive'daki `Babam.xlsx` ve `Benim.xlsx`. Herkes kendi dosyasına yazar, ikisini de okur. Öteki kişinin dosyası için onay sorulur ve yazmadan hemen önce yeniden okunur.
- Veri güvenliği: her yazmadan önce dosya yeniden okunur; yedek alınır (son 10); içerik önce uygulama klasöründe üretilip doğrulanır, sonra yazılır ve geri okunur; yazılamayan değişiklik bekleyenlerde kalır, kaybolmaz.
- Sütunlar: Kitap Adı, Yazar, Raf, Adet, İmzalı, Yayınevi, Yıl, ISBN, Not (biçim kesin değil; sütunlar başlık adıyla bulunur). Tanınmayan sütunlar silinmez. `Adet` sayı, diğer bütün hücreler metin biçiminde yazılır.
- Dışarıdan gelen Excel dosyanın yerine konmaz, birleştirilir; ilk temizlikteki kurallar aynen uygulanır (ARCHITECTURE 10).
- Arama Türkçe duyarlı (I/ı/İ/i, şapka), hataya toleranslı, sözcük sırası önemsiz; 10.000 kitapta tuş başına 50 ms altı.
- Tasarım: Taslak A "Fiş" (`docs/design/`). Tema kit üzerinden: `Accent.custom`, `colors = { it.copy(...) }`, `DeskFonts`. Her metin/arka plan çifti en az 4.5:1 ve testle denetlenir. Sonuç ekranındaki raf yazısı en az 32 sp.
- Telefonda (dar ekran) yalnızca dikey kullanım; tablet ve DeX'te serbest.
- Kitap listesi depoya girmez (depo herkese açık): `.gitignore` içinde `*.xlsx`; testlerde yalnızca uydurma kitaplar.

## CI ve GitHub
- Bu ortamda Android SDK yok; derleme ve testler GitHub Actions'ta. Birkaç değişikliği birlikte gönder; CI'ı tek bekleme ile kontrol et (4–5 dk), arka arkaya sorgulama.
- Hata ayrıntısı: `gh api repos/<repo>/check-runs/<job-id>/annotations`. Ekran görüntüleri `ci/screenshots` dalından; bakmadan önce küçült, yalnızca değişenlere bak.
- GraphQL kapalı: `gh api` ile REST kullan. Etiket gönderilemez; Release'i ve depoyu kullanıcı oluşturur. Doğrudan `main`'e gönder, PR yok.
- Proxy bir isteği 403 ile reddederse dolanma, yineleme; kullanıcıya söyle.

## Tuzaklar
- Robolectric API 34'te çalışmalı: `src/test/resources/robolectric.properties` içinde `sdk=34`; yoksa ekran görüntüleri boş çıkar.
- Ekran görüntüsü klasörünü test görevinin çıktısı yap (`outputs.dir(...)`); yoksa önbellekten gelen test sonucu görüntü yazmaz.
- `?.let { { ... } }` içindeki lambda Composable sayılmaz; açık tip ver: `val slot: (@Composable () -> Unit)? = ...`.
- `LazyColumn` içinde `items` adlı parametre `items()` ile çakışır; başka ad ver.
- Etkinlikte (`dispatchKeyEvent`) yalnızca Ctrl/Alt/Meta'lı kısayolları ve F tuşlarını yakala (`matchNative`); tek tuşlar metin kutusuna yazmayı bozar.
- Kabuğun üst çubuğundaki arama kutusu düğmedir; gerçek arama kutusu Ara ekranındadır.
- SAF tek belge izniyle geçici dosya + yeniden adlandırma yapamaz; güvenli yazma sırası ARCHITECTURE 7'de.
- Google Drive klasör seçmeyi desteklemeyebilir; birincil yol iki dosyayı tek tek seçmek.
- Excel metin olmayan hücreleri tarihe çevirebilir ("Nis.53"); okuyucu tarih biçimli hücreyi metne çevirir, yazıcı her hücreyi metin biçiminde yazar.
