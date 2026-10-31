import subprocess, sys, numpy as np
from scipy import ndimage as ndi
SRC, OUT = sys.argv[1], sys.argv[2]
W, H, FPS = 1280, 720, 24
FOREST = np.array([0x0F, 0x2A, 0x24], dtype=np.float32)
GLOW = np.array([0x30, 0xDA, 0x8B], dtype=np.float32)

# Fondo: verde bosque (igual que la splash del sistema) con un brillo verde suave al centro.
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
d = np.sqrt(((xx - W / 2) / (H * 0.75)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
t = np.clip(1.0 - d, 0, 1) ** 1.6 * 0.30           # 0 en los bordes superior/inferior
BG = FOREST[None, None, :] * (1 - t[..., None]) + GLOW[None, None, :] * t[..., None]
assert np.allclose(BG[0, :, :], FOREST, atol=0.5) and np.allclose(BG[-1, :, :], FOREST, atol=0.5)

# Desvanecer arriba y abajo: el monito toca esos bordes y quedaría cortado en seco.
FADE = 40
ramp = np.ones(H, dtype=np.float32)
ramp[:FADE] = np.linspace(0, 1, FADE)
ramp[-FADE:] = np.linspace(1, 0, FADE)

dec = subprocess.Popen(['ffmpeg', '-v', 'error', '-i', SRC, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'],
                       stdout=subprocess.PIPE)
enc = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}',
                        '-r', str(FPS), '-i', '-', '-i', SRC, '-map', '0:v', '-map', '1:a?',
                        '-c:v', 'libx264', '-preset', 'slow', '-crf', '22', '-pix_fmt', 'yuv420p',
                        '-profile:v', 'high', '-c:a', 'aac', '-b:a', '96k', '-shortest', '-movflags', '+faststart', OUT],
                       stdin=subprocess.PIPE)
n = 0
size = W * H * 3
while True:
    buf = dec.stdout.read(size)
    if len(buf) < size:
        break
    f = np.frombuffer(buf, np.uint8).reshape(H, W, 3).astype(np.int16)
    mx, mn = f.max(2), f.min(2)
    neutral = (mx - mn <= 16) & (mn >= 190)
    lab, k = ndi.label(neutral)                       # 4-vecinos
    border = np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
    border = border[border != 0]
    bg = np.isin(lab, border)
    bg = ndi.binary_dilation(bg, iterations=2)        # se come el halo de cuadritos
    alpha = 1.0 - ndi.gaussian_filter(bg.astype(np.float32), 0.8)
    alpha *= ramp[:, None]
    out = f.astype(np.float32) * alpha[..., None] + BG * (1 - alpha[..., None])
    enc.stdin.write(np.clip(out, 0, 255).astype(np.uint8).tobytes())
    n += 1
enc.stdin.close(); enc.wait(); dec.wait()
print('frames', n)
