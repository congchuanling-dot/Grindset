from PIL import Image
import numpy as np

src = r"c:\Users\从传领\.trae-cn\attachments\66fba948-c502-425c-a6e4-991a4d7fd42e_9dfcc903-1195-4cb9-8d44-a1cb139a6b04_image.png"
a = np.array(Image.open(src).convert("L")).astype(float)

ys = slice(5,155); xs = slice(10,160)
reg = a[ys, xs]
d = reg < 140
print("cols (orig index): count")
print([(i+10,int(d[:,i].sum())) for i in range(reg.shape[1]) if d[:,i].sum()>60])
print("rows (orig index): count")
print([(i+5,int(d[i,:].sum())) for i in range(reg.shape[0]) if d[i,:].sum()>60])
