#!/usr/bin/env python3
"""Download a pinned real speech model and a speech sample for the Android JNI smoke test."""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import sys
import time
import urllib.request
import wave
import array

root = Path(sys.argv[1])
root.mkdir(parents=True, exist_ok=True)
catalog = json.loads(Path('speech/src/main/assets/sherpa_stt_catalog.json').read_text())
model = next(m for m in catalog['models'] if m['id'] == 'sherpa-moonshine-tiny-en-quantized-2026-02-27')
files = [(root / model['files'][role], spec['url'], spec['sha256'], spec['sizeBytes'])
         for role, spec in model['downloads'].items()]
streaming = next(m for m in catalog['models'] if m['id'] == 'sherpa-streaming-zipformer-en-20m-2023-02-17')
(root / 'streaming').mkdir(exist_ok=True)
files += [(root / 'streaming' / streaming['files'][role], spec['url'], spec['sha256'], spec['sizeBytes'])
          for role, spec in streaming['downloads'].items()]
audio_url = model['downloads']['encoder']['url'].rsplit('/', 1)[0] + '/test_wavs/0.wav'
files.append((root / 'source.wav', audio_url,
              '148b936b43ce7c546a866e64da059f0458aee2d65e617f16e9d94f06e8d99ed6', 184608))

def fetch(item):
    destination, url, expected_hash, expected_size = item
    for attempt in range(3):
        try:
            digest = hashlib.sha256()
            partial = destination.with_suffix(destination.suffix + '.part')
            with urllib.request.urlopen(url, timeout=90) as response, partial.open('wb') as output:
                while data := response.read(256 * 1024):
                    digest.update(data)
                    output.write(data)
            assert partial.stat().st_size == expected_size, destination.name
            assert digest.hexdigest() == expected_hash, destination.name
            partial.replace(destination)
            return
        except Exception:
            if attempt == 2:
                raise
            time.sleep(2)

with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(fetch, files))
with wave.open(str(root / 'source.wav')) as audio:
    assert audio.getnchannels() == 1 and audio.getsampwidth() == 2
    rate = audio.getframerate()
    pcm = array.array('h', audio.readframes(audio.getnframes()))
    if sys.byteorder != 'little':
        pcm.byteswap()
# The upstream fixture is 24 kHz; the Android microphone/runtime contract is 16 kHz.
resampled = array.array('h')
for index in range(len(pcm) * 16000 // rate):
    position = index * rate / 16000
    lower = int(position)
    fraction = position - lower
    resampled.append(round(pcm[lower] * (1 - fraction) + pcm[min(lower + 1, len(pcm) - 1)] * fraction))
if sys.byteorder != 'little':
    resampled.byteswap()
with wave.open(str(root / 'speech.wav'), 'wb') as audio:
    audio.setnchannels(1)
    audio.setsampwidth(2)
    audio.setframerate(16000)
    audio.writeframes(resampled.tobytes())
print('Verified Moonshine Tiny, streaming Zipformer 20M, and real 16 kHz speech fixture')
