from PIL import Image
import numpy as np, os
src = r"c:\Users\从传领\.trae-cn\attachments\6848d782-0156-414d-8f6a-19036ec58bcf_116f57af-5d50-4962-add3-43956646c795_image.png"
outdir = r"g:\beliveOnly\Grindset\.tmp_img"
img = Image.open(src)
a = np.array(img.convert("L")).astype(float)
# locate dark blobs in option column x 40..160
reg = a[:, 40:170] < 120
rows = reg.sum(axis=1)
# print row ranges with dark
runs=[]
inr=False
for y,v in enumerate(rows):
    if v>3 and not inr: start=y; inr=True
    elif v<=3 and inr: runs.append((start,y)); inr=False
print("row runs:", runs)
