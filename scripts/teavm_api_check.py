"""Lists JDK members referenced by compiled classes that TeaVM's classlib lacks.
usage: teavm_api_check.py <classes dir> <teavm-classlib.jar>"""
import subprocess, sys, re, os, zipfile
cls_dir, jar = sys.argv[1], sys.argv[2]
files = [os.path.join(d, f) for d, _, fs in os.walk(cls_dir) for f in fs if f.endswith('.class')]
out = subprocess.run(['javap', '-c', '-p'] + files, capture_output=True, text=True).stdout
refs = set(re.findall(r'// (?:Method|InterfaceMethod|Field) (java/[\w/$]+)\.([\w<>$"]+):', out))
refs |= set((c, None) for c in re.findall(r'// class (java/[\w/$]+)', out))
z = zipfile.ZipFile(jar)
names = set(z.namelist())
cache = {}
def members(tcls):
    if tcls not in cache:
        r = subprocess.run(['javap', '-p', '-cp', jar, tcls.replace('/', '.')], capture_output=True, text=True).stdout
        cache[tcls] = r
    return cache[tcls]
def has_member(tcls, m, seen=None):
    seen = seen or set()
    if tcls in seen: return False
    seen.add(tcls)
    txt = members(tcls)
    if re.search(r'[\s.]' + re.escape(m) + r'\(', txt) or re.search(r'\s' + re.escape(m) + r';', txt): return True
    head = txt.split('{')[0]
    for sup in re.findall(r'(org\.teavm\.classlib\.[\w.$]+|java\.lang\.Object)', head.split(' extends ',1)[-1] if ' extends ' in head or ' implements ' in head else ''):
        if sup.replace('.', '/') == tcls: continue
        if has_member(sup.replace('.', '/'), m, seen): return True
    return False
bad = []
for c, m in sorted(refs, key=lambda x: (x[0], x[1] or '')):
    pkg, _, simple = c.rpartition('/')
    tcls = 'org/teavm/classlib/' + pkg + '/T' + simple
    if tcls + '.class' not in names:
        tcls = 'org/teavm/classlib/' + pkg + '/T' + simple.replace('$', '$T')
    if tcls + '.class' not in names:
        bad.append(c + ' (class missing)'); continue
    m = m.strip('"') if m else m
    if m and m != '<init>' and not has_member(tcls, m):
        bad.append(c + '.' + m)
print('\n'.join(bad) if bad else 'all JDK references exist in the TeaVM classlib')
