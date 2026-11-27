from PIL import Image
import os
src = r"c:\Users\从传领\.trae-cn\attachments\6848d782-0156-414d-8f6a-19036ec58bcf_116f57af-5d50-4962-add3-43956646c795_image.png"
outdir = r"g:\beliveOnly\Grindset\.tmp_img"
img = Image.open(src)
def save(name, box, scale=12):
    c = img.crop(box); c = c.resize((c.width*scale, c.height*scale), Image.LANCZOS)
    p = os.path.join(outdir, name); c.save(p); print(p, c.size)
# gem boxes guessed from 722x600 image
save("gem1.png", (40, 15, 115, 80))
save("gem2.png", (115, 15, 190, 80))
save("gem3.png", (190, 15, 265, 80))
save("cir1.png", (370, 25, 430, 75))
save("cir2.png", (445, 25, 505, 75))
save("circle_row.png", (330, 15, 560, 80), 8)
