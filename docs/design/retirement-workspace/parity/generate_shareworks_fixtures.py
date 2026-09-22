"""Reproducible fictional OOXML fixtures. No private workbook is opened."""
import json
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
from xml.sax.saxutils import escape

ROOT = Path(__file__).parent
OUT = ROOT / 'shareworks'
NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
REL = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'

def generate(cached=False):
    case = json.loads((ROOT / 'cases.json').read_text())['workbook']
    entries = {}
    sheets = list(case['sheets'])
    entries['[Content_Types].xml'] = '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.ms-excel.sheet.macroEnabled.main+xml"/>' + ''.join(f'<Override PartName="/xl/worksheets/sheet{i}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' for i in range(1,5)) + '</Types>'
    entries['_rels/.rels'] = f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="{REL}/officeDocument" Target="xl/workbook.xml"/></Relationships>'
    entries['xl/workbook.xml'] = f'<workbook xmlns="{NS}" xmlns:r="{REL}"><sheets>' + ''.join(f'<sheet name="{name}" sheetId="{i}" r:id="rId{i}"/>' for i,name in enumerate(sheets,1)) + '</sheets><calcPr calcId="191029" calcMode="auto" fullCalcOnLoad="0" forceFullCalc="0" calcCompleted="1"/></workbook>'
    entries['xl/_rels/workbook.xml.rels'] = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' + ''.join(f'<Relationship Id="rId{i}" Type="{REL}/worksheet" Target="worksheets/sheet{i}.xml"/>' for i in range(1,5)) + '</Relationships>'
    for i,(name,cells) in enumerate(case['sheets'].items(),1):
        rows = {}
        for address,value in cells.items():
            row = int(''.join(c for c in address if c.isdigit()))
            if isinstance(value,str):
                cell=f'<c r="{address}" t="inlineStr"><is><t>{escape(value)}</t></is></c>'
            else:
                formula=f'<f>{value}</f>' if cached else ''
                cell=f'<c r="{address}" t="n">{formula}<v>{value}</v></c>'
            rows.setdefault(row,[]).append(cell)
        entries[f'xl/worksheets/sheet{i}.xml'] = f'<worksheet xmlns="{NS}"><sheetData>' + ''.join(f'<row r="{row}">'+''.join(cells)+'</row>' for row,cells in sorted(rows.items())) + '</sheetData></worksheet>'
    return entries

if __name__ == '__main__':
    OUT.mkdir(exist_ok=True)
    for cached in (False, True):
        with ZipFile(OUT / ('cached.xlsm' if cached else 'values.xlsm'), 'w', compression=ZIP_DEFLATED) as archive:
            for name,body in generate(cached).items():
                info = ZipInfo(name, (2026,1,1,0,0,0)); info.compress_type=ZIP_DEFLATED
                archive.writestr(info,body)
    print('Generated two fictional Shareworks fixtures.')
