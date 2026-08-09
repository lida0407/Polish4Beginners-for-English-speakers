import struct, sys

def palmdoc_decompress(data):
    out = bytearray(); i = 0; n = len(data)
    while i < n:
        b = data[i]; i += 1
        if b == 0:
            out.append(0)
        elif b <= 8:                      # 1..8 literal bytes
            out += data[i:i+b]; i += b
        elif b <= 0x7f:
            out.append(b)
        elif b <= 0xbf:                   # LZ77 back-reference
            if i >= n: break
            v = (b << 8) | data[i]; i += 1
            dist = (v >> 3) & 0x7ff
            length = (v & 7) + 3
            if dist == 0 or dist > len(out): break
            start = len(out) - dist
            for j in range(length):
                out.append(out[start + j])
        else:                             # 0xc0..0xff -> space + char
            out.append(32); out.append(b ^ 0x80)
    return bytes(out)

F = sys.argv[1]
d = open(F,'rb').read()
nrec = struct.unpack('>H', d[76:78])[0]
offs = [struct.unpack('>I', d[78+i*8:82+i*8])[0] for i in range(nrec)] + [len(d)]
rec0 = d[offs[0]:offs[1]]
tlen, nrecs = struct.unpack('>IH', rec0[4:10])
# trailing-entry flags live in the MOBI header extra-data flags
extra_flags = 0
if rec0[16:20] == b'MOBI':
    hlen = struct.unpack('>I', rec0[20:24])[0]
    if hlen >= 244:
        extra_flags = struct.unpack('>H', rec0[242:244])[0]

def strip_trailing(rec, flags):
    # multibyte/trailing entries appended to each text record
    for bit in range(15, 0, -1):
        if flags & (1 << bit):
            num = 0
            for k in range(1, 5):          # backwards varint
                v = rec[-k]
                num = (num << 7) | (v & 0x7f)
                if v & 0x80: break
            rec = rec[:-num]
    if flags & 1:
        rec = rec[:-((rec[-1] & 3) + 1)]
    return rec

parts = []
for i in range(1, nrecs+1):
    rec = d[offs[i]:offs[i+1]]
    rec = strip_trailing(rec, extra_flags)
    parts.append(palmdoc_decompress(rec))
text = b''.join(parts)
print("decompressed bytes:", len(text), "(header said %d)" % tlen)
open('dict_raw.html','wb').write(text)
