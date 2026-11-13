"""Original 20 s soundtrack for the Calme motion film.

The track is synthesised from scratch with numpy: warm pad, soft pulse,
plucked arpeggio and a handful of synchronised UI sounds. No external
sample or copyrighted music is used.
"""
from __future__ import annotations

import math
import sys
import wave
from pathlib import Path

import numpy as np

SR = 48_000
DURATION = 20.0
N = int(SR * DURATION)
LEFT = np.zeros(N, dtype=np.float64)
RIGHT = np.zeros(N, dtype=np.float64)


def add(signal: np.ndarray, start: float, pan: float = 0.0, gain: float = 1.0) -> None:
    begin = max(0, int(start * SR))
    end = min(N, begin + len(signal))
    if end <= begin:
        return
    chunk = signal[: end - begin] * gain
    pan = max(-1.0, min(1.0, pan))
    left_gain = math.sqrt((1.0 - pan) / 2.0)
    right_gain = math.sqrt((1.0 + pan) / 2.0)
    LEFT[begin:end] += chunk * left_gain
    RIGHT[begin:end] += chunk * right_gain


def adsr(length: int, attack: float, release: float, curve: float = 1.0) -> np.ndarray:
    env = np.ones(length, dtype=np.float64)
    a = min(length, max(1, int(attack * SR)))
    r = min(length, max(1, int(release * SR)))
    env[:a] = np.linspace(0.0, 1.0, a) ** curve
    env[-r:] *= np.linspace(1.0, 0.0, r) ** curve
    return env


def note(freq: float, duration: float, kind: str = "sine", decay: float = 0.6,
         attack: float = 0.01, gain: float = 1.0) -> np.ndarray:
    n = int(duration * SR)
    t = np.arange(n) / SR
    if kind == "sine":
        wave_data = np.sin(2 * math.pi * freq * t)
    elif kind == "soft":
        wave_data = 0.75 * np.sin(2 * math.pi * freq * t) + 0.20 * np.sin(4 * math.pi * freq * t) + 0.05 * np.sin(6 * math.pi * freq * t)
    elif kind == "triangle":
        wave_data = 2.0 / math.pi * np.arcsin(np.sin(2 * math.pi * freq * t))
    else:
        raise ValueError(kind)
    env = np.exp(-t / max(0.03, decay))
    env[: max(1, int(attack * SR))] *= np.linspace(0.0, 1.0, max(1, int(attack * SR)))
    return wave_data * env * gain


def noise(duration: float) -> np.ndarray:
    return np.random.default_rng(42).normal(0.0, 1.0, int(duration * SR))


def kick(duration: float = 0.28, gain: float = 0.22) -> np.ndarray:
    n = int(duration * SR)
    t = np.arange(n) / SR
    freq = 92.0 * np.exp(-t * 18.0) + 42.0
    phase = 2 * math.pi * np.cumsum(freq) / SR
    body = np.sin(phase) * np.exp(-t * 9.0)
    click = noise(duration) * np.exp(-t * 90.0) * 0.18
    return (body + click) * gain


def hat(gain: float = 0.025) -> np.ndarray:
    n = int(0.055 * SR)
    t = np.arange(n) / SR
    nz = noise(0.055)
    nz = np.diff(nz, prepend=0.0)
    return nz * np.exp(-t * 52.0) * gain


def whoosh(duration: float = 0.7, gain: float = 0.075, reverse: bool = False) -> np.ndarray:
    n = int(duration * SR)
    t = np.arange(n) / SR
    nz = noise(duration)
    nz = np.convolve(nz, np.ones(24) / 24, mode="same")
    env = np.sin(np.pi * np.clip(t / duration, 0, 1)) ** 1.8
    if reverse:
        env = env[::-1]
    return nz * env * gain


def pop(gain: float = 0.07) -> np.ndarray:
    n = int(0.16 * SR)
    t = np.arange(n) / SR
    freq = 520.0 * np.exp(-t * 14.0) + 170.0
    phase = 2 * math.pi * np.cumsum(freq) / SR
    return np.sin(phase) * np.exp(-t * 16.0) * gain


def click(gain: float = 0.10) -> np.ndarray:
    n = int(0.06 * SR)
    t = np.arange(n) / SR
    return (np.sin(2 * math.pi * 1700 * t) * 0.5 + noise(0.06) * 0.5) * np.exp(-t * 90.0) * gain


def chime(root: float, gain: float = 0.055) -> np.ndarray:
    out = np.zeros(int(1.7 * SR))
    for index, ratio in enumerate((1.0, 1.25, 1.5, 2.0)):
        tone = note(root * ratio, 1.7, "sine", decay=0.8 - index * 0.1, attack=0.005)
        out += tone * (0.65 ** index)
    return out * gain


def chord_pad(notes: list[float], duration: float, gain: float = 0.026) -> np.ndarray:
    n = int(duration * SR)
    t = np.arange(n) / SR
    out = np.zeros((n, 2))
    for index, freq in enumerate(notes):
        pan = -0.55 + index * 0.28
        detune = 1.0 + (index % 2) * 0.0012
        tone = 0.7 * np.sin(2 * math.pi * freq * detune * t)
        tone += 0.2 * np.sin(2 * math.pi * freq * 2.0 * t)
        tone += 0.08 * np.sin(2 * math.pi * freq * 3.0 * t)
        env = adsr(n, 0.55, 1.35, 1.2)
        lfo = 0.86 + 0.14 * np.sin(2 * math.pi * (0.07 + index * 0.011) * t)
        mono = tone * env * lfo
        out[:, 0] += mono * math.sqrt((1.0 - pan) / 2.0)
        out[:, 1] += mono * math.sqrt((1.0 + pan) / 2.0)
    return out * gain


# Chord bed: one chord every four seconds.
chords = [
    (0.0, 4.0, [110.00, 261.63, 329.63, 392.00, 493.88]),  # Am9
    (4.0, 8.0, [87.31, 220.00, 261.63, 329.63, 392.00]),    # Fmaj9
    (8.0, 12.0, [130.81, 164.81, 196.00, 246.94, 293.66]),  # Cmaj9
    (12.0, 16.0, [98.00, 123.47, 146.83, 164.81, 220.00]),  # G6
    (16.0, 18.0, [110.00, 261.63, 329.63, 392.00, 493.88]), # Am9
    (18.0, 20.0, [87.31, 220.00, 261.63, 329.63, 392.00]),  # Fmaj9
]

pad = np.zeros((N, 2))
for start, end, notes in chords:
    segment = chord_pad(notes, end - start)
    begin = int(start * SR)
    pad[begin:begin + len(segment)] += segment

# Soft sidechain on the pad once the pulse starts.
duck = np.ones(N)
for beat in np.arange(4.0, 16.0, 0.5):
    begin = int(beat * SR)
    length = min(int(0.22 * SR), N - begin)
    duck[begin:begin + length] *= 1.0 - 0.22 * np.exp(-np.arange(length) / (0.08 * SR))
pad *= duck[:, None]
LEFT += pad[:, 0]
RIGHT += pad[:, 1]

# Bass and arpeggio.
for start, end, notes in chords:
    root = notes[0] / 2.0
    for beat in np.arange(start, end, 0.5):
        add(note(root, 0.42, "triangle", decay=0.28, attack=0.008), beat, 0.0, 0.055)
    arp_notes = [notes[1], notes[2], notes[3], notes[4], notes[3], notes[2]]
    for index, beat in enumerate(np.arange(start, end, 0.25)):
        if index % 2 == 0 or end - beat > 0.5:
            add(note(arp_notes[index % len(arp_notes)], 0.22, "sine", decay=0.18, attack=0.006),
                beat, -0.35 if index % 2 else 0.35, 0.034)

# Pulse and percussion.
for beat in np.arange(0.0, 4.0, 1.0):
    add(kick(0.32, 0.17), beat, 0.0)
for beat in np.arange(4.0, 16.0, 0.5):
    add(kick(0.25, 0.20), beat, 0.0)
for beat in np.arange(16.0, 20.0, 0.5):
    add(kick(0.3, 0.18), beat, 0.0)

for beat in np.arange(4.0, 16.0, 0.25):
    if (beat * 2) % 1 == 0.5:
        add(hat(0.032), beat, -0.25 if int(beat * 2) % 2 else 0.25)
for beat in np.arange(4.0, 16.0, 2.0):
    add(noise(0.11) * np.exp(-np.arange(int(0.11 * SR)) / (0.032 * SR)) * 0.028, beat, -0.1)

# Word impacts and the cut into the app.
for index, start in enumerate((0.5, 1.5, 2.5, 3.5)):
    add(note(440.0 * (2 ** (index / 12.0)), 1.1, "soft", decay=0.45, attack=0.006), start, -0.3 + index * 0.2, 0.09)
    if index == 3:
        add(kick(0.5, 0.15), start, 0.0)
add(whoosh(0.8, 0.08), 3.2, 0.0)
add(whoosh(0.7, 0.075), 4.0, 0.0)
add(kick(0.5, 0.16), 4.0, 0.0)

# UI events.
add(pop(0.065), 6.72, 0.2)
for index, start in enumerate(np.arange(7.05, 8.15, 0.16)):
    add(click(0.018), start, 0.25)
add(click(0.11), 8.15, 0.0)
add(pop(0.07), 8.42, -0.2)
add(pop(0.075), 8.9, 0.2)
add(chime(659.25, 0.07), 9.3, -0.15)

add(whoosh(0.7, 0.07), 11.55, 0.0)
for start in (12.05, 12.65, 13.25):
    add(click(0.025), start, -0.2)

add(pop(0.07), 15.35, 0.0)
add(click(0.05), 15.75, 0.2)
add(chime(523.25, 0.045), 15.8, 0.0)

# Final cut and sonic signature.
add(whoosh(0.8, 0.08), 18.0, 0.0)
add(kick(0.6, 0.14), 18.1, 0.0)
add(chime(523.25, 0.065), 19.0, -0.2)
add(chime(659.25, 0.05), 19.18, 0.0)
add(chime(783.99, 0.045), 19.36, 0.2)

# Simple stereo delay on the pad send, then master.
def echo(signal: np.ndarray, delay_s: float, feedback: float) -> np.ndarray:
    out = signal.copy()
    d = int(delay_s * SR)
    for tap in range(1, 4):
        out[d * tap:] += signal[:-d * tap] * (feedback ** tap)
    return out

wet_l = echo(pad[:, 0], 0.28, 0.28) * 0.18
wet_r = echo(pad[:, 1], 0.36, 0.24) * 0.18
LEFT += wet_l
RIGHT += wet_r

# Fade edges and normalise.
fade_in = int(0.25 * SR)
fade_out = int(0.9 * SR)
envelope = np.ones(N)
envelope[:fade_in] = np.linspace(0.0, 1.0, fade_in) ** 2
envelope[-fade_out:] *= np.linspace(1.0, 0.0, fade_out) ** 1.4
LEFT *= envelope
RIGHT *= envelope
peak = max(1e-9, float(np.max(np.abs(np.concatenate([LEFT, RIGHT])))))
LEFT *= 0.88 / peak
RIGHT *= 0.88 / peak

stereo = np.stack([LEFT, RIGHT], axis=1)
pcm = np.clip(stereo * 32767.0, -32768, 32767).astype(np.int16)

output = Path(sys.argv[1] if len(sys.argv) > 1 else "soundtrack.wav")
with wave.open(str(output), "wb") as handle:
    handle.setnchannels(2)
    handle.setsampwidth(2)
    handle.setframerate(SR)
    handle.writeframes(pcm.tobytes())

rms = float(np.sqrt(np.mean(stereo ** 2)))
print(f"wrote {output} — {DURATION:.1f}s — peak {np.max(np.abs(stereo)):.2f} — rms {rms:.3f}")
