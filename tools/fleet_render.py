#!/usr/bin/env python3
"""Depth-buffered textured renders of the actual model exports; no game access."""
from pathlib import Path
import json, math
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
BG=(18,31,27)
def render(m,width=700,height=800,yaw=28,elev=16,fit=.88,bg=BG,transparent=False,root=ROOT):
 atlas=np.array(Image.open(root/m['texture']))[:,:,:3];th,tw=atlas.shape[:2]
 vs=np.array([v for f in m['faces'] for v in f['v']]);lo,hi=vs.min(0),vs.max(0);center=(lo+hi)/2
 a,b=np.radians([yaw,elev]);eye=np.array([math.sin(a)*math.cos(b),math.sin(b),math.cos(a)*math.cos(b)])
 right=np.cross([0,1,0],eye);right/=np.linalg.norm(right);up=np.cross(eye,right);basis=np.stack([right,up,eye])
 pv=(vs-center)@basis.T;scale=min(width/np.ptp(pv[:,0]),height/np.ptp(pv[:,1]))*fit
 def project(v):
  p=(np.array(v)-center)@basis.T;return np.stack([p[...,0]*scale+width/2,-p[...,1]*scale+height/2,p[...,2]],axis=-1)
 yy=np.linspace(0,1,height)[:,None,None];rgb=np.broadcast_to(np.array(bg)*(1-yy)+np.array(bg)*1.32*yy,(height,width,3)).copy()
 zb=np.full((height,width),-np.inf);light=np.array([-.45,.8,.6]);light/=np.linalg.norm(light)
 for f in m['faces']:
  n=np.array(f['n'])
  if n@eye<=0:continue
  shade=.53+.48*max(0,n@light)
  if f['mat']=='cyan':shade=1.08
  p=project(f['v']);uv=np.array(f['uv'])*[tw,th]
  for ids in [[0,1,2],[0,2,3]]:
   tri=p[ids];tx=uv[ids];mn=np.maximum(np.floor(tri[:,:2].min(0)).astype(int),0);mx=np.minimum(np.ceil(tri[:,:2].max(0)).astype(int),[width-1,height-1])
   if (mx<mn).any():continue
   xx,yy=np.meshgrid(np.arange(mn[0],mx[0]+1)+.5,np.arange(mn[1],mx[1]+1)+.5);a,c,e=tri
   den=(c[1]-e[1])*(a[0]-e[0])+(e[0]-c[0])*(a[1]-e[1])
   if abs(den)<1e-9:continue
   w0=((c[1]-e[1])*(xx-e[0])+(e[0]-c[0])*(yy-e[1]))/den;w1=((e[1]-a[1])*(xx-e[0])+(a[0]-e[0])*(yy-e[1]))/den;w2=1-w0-w1
   z=w0*a[2]+w1*c[2]+w2*e[2];region=zb[mn[1]:mx[1]+1,mn[0]:mx[0]+1]
   mask=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)&(z>region+1e-6)
   if not mask.any():continue
   u=np.floor(w0*tx[0,0]+w1*tx[1,0]+w2*tx[2,0]).astype(int).clip(0,tw-1);v=np.floor(w0*tx[0,1]+w1*tx[1,1]+w2*tx[2,1]).astype(int).clip(0,th-1)
   colour=np.clip(atlas[v,u]*shade,0,255);rgb[mn[1]:mx[1]+1,mn[0]:mx[0]+1][mask]=colour[mask];region[mask]=z[mask]
 if transparent:return Image.fromarray(np.dstack([rgb.astype('uint8'),np.where(np.isfinite(zb),255,0).astype('uint8')]))
 return Image.fromarray(rgb.astype('uint8'))
