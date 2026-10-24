"""Flat 16x16 item textures for the coat and trousers (the particle texture when one breaks; the 3D model is the icon)."""
from PIL import Image
import os

OUT = "src/main/resources/assets/geist_hellblazer/textures/item"
os.makedirs(OUT, exist_ok=True)
P = {".": None, "t": (176, 140, 92), "T": (142, 108, 66), "d": (108, 80, 48), "w": (236, 232, 222), "r": (176, 22, 30), "k": (28, 26, 30),
     "K": (48, 46, 52), "s": (150, 150, 158)}
COAT = [
    "................",
    "....TTt..tTT....",
    "...TttTwwTttT...",
    "..TtttTwrwTtttT.",
    "..TtttTwrwTtttT.",
    ".TttttTwrwTttttT",
    ".TttttTkskTttttT",
    ".TtdtTttrttTtdtT",
    ".Ttd.TtkKktT.dtT",
    ".Tt..TtkKktT..tT",
    ".Tt..TtkKktT..tT",
    ".dd..TtkKktT..dd",
    "....TttkKkttT...",
    "....TttkKkttT...",
    "....TTTkKkTTT...",
    "................",
]
TROUSERS = [
    "................",
    "...kkkkkkkkkk...",
    "...KsKKKKKKKK...",
    "...kkkkkkkkkk...",
    "...kKkkkkkKkk...",
    "...kKkkkkkKkk...",
    "...kKkk..kKkk...",
    "...kKkk..kKkk...",
    "...kKkk..kKkk...",
    "...kKkk..kKkk...",
    "...kKkk..kKkk...",
    "...kKkk..kKkk...",
    "...kKkk..kKkk...",
    "...KKKK..KKKK...",
    "...kkkk..kkkk...",
    "................",
]
for name, rows in (("trench_coat", COAT), ("black_trousers", TROUSERS)):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            c = P.get(ch)
            if c: im.putpixel((x, y), c + (255,))
    im.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)
