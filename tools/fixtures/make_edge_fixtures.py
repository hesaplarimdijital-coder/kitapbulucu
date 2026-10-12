"""Hand-built workbooks that exercise the corners of the .xlsx format.

Usage: make_edge_fixtures.py OUT_DIR   (normally app/src/test/resources/fixtures)

Every book here is invented. Files:
- ornek-uc-durumlar.xlsx: shared strings with rich text and phonetic hints, inline strings
  with kept spaces, sparse rows and columns, cells without addresses, formula results,
  booleans, errors, large and exponent numbers, built-in and custom date formats, a time,
  an empty sheet and a third sheet.
- ornek-onekli.xlsx: the same first sheet with namespace prefixes on every tag and odd part
  names reached only through the relationships file.
- ornek-1904.xlsx: a workbook on the 1904 date system.
- ornek-1254.csv, ornek-bom.csv, ornek-virgul.csv, ornek-tirnak.csv: text tables in
  Windows-1254, UTF-8 with a byte-order mark, comma-separated, and with quoted cells.
- bozuk.xlsx: a zip that is cut off; belge.zip: a zip that is not a workbook.
"""
import os
import sys
import zipfile

NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

CONTENT_TYPES = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
{{overrides}}
</Types>"""

ROOT_RELS = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="{PKG_REL}"><Relationship Id="rId1" Type="{REL}/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

SHARED = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="{NS}" count="6" uniqueCount="6">
<si><t>Kitap Adı</t></si>
<si><t>Yazar</t></si>
<si><r><rPr><b/></rPr><t>Kürk </t></r><r><t>Mantolu Madonna</t></r></si>
<si><t>B3</t></si>
<si><t>Tokyo</t><rPh sb="0" eb="5"><t>トウキョウ</t></rPh><phoneticPr fontId="1"/></si>
<si><t xml:space="preserve">  iki boşlukla başlar</t></si>
</sst>"""

STYLES = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="{NS}">
<numFmts count="4">
<numFmt numFmtId="164" formatCode="mmm/yy"/>
<numFmt numFmtId="165" formatCode="dd/mmm/yy"/>
<numFmt numFmtId="166" formatCode="h:mm"/>
<numFmt numFmtId="167" formatCode="&quot;Yıl: &quot;0"/>
</numFmts>
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="6">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="167" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
</cellXfs>
</styleSheet>"""

# style indexes: 1 = mmm/yy, 2 = dd/mmm/yy, 3 = built-in 14, 4 = h:mm, 5 = "Yıl: "0 (a number, not a date)
SHEET_ROWS = """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="inlineStr"><is><t>Raf</t></is></c><c r="D1" t="inlineStr"><is><t>Adet</t></is></c><c r="E1" t="inlineStr"><is><t>Tarih</t></is></c></row>
<row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="inlineStr"><is><t>Sabahattin Ali</t></is></c><c r="C2" t="s"><v>3</v></c><c r="D2"><v>1</v></c><c r="E2" s="1"><v>19450</v></c></row>
<row r="3"><c r="A3" t="inlineStr"><is><t xml:space="preserve"> boşluklu </t></is></c><c r="C3" t="s"><v>4</v></c><c r="D3"><v>2.0</v></c><c r="E3" s="2"><v>35869</v></c></row>
<row r="5"><c r="A5" t="str"><f>CONCATENATE("Formül"," sonucu")</f><v>Formül sonucu</v></c><c r="B5" t="b"><v>1</v></c><c r="C5" t="e"><v>#N/A</v></c><c r="D5"><v>9789750718533</v></c><c r="E5"><v>1E3</v></c></row>
<row><c t="inlineStr"><is><t>A6 adressiz</t></is></c><c><v>0.5</v></c></row>
<row r="7"><c r="A7" s="3"><v>42705</v></c><c r="B7" s="4"><v>0.5</v></c><c r="C7"><v></v></c><c r="D7"><v>1.50</v></c><c r="E7" s="5"><v>1998</v></c></row>
<row r="8"><c r="A8" t="s"><v>5</v></c><c r="B8" t="inlineStr"><is><r><t>satır </t></r><r><rPr><i/></rPr><t>içi zengin</t></r></is></c><c r="C8" t="b"><v>0</v></c></row>"""


def sheet_xml(prefix=""):
    p = prefix
    if prefix:
        rows = SHEET_ROWS
        for tag in ("row", "c", "is", "t", "v", "f", "r", "rPr", "b", "i"):
            rows = rows.replace(f"<{tag} ", f"<{p}{tag} ").replace(f"<{tag}>", f"<{p}{tag}>").replace(f"</{tag}>", f"</{p}{tag}>")
        rows = rows.replace("<r><", f"<{p}r><").replace("</r>", f"</{p}r>")
        return (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<{p}worksheet xmlns:{prefix[:-1]}="{NS}">'
                f'<{p}sheetData>{rows}</{p}sheetData></{p}worksheet>')
    return f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<worksheet xmlns="{NS}"><sheetData>{SHEET_ROWS}</sheetData></worksheet>'


def workbook_xml(sheets, date1904=False, prefix=""):
    p = prefix
    pr = f'<{p}workbookPr date1904="1"/>' if date1904 else ""
    items = "".join(f'<{p}sheet name="{name}" sheetId="{i + 1}" r:id="{rid}"/>' for i, (name, rid, _) in enumerate(sheets))
    if prefix:
        return (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<{p}workbook xmlns:{prefix[:-1]}="{NS}" xmlns:r="{REL}">'
                f'{pr}<{p}sheets>{items}</{p}sheets></{p}workbook>')
    return f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<workbook xmlns="{NS}" xmlns:r="{REL}">{pr}<sheets>{items}</sheets></workbook>'


def workbook_rels(sheets, absolute=False):
    rels = "".join(
        f'<Relationship Id="{rid}" Type="{REL}/worksheet" Target="{"/xl/" if absolute else ""}{target}"/>' for _, rid, target in sheets)
    rels += f'<Relationship Id="rId90" Type="{REL}/sharedStrings" Target="sharedStrings.xml"/>'
    rels += f'<Relationship Id="rId91" Type="{REL}/styles" Target="styles.xml"/>'
    return f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Relationships xmlns="{PKG_REL}">{rels}</Relationships>'


def write_workbook(path, sheets, parts, date1904=False, prefix="", absolute=False):
    overrides = "".join(
        f'<Override PartName="/xl/{target}" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
        for _, _, target in sheets)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        # parts in a deliberately unhelpful order: sheets before shared strings and styles
        for target, xml in parts.items():
            z.writestr(f"xl/{target}", xml)
        z.writestr("xl/sharedStrings.xml", SHARED)
        z.writestr("xl/styles.xml", STYLES)
        z.writestr("xl/workbook.xml", workbook_xml(sheets, date1904, prefix))
        z.writestr("xl/_rels/workbook.xml.rels", workbook_rels(sheets, absolute))
        z.writestr("_rels/.rels", ROOT_RELS)
        z.writestr("[Content_Types].xml", CONTENT_TYPES.replace("{overrides}", overrides))


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    empty = f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<worksheet xmlns="{NS}"><sheetData/></worksheet>'
    third = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<worksheet xmlns="{NS}"><sheetData>'
             '<row r="3"><c r="B3" t="inlineStr"><is><t>Üçüncü sayfa</t></is></c></row></sheetData></worksheet>')

    sheets = [("Kitaplar", "rId1", "worksheets/sheet1.xml"), ("Boş", "rId2", "worksheets/sheet2.xml"), ("Diğer", "rId3", "worksheets/sheet3.xml")]
    write_workbook(os.path.join(out_dir, "ornek-uc-durumlar.xlsx"), sheets,
                   {"worksheets/sheet1.xml": sheet_xml(), "worksheets/sheet2.xml": empty, "worksheets/sheet3.xml": third})

    odd = [("Kitaplar", "rId7", "worksheets/liste.xml"), ("Diğer", "rId8", "worksheets/sheet1.xml")]
    write_workbook(os.path.join(out_dir, "ornek-onekli.xlsx"), odd,
                   {"worksheets/liste.xml": sheet_xml("x:"), "worksheets/sheet1.xml": third}, prefix="x:", absolute=True)

    old = [("Eski", "rId1", "worksheets/sheet1.xml")]
    sheet_1904 = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<worksheet xmlns="{NS}"><sheetData>'
                  '<row r="1"><c r="A1" s="3"><v>0</v></c><c r="B1" s="1"><v>17988</v></c></row></sheetData></worksheet>')
    write_workbook(os.path.join(out_dir, "ornek-1904.xlsx"), old, {"worksheets/sheet1.xml": sheet_1904}, date1904=True)

    table = [["Kitap Adı", "Yazar", "Raf"], ["Islıklı Kuşların Sokağı", "Nevin Çağlar", "A1"],
             ["Çatıdaki Rasathane; ikinci baskı", "Ayşe İnce", ""], ["Dağ \"Sessizlik\"", "Ferit Ağaoğlu", "B2"]]

    def csv(rows, sep):
        def cell(v):
            return '"' + v.replace('"', '""') + '"' if any(ch in v for ch in (sep, '"', "\n")) else v
        return "\r\n".join(sep.join(cell(v) for v in row) for row in rows) + "\r\n"

    with open(os.path.join(out_dir, "ornek-1254.csv"), "wb") as f:
        f.write(csv(table, ";").encode("cp1254"))
    with open(os.path.join(out_dir, "ornek-bom.csv"), "wb") as f:
        f.write(b"\xef\xbb\xbf" + csv(table, ";").encode("utf-8"))
    with open(os.path.join(out_dir, "ornek-virgul.csv"), "wb") as f:
        f.write(csv(table, ",").encode("utf-8"))
    with open(os.path.join(out_dir, "ornek-tirnak.csv"), "wb") as f:
        f.write(('"Kitap Adı";"Not"\n"Satır\niçinde yeni satır";"tırnak "" içinde"\n\n"son";\n').encode("utf-8"))

    with zipfile.ZipFile(os.path.join(out_dir, "belge.zip"), "w") as z:
        z.writestr("word/document.xml", "<document/>")
    whole = open(os.path.join(out_dir, "ornek-uc-durumlar.xlsx"), "rb").read()
    with open(os.path.join(out_dir, "bozuk.xlsx"), "wb") as f:
        f.write(whole[: len(whole) // 2])
    print("written to", out_dir)


if __name__ == "__main__":
    main(sys.argv[1])
