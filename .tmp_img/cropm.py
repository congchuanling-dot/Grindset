from PIL import Image
import os
src = r"c:\Users\从传领\.trae-cn\attachments\ed3c6288-829c-4c6c-a38a-5c3d5485e587_133d2f1a-6098-4a38-8702-a9ac8b8da1d2_image.png"
outdir = r"g:\beliveOnly\Grindset\.tmp_img"
img = Image.open(src)
print("size", img.size)
def save(name, box, scale=10):
    c = img.crop(box); c = c.resize((c.width*scale, c.height*scale), Image.LANCZOS)
    p = os.path.join(outdir, name); c.save(p); print(p, c.size)
# matrix approx top-left
save("m_all.png", (10, 5, 130, 105), 8)
for r in range(3):
    for c in range(3):
        x0 = 12 + c*38; y0 = 8 + r*32
        save(f"m_r{r+1}c{c+1}.png", (x0, y0, x0+38, y0+32), 14)
