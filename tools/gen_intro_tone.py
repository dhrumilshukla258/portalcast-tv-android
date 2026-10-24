# Generates the intro's tone (res/raw/intro_tone.wav, and the web app's public/intro-tone.wav):
# an original, quiet 1.9 s sound that follows the intro's animation (tools/gen_intro_avd.py):
#   0 ms      a soft low swell as the P fades and zooms in
#   300/440   two small chimes as the signal arcs draw (inner, then outer)
#   740       a warm chord as "ortalcast" comes out, ringing through the hold
#   1580      everything fades with the picture (gone at 1840)
# Plain Python (no numpy). Usage: gen_intro_tone.py OUT.wav
import math, struct, sys, wave, random

RATE = 32000
LENGTH = 1.9
N = int(RATE * LENGTH)
out = [0.0] * N
random.seed(7)

def tone(start, freq, dur, amp, attack=0.01, decay=3.0, harmonics=((1, 1.0), (2, 0.25), (3, 0.08)), detune=0.0):
    """A sine voice with a few harmonics, a short attack and an exponential decay."""
    s0 = int(start * RATE)
    for i in range(int(dur * RATE)):
        n = s0 + i
        if n >= N: break
        t = i / RATE
        env = (t / attack if t < attack else 1.0) * math.exp(-decay * t)
        v = 0.0
        for h, a in harmonics:
            v += a * math.sin(2 * math.pi * freq * h * t)
            if detune: v += a * 0.6 * math.sin(2 * math.pi * freq * h * (1 + detune) * t)
        out[n] += amp * env * v

def swell(start, freq, dur, amp):
    """A low pad that rises and falls (the P appearing)."""
    s0 = int(start * RATE)
    for i in range(int(dur * RATE)):
        n = s0 + i
        if n >= N: break
        t = i / RATE
        env = math.sin(math.pi * min(1.0, t / dur)) ** 2
        out[n] += amp * env * (math.sin(2 * math.pi * freq * t) + 0.3 * math.sin(2 * math.pi * freq * 2 * t))

# The P: a low swell (D2/A2) under a soft body.
swell(0.00, 73.42, 0.9, 0.18)
swell(0.02, 110.0, 0.8, 0.10)
# The arcs: two small chimes, the outer one higher and quieter (its arc is 55% opaque).
tone(0.30, 1174.66, 0.9, 0.07, attack=0.004, decay=6.0, harmonics=((1, 1.0), (2.76, 0.12)))
tone(0.44, 1479.98, 0.9, 0.05, attack=0.004, decay=6.5, harmonics=((1, 1.0), (2.76, 0.12)))
# "ortalcast": a warm D major chord, slightly detuned for width, ringing through the hold.
for f, a in ((146.83, 0.10), (220.0, 0.08), (293.66, 0.08), (369.99, 0.06), (440.0, 0.05), (587.33, 0.035)):
    tone(0.74, f, 1.2, a, attack=0.05, decay=1.6, detune=0.0025)

# The fade with the picture (1580-1840 ms), and a peak at -12 dBFS.
for n in range(N):
    t = n / RATE
    if t > 1.58: out[n] *= max(0.0, 1 - (t - 1.58) / 0.26) ** 1.5
peak = max(abs(v) for v in out) or 1.0
gain = 10 ** (-12 / 20) / peak
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
    w.writeframes(b''.join(struct.pack('<h', int(max(-1.0, min(1.0, v * gain)) * 32767)) for v in out))
print('wrote', sys.argv[1], N, 'samples')
