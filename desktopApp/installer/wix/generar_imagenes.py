"""
Genera banner.bmp y dialog.bmp del instalador (ver main.wxs). No corre en
el build: los .bmp van en el repo. Solo hay que volver a correrlo si se
cambia el diseno:

    python desktopApp/installer/wix/generar_imagenes.py

Requiere Pillow y las fuentes Segoe UI de Windows.

Tamanos de WixUI: banner 493x58 (arriba de los dialogos interiores) y
dialogo 493x312 (bienvenida y final). En el dialogo, el texto empieza en
X=135 de 370 unidades (~180 px): el panel de color ocupa menos que eso y el
resto queda blanco para que el texto se lea.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

AQUI = Path(__file__).resolve().parent
ESCALA = 4  # se dibuja en grande y se reduce: bordes suavizados

AZUL_PROFUNDO = (10, 46, 82)
TURQUESA = (14, 124, 134)
BLANCO = (255, 255, 255)

FUENTES = Path("C:/Windows/Fonts")


def fuente(nombre: str, puntos: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FUENTES / nombre), puntos * ESCALA)


def mezclar(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def olas(dibujo, x0, x1, y_base, alto, color, n=4, separacion=9, grosor=1.2):
    """Lineas de ola paralelas, de x0 a x1 (en pixeles finales)."""
    for i in range(n):
        puntos = []
        for x in range(x0 * ESCALA, x1 * ESCALA + 1, ESCALA):
            fase = x / ESCALA / 38.0 + i * 0.6
            y = y_base + i * separacion + math.sin(fase) * alto
            puntos.append((x, y * ESCALA))
        dibujo.line(puntos, fill=color + (70 - i * 12,), width=round(grosor * ESCALA))


def dialogo():
    ancho, alto, panel = 493, 312, 164
    img = Image.new("RGB", (ancho * ESCALA, alto * ESCALA), BLANCO)
    d = ImageDraw.Draw(img)
    for y in range(alto * ESCALA):
        d.line([(0, y), (panel * ESCALA, y)], fill=mezclar(AZUL_PROFUNDO, TURQUESA, y / (alto * ESCALA)))

    capa = Image.new("RGBA", img.size, (0, 0, 0, 0))
    olas(ImageDraw.Draw(capa), 0, panel, 150, 5, BLANCO, n=5, separacion=11)
    img.paste(capa, (0, 0), capa)

    d = ImageDraw.Draw(img)
    d.text((16 * ESCALA, 244 * ESCALA), "CalcofiLarvae", font=fuente("seguisb.ttf", 15), fill=BLANCO)
    d.text((16 * ESCALA, 266 * ESCALA), "CalCOFI Atlas 33", font=fuente("segoeui.ttf", 9),
           fill=mezclar(BLANCO, TURQUESA, 0.35))
    img.resize((ancho, alto), Image.LANCZOS).save(AQUI / "dialog.bmp")


def banner():
    ancho, alto = 493, 58
    img = Image.new("RGB", (ancho * ESCALA, alto * ESCALA), BLANCO)
    capa = Image.new("RGBA", img.size, (0, 0, 0, 0))
    olas(ImageDraw.Draw(capa), 400, ancho, 18, 3, TURQUESA, n=4, separacion=7, grosor=1.4)
    img.paste(capa, (0, 0), capa)
    img.resize((ancho, alto), Image.LANCZOS).save(AQUI / "banner.bmp")


if __name__ == "__main__":
    dialogo()
    banner()
    print("banner.bmp y dialog.bmp en", AQUI)
