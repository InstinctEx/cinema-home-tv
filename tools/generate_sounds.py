#!/usr/bin/env python3
"""Reproduce the two original, synthesized navigation tones. No TV connection."""
import math
from pathlib import Path
import struct
import wave


def main():
    output = Path(__file__).resolve().parent.parent / "launcher/app/src/main/res/raw"
    output.mkdir(parents=True, exist_ok=True)
    for name, frequency in (("nav_move", 620), ("nav_select", 840)):
        count = 1102
        frames = []
        for sample in range(count):
            envelope = math.sin(math.pi * sample / count) ** 2 * math.exp(-4 * sample / count)
            amplitude = int(10000 * envelope * math.sin(2 * math.pi * frequency * sample / 22050))
            frames.append(struct.pack("<h", amplitude))
        with wave.open(str(output / (name + ".wav")), "wb") as tone:
            tone.setnchannels(1)
            tone.setsampwidth(2)
            tone.setframerate(22050)
            tone.writeframes(b"".join(frames))


if __name__ == "__main__":
    main()
