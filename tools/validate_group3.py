"""Verify sample extraction and the section 7.II experiment with the packaged Java CLI."""
import argparse
import hashlib
import json
import math
import struct
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('samples', type=Path)
    parser.add_argument('--java', default='java')
    parser.add_argument('--jar', type=Path, default=Path('target/stegobmp-1.0.0-SNAPSHOT.jar'))
    parser.add_argument('--output', type=Path, default=Path('output/stegoanalysis'))
    args = parser.parse_args()
    root = args.output
    dest = root / 'java-validation'
    dest.mkdir(parents=True, exist_ok=True)
    commands = []

    def run(*options):
        command = [args.java, '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', str(args.jar), *map(str, options)]
        result = subprocess.run(command, capture_output=True, encoding='utf-8')
        commands.append(dict(command=command, exit_code=result.returncode, stderr=result.stderr))
        if result.returncode:
            raise RuntimeError(result.stderr)

    for name, method, ext, extra in [
        ('roma', 'LSBI', '.pdf', []), ('hugo4', 'LSB4', '.png', []),
        ('frozen', 'LSB1', '.wmv', ['-a', 'aes128', '-m', 'cbc', '-pass', 'exitoso'])
    ]:
        run('-extract', '-p', args.samples / (name + '.bmp'), '-out', dest / name, '-steg', method, *extra)
        if (dest / (name + ext)).read_bytes() != (root / (name + '-' + method + ext)).read_bytes():
            raise ValueError(f'Java and independent extraction differ: {name}')

    carrier = Path('docs/docs_catedra/ejemplo/lado.bmp')
    original = carrier.read_bytes()
    offset = struct.unpack_from('<I', original, 10)[0]
    width, height = struct.unpack_from('<ii', original, 18)
    stride = (width * 3 + 3) // 4 * 4
    body_size = stride * abs(height)
    old = np.frombuffer(original[offset:offset + body_size], dtype=np.uint8)
    old_pixels = old.reshape(abs(height), stride)[:, :width * 3].astype(np.int16)
    hidden = root / 'hugo4-LSB4.png'
    experiment = []
    for method in ('LSB1', 'LSB4', 'LSBI'):
        target = dest / ('comparison-' + method + '.bmp')
        run('-embed', '-in', hidden, '-p', carrier, '-out', target, '-steg', method)
        recovered = dest / ('comparison-' + method)
        run('-extract', '-p', target, '-out', recovered, '-steg', method)
        if recovered.with_suffix('.png').read_bytes() != hidden.read_bytes():
            raise ValueError(f'Round-trip content differs: {method}')
        stego = target.read_bytes()
        if original[:offset] != stego[:offset] or original[offset + body_size:] != stego[offset + body_size:]:
            raise ValueError(f'Non-pixel BMP bytes were changed: {method}')
        new = np.frombuffer(stego[offset:offset + body_size], dtype=np.uint8)
        new_pixels = new.reshape(abs(height), stride)[:, :width * 3].astype(np.int16)
        delta = new_pixels - old_pixels
        mse = float(np.mean(delta.astype(np.float64) ** 2))
        changes = int(np.count_nonzero(delta))
        positions = np.arange(body_size)
        columns = positions % stride
        selected = positions[(positions >= 4) & ((columns >= width * 3) | (columns % 3 != 2))]
        capacity = body_size // (2 if method == 'LSB4' else 8) if method != 'LSBI' else len(selected) // 8
        row = dict(method=method, capacity_bytes=capacity, stream_bytes=hidden.stat().st_size + 9,
                   changed_components=changes, mse=mse, psnr_db=10 * math.log10(255 ** 2 / mse),
                   max_absolute_change=int(np.max(np.abs(delta))), output_sha256=hashlib.sha256(stego).hexdigest(),
                   round_trip_equal=True)
        if method == 'LSBI':
            payload = len(hidden.read_bytes()).to_bytes(4, 'big') + hidden.read_bytes() + b'.png\0'
            bits = np.unpackbits(np.frombuffer(payload, dtype=np.uint8))
            used = selected[:len(bits)]
            standard_changes = int(np.count_nonzero((old[used] & 1) != bits))
            row.update(same_bg_without_inversion_changes=standard_changes,
                       lsbi_payload_changes=int(np.count_nonzero(old[used] != new[used])),
                       flag_changes=int(np.count_nonzero(old[:4] != new[:4])),
                       flags=[int(v & 1) for v in new[:4]])
        experiment.append(row)
        # Visible error map: black = unchanged, brighter = larger modification (amplified x16).
        visual = np.clip(np.abs(delta).reshape(abs(height), width, 3)[:, :, ::-1] * 16, 0, 255).astype(np.uint8)
        Image.fromarray(visual[::-1] if height > 0 else visual).save(dest / ('difference-' + method + '.png'))
    (root / 'comparison.json').write_text(json.dumps(experiment, indent=2), encoding='utf-8')
    (root / 'java-validation.json').write_text(json.dumps(commands, indent=2), encoding='utf-8')
    print(json.dumps(experiment, indent=2))


if __name__ == '__main__':
    main()
