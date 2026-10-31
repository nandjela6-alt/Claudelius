"""Merge aapt2's resource APK with classes.dex into one zip.

resources.arsc must be STORED and 4-byte aligned for targetSdk 30 (Android 11 refuses
otherwise), so we pad the local header's extra field like zipalign does.
Usage: python3 -I package_apk.py <resources.apk> <classes.dex> <out.apk>
"""
import sys, zipfile

src, dex, out = sys.argv[1:4]
STORE_ALIGNED = {"resources.arsc"}

with zipfile.ZipFile(src) as zin, open(out, "wb") as raw:
    zout = zipfile.ZipFile(raw, "w")
    entries = [(i, zin.read(i.filename)) for i in zin.infolist()]
    entries.append((zipfile.ZipInfo("classes.dex", date_time=(2008, 1, 1, 0, 0, 0)), open(dex, "rb").read()))
    for info, data in entries:
        zi = zipfile.ZipInfo(info.filename, date_time=(2008, 1, 1, 0, 0, 0))
        stored = info.filename in STORE_ALIGNED or info.filename.endswith(".png")
        zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        if stored:
            # header = 30 bytes + name + extra; data must start on a 4-byte boundary
            offset = raw.tell() + 30 + len(zi.filename.encode())
            zi.extra = b"\x00" * ((4 - offset % 4) % 4)
        zout.writestr(zi, data)
    zout.close()

with zipfile.ZipFile(out) as z:
    for i in z.infolist():
        if i.filename in STORE_ALIGNED:
            hdr_extra = len(i.extra)
            data_off = i.header_offset + 30 + len(i.filename.encode()) + hdr_extra
            assert i.compress_type == zipfile.ZIP_STORED and data_off % 4 == 0, i.filename
print("packaged", out)
