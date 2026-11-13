import struct, sys
def chunks(data, off, end):
    while off < end:
        cid = data[off:off+4].decode('latin1'); size = struct.unpack_from('<I', data, off+4)[0]
        yield cid, off+8, size
        off += 8 + size + (size & 1)
def parse(path):
    d = open(path,'rb').read()
    assert d[:4]==b'RIFF' and d[8:12]==b'sfbk'
    out = {}
    for cid, o, s in chunks(d, 12, len(d)):
        if cid == 'LIST':
            lt = d[o:o+4].decode()
            for c2, o2, s2 in chunks(d, o+4, o+s):
                out[(lt, c2)] = d[o2:o2+s2]
    return out
def recs(b, size, fmt):
    return [struct.unpack_from(fmt, b, i*size) for i in range(len(b)//size)]
def main(src, dst, listonly=False):
    c = parse(src)
    phdr = recs(c[('pdta','phdr')],38,'<20sHHHIII'); pbag = recs(c[('pdta','pbag')],4,'<HH')
    pgen = recs(c[('pdta','pgen')],4,'<Hh'); inst = recs(c[('pdta','inst')],22,'<20sH')
    ibag = recs(c[('pdta','ibag')],4,'<HH'); igen = recs(c[('pdta','igen')],4,'<Hh')
    shdr = recs(c[('pdta','shdr')],46,'<20sIIIIIBbHH')
    smpl = c[('sdta','smpl')]
    nm = lambda b: b.split(b'\0')[0].decode('latin1')
    drums = [i for i in range(len(phdr)-1) if phdr[i][2]==128]
    for i in drums: print(phdr[i][1], nm(phdr[i][0]))
    if listonly: return
    # collect
    def pzones(i): return range(phdr[i][3], phdr[i+1][3])
    def izones(i): return range(inst[i][1], inst[i+1][1])
    def gens(bags, gl, z): return gl[bags[z][0]:bags[z+1][0]]
    used_inst = []; 
    for p in drums:
        for z in pzones(p):
            for op,am in gens(pbag,pgen,z):
                if op==41 and (am & 0xffff) not in used_inst: used_inst.append(am & 0xffff)
    used_smp = []
    def addsmp(s):
        if s not in used_smp:
            used_smp.append(s)
            t = shdr[s][9]; link = shdr[s][8]
            if t in (2,4) and link < len(shdr)-1: addsmp(link)
    for ii in used_inst:
        for z in izones(ii):
            for op,am in gens(ibag,igen,z):
                if op==53: addsmp(am & 0xffff)
    imap = {o:n for n,o in enumerate(used_inst)}; smap = {o:n for n,o in enumerate(used_smp)}
    # samples
    newsmpl = bytearray(); newshdr = []
    for s in used_smp:
        name,st,en,ls,le,sr,op_,pc,link,typ = shdr[s]
        data = smpl[st*2:en*2]; nst = len(newsmpl)//2
        newsmpl += data + b'\0'*92
        nlink = smap.get(link, 0) if typ in (2,4) else 0
        if typ in (2,4) and link not in smap: typ = 1
        newshdr.append(struct.pack('<20sIIIIIBbHH', name, nst, nst+(en-st), nst+(ls-st), nst+(le-st), sr, op_, pc, nlink, typ))
    newshdr.append(struct.pack('<20sIIIIIBbHH', b'EOS', 0,0,0,0,0,0,0,0,0))
    # instruments
    ninst=b''; nibag=b''; nigen=b''; bi=0; gi=0
    for ii in used_inst:
        ninst += struct.pack('<20sH', inst[ii][0], bi)
        for z in izones(ii):
            nibag += struct.pack('<HH', gi, 0); bi+=1
            for op,am in gens(ibag,igen,z):
                if op==53: am = smap[am & 0xffff]
                nigen += struct.pack('<Hh' if am<0 else '<HH', op, am); gi+=1
    ninst += struct.pack('<20sH', b'EOI', bi); nibag += struct.pack('<HH', gi, 0); nigen += struct.pack('<HH',0,0)
    nphdr=b''; npbag=b''; npgen=b''; bi=0; gi=0
    for p in drums:
        h = phdr[p]; nphdr += struct.pack('<20sHHHIII', h[0], h[1], h[2], bi, 0,0,0)
        for z in pzones(p):
            npbag += struct.pack('<HH', gi, 0); bi+=1
            for op,am in gens(pbag,pgen,z):
                if op==41: am = imap[am & 0xffff]
                npgen += struct.pack('<Hh' if am<0 else '<HH', op, am); gi+=1
    nphdr += struct.pack('<20sHHHIII', b'EOP',0,0,bi,0,0,0); npbag += struct.pack('<HH', gi, 0); npgen += struct.pack('<HH',0,0)
    mod = b'\0'*10
    def ck(i, b): return i.encode()+struct.pack('<I',len(b))+b+(b'\0' if len(b)&1 else b'')
    def lst(t, body): return ck('LIST', t.encode()+body)
    info = ck('ifil', struct.pack('<HH',2,1)) + ck('isng', b'EMU8000\0') + ck('INAM', b'DrumLive Kits (GeneralUser GS)\0\0') + ck('ICMT', b'Drum kits extracted from GeneralUser GS v2 by S. Christian Collins\0\0')
    sdta = ck('smpl', bytes(newsmpl))
    pdta = ck('phdr',nphdr)+ck('pbag',npbag)+ck('pmod',mod)+ck('pgen',npgen)+ck('inst',ninst)+ck('ibag',nibag)+ck('imod',mod)+ck('igen',nigen)+ck('shdr',b''.join(newshdr))
    body = b'sfbk' + lst('INFO',info) + lst('sdta',sdta) + lst('pdta',pdta)
    open(dst,'wb').write(b'RIFF'+struct.pack('<I',len(body))+body)
    print('samples', len(used_smp), 'insts', len(used_inst), 'bytes', len(body))
if __name__=='__main__': main(sys.argv[1], sys.argv[2] if len(sys.argv)>2 else None, len(sys.argv)<3)
