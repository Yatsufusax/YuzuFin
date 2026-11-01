#!/usr/bin/env python3
"""
Convierte un video del monito sobre fondo verde puro (#00FF00) en un WebP animado con
transparencia, listo para poner en app/src/main/res/drawable-nodpi/.

Uso:
    python tools/clip_verde_a_webp.py entrada.mp4 app/src/main/res/drawable-nodpi/monito_clip_xxx.webp
    python tools/clip_verde_a_webp.py entrada.mp4 salida.webp --desde 0.5 --hasta 3.5 --alto 400

Necesita: ffmpeg en el PATH y `pip install pillow numpy`.

Qué hace:
  1. Saca los cuadros del video (por defecto 12 por segundo).
  2. Quita el verde por dominancia de color (no toca los amarillos de las monedas).
  3. Recorta justo alrededor del monito, igual que las poses estáticas, para que al
     cambiar de la imagen fija al clip no se note un salto de tamaño.
  4. Arma el WebP animado. Por defecto se reproduce una sola vez (--bucle lo repite sin fin).
"""
import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image

# Cuánto más verde que rojo y azul tiene que ser un pixel para contarse como fondo.
UMBRAL_BAJO = 30
UMBRAL_ALTO = 110


def sacar_verde(ruta_png):
    a = np.asarray(Image.open(ruta_png).convert("RGB")).astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    dominancia = g - np.maximum(r, b)
    alfa = 1 - np.clip((dominancia - UMBRAL_BAJO) / (UMBRAL_ALTO - UMBRAL_BAJO), 0, 1)
    g_limpio = np.minimum(g, np.maximum(r, b))  # saca el verde que se filtra en los bordes
    return np.dstack([r, g_limpio, b, alfa * 255]).astype(np.uint8)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("entrada")
    p.add_argument("salida")
    p.add_argument("--alto", type=int, default=440, help="alto final en px (440 por defecto)")
    p.add_argument("--fps", type=int, default=12, help="cuadros por segundo (12 por defecto)")
    p.add_argument("--desde", type=float, default=0.0, help="segundo de inicio")
    p.add_argument("--hasta", type=float, default=None, help="segundo final")
    p.add_argument("--calidad", type=int, default=50, help="calidad WebP 1-100 (50 por defecto)")
    p.add_argument("--margen", type=int, default=2, help="px de margen alrededor del monito")
    p.add_argument("--bucle", action="store_true", help="repetir sin fin (por defecto una sola vez)")
    args = p.parse_args()

    if shutil.which("ffmpeg") is None:
        sys.exit("No encuentro ffmpeg. Instalalo y volvé a probar.")

    tmp = tempfile.mkdtemp()
    try:
        cmd = ["ffmpeg", "-v", "error", "-y"]
        if args.desde:
            cmd += ["-ss", str(args.desde)]
        if args.hasta is not None:
            cmd += ["-t", str(args.hasta - args.desde)]
        cmd += ["-i", args.entrada, "-an", "-vf", f"fps={args.fps}", os.path.join(tmp, "f%04d.png")]
        subprocess.run(cmd, check=True)

        cuadros = [sacar_verde(f) for f in sorted(glob.glob(os.path.join(tmp, "f*.png")))]
        if not cuadros:
            sys.exit("El video no dio ningún cuadro.")

        # Recuadro mínimo que contiene al monito en todos los cuadros.
        x0 = y0 = 10**9
        x1 = y1 = 0
        for c in cuadros:
            caja = Image.fromarray(c[..., 3]).point(lambda v: 255 if v > 20 else 0).getbbox()
            if caja:
                x0, y0 = min(x0, caja[0]), min(y0, caja[1])
                x1, y1 = max(x1, caja[2]), max(y1, caja[3])
        h_total, w_total = cuadros[0].shape[:2]
        m = args.margen
        caja = (max(0, x0 - m), max(0, y0 - m), min(w_total, x1 + m), min(h_total, y1 + m))
        ancho = round(args.alto * (caja[2] - caja[0]) / (caja[3] - caja[1]))

        salida = [
            Image.fromarray(c, "RGBA").crop(caja).resize((ancho, args.alto), Image.LANCZOS)
            for c in cuadros
        ]
        salida[0].save(
            args.salida,
            save_all=True,
            append_images=salida[1:],
            duration=round(1000 / args.fps),
            loop=0 if args.bucle else 1,
            quality=args.calidad,
            method=6,
            exact=True,
        )
        kb = os.path.getsize(args.salida) / 1024
        print(f"{args.salida}: {ancho}x{args.alto}, {len(salida)} cuadros, {kb:.0f} KB")
        if kb > 1200:
            print("Pesa bastante: probá --alto 380, --fps 10 o --calidad 40.")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
