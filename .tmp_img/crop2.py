from PIL import Image
import os
src = r"c:\Users\从传领\.trae-cn\attachments\66fba948-c502-425c-a6e4-991a4d7fd42e_9dfcc903-1195-4cb9-8d44-a1cb139a6b04_image.png"
outdir = r"g:\beliveOnly\Grindset\.tmp_img"
img = Image.open(src)
def save(name, box, scale=6):
    c = img.crop(box)
    c = c.resize((c.width*scale, c.height*scale), Image.LANCZOS)
    p = os.path.join(outdir, name); c.save(p); print(p, c.size)
save("matrix2.png", (0, 0, 220, 190), 6)
save("opt1.png", (30, 250, 120, 320), 10)
save("opt2.png", (30, 330, 120, 400), 10)
save("opt3.png", (30, 420, 120, 490), 10)
save("opt4.png", (30, 500, 120, 570), 10)
