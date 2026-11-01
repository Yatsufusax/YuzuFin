#!/usr/bin/env python3
"""
Convierte un video del monito sobre fondo verde en el clip de la tarjeta de Inicio (CardMonkey),
centrado en el monito del primer cuadro para que al girar la tarjeta no cambie de tamaño.

Uso:
    python tools/clip_tarjeta_a_webp.py video.mp4 app/src/main/res/drawable-nodpi/monito_clip_paraguay.webp

Al final imprime el ancho y el alto en dp para `CardClip` en CardMonkey.kt (el monito fijo de la
tarjeta mide 128 dp de alto). Necesita ffmpeg y `pip install pillow numpy`.
Guarda con calidad 35, alfa 60 y método 4 (más liviano que los ajustes de clip_verde_a_webp.py).
"""
import argparse
import glob
import importlib.util
import os
import subprocess
import tempfile
import shutil

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("clip_verde", os.path.join(AQUI, "clip_verde_a_webp.py"))
clip_verde = importlib.util.module_from_spec(spec)
spec.loader.exec_module(clip_verde)

MONITO_FIJO_DP = 128.0  # alto del monito fijo de la tarjeta


def caja(a, umbral=10):
    return Image.fromarray(a[..., 3]).point(lambda v: 255 if v > umbral else 0).getbbox()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("entrada")
    p.add_argument("salida")
    p.add_argument("--fps", type=int, default=12)
    p.add_argument("--alto-monito", type=int, default=380, help="px de alto del monito del primer cuadro")
    p.add_argument("--calidad", type=int, default=35)
    args = p.parse_args()

    tmp = tempfile.mkdtemp()
    try:
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", args.entrada, "-an", "-vf", f"fps={args.fps}",
                        os.path.join(tmp, "f%04d.png")], check=True)
        cuadros = []
        for f in sorted(glob.glob(os.path.join(tmp, "f*.png"))):
            a = clip_verde.sacar_verde(f)
            a[..., 3] = np.where(a[..., 3] < 24, 0, a[..., 3])  # sin velos casi invisibles: pesa menos
            cuadros.append(a)

        b0 = caja(cuadros[0])
        cx, cy = (b0[0] + b0[2]) / 2, (b0[1] + b0[3]) / 2
        mh = b0[3] - b0[1]
        u = [10**9, 10**9, 0, 0]
        for a in cuadros:
            b = caja(a)
            u = [min(u[0], b[0]), min(u[1], b[1]), max(u[2], b[2]), max(u[3], b[3])]
        mitad_ancho = max(cx - u[0], u[2] - cx) + 4
        mitad_alto = max(cy - u[1], u[3] - cy) + 4
        recuadro = (round(cx - mitad_ancho), round(cy - mitad_alto), round(cx + mitad_ancho), round(cy + mitad_alto))
        ancho_l, alto_l = recuadro[2] - recuadro[0], recuadro[3] - recuadro[1]
        escala = args.alto_monito / mh
        W, H = round(ancho_l * escala), round(alto_l * escala)

        salida = []
        for a in cuadros:
            im = Image.fromarray(a, "RGBA")
            lienzo = Image.new("RGBA", (ancho_l, alto_l), (0, 0, 0, 0))  # el recuadro puede salirse del video
            lienzo.paste(
                im.crop((max(0, recuadro[0]), max(0, recuadro[1]), min(im.width, recuadro[2]), min(im.height, recuadro[3]))),
                (max(0, -recuadro[0]), max(0, -recuadro[1])),
            )
            salida.append(lienzo.resize((W, H), Image.LANCZOS))
        salida[0].save(args.salida, save_all=True, append_images=salida[1:], duration=round(1000 / args.fps),
                       loop=1, exact=False, alpha_quality=60, quality=args.calidad, method=4)
        alto_dp = MONITO_FIJO_DP * alto_l / mh
        print(f"{args.salida}: {W}x{H}, {len(salida)} cuadros, {os.path.getsize(args.salida) // 1024} KB")
        print(f"CardClip: widthDp = {alto_dp * ancho_l / alto_l:.1f}f, heightDp = {alto_dp:.1f}f")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
