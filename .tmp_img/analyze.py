from PIL import Image
import numpy as np

src = r"c:\Users\从传领\.trae-cn\attachments\66fba948-c502-425c-a6e4-991a4d7fd42e_9dfcc903-1195-4cb9-8d44-a1cb139a6b04_image.png"
img = Image.open(src).convert("L")
a = np.array(img).astype(float)
print("shape", a.shape)

# focus region of the matrix
reg = a[0:200, 0:220]
dark = reg < 120

colsum = dark.sum(axis=0)
rowsum = dark.sum(axis=1)
print("cols with many dark pixels (>20):")
print([ (i,int(colsum[i])) for i in range(len(colsum)) if colsum[i] > 20 ])
print("rows with many dark pixels (>20):")
print([ (i,int(rowsum[i])) for i in range(len(rowsum)) if rowsum[i] > 20 ])
