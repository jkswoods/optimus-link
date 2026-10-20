#!/usr/bin/env python3
"""Pinned local build; never installs, launches or downloads Hytale."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess

ROOT=Path(__file__).resolve().parents[1]
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--server',type=Path,required=True);ap.add_argument('--jdk',type=Path,required=True);ap.add_argument('--test',action='store_true');args=ap.parse_args()
    pin=json.loads((ROOT/'target.json').read_text())
    if digest(args.server)!=pin['server_sha256']:raise SystemExit('Server dependency fingerprint differs from target.json')
    java=args.jdk/'bin/java';javac=args.jdk/'bin/javac';jar=args.jdk/'bin/jar'
    out=ROOT/'build';classes=out/'classes';tests=out/'test-classes'
    if classes.exists():shutil.rmtree(classes)
    shutil.copytree(ROOT/'src/main/resources',classes)
    src=sorted((ROOT/'src/main/java').rglob('*.java'))
    subprocess.run([str(javac),'--release',str(pin['java_release']),'-Xlint:all','-cp',str(args.server),'-d',str(classes),*map(str,src)],check=True)
    if args.test:
        if tests.exists():shutil.rmtree(tests)
        tests.mkdir(parents=True)
        compile_cp=os.pathsep.join(map(str,[classes,args.server]))
        subprocess.run([str(javac),'--release',str(pin['java_release']),'-Xlint:all','-cp',compile_cp,'-d',str(tests),*map(str,sorted((ROOT/'src/test/java').rglob('*.java')))],check=True)
        runtime_cp=os.pathsep.join(map(str,[classes,tests,args.server]))
        for suite in ('core.FleetRegistryTest','core.FleetStateCodecTest','core.ControlOutputScopeTest','ActorHandlerTest'):
            subprocess.run([str(java),'-ea','-cp',runtime_cp,'dev.optimuslink.'+suite],check=True)
    artifact=out/('OptimusLink-'+pin['version']+'.jar')
    subprocess.run([str(jar),'--create','--date='+pin['archive_date'],'--file',str(artifact),'-C',str(classes),'.'],check=True)
    (out/'SHA256SUMS.txt').write_text(digest(artifact)+'  '+artifact.name+'\n')
    print(artifact.name,digest(artifact))
if __name__=='__main__':main()
