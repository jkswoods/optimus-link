#!/usr/bin/env python3
"""Package only the supplied development JAR and built player guide; never installs or publishes."""
from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
import argparse,hashlib,json

ROOT=Path(__file__).resolve().parents[1]
def digest(data):return hashlib.sha256(data).hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--site',type=Path,default=ROOT/'site');ap.add_argument('--out',type=Path,default=ROOT/'build/packages');args=ap.parse_args()
    target=json.loads((ROOT/'target.json').read_text());version=target['version'];jar=args.jar.read_bytes()
    with ZipFile(args.jar) as z:
        manifest=json.loads(z.read('manifest.json'))
        if manifest['Version']!=version or manifest['Main']!='dev.optimuslink.OptimusLinkPlugin':raise SystemExit('JAR identity does not match this project')
        if z.testzip():raise SystemExit('JAR integrity check failed')
    if not (args.site/'index.html').is_file():raise SystemExit('Build the player guide first')
    entries={args.jar.name:jar,'THIRD_PARTY_NOTICES.md':(ROOT/'THIRD_PARTY_NOTICES.md').read_bytes()}
    entries['READ-ME.txt']=(f'Optimus Link {version}\nHytale {target["hytale"]}; Java {target["java_release"]}.\nExperimental private development candidate. Native gameplay verification is pending; use a disposable world.\nOpen guide/index.html for installation, recipes, controls and known limits.\nThis package is not a public release. No distribution license has been selected.\n\nJAR SHA256: {digest(jar)}\n').encode()
    for path in sorted(args.site.rglob('*')):
        if path.is_symlink():raise SystemExit('The site contains a symlink')
        if path.is_file():
            rel=path.relative_to(args.site)
            if any(part.startswith('.') for part in rel.parts):raise SystemExit('The site contains an unexpected hidden file')
            entries['guide/'+rel.as_posix()]=path.read_bytes()
    entries['SHA256SUMS.txt']=''.join(digest(data)+'  '+name+'\n' for name,data in sorted(entries.items())).encode()
    args.out.mkdir(parents=True,exist_ok=True);out=args.out/f'OptimusLink-{version}-candidate.zip'
    with ZipFile(out,'w',compression=ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            info=ZipInfo(name,date_time=(2026,10,20,0,0,0));info.compress_type=ZIP_DEFLATED;info.create_system=3;info.external_attr=0o100644<<16;z.writestr(info,data)
    with ZipFile(out) as z:
        if z.testzip() or z.read(args.jar.name)!=jar:raise SystemExit('Package verification failed')
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            checksum,name=line.split('  ',1)
            if digest(z.read(name))!=checksum:raise SystemExit('Packaged checksum mismatch')
    (args.out/'SHA256SUMS.txt').write_text(digest(out.read_bytes())+'  '+out.name+'\n')
    print(out.name,digest(out.read_bytes()),len(entries),'entries')
if __name__=='__main__':main()
