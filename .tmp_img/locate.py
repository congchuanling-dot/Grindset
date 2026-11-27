from PIL import Image
import numpy as np, os
src = r"c:\Users\从传领\.trae-cn\attachments\ed3c6288-829c-4c6c-a38a-5c3d5485e587_133d2f1a-6098-4a38-8702-a9ac8b8da1d2_image.png"
outdir = r"g:\beliveOnly\Grindset\.tmp_img"
img = Image.open(src)
a = np.array(img.convert("L")).astype(float)
d = a < 120
# find dark columns/rows in top-left quadrant
reg = d[0:250, 0:350]
cols = reg.sum(axis=0); rows = reg.sum(axis=1)
print("cols>30:", [i for i in range(len(cols)) if cols[i] > 30])
print("rows>30:", [i for i in range(len(rows)) if rows[i] > 30])
