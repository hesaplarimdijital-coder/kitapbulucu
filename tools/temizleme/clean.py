"""Clean a book list kept in Excel.

Usage: clean.py SOURCE.xlsx OUTPUT_DIR [DECISIONS.json]

Writes Babam.xlsx, Benim.xlsx and "Temizleme raporu.xlsx". Nothing is dropped
silently: every removed, merged or changed row is listed in the report.

DECISIONS.json holds the few hand-made decisions about one particular list and
is never committed (the list itself is private):

    {"splits": {"TITLE - AUTHOR YEAR as typed": ["TITLE", "AUTHOR", "YEAR"]},
     "notes": ["free text shown on the summary sheet"]}
"""
import collections
import datetime
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import booklib as bl  # noqa: E402

import numpy as np  # noqa: E402
import openpyxl  # noqa: E402
from openpyxl.styles import Alignment, Font  # noqa: E402
from openpyxl.utils import get_column_letter  # noqa: E402
from rapidfuzz import fuzz, process  # noqa: E402

COLUMNS = ["Kitap Adı", "Yazar", "Raf", "Adet", "İmzalı", "Yayınevi", "Yıl", "ISBN", "Not"]
WIDTHS = [62, 42, 12, 8, 9, 24, 8, 18, 30]
COUNT_COLUMN = COLUMNS.index("Adet") + 1  # the only number column; everything else is text
SIGNED_YES = "Evet"
FONT = "Arial"
HEADER_TITLES = {"kitap", "kitap adı"}
HEADER_AUTHORS = {"yazar"}

# Cells where the title also holds the author ("Ad - Yazar"), keyed by the
# cleaned title text. Filled from DECISIONS.json; only unambiguous cases.
TITLE_AUTHOR_SPLITS = {}
SUMMARY_NOTES = []

Book = collections.namedtuple("Book", "title author year signed note")


def cell_text(cell, changes):
    """Return a cell as text; dates and numbers are converted and reported."""
    value = cell.value
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (datetime.datetime, datetime.date)):
        text = bl.date_to_text(value, cell.number_format)
        changes.append((cell.row, cell.column_letter, value.strftime("%d.%m.%Y") + " (tarih hücresi)", text,
                        "Excel tarihe çevirmiş; metin olarak yazıldı"))
        return text
    text = str(int(value)) if isinstance(value, float) and value.is_integer() else str(value)
    changes.append((cell.row, cell.column_letter, repr(value) + " (sayı hücresi)", text, "Sayı metin olarak yazıldı"))
    return text


def whitespace_kinds(raw):
    kinds = []
    if raw != raw.lstrip():
        kinds.append("baştaki boşluk")
    if raw != raw.rstrip():
        kinds.append("sondaki boşluk")
    if bl.squeeze(raw) != raw.strip():
        kinds.append("çift boşluk")
    return kinds


def read_source(path):
    workbook = openpyxl.load_workbook(path)
    sheet = workbook.worksheets[0]
    other_sheets = [(ws.title, sum(1 for row in ws.iter_rows() for c in row if c.value is not None))
                    for ws in workbook.worksheets[1:]]
    info = {
        "sheet": sheet.title,
        "sheets": workbook.sheetnames,
        "other_sheets": other_sheets,
        "rows": sheet.max_row,
        "merged": len(sheet.merged_cells.ranges),
    }
    return sheet, info


def clean(sheet):
    deleted, value_changes, space_fixes, signed_moves, splits = [], [], [], [], []
    records = []  # (source_row, Book)
    for row in sheet.iter_rows(min_col=1, max_col=2):
        title_cell, author_cell = row[0], row[1]
        number = title_cell.row
        raw_title = cell_text(title_cell, value_changes)
        raw_author = cell_text(author_cell, value_changes)

        if not raw_title.strip() and not raw_author.strip():
            deleted.append((number, raw_title, raw_author, "Boş satır"))
            continue
        if bl.strict_key(raw_title) in HEADER_TITLES and bl.strict_key(raw_author) in HEADER_AUTHORS:
            deleted.append((number, raw_title, raw_author,
                            "Başlık satırı (kitap değil); sıralama sırasında listenin içine karışmış"))
            continue

        for column, raw in (("Kitap Adı", raw_title), ("Yazar", raw_author)):
            kinds = whitespace_kinds(raw)
            if kinds:
                space_fixes.append((number, column, bl.squeeze(raw), ", ".join(kinds)))

        title, title_note = bl.extract_signed(raw_title)
        author, author_note = bl.extract_signed(raw_author)
        notes = [n for n in (title_note, author_note) if n]
        signed = SIGNED_YES if notes else ""
        # a bare marker only fills İmzalı; a dedication ("X'e imzalı") is also kept in Not
        note = "; ".join(dict.fromkeys(n for n in notes if n != bl.SIGNED_NOTE))
        if notes:
            signed_moves.append((number, bl.squeeze(raw_title), bl.squeeze(raw_author), title, author, signed, note))

        year = ""
        if not author and title in TITLE_AUTHOR_SPLITS:
            new_title, author, year = TITLE_AUTHOR_SPLITS[title]
            splits.append((number, title, new_title, author, year))
            title = new_title

        records.append((number, Book(title, author, year, signed, note)))
    return records, deleted, value_changes, space_fixes, signed_moves, splits


def merge_copies(records):
    """Identical rows are copies of one book: keep one row and count them."""
    groups = collections.OrderedDict()
    for number, book in records:
        key = tuple(bl.strict_key(part) for part in book)
        groups.setdefault(key, []).append((number, book))
    merged = []
    for members in groups.values():
        book = members[0][1]
        count = len(members)
        merged.append({"book": book, "count": count, "rows": [n for n, _ in members]})
    return merged


def authors_related(first, second):
    """Same person written differently: a typo, an initial, or extra words around the name."""
    if fuzz.ratio(first, second) >= 80:
        return True
    short, long_ = sorted((first.split(), second.split()), key=len)

    def has_partner(token):
        for other in long_:
            if other == token:
                return True
            if min(len(other), len(token)) <= 2 and (other.startswith(token) or token.startswith(other)):
                return True  # "H." for "HENRYK"
            if min(len(other), len(token)) >= 4 and fuzz.ratio(other, token) >= 80:
                return True
        return False

    return all(has_partner(token) for token in short)


def different_word_swapped(first, second):
    """True when two titles differ by a whole different word ("ESNAFI" / "RESSAMI")."""
    tokens_a, tokens_b = bl.fold(first).split(), bl.fold(second).split()
    if len(tokens_a) != len(tokens_b):
        return False
    for a, b in zip(tokens_a, tokens_b):
        if a == b:
            continue
        if bl.is_volume_token(a) and bl.is_volume_token(b):
            return True
        if min(len(a), len(b)) >= 4 and fuzz.ratio(a, b) < 65:
            return True
    return False


def find_similar(merged):
    """Pairs that look like the same book but are not identical. Never merged."""
    titles = [bl.fold(item["book"].title) for item in merged]
    authors = [bl.fold(item["book"].author) for item in merged]
    scores = process.cdist(titles, titles, scorer=fuzz.ratio, score_cutoff=85, dtype=np.uint8, workers=-1)
    pairs = []
    for i, j in np.argwhere(np.triu(scores, k=1) >= 85):
        i, j = int(i), int(j)
        a, b = merged[i]["book"], merged[j]["book"]
        title_score = int(scores[i, j])
        if bl.strict_key(a.title) == bl.strict_key(b.title) and bl.strict_key(a.author) == bl.strict_key(b.author):
            continue  # same book, one copy signed: kept apart on purpose
        if bl.differs_only_by_number(a.title, b.title) or bl.differs_only_by_number(a.author, b.author):
            continue  # volume 2 / volume 3, issue 4 / issue 5, almanac 2006 / 2007
        if different_word_swapped(a.title, b.title):
            continue  # same series, different book
        if not authors[i] and not authors[j]:
            if title_score < 95:
                continue  # authorless series titles resemble each other a lot
            kind = "Kitap adında küçük fark"
        elif not authors[i] or not authors[j]:
            if title_score < 92:
                continue
            kind = "Birinde yazar yok"
        else:
            if not authors_related(authors[i], authors[j]):
                continue
            kind = "Kitap adı aynı, yazar yazımı farklı" if titles[i] == titles[j] else "Kitap adında küçük fark"
        if titles[i] == titles[j] and authors[i] == authors[j]:
            kind = "Yalnızca noktalama ya da İ/I farkı"
        pairs.append({"score": title_score, "kind": kind, "i": i, "j": j})
    order = {"Yalnızca noktalama ya da İ/I farkı": 0, "Kitap adı aynı, yazar yazımı farklı": 1,
             "Kitap adında küçük fark": 2, "Birinde yazar yok": 3}
    pairs.sort(key=lambda p: (order[p["kind"]], -p["score"], p["i"]))
    return pairs


# ---------------------------------------------------------------- writing


def put(sheet, row, column, value, bold=False, wrap=False):
    cell = sheet.cell(row=row, column=column)
    if value not in (None, ""):
        cell.value = value
        if isinstance(value, str):
            cell.data_type = "s"  # never let a leading "=" turn into a formula
    cell.font = Font(name=FONT, size=11, bold=bold)
    if isinstance(value, str) or value in (None, ""):
        cell.number_format = "@"
    if wrap:
        cell.alignment = Alignment(wrap_text=True, vertical="top")
    return cell


def write_library(path, sheet_name, items):
    workbook = openpyxl.Workbook()
    sheet = workbook.active
    sheet.title = sheet_name
    for index, (name, width) in enumerate(zip(COLUMNS, WIDTHS), start=1):
        put(sheet, 1, index, name, bold=True)
        dimension = sheet.column_dimensions[get_column_letter(index)]
        dimension.width = width
        # text everywhere (Excel must not turn "3-4" into a date), a plain number for Adet
        dimension.number_format = "0" if index == COUNT_COLUMN else "@"
        dimension.font = Font(name=FONT, size=11)
    for offset, item in enumerate(items, start=2):
        book = item["book"]
        values = (book.title, book.author, "", item["count"], book.signed, "", book.year, "", book.note)
        for index, value in enumerate(values, start=1):
            cell = put(sheet, offset, index, value)
            if index == COUNT_COLUMN:
                cell.number_format = "0"
    sheet.freeze_panes = "A2"
    workbook.properties.creator = "Kitap Bulucu"
    workbook.properties.title = sheet_name
    workbook.save(path)


def add_table(workbook, title, headers, widths, rows, legend=None):
    sheet = workbook.create_sheet(title)
    top = 1
    if legend:
        put(sheet, 1, 1, legend)
        top = 2
    for index, (name, width) in enumerate(zip(headers, widths), start=1):
        put(sheet, top, index, name, bold=True)
        sheet.column_dimensions[get_column_letter(index)].width = width
    for offset, values in enumerate(rows, start=top + 1):
        for index, value in enumerate(values, start=1):
            put(sheet, offset, index, value)
    sheet.freeze_panes = sheet.cell(row=top + 1, column=1)
    return sheet


def write_report(path, source_name, info, records, merged, deleted, value_changes, space_fixes,
                 signed_moves, splits, similar):
    workbook = openpyxl.Workbook()
    summary = workbook.active
    summary.title = "Özet"
    out_row = {id(item): index for index, item in enumerate(merged, start=2)}
    multi = [item for item in merged if item["count"] > 1]
    merged_away = sum(item["count"] - 1 for item in multi)
    total_copies = sum(item["count"] for item in merged)
    space_rows = len({row for row, _, _, _ in space_fixes})

    lines = [
        ("Temizleme raporu", None, True),
        (f"Kaynak: {source_name} → Babam.xlsx", None, False),
        (f"Tarih: {datetime.date.today().strftime('%d.%m.%Y')}", None, False),
        ("", None, False),
        ("Sayılar", None, True),
        ("Kaynak dosyadaki dolu satır", info["rows"], False),
        ("Silinen satır (kitap olmayan başlık satırı)", len(deleted), False),
        ("Kitap satırı (kopyalar dahil)", len(records), False),
        ("Birden çok kopyası olan kitap (tek satıra indi)", len(multi), False),
        ("Birleşme yüzünden azalan satır", merged_away, False),
        ("Babam.xlsx'teki kitap satırı", len(merged), False),
        ("Babam.xlsx'teki toplam adet (kopyalar sayılarak)", total_copies, False),
        ("", None, False),
        ("Yapılan düzeltmeler", None, True),
        ("\"İMZALI\" yazısı İmzalı sütununa taşınan satır", len(signed_moves), False),
        ("Boşluğu düzeltilen satır", space_rows, False),
        ("Tarihe dönmüş hücre (metne çevrildi)", len(value_changes), False),
        ("Kitap adından yazarı ayrılan satır", len(splits), False),
        ("Benzer görünen ama birleştirilmeyen çift", len(similar), False),
        ("", None, False),
        ("Sağlama", None, True),
        (f"{info['rows']} kaynak satır = {len(deleted)} silinen + {len(records)} kitap satırı", None, False),
        (f"{len(records)} kitap satırı = {len(merged)} satır + {merged_away} birleşen kopya", None, False),
        ("", None, False),
        ("Dokunulmayanlar", None, True),
        ("Büyük harfler, yazar adı yazımları, yazar sütunundaki yayınevi ya da kurum adları olduğu gibi kaldı.", None, False),
        ("Satır sırası kaynak dosyadaki gibi (yazara göre) korundu. Raf, Yayınevi ve ISBN sütunları boş.", None, False),
        *[(note, None, False) for note in SUMMARY_NOTES],
        ("", None, False),
        ("Sayfalar", None, True),
        ("Birleşen kopyalar: tek satıra inen her kitap ve eski satır numaraları.", None, False),
        ("Silinen satırlar: listeden çıkarılan satırlar.", None, False),
        ("İmzalı kitaplar: \"İMZALI\" yazısı kitap adından ya da yazardan İmzalı sütununa taşınan satırlar; ithaflar Not'ta.", None, False),
        ("Diğer değişiklikler: tarih hücreleri ve ad–yazar ayrımı.", None, False),
        ("Boşluk düzeltmeleri: baştaki, sondaki ve çift boşluğu silinen hücreler.", None, False),
        ("Benzerler: aynı kitap olabilecek ama birleştirilmeyen çiftler. Karar senin.", None, False),
    ]
    for index, (label, value, bold) in enumerate(lines, start=1):
        put(summary, index, 1, label, bold=bold, wrap=len(label) > 62)
        if value is not None:
            cell = summary.cell(row=index, column=2, value=value)
            cell.font = Font(name=FONT, size=11)
            cell.number_format = "#,##0"
    summary.column_dimensions["A"].width = 70
    summary.column_dimensions["B"].width = 12

    add_table(workbook, "Birleşen kopyalar",
              ["Kitap Adı", "Yazar", "İmzalı", "Adet", "Eski dosyadaki satırlar", "Babam.xlsx satırı"],
              [58, 36, 9, 8, 30, 16],
              [(item["book"].title, item["book"].author, item["book"].signed, item["count"],
                ", ".join(str(n) for n in item["rows"]), out_row[id(item)]) for item in multi])
    add_table(workbook, "Silinen satırlar",
              ["Eski satır", "A sütunu", "B sütunu", "Neden"], [12, 30, 30, 70], deleted)
    row_of = {}
    for item in merged:
        for number in item["rows"]:
            row_of[number] = out_row[id(item)]
    add_table(workbook, "İmzalı kitaplar",
              ["Eski satır", "Eski Kitap Adı", "Eski Yazar", "Yeni Kitap Adı", "Yeni Yazar", "İmzalı", "Not", "Babam.xlsx satırı"],
              [12, 52, 34, 46, 30, 9, 24, 16],
              [(n, old_t, old_a, new_t, new_a, signed, note, row_of[n])
               for n, old_t, old_a, new_t, new_a, signed, note in signed_moves])
    other = [(row, "Kitap Adı" if column == "A" else "Yazar", old, new, why, row_of[row])
             for row, column, old, new, why in value_changes]
    other += [(n, "Kitap Adı", old_title, f"{new_title}  |  Yazar: {author}  |  Yıl: {year}",
               "Yazar ve yıl kitap adının içindeydi; ayrıldı", row_of[n])
              for n, old_title, new_title, author, year in splits]
    add_table(workbook, "Diğer değişiklikler",
              ["Eski satır", "Sütun", "Eski değer", "Yeni değer", "Neden", "Babam.xlsx satırı"],
              [12, 12, 44, 60, 48, 16], other)
    add_table(workbook, "Boşluk düzeltmeleri",
              ["Eski satır", "Sütun", "Düzeltilmiş metin", "Silinen", "Babam.xlsx satırı"],
              [12, 12, 70, 28, 16],
              [(n, column, text, kinds, row_of[n]) for n, column, text, kinds in space_fixes])
    add_table(workbook, "Benzerler",
              ["Karar", "Fark", "Benzerlik %", "Satır 1", "Kitap Adı 1", "Yazar 1", "Satır 2", "Kitap Adı 2", "Yazar 2"],
              [12, 34, 12, 9, 50, 30, 9, 50, 30],
              [("", p["kind"], p["score"], out_row[id(merged[p["i"]])], merged[p["i"]]["book"].title,
                merged[p["i"]]["book"].author, out_row[id(merged[p["j"]])], merged[p["j"]]["book"].title,
                merged[p["j"]]["book"].author) for p in similar],
              legend="Bu çiftler birleştirilmedi. Doldurulacak tek sütun \"Karar\": aynı kitapsa AYNI, farklıysa FARKLI yaz. "
                     "Boş bırakırsan iki satır da olduğu gibi kalır. Satır numaraları Babam.xlsx'e göredir.")
    workbook.properties.creator = "Kitap Bulucu"
    workbook.save(path)
    return {"multi": len(multi), "merged_away": merged_away, "total_copies": total_copies, "space_rows": space_rows}


def main(source, out_dir, decisions_path=None):
    os.makedirs(out_dir, exist_ok=True)
    if decisions_path:
        with open(decisions_path, encoding="utf-8") as handle:
            decisions = json.load(handle)
        TITLE_AUTHOR_SPLITS.update({key: tuple(value) for key, value in decisions.get("splits", {}).items()})
        SUMMARY_NOTES.extend(decisions.get("notes", []))
    sheet, info = read_source(source)
    records, deleted, value_changes, space_fixes, signed_moves, splits = clean(sheet)
    merged = merge_copies(records)
    similar = find_similar(merged)
    books = [item["book"] for item in merged]
    write_library(os.path.join(out_dir, "Babam.xlsx"), "Babam", merged)
    write_library(os.path.join(out_dir, "Benim.xlsx"), "Benim", [])
    extra = write_report(os.path.join(out_dir, "Temizleme raporu.xlsx"), "Kütüphane.xlsx", info, records, merged,
                         deleted, value_changes, space_fixes, signed_moves, splits, similar)
    stats = {
        "source_rows": info["rows"], "sheets": info["sheets"], "other_sheets": info["other_sheets"],
        "merged_cells": info["merged"], "deleted": deleted, "book_rows": len(records), "output_rows": len(merged),
        "multi_copy_books": extra["multi"], "merged_away": extra["merged_away"], "total_copies": extra["total_copies"],
        "copy_sizes": dict(collections.Counter(item["count"] for item in merged)),
        "signed_rows": len(signed_moves), "space_rows": extra["space_rows"], "space_cells": len(space_fixes),
        "value_changes": [list(map(str, change)) for change in value_changes], "splits": splits,
        "similar_pairs": len(similar), "similar_kinds": dict(collections.Counter(p["kind"] for p in similar)),
        "no_author_rows": sum(1 for book in books if not book.author),
    }
    print(json.dumps(stats, ensure_ascii=False, indent=1, default=str))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
