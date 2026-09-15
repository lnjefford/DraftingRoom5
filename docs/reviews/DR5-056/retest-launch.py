"""Real launcher/widget/input smoke checks. Does not clear data or ANR history."""
import argparse, json, re, subprocess, time
from pathlib import Path
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--serial', default='emulator-5554')
p.add_argument('--output', required=True)
p.add_argument('--rounds', type=int, default=6)
a = p.parse_args()
out = Path(a.output); out.mkdir(parents=True, exist_ok=True)
adb = str(Path('tools/android-sdk/platform-tools/adb.exe').resolve())
def run(*args):
    return subprocess.check_output([adb, '-s', a.serial, *args], timeout=45)
def shell(*args): return run('shell', *args).decode(errors='replace')
def dump(name):
    remote = '/data/local/tmp/dr5-retest.xml'
    shell('rm','-f',remote)
    status=shell('uiautomator','dump',remote)
    if 'dumped to:' not in status:
        raise RuntimeError('No fresh UI hierarchy: '+status)
    data = run('exec-out','cat',remote)
    (out/f'{name}.xml').write_bytes(data)
    return ET.fromstring(data)
def center(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    return str((x1+x2)//2),str((y1+y2)//2)
def find(tree,label):
    return next(n for n in tree.iter('node') if label in (n.get('text'),n.get('content-desc')))
def anrs(name):
    data=shell('dumpsys','dropbox','--print','data_app_anr')
    (out/f'{name}.txt').write_text(data,encoding='utf-8')
    return re.findall(r'\d{4}-\d\d-\d\d [^\r\n]+data_app_anr[^\r\n]*',data)
before=anrs('anr-before')
results=[]
for i in range(a.rounds):
    shell('input','keyevent','KEYCODE_WAKEUP')
    shell('wm','dismiss-keyguard')
    shell('input','keyevent','KEYCODE_HOME')
    host_start=time.monotonic()
    while True:
        try:
            home=dump(f'{i+1}-home')
            tile=find(home,'Open DraftingRoom5')
            break
        except (RuntimeError,StopIteration):
            if time.monotonic()-host_start>30: raise
            time.sleep(.3)
    if i%2==0:
        shell('am','kill','dev.draftingroom5') # Android kills background app processes; preserves widgets/data.
        time.sleep(.5)
    start=time.monotonic()
    shell('input','tap',*center(tile))
    while True:
        try:
            screen=dump(f'{i+1}-app')
            settings=next(n for n in screen.iter('node') if n.get('content-desc')=='Settings')
            break
        except (RuntimeError,StopIteration):
            if time.monotonic()-start>15: raise
            time.sleep(.3)
    seconds=round(time.monotonic()-start,3)
    assert seconds<15, f'Launch/hierarchy took {seconds}s'
    assert not any("isn't responding" in n.get('text','') for n in screen.iter('node'))
    shell('input','tap',*center(settings))
    settings_screen=dump(f'{i+1}-settings')
    find(settings_screen,'Settings')
    assert any('Back' in n.get('content-desc','') for n in settings_screen.iter('node')), 'Settings navigation did not complete'
    (out/f'{i+1}-settings.png').write_bytes(run('exec-out','screencap','-p'))
    shell('input','keyevent','KEYCODE_BACK')
    results.append({'round':i+1,'background_process_kill':i%2==0,'tap_to_hierarchy_seconds':seconds,'settings_input':'passed'})
    (out/'launch-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(results[-1],flush=True)
after=anrs('anr-after')
assert before==after, 'New ANR recorded during launch checks'
shell('input','keyevent','KEYCODE_HOME')
print('PASS: repeated real widget launch, Settings input and unchanged ANR history',flush=True)
