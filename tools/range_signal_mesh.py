#!/usr/bin/env python3
"""Original steady fullbright arc. Unit chord; native model scale sets mast distance."""
from pathlib import Path
import json,math,struct,zlib
R=Path(__file__).resolve().parents[1];common=R/'src/main/resources/Common';target=common/'Characters/OptimusLink';nodes=[]
width=.14
for i in range(48):
    a=i/48;b=(i+1)/48
    x1,y1=-64*a,128*a*(1-a);x2,y2=-64*b,128*b*(1-b)
    angle=math.atan2(y2-y1,x2-x1)
    nodes.append({'id':str(i+1),'name':f'Signal_{i:02d}','position':{'x':(x1+x2)/2,'y':(y1+y2)/2,'z':0},'orientation':{'x':0,'y':0,'z':math.sin(angle/2),'w':math.cos(angle/2)},'children':[], 'shape':{'type':'box','offset':{'x':0,'y':0,'z':0},'stretch':{'x':1,'y':1,'z':1},'settings':{'size':{'x':math.hypot(x2-x1,y2-y1)+width/2,'y':width,'z':width},'isStaticBox':True},'textureLayout':{f:{'offset':{'x':0,'y':0},'mirror':{'x':False,'y':False},'angle':0} for f in ['front','back','left','right','top','bottom']},'unwrapMode':'custom','visible':True,'doubleSided':False,'shadingMode':'fullbright'}})
(target/'RangeSignalCurve.blockymodel').write_text(json.dumps({'nodes':nodes,'format':'character','lod':'off'},separators=(',',':'))+'\n')
# Original approved signal's centre RGB, fully opaque: no alpha-dither gaps.
def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
size=8
pixels=b''.join(b'\x00'+bytes((204,255,249,255))*size for _ in range(size))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(pixels))+chunk(b'IEND',b'')
(target/'RangeSignalGlow.png').write_bytes(png)
asset={'Model':'Characters/OptimusLink/RangeSignalCurve.blockymodel','Texture':'Characters/OptimusLink/RangeSignalGlow.png','MinScale':1,'MaxScale':1,'HitBox':{'Min':{'X':-1.05,'Y':-.05,'Z':-.05},'Max':{'X':.05,'Y':.55,'Z':.05}}}
(R/'src/main/resources/Server/Models/OptimusLink/OP_RangeSignal.json').write_text(json.dumps(asset,indent=2)+'\n')
