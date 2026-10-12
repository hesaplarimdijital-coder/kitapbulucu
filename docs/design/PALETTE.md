# Renkler ve kontrast

Kitin renk hesabı (`mixSrgb`, `ensureContrast`) aynen uygulanıp yüzeyler ısıtıldı. Her tema × vurgu için kitin denetlediği bütün metin/arka plan çiftleri ve ek olarak seçili satır ve durum renkleri ölçüldü. Alt sınır 4.5:1.

| Taslak / tema / vurgu | Vurgu | Seçili satır | En düşük çift | Oran |
| --- | --- | --- | --- | --- |
| A/light/Bordo | `#6B1E29` | `#F0D0CD` | textTertiary / selection | 5.00 |
| A/light/Mürekkep laciverti | `#233B6C` | `#D5DBE6` | textTertiary / selection | 5.17 |
| A/light/Zeytin yeşili | `#515C27` | `#E6E7CD` | textTertiary / hover | 5.27 |
| A/light/Eskitilmiş altın | `#775A18` | `#F4E6C7` | accentText / hover | 4.74 |
| A/light/Blue | `#485C85` | `#E4E9F1` | accentText / hover | 4.90 |
| A/light/Amber | `#785B25` | `#F9EACD` | accentText / hover | 4.63 |
| A/light/Green | `#2C694C` | `#DBF0DD` | accentText / hover | 4.76 |
| A/light/Coral | `#8C433D` | `#FCDED4` | accentText / hover | 5.16 |
| A/dark/Bordo | `#D98390` | `#3F1F20` | accentText / hover | 4.76 |
| A/dark/Mürekkep laciverti | `#7F9DD8` | `#252A38` | accentText / hover | 4.84 |
| A/dark/Zeytin yeşili | `#93A847` | `#35361F` | accentText / selection | 4.68 |
| A/dark/Eskitilmiş altın | `#D9A32B` | `#433519` | textTertiary / selection | 4.77 |
| A/dark/Blue | `#8AB0FF` | `#343744` | textTertiary / selection | 4.72 |
| A/dark/Amber | `#F2B84B` | `#45361E` | textTertiary / selection | 4.67 |
| A/dark/Green | `#59D49A` | `#283B2D` | textTertiary / selection | 4.77 |
| A/dark/Coral | `#FF7A6E` | `#4B2C27` | accentText / selection | 4.88 |
| B/light/Bordo | `#6B1E29` | `#F1D4D4` | textTertiary / selection | 4.73 |
| B/light/Mürekkep laciverti | `#233B6C` | `#D7DEEC` | textTertiary / selection | 4.89 |
| B/light/Zeytin yeşili | `#515C27` | `#E8EAD3` | textTertiary / hover | 5.11 |
| B/light/Eskitilmiş altın | `#775A18` | `#F6E9CD` | accentText / hover | 5.01 |
| B/light/Blue | `#4C618C` | `#E6ECF8` | accentText / hover | 4.80 |
| B/light/Amber | `#785B25` | `#FBEED4` | accentText / hover | 4.90 |
| B/light/Green | `#2F6F50` | `#DCF3E4` | accentText / hover | 4.65 |
| B/light/Coral | `#8C433D` | `#FDE1DB` | textTertiary / hover | 5.11 |
| B/dark/Bordo | `#D98390` | `#3D2022` | accentText / hover | 4.67 |
| B/dark/Mürekkep laciverti | `#7F9DD8` | `#232A3A` | accentText / hover | 4.75 |
| B/dark/Zeytin yeşili | `#93A847` | `#343621` | accentText / selection | 4.66 |
| B/dark/Eskitilmiş altın | `#D9A32B` | `#42351C` | textTertiary / selection | 4.82 |
| B/dark/Blue | `#8AB0FF` | `#323846` | textTertiary / selection | 4.75 |
| B/dark/Amber | `#F2B84B` | `#433720` | textTertiary / selection | 4.71 |
| B/dark/Green | `#59D49A` | `#263C30` | textTertiary / selection | 4.79 |
| B/dark/Coral | `#FF7A6E` | `#492D29` | accentText / selection | 4.87 |

## Yüzey ve metin renkleri

### Taslak A, Kâğıt

background `#F3ECDD`, chrome `#EBE2CF`, surface `#FBF7EE`, surfaceRaised `#FFFCF5`, hover `#E6DCC7`, pressed `#DBD0B9`, border `#DDD2BC`, borderStrong `#C9BCA2`, text `#2A2018`, textSecondary `#4E4034`, textTertiary `#63554A`, success `#2F6B3F`, warning `#7A5510`, danger `#A3312A`

### Taslak A, Gece okuması

background `#1E1915`, chrome `#171310`, surface `#27211B`, surfaceRaised `#302921`, hover `#372F27`, pressed `#41382E`, border `#342C25`, borderStrong `#43392F`, text `#F0E8DA`, textSecondary `#D3C8B6`, textTertiary `#AFA28F`, success `#7FCB93`, warning `#E3B45C`, danger `#F08A7C`

### Taslak B, Kâğıt

background `#F4EFE4`, chrome `#FAF6EE`, surface `#FDFBF6`, surfaceRaised `#FFFDF9`, hover `#E9E2D3`, pressed `#DED6C5`, border `#DFD7C7`, borderStrong `#CCC2AE`, text `#18223B`, textSecondary `#3B4660`, textTertiary `#525D76`, success `#2B6A45`, warning `#7A5510`, danger `#A3312A`

### Taslak B, Gece okuması

background `#1C1A18`, chrome `#151412`, surface `#252220`, surfaceRaised `#2E2A27`, hover `#35312D`, pressed `#3F3A35`, border `#322E2A`, borderStrong `#423D37`, text `#EEE9E0`, textSecondary `#D0C9BD`, textTertiary `#ABA498`, success `#7FCB93`, warning `#E3B45C`, danger `#F08A7C`

## Vurgu renkleri (`Accent.custom`)

- Bordo: `#C2364B`
- Mürekkep laciverti: `#3F6CC4`
- Zeytin yeşili: `#93A847`
- Eskitilmiş altın: `#D9A32B`
