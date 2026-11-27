from PIL import Image
import os
src = r"c:\Users\从传领\.trae-cn\attachments\44a2bbf7-9955-4c54-bfe4-009628101cf6_7f3623c2-0415-4287-b9ea-cfc0415be9df_image.png"
outdir = r"g:\beliveOnly\Grindset\.tmp_img"
img = Image.open(src)
print("size", img.size)
def save(name, box, scale=10):
    c = img.crop(box); c = c.resize((c.width*scale, c.height*scale), Image.LANCZOS)
    p = os.path.join(outdir, name); c.save(p); print(p, c.size)
# question stem row
save("stem_left.png", (60, 5, 240, 70))
save("stem_right.png", (360, 5, 540, 70))
# option rows
save("op1.png", (55, 135, 145, 205))
save("op2.png", (55, 240, 145, 310))
save("op3.png", (55, 345, 145, 415))
save("op4.png", (55, 455, 145, 525))
