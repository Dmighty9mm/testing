"""Extracts Apollo message field tables from the public protobufjs bundle.

usage: python3 scripts/extract_apollo_schema.py <proto.bundle.js> docs/apollo/apollo-schema-1.0.2.json
(the bundle ships in the npm package lunar-apollo-protobuf@1.0.2 at
bin/proto.bundle.js; it is GPLv3 and is not vendored - only the facts
read from it (message/field names, field numbers, wire types) are kept.)
"""
import re, sys, json
src = open(sys.argv[1]).read()
lines = src.split('\n')
# message blocks: "v1.Name = (function() {" with memberof in following doc
msgs = {}
enums = {}
cur_ns = None
# Interface property docs: "@interface IName" preceded by "@memberof ns"
for m in re.finditer(r'\* Properties of an? (\w+)\.\s*\n\s*\* @memberof ([\w.]+)\s*\n\s*\* @interface I\w+\s*\n((?:\s*\* @property .*\n)*)', src):
    name, ns, props = m.group(1), m.group(2), m.group(3)
    full = ns + '.' + name
    fields = []
    for p in re.finditer(r'@property \{(.+?)\} \[\"?(\w+)\"?\]', props):
        fields.append({'name': p.group(2), 'jstype': p.group(1)})
    msgs[full] = {'fields': fields}
# messages with no properties
for m in re.finditer(r'\* Constructs a new (\w+)\.\s*\n\s*\* @memberof ([\w.]+)', src):
    full = m.group(2) + '.' + m.group(1)
    msgs.setdefault(full, {'fields': []})
# encode functions: find "Name.encode = function encode" then scan until "return writer;"
for m in re.finditer(r'\n\s*(\w+)\.encode = function encode\(message, writer\) \{(.*?)return writer;', src, re.S):
    name, body = m.group(1), m.group(2)
    # find memberof from preceding doc "@memberof ns.Name" just before
    pre = src[:m.start()]
    mm = list(re.finditer(r'@memberof ([\w.]+)\.' + name + r'\b', pre[-3000:]))
    if not mm: continue
    full = mm[-1].group(1) + '.' + name
    if full not in msgs: msgs[full] = {'fields': []}
    ids = {}
    for f in re.finditer(r'id (\d+), wireType (\d+) =\*/\d+\)(?:\.fork\(\)\.uint32\(/\* id 1, wireType \d+ =\*/\d+\)\.(\w+)\(keys\[i\]\))?\.?(\w*)\(?(?:message\.(\w+)|message\["(\w+)"\])?', body):
        pass
    # map entries: writer.uint32(/* id N, wireType 2 =*/X).fork().uint32(... keys[i]) inside "for (var keys = Object.keys(message.F)"
    for st in re.finditer(r'Object\.keys\(message\.(\w+)\)[^\n]*\n[^\n]*?id (\d+), wireType 2', body):
        ids.setdefault(st.group(1), (int(st.group(2)), 2))
    # simpler: per statement, find field name and id
    for st in re.finditer(r'message(?:\.(\w+)|\["(\w+)"\])[^;\n]*?writer\.uint32\(/\* id (\d+), wireType (\d+)', body):
        fname = st.group(1) or st.group(2); ids.setdefault(fname, (int(st.group(3)), int(st.group(4))))
    for st in re.finditer(r'writer\.uint32\(/\* id (\d+), wireType (\d+) =\*/\d+\)\.(\w+)\(message(?:\.(\w+)|\["(\w+)"\])', body):
        fname = st.group(4) or st.group(5); ids.setdefault(fname, (int(st.group(1)), int(st.group(2))))
    for st in re.finditer(r'message\.(\w+)\.length[^\n]*\n\s*(?:\{\s*\n\s*)?writer\.uint32\(/\* id (\d+), wireType (\d+)', body):
        ids.setdefault(st.group(1), (int(st.group(2)), int(st.group(3))))
    # packed repeated: "writer.uint32(/* id N, wireType 2 =*/X).fork(); for (...message.F.length...) writer.int32(message.F[i])"
    for st in re.finditer(r'id (\d+), wireType 2 =\*/\d+\)\.fork\(\);\s*\n\s*for \(var i = 0; i < message\.(\w+)\.length', body):
        ids[st.group(2)] = (int(st.group(1)), 2)
    meth = {}
    for fname in ids:
        mm2 = re.search(r'\.(\w+)\(message(?:\.' + fname + r'|\["' + fname + r'"\])\b', body)
        if mm2: meth[fname] = mm2.group(1)
        if re.search(r'\$root\.[\w.]+\.encode\(message\.' + fname + r'\b', body): meth[fname] = 'message'
    msgs[full]['ids'] = ids
    msgs[full]['meth'] = meth
for m in re.finditer(r'\* (\w+) enum\.\s*\n\s*\* @name ([\w.]+)\s*\n\s*\* @enum \{number\}((?:\s*\* @property .*\n)*)', src):
    full = m.group(2)
    vals = {}
    for p in re.finditer(r'@property \{number\} (\w+)=(\d+)', m.group(3)):
        vals[p.group(1)] = int(p.group(2))
    enums[full] = vals
# merge
out = {'messages': {}, 'enums': enums}
miss = 0
for full, d in sorted(msgs.items()):
    ids = d.get('ids', {})
    fl = []
    for f in d['fields']:
        if f['name'] not in ids:
            miss += 1; print('NOID', full, f['name'], file=sys.stderr); continue
        fl.append({'name': f['name'], 'id': ids[f['name']][0], 'wt': ids[f['name']][1], 'jstype': f['jstype'], 'm': d.get('meth',{}).get(f['name'],'?')})
    out['messages'][full] = fl
print(len(out['messages']), 'messages', len(enums), 'enums', miss, 'missing ids', file=sys.stderr)
json.dump(out, open(sys.argv[2], 'w'), indent=1, sort_keys=True)
