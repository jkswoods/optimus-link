#!/usr/bin/env python3
"""Compile original palette variants and UI portraits from the shipped native robot rig.

Authoring only; needs numpy and Pillow. Does not install or inspect the game.
"""
from pathlib import Path
import json, math
import numpy as np
from PIL import Image, ImageDraw
from fleet_render import render
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
PALETTE=dict(shell='#e5e5df',edge='#b7bcb9',steel='#78838a',dark='#262d32',black='#10161b',visor='#111d27',rubber='#1a2024',silver='#c4cccb',cyan='#9acfd0',red='#a83e3b',screen='#263b43',ink='#596569',white='#f1f1e9',brass='#867962',blue='#547e8c',grip='#353e42')
PAINTS={'white':{},'graphite':dict(shell='#68747e',edge='#424e58',white='#9ca9b1'),'blue':dict(shell='#557eaf',edge='#395873',white='#99b3d0'),'ochre':dict(shell='#ac863e',edge='#775e32',white='#d3b874'),'green':dict(shell='#648164',edge='#425d45',white='#a4b99a'),'red':dict(shell='#a45852',edge='#713f3d',white='#d29a90')}
FACES={
 'front':([[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]],[0,0,1],(0,1)),
 'back':([[1,-1,-1],[-1,-1,-1],[-1,1,-1],[1,1,-1]],[0,0,-1],(0,1)),
 'right':([[1,-1,1],[1,-1,-1],[1,1,-1],[1,1,1]],[1,0,0],(2,1)),
 'left':([[-1,-1,-1],[-1,-1,1],[-1,1,1],[-1,1,-1]],[-1,0,0],(2,1)),
 'top':([[-1,1,1],[1,1,1],[1,1,-1],[-1,1,-1]],[0,1,0],(0,2)),
 'bottom':([[-1,-1,-1],[1,-1,-1],[1,-1,1],[-1,-1,1]],[0,-1,0],(0,2))}
def atlas(paint):
 im=Image.new('RGBA',(128,128));d=ImageDraw.Draw(im)
 for i,(name,h) in enumerate((PALETTE|paint).items()):
  c=bytes.fromhex(h[1:]);ox=i%4*32;oy=i//4*32
  def col(k):return tuple(min(255,max(0,round(v*k))) for v in c)+(255,)
  for y in range(32):
   for x in range(32):im.putpixel((ox+x,oy+y),col(1.06-y*.003+(.045 if x<2 else -.045 if x>29 else 0)))
  d.line((ox+1,oy+1,ox+30,oy+1),fill=col(1.16));d.line((ox+1,oy+30,ox+30,oy+30),fill=col(.78))
  if name in ('rubber','grip'):
   for y in range(4,30,4):d.line((ox+1,oy+y,ox+30,oy+y),fill=col(1.18))
  if name=='visor':d.polygon([(ox+2,oy+2),(ox+6,oy+2),(ox+30,oy+25),(ox+30,oy+29)],fill=col(1.85))
 return im

def vector(d):return np.array([d.get(k,0) for k in 'xyz'],dtype=float)
def rotation(q):
 x,y,z,w=[q.get(k,1 if k=='w' else 0) for k in 'xyzw']
 return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def geometry():
 source=json.loads((RES/'Common/Characters/OptimusLink/OP_Optimus.blockymodel').read_text());faces=[]
 def visit(n,parent):
  local=np.eye(4);local[:3,:3]=rotation(n['orientation']);local[:3,3]=vector(n['position']);world=parent@local
  s=n['shape']
  if s['type']=='box' and s.get('visible',True):
   size=vector(s['settings']['size'])*vector(s['stretch']);offset=vector(s['offset'])
   for name,(corners,normal,axes) in FACES.items():
    v=(np.array(corners)*size/2+offset)@world[:3,:3].T+world[:3,3]
    tex=s['textureLayout'][name];ox,oy=tex['offset']['x'],tex['offset']['y'];w,h=[s['settings']['size']['xyz'[j]] for j in axes]
    uv=np.array([[ox,oy+h],[ox+w,oy+h],[ox+w,oy],[ox,oy]])/128
    faces.append({'v':(v/64).tolist(),'n':(np.array(normal)@world[:3,:3].T).tolist(),'uv':uv.tolist(),'mat':'cyan' if ox==0 and oy==64 else 'shell'})
  for child in n.get('children',[]):visit(child,world)
 for n in source['nodes']:visit(n,np.eye(4))
 return faces

def ui_art(ui):
 def patch(name,edge,top,bottom):
  im=Image.new('RGBA',(64,64),edge);d=ImageDraw.Draw(im)
  a=bytes.fromhex(top[1:]);b=bytes.fromhex(bottom[1:])
  for y in range(3,61):
   t=(y-3)/57;d.line((3,y,60,y),fill=tuple(round(x*(1-t)+z*t) for x,z in zip(a,b))+(255,))
  im.save(ui/name)
 for args in [
  ('Action.png','#769de0','#3c6bc0','#2f58a0'),('ActionHovered.png','#9cbcf0','#4c7dd2','#3c69b4'),('ActionPressed.png','#81a9e9','#2a5199','#315da6'),('ActionDisabled.png','#46576e','#2c394b','#263446'),
  ('Field.png','#4c6382','#172a40','#142336'),('FieldHovered.png','#7597c2','#20354e','#1c2f46'),('Header.png','#294b6d','#213e5b','#18324f'),
  ('Swatch.png','#566577','#273344','#263244'),('PortraitFrame.png','#536d8c','#152638','#142132'),('PanelBody.png','#263b53','#142132','#0f1a28'),
  ('UnlinkButton.png','#a45660','#30212b','#291d27'),('UnlinkHovered.png','#d57b82','#472e39','#3d2934'),('ScrollHandle.png','#3c5a80','#5b7aa5','#435f85')]:patch(*args)
 row=Image.new('RGBA',(16,16),'#121e2c');ImageDraw.Draw(row).rectangle((0,14,15,15),fill='#344459');row.save(ui/'RowNormal.png')
 selected=Image.new('RGBA',(12,12),'#92bcfb');ImageDraw.Draw(selected).rectangle((3,3,8,8),fill=(0,0,0,0));selected.save(ui/'SwatchSelected.png')
 for name,bg in [('Close.png','#203a55'),('CloseHovered.png','#365676'),('ClosePressed.png','#182f49')]:
  im=Image.new('RGBA',(48,48),bg);d=ImageDraw.Draw(im);d.rectangle((0,0,47,47),outline='#476583',width=2);d.line((15,15,33,33),fill='#c1d0e5',width=3);d.line((33,15,15,33),fill='#c1d0e5',width=3);im.save(ui/name)
 for name,points in [('Chevron.png',[(2,2),(10,9),(18,2)]),('ChevronUp.png',[(2,9),(10,2),(18,9)])]:
  im=Image.new('RGBA',(20,12));ImageDraw.Draw(im).line(points,fill='#dbe7f7',width=3);im.save(ui/name)
 im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im);d.ellipse((2,2,29,29),outline='#a3c6e7',width=2);d.ellipse((14,7,17,10),fill='#a3c6e7');d.line((16,14,16,24),fill='#a3c6e7',width=3);im.save(ui/'Info.png')

def main():
 faces=geometry();ui=RES/'Common/UI/Custom/Pages/OptimusLink';portraits=ui/'Portraits';portraits.mkdir(parents=True,exist_ok=True)
 base=RES/'Common/Characters/OptimusLink/OP_Optimus.png'
 assert Image.open(base).tobytes()==atlas({}).tobytes(),'Original palette differs; review before authoring variants'
 for colour,paint in PAINTS.items():
  texture=base
  if colour!='white':
   texture=base.with_name('OP_Optimus_'+colour+'.png');atlas(paint).save(texture)
   model=RES/'Server/Models/OptimusLink'/('OP_Optimus_'+colour+'.json')
   model.write_text(json.dumps({'Parent':'OP_Optimus','Texture':'Characters/OptimusLink/'+texture.name},indent=2)+'\n')
  model={'faces':faces,'texture':str(texture.relative_to(ROOT))}
  render(model,256,304,25,10,.94,transparent=True,root=ROOT).save(portraits/(colour+'.png'))
  bust={**model,'faces':[f for f in faces if np.max(np.array(f['v'])[:,1])>1.0]}
  render(bust,256,256,25,8,.94,transparent=True,root=ROOT).save(portraits/(colour+'-bust.png'))
  render(bust,312,312,25,8,.98,transparent=True,root=ROOT).crop((0,0,312,216)).save(portraits/(colour+'-roster.png'))
 pencil=Image.new('RGBA',(24,24));d=ImageDraw.Draw(pencil)
 d.polygon([(4,16),(16,4),(20,8),(8,20),(3,21)],fill='#d5dfec');d.line((15,6,18,9),fill='#283a52',width=2);d.polygon([(3,21),(4,16),(8,20)],fill='#8ca4c1');pencil.save(ui/'Pencil.png')
 for filename,border,fill in [('SelectedRow.png','#679de9','#1a2d43'),('UnlinkButton.png','#a45660','#332630')]:
  patch=Image.new('RGBA',(12,12),border);ImageDraw.Draw(patch).rectangle((2,2,9,9),fill=fill);patch.save(ui/filename)
 ui_art(ui)
 print('Compiled original palette portraits and concept-matched native handset UI art.')
if __name__=='__main__':main()
