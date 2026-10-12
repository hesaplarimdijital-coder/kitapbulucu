"""Independent check of the cleaned files against the source workbook.

Deliberately does not import booklib: the comparison logic is written again
from scratch so a bug in the cleaning rules cannot hide itself.

Usage: verify.py SOURCE.xlsx OUTPUT_DIR [DECISIONS.json]
"""
import collections
import datetime
import json
import os
import re
import sys

import openpyxl

EXPECTED_COLUMNS = ["Kitap Adı", "Yazar", "Raf", "Adet", "İmzalı", "Yayınevi", "Yıl", "ISBN", "Not"]
COUNT_INDEX = EXPECTED_COLUMNS.index("Adet")
MONTHS = ["OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN", "TEMMUZ", "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK"]


def key(text):
    text = " ".join(text.split())
    return text.replace("I", "ı").replace("İ", "i").lower()


def words(text):
    return re.findall(r"\w+", key(text))


def is_signed(text):
    return re.search(r"(?<!\w)imzalı(?!\w)", key(text)) is not None


def main(source_path, out_dir, decisions_path=None):
    problems = []
    split_titles = set()
    if decisions_path:
        with open(decisions_path, encoding="utf-8") as handle:
            split_titles = {key(title) for title in json.load(handle).get("splits", {})}

    def check(condition, message):
        print(("ok   " if condition else "FAIL ") + message)
        if not condition:
            problems.append(message)

    # ---- source
    source = openpyxl.load_workbook(source_path, read_only=True).worksheets[0]
    plain, signed, special, header_rows = collections.Counter(), [], [], []
    total = 0
    for number, (a, b) in enumerate(source.iter_rows(min_col=1, max_col=2, values_only=True), start=1):
        total += 1
        if isinstance(a, str) and isinstance(b, str) and key(a) == "kitap" and key(b) == "yazar":
            header_rows.append(number)
            continue
        if isinstance(b, (datetime.datetime, datetime.date)):
            special.append((number, a, b))
            continue
        a, b = a or "", b or ""
        if is_signed(a) or is_signed(b):
            signed.append((number, a, b))
        elif not b and key(a) in split_titles:
            special.append((number, a, b))
        else:
            plain[(key(a), key(b))] += 1

    # ---- output
    workbook = openpyxl.load_workbook(os.path.join(out_dir, "Babam.xlsx"))
    check(workbook.sheetnames == ["Babam"], f"Babam.xlsx has exactly one sheet named Babam ({workbook.sheetnames})")
    sheet = workbook.active
    header = [c.value for c in sheet[1]]
    check(header == EXPECTED_COLUMNS, f"header row is the 7 standard columns ({header})")
    check(all(c.font.b for c in sheet[1]), "header row is bold")
    check(sheet.freeze_panes == "A2", f"header row is frozen ({sheet.freeze_panes})")
    check(len(sheet.merged_cells.ranges) == 0, "no merged cells")

    out_plain, out_signed, out_special = collections.Counter(), [], []
    copies = 0
    non_text, untrimmed, empty_title, bad_count, bad_signed, filled_extra = [], [], [], [], [], []
    seen = collections.Counter()
    for row in sheet.iter_rows(min_row=2):
        cells = row[:len(EXPECTED_COLUMNS)]
        for index, cell in enumerate(cells):
            if index == COUNT_INDEX:
                if not isinstance(cell.value, int) or isinstance(cell.value, bool) or cell.value < 1:
                    bad_count.append(cell.coordinate)
                continue
            if cell.value is not None and (cell.data_type != "s" or not isinstance(cell.value, str)):
                non_text.append(cell.coordinate)
            if isinstance(cell.value, str) and (cell.value != " ".join(cell.value.split())):
                untrimmed.append(cell.coordinate)
            if cell.number_format != "@":
                non_text.append(cell.coordinate + ":fmt")
        values = [c.value for c in cells]
        count = values[COUNT_INDEX] if isinstance(values[COUNT_INDEX], int) else 0
        title, author, shelf, _, signed_mark, publisher, year, isbn, note = [v or "" for v in values]
        if not title:
            empty_title.append(row[0].row)
        if shelf or publisher or isbn:
            filled_extra.append(row[0].row)
        if signed_mark not in ("", "Evet") or (note and not signed_mark):
            bad_signed.append(row[0].row)  # Not only carries dedications of signed copies here
        copies += count
        seen[(key(title), key(author), key(year), signed_mark, key(note))] += 1
        if year or re.fullmatch(r"(\d+ )?(%s) \d{4}" % "|".join(MONTHS), author):
            out_special.append((title, author, year, signed_mark, count))
        elif signed_mark:
            out_signed.append((title, author, note, count))
        else:
            out_plain[(key(title), key(author))] += count

    check(not non_text, f"every filled cell except Adet is text with text format ({non_text[:5]})")
    check(not bad_count, f"every Adet is a whole number of at least 1 ({bad_count[:5]})")
    check(not bad_signed, f"İmzalı is 'Evet' or empty; Not is used only for signed copies ({bad_signed[:5]})")
    check(not untrimmed, f"no leading, trailing or double spaces left ({untrimmed[:5]})")
    check(not empty_title, f"no row without a title ({empty_title[:5]})")
    check(not filled_extra, "Raf, Yayınevi and ISBN are empty everywhere")
    check(max(seen.values()) == 1, "no two output rows are identical")

    # ---- reconciliation
    print(f"     source rows: {total}; header rows found inside the data: {header_rows}")
    check(copies == total - len(header_rows), f"copies in output ({copies}) = source rows minus header ({total - len(header_rows)})")
    check(out_plain == plain, "unsigned books: same titles, authors and copy counts as the source "
          f"({sum(plain.values())} rows; only-in-source {len(plain - out_plain)}, only-in-output {len(out_plain - plain)})")
    signed_copies = sum(count for *_, count in out_signed)
    check(signed_copies == len(signed), f"signed copies in output ({signed_copies}) = signed rows in source ({len(signed)})")
    check(len(out_special) == len(special), f"{len(special)} special rows (date cells, title/author splits): {out_special}")

    # each signed source row must map to an output row made only of its own words
    pool = collections.Counter()
    for title, author, note, count in out_signed:
        pool[(tuple(words(title)), tuple(words(author)))] += count
    unmatched = []
    for number, a, b in signed:
        source_title = [w for w in words(a) if w != "imzalı"]
        source_author = [w for w in words(b) if w != "imzalı"]
        hit = None
        for (title_words, author_words) in pool:
            if pool[(title_words, author_words)] > 0 and list(author_words) == source_author \
                    and list(title_words) == source_title[:len(title_words)]:
                if hit is None or len(title_words) > len(hit[0]):
                    hit = (title_words, author_words)
        if hit is None:
            unmatched.append((number, a, b))
        else:
            pool[hit] -= 1
    check(not unmatched, f"every signed source row has its own output row ({unmatched[:3]})")
    check(sum(pool.values()) == 0, "no signed output copy is left without a source row")
    for title, author, note, count in out_signed:
        source_words = set()
        for number, a, b in signed:
            if words(a)[:len(words(title))] == words(title):
                source_words |= set(words(a)) | set(words(b))
        extra = set(words(note)) - source_words - {"imzalı"}
        if extra:
            problems.append(f"note has words not in the source: {title} | {note} | {extra}")
            print("FAIL note words", title, note, extra)

    # ---- the empty library and the report
    mine = openpyxl.load_workbook(os.path.join(out_dir, "Benim.xlsx"))
    check(mine.sheetnames == ["Benim"] and mine.active.max_row == 1
          and [c.value for c in mine.active[1]] == EXPECTED_COLUMNS, "Benim.xlsx holds only the header row")
    report = openpyxl.load_workbook(os.path.join(out_dir, "Temizleme raporu.xlsx"))
    print("report sheets:", {ws.title: ws.max_row for ws in report.worksheets})
    merged_sheet = report["Birleşen kopyalar"]
    listed, listed_rows = 0, []
    for row in merged_sheet.iter_rows(min_row=2, values_only=True):
        numbers = [int(n) for n in str(row[4]).split(", ")]
        check_count = len(numbers) == row[3]
        if not check_count:
            problems.append(f"merged group count mismatch: {row}")
        listed += len(numbers) - 1
        listed_rows += numbers
    check(listed == total - len(header_rows) - (sheet.max_row - 1),
          f"report lists every merged-away row ({listed}) = {total - len(header_rows)} - {sheet.max_row - 1}")
    check(len(listed_rows) == len(set(listed_rows)), "no source row is listed in two merged groups")
    deleted_sheet = report["Silinen satırlar"]
    deleted_rows = [row[0] for row in deleted_sheet.iter_rows(min_row=2, values_only=True)]
    check(deleted_rows == header_rows, f"report lists every deleted row ({deleted_rows})")

    print("\nRESULT:", "ALL CHECKS PASSED" if not problems else f"{len(problems)} PROBLEM(S)")
    for problem in problems:
        print("  -", problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None))
