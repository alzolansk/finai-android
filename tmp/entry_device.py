import subprocess, pathlib, xml.etree.ElementTree as ET, re, sys
adb=r'C:/Users/joaov/AppData/Local/Android/Sdk/platform-tools/adb.exe'
def run(*args): return subprocess.check_output([adb,*args])
def nodes():
    run('shell','uiautomator','dump','/sdcard/entry-window.xml')
    return ET.fromstring(run('shell','cat','/sdcard/entry-window.xml'))
action=sys.argv[1]
if action=='dump':
    print([(n.get('text'),n.get('content-desc'),n.get('bounds')) for n in nodes().iter('node') if n.get('text') or n.get('content-desc')])
elif action=='tap':
    label=sys.argv[2]
    n=next(n for n in nodes().iter('node') if label in [n.get('text'),n.get('content-desc')])
    x,y,xx,yy=map(int,re.findall(r'\d+',n.get('bounds')))
    run('shell','input','tap',str((x+xx)//2),str((y+yy)//2))
elif action=='shot':
    pathlib.Path('tmp/entry-qa').mkdir(exist_ok=True)
    pathlib.Path('tmp/entry-qa/'+sys.argv[2]+'.png').write_bytes(run('exec-out','screencap','-p'))
