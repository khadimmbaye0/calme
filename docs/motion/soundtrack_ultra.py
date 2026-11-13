"""24 s soundtrack for the Calme film.

The first four seconds accompany the image-reading scene; the existing
twenty-second track is then crossfaded in. Original synthesis, no samples.
"""
from __future__ import annotations

import math
import sys
import wave
from pathlib import Path

import numpy as np

SR = 48_000
INTRO = 4.0
INTRO_N = int(SR * INTRO)
INTRO_L = np.zeros(INTRO_N)
INTRO_R = np.zeros(INTRO_N)


def add(signal: np.ndarray, start: float, pan: float = 0.0, gain: float = 1.0) -> None:
    begin = max(0, int(start * SR))
    end = min(INTRO_N, begin + len(signal))
    if end <= begin:
        return
    chunk = signal[: end - begin] * gain
    pan = max(-1.0, min(1.0, pan))
    INTRO_L[begin:end] += chunk * math.sqrt((1.0 - pan) / 2.0)
    INTRO_R[begin:end] += chunk * math.sqrt((1.0 + pan) / 2.0)


def note(freq: float, duration: float, decay: float = 0.8, attack: float = 0.01, kind: str = 'sine') -> np.ndarray:
    n = int(duration * SR)
    t = np.arange(n) / SR
    if kind == 'soft':
        wave_data = 0.72 * np.sin(2 * math.pi * freq * t) + 0.22 * np.sin(4 * math.pi * freq * t) + 0.06 * np.sin(6 * math.pi * freq * t)
    else:
        wave_data = np.sin(2 * math.pi * freq * t)
    env = np.exp(-t / max(0.05, decay))
    a = max(1, int(attack * SR))
    env[:a] *= np.linspace(0.0, 1.0, a)
    return wave_data * env


def noise(duration: float) -> np.ndarray:
    return np.random.default_rng(7).normal(0.0, 1.0, int(duration * SR))


def whoosh(duration: float = 0.7, gain: float = 0.07) -> np.ndarray:
    n = int(duration * SR)
    t = np.arange(n) / SR
    nz = np.convolve(noise(duration), np.ones(20) / 20, mode='same')
    env = np.sin(np.pi * np.clip(t / duration, 0, 1)) ** 1.7
    return nz * env * gain


def click(gain: float = 0.08) -> np.ndarray:
    n = int(0.1 * SR)
    t = np.arange(n) / SR
    return (np.sin(2 * math.pi * 1200 * t) + 0.5 * noise(0.1)) * np.exp(-t * 48) * gain


def chime(freq: float, gain: float = 0.05) -> np.ndarray:
    n = int(1.4 * SR)
    t = np.arange(n) / SR
    return (np.sin(2 * math.pi * freq * t) * 0.7 + np.sin(4 * math.pi * freq * t) * 0.2) * np.exp(-t * 1.4) * gain


def pad(notes: list[float], duration: float, gain: float = 0.024) -> None:
    n = int(duration * SR)
    t = np.arange(n) / SR
    env = np.ones(n)
    attack = int(0.55 * SR)
    release = int(0.7 * SR)
    env[:attack] = np.linspace(0.0, 1.0, attack) ** 1.2
    env[-release:] *= np.linspace(1.0, 0.0, release) ** 1.4
    for index, freq in enumerate(notes):
        pan = -0.55 + index * 0.30
        tone = 0.7 * np.sin(2 * math.pi * freq * t) + 0.2 * np.sin(4 * math.pi * freq * t)
        mono = tone * env * gain
        add(mono, 0.0, pan, 1.0)


pad([110.00, 261.63, 329.63, 392.00], 4.0)
# Shutter / image arrival.
add(click(0.10), 0.55, 0.15)
add(note(180, 0.5, 0.35, 0.005, 'soft'), 0.55, 0.0, 0.08)
# Scanning sweep and row ticks.
add(whoosh(0.9, 0.065), 1.35, -0.1)
for index, start in enumerate((1.65, 1.95, 2.25)):
    add(note(520 + index * 80, 0.25, 0.18, 0.003), start, -0.2 + index * 0.2, 0.055)
# Read confirmation and a soft lift into the manifesto.
add(chime(659.25, 0.055), 2.32, -0.15)
add(chime(783.99, 0.04), 2.52, 0.15)
add(whoosh(0.8, 0.055), 3.15, 0.1)
add(note(110, 1.2, 0.9, 0.02, 'soft'), 3.2, 0.0, 0.07)


def read_wav(path: Path) -> np.ndarray:
    with wave.open(str(path), 'rb') as handle:
        assert handle.getnchannels() == 2
        assert handle.getsampwidth() == 2
        data = np.frombuffer(handle.readframes(handle.getnframes()), dtype=np.int16)
    return data.reshape(-1, 2).astype(np.float64) / 32768.0


def main() -> None:
    base_path = Path(sys.argv[1] if len(sys.argv) > 1 else '/tmp/calme-base.wav')
    output = Path(sys.argv[2] if len(sys.argv) > 2 else 'soundtrack-ultra.wav')
    base = read_wav(base_path)
    intro = np.stack([INTRO_L, INTRO_R], axis=1)
    overlap = int(0.18 * SR)
    intro[-overlap:] *= np.linspace(1.0, 0.0, overlap)[:, None] ** 0.7
    base[:overlap] *= np.linspace(0.0, 1.0, overlap)[:, None] ** 0.7
    mix = np.concatenate([intro[:-overlap], intro[-overlap:] + base[:overlap], base[overlap:]], axis=0)
    target = int(24.0 * SR)
    if len(mix) < target:
        mix = np.pad(mix, ((0, target - len(mix)), (0, 0)))
    else:
        mix = mix[:target]

    fade_in = int(0.2 * SR)
    fade_out = int(0.8 * SR)
    mix[:fade_in] *= np.linspace(0.0, 1.0, fade_in)[:, None]
    mix[-fade_out:] *= np.linspace(1.0, 0.0, fade_out)[:, None] ** 1.2
    peak = max(1e-9, float(np.max(np.abs(mix))))
    mix *= 0.88 / peak
    pcm = np.clip(mix * 32767.0, -32768, 32767).astype(np.int16)
    with wave.open(str(output), 'wb') as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(SR)
        handle.writeframes(pcm.tobytes())
    print(f'wrote {output} — {len(mix) / SR:.1f}s — peak {np.max(np.abs(mix)):.2f}')


if __name__ == '__main__':
    main()
