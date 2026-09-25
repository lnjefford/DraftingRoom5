"""Read final build reports/artifact without changing build outputs or references."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
BUILD = Path(os.environ['LOCALAPPDATA']) / 'DraftingRoom5/repositories/cf5b26b6651a/build/app'

def results(folder):
    suites = [ET.parse(p).getroot() for p in folder.glob('TEST-*.xml')]
    return {'suites': len(suites), **{key: sum(int(s.get(key, 0)) for s in suites)
            for key in ('tests', 'failures', 'errors', 'skipped')}}

summary = {'unit': results(BUILD / 'test-results/testDebugUnitTest'),
           'screenshots': results(BUILD / 'test-results/validateDebugScreenshotTest')}
lint = ET.parse(BUILD / 'reports/lint-results-debug.xml').getroot()
summary['lint'] = {severity: sum(i.get('severity') == severity for i in lint.findall('issue'))
                   for severity in ('Fatal', 'Error', 'Warning', 'Information')}
(OUT / 'final-check-summary.json').write_text(json.dumps(summary, indent=2)+'\n')

apk = BUILD / 'outputs/apk/debug/app-debug.apk'
canaries = json.loads((ROOT / 'docs/reviews/DR5-078/android17-apk-scan.json').read_text())['canaries']
matches = []
with zipfile.ZipFile(apk) as archive:
    for entry in archive.infolist():
        data = archive.read(entry)
        for canary in canaries:
            if canary.encode() in data or canary in entry.filename:
                matches.append({'entry': entry.filename, 'canary': canary})
scan = {'apk_bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'canaries': canaries, 'matches': matches}
(OUT / 'final-apk-scan.json').write_text(json.dumps(scan, indent=2)+'\n')

tool = ROOT / 'tools/android-sdk/build-tools/37.0.0/aapt2.exe'
for name, args in [('apk-identity', ['dump', 'badging', str(apk)]),
                   ('apk-manifest', ['dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml']),
                   ('apk-backup-rules', ['dump', 'xmltree', str(apk), '--file', 'res/xml/backup_rules.xml']),
                   ('apk-transfer-rules', ['dump', 'xmltree', str(apk), '--file', 'res/xml/data_extraction_rules.xml'])]:
    result = subprocess.run([str(tool), *args], capture_output=True, text=True, check=True)
    (OUT / f'{name}.log').write_text(result.stdout)
result = subprocess.run([str(tool.with_name('zipalign.exe')), '-c', '-P', '16', '-v', '4', str(apk)],
                        capture_output=True, text=True, check=True)
(OUT / 'apk-zip-alignment.log').write_text(result.stdout)
print(json.dumps(summary))
print(json.dumps({'apk_bytes': scan['apk_bytes'], 'canary_matches': len(matches)}))
assert not matches
assert summary['unit']['tests'] > 0 and summary['screenshots']['tests'] > 0
assert all(summary[group][key] == 0 for group in ('unit', 'screenshots')
           for key in ('failures', 'errors', 'skipped'))
assert summary['lint']['Error'] == summary['lint']['Fatal'] == 0
