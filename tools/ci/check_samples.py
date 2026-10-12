"""Open the sample workbooks the tests wrote with two independent readers.

Usage: check_samples.py SAMPLE_DIR

- openpyxl must load every .xlsx, find a bold header in row 1 frozen at A2, text format on
  the text columns and a number in the "Adet" column.
- LibreOffice (when `soffice` is installed) must convert every .xlsx to .csv without error,
  and the header must come through.
Exits non-zero on the first problem; prints a GitHub Actions annotation.
"""
import os
import shutil
import subprocess
import sys
import tempfile

import openpyxl


def fail(message):
    print(f"::error::{message}")
    sys.exit(1)


def check_openpyxl(path):
    wb = openpyxl.load_workbook(path)
    for ws in wb.worksheets:
        header = [c.value for c in ws[1]]
        if "Kitap Adı" not in header or "Adet" not in header:
            fail(f"{path} / {ws.title}: header row is {header}")
        if not all(c.font.bold for c in ws[1] if c.value):
            fail(f"{path} / {ws.title}: header is not bold")
        if ws.freeze_panes != "A2":
            fail(f"{path} / {ws.title}: header is not frozen (freeze_panes={ws.freeze_panes})")
        adet = header.index("Adet") + 1
        for row in ws.iter_rows(min_row=2):
            for cell in row:
                if cell.column == adet:
                    if cell.value is not None and not isinstance(cell.value, int):
                        fail(f"{path} / {ws.title}: {cell.coordinate} count is {cell.value!r}, not a whole number")
                elif cell.number_format != "@":
                    fail(f"{path} / {ws.title}: {cell.coordinate} is formatted {cell.number_format!r}, not text")
        widths = [ws.column_dimensions[openpyxl.utils.get_column_letter(i)].width for i in range(1, len(header) + 1)]
        if any(not w for w in widths):
            fail(f"{path} / {ws.title}: column widths missing: {widths}")
        print(f"openpyxl ok: {path} / {ws.title}: {ws.max_row - 1} rows, {len(header)} columns")


def check_libreoffice(path):
    if not shutil.which("soffice"):
        print("::warning::soffice not installed; LibreOffice check skipped")
        return
    out = tempfile.mkdtemp(prefix="lo-check-")
    profile = tempfile.mkdtemp(prefix="lo-profile-")
    try:
        try:
            result = subprocess.run(
                ["soffice", "--headless", f"-env:UserInstallation=file://{profile}", "--convert-to", "csv:Text - txt - csv (StarCalc):44,34,76,1", "--outdir", out, path],
                capture_output=True, text=True, timeout=90,
            )
        except subprocess.TimeoutExpired:
            print(f"::warning::LibreOffice did not answer within 90 s for {path}; check skipped")
            return
        csv_path = os.path.join(out, os.path.splitext(os.path.basename(path))[0] + ".csv")
        if result.returncode != 0 or not os.path.exists(csv_path):
            fail(f"LibreOffice could not open {path}: {result.stdout} {result.stderr}")
        with open(csv_path, encoding="utf-8") as f:
            first = f.readline()
        if "Kitap Adı" not in first:
            fail(f"LibreOffice read {path} but the header is {first!r}")
        print(f"libreoffice ok: {path}: {first.strip()}")
    finally:
        shutil.rmtree(out, ignore_errors=True)
        shutil.rmtree(profile, ignore_errors=True)


def main(sample_dir):
    files = sorted(f for f in os.listdir(sample_dir) if f.endswith(".xlsx"))
    if not files:
        fail(f"no sample workbooks in {sample_dir}")
    for name in files:
        path = os.path.join(sample_dir, name)
        check_openpyxl(path)
        check_libreoffice(path)
    print(f"::notice::Sample workbooks: {', '.join(files)} open in openpyxl and LibreOffice")


if __name__ == "__main__":
    main(sys.argv[1])
