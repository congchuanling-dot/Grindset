from PIL import Image
import numpy as np

src = r"c:\Users\从传领\.trae-cn\attachments\66fba948-c502-425c-a6e4-991a4d7fd42e_9dfcc903-1195-4cb9-8d44-a1cb139a6b04_image.png"
a = np.array(Image.open(src).convert("L")).astype(float)

gx = [(17,58),(62,101),(107,147)]
gy = [(13,53),(56,95),(99,137)]

def cell_mean(x0,x1,y0,y1,i,j):
    w=(x1-x0)/3.0; h=(y1-y0)/3.0
    cx0=x0+j*w; cx1=x0+(j+1)*w
    cy0=y0+i*h; cy1=y0+(i+1)*h
    # shrink 30%
    mx=(cx1-cx0)*0.3; my=(cy1-cy0)*0.3
    xs=int(round(cx0+mx)); xe=int(round(cx1-mx))
    ys=int(round(cy0+my)); ye=int(round(cy1-my))
    return a[ys:ye, xs:xe].mean()

for gi,(x0,x1) in enumerate(gx):
    for gj,(y0,y1) in enumerate(gy):
        vals=[[cell_mean(x0,x1,y0,y1,i,j) for j in range(3)] for i in range(3)]
        print(f"grid r{gj+1}c{gi+1}  x[{x0},{x1}] y[{y0},{y1}]")
        for row in vals:
            print("   ", "  ".join(f"{v:6.1f}" for v in row))
