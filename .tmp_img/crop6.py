from PIL import Image
import os
src = r"c:\Users\从传领\.trae-cn\attachments\6848d782-0156-414d-8f6a-19036ec58bcf_116f57af-5d50-4962-add3-43956646c795_image.png"
outdir = r"g:\beliveOnly\Grindset\.tmp_img"
img = Image.open(src)
def save(name, box, scale=14):
    c = img.crop(box); c = c.resize((c.width*scale, c.height*scale), Image.LANCZOS)
    p = os.path.join(outdir, name); c.save(p); print(p, c.size)
save("c1.png", (330, 20, 410, 80))
save("c2.png", (430, 20, 530, 85))
