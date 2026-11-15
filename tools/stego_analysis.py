"""Reproducible, read-only carrier analysis; writes extracted payloads separately."""
import argparse
import hashlib
import itertools
import json
import struct
import zipfile
import zlib
from pathlib import Path

import numpy as np


def packed(bits):
    return np.packbits(bits[:len(bits) // 8 * 8]).tobytes()


def streams(body, width, height):
    a = np.frombuffer(body, dtype=np.uint8)
    yield 'LSB1', packed(a & 1)
    n = len(a) // 2 * 2
    yield 'LSB4', ((a[:n:2] & 15) << 4 | (a[1:n:2] & 15)).tobytes()
    stride = (width * 3 + 3) // 4 * 4
    selected = a.reshape(abs(height), stride)[:, :width * 3].reshape(-1, 3)[:, :2].reshape(-1)
    skipped = sum(i % stride < width * 3 and (i % stride) % 3 != 2 for i in range(min(4, len(a))))
    selected = selected[skipped:]
    flags = a[:4] & 1
    yield 'LSBI', packed((selected & 1) ^ flags[(selected >> 1) & 3])


def parse_payload(stream):
    if len(stream) < 6:
        return None
    size = int.from_bytes(stream[:4], 'big')
    end = 4 + size
    if end + 2 > len(stream) or stream[end] != 46:
        return None
    nul = stream.find(b'\0', end, end + 256)
    if nul < 0:
        return None
    extension = stream[end:nul]
    if any(x < 32 or x > 126 or x in (47, 92) for x in extension):
        return None
    return stream[4:end], extension.decode('ascii'), nul + 1


def analyze(path, output):
    data = path.read_bytes()
    offset = struct.unpack_from('<I', data, 10)[0]
    dib, width, height = struct.unpack_from('<Iii', data, 14)
    stride = (width * 3 + 3) // 4 * 4
    body_end = offset + stride * abs(height)
    result = dict(source=str(path), bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                  offset=offset, dib=dib, width=width, height=height,
                  trailing_hex=data[body_end:].hex(), candidates=[])
    for method, stream in streams(data[offset:body_end], width, height):
        candidate = dict(method=method, capacity=len(stream), declared_size=int.from_bytes(stream[:4], 'big'),
                         prefix_hex=stream[:32].hex())
        parsed = parse_payload(stream)
        if parsed:
            payload, ext, consumed = parsed
            target = output / (path.stem + '-' + method + ext)
            target.write_bytes(payload)
            candidate.update(extracted=str(target), size=len(payload), extension=ext, consumed=consumed,
                             sha256=hashlib.sha256(payload).hexdigest())
        result['candidates'].append(candidate)
    return result


def solve_mines():
    # Transcription of the recovered PNG: F = flag, ? = covered cell.
    grid = [[1, 1, 1, 1, 3, 'F'], ['F', 3, 2, 'F', 5, 'F'],
            ['F', 'F', 2, 3, 'F', 'F'], [3, 3, '?', 3, '?', 'F'],
            ['?', 2, 0, 3, '?', '?'], ['F', 2, 0, 2, 'F', 'F']]
    flags = {(r, c) for r in range(6) for c in range(6) if grid[r][c] == 'F'}
    unknown = [(r, c) for r in range(6) for c in range(6) if grid[r][c] == '?']
    solutions = []
    for extra in itertools.combinations(unknown, 3):
        mines = flags | set(extra)
        if all(sum((r + dr, c + dc) in mines for dr in (-1, 0, 1) for dc in (-1, 0, 1)
                   if dr or dc) == grid[r][c]
               for r in range(6) for c in range(6) if isinstance(grid[r][c], int)):
            rows = ['01' + ''.join('1' if (r, c) in mines else '0' for c in range(6)) for r in range(6)]
            solutions.append(dict(extra_mines_1based=[(r + 1, c + 1) for r, c in extra],
                                  rows=rows, text=''.join(chr(int(row, 2)) for row in rows)))
    if len(solutions) != 1:
        raise ValueError(f'Expected one Minesweeper solution, got {len(solutions)}')
    return solutions[0]


def solve_group3(inputs, output):
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.primitives.padding import PKCS7
    paths = {p.stem.lower(): p for p in inputs}
    png = output / 'hugo4-LSB4.png'
    # Validate every PNG chunk and locate the actual image end, independently of ZIP parsing.
    raw = png.read_bytes()
    offset = 8
    while True:
        size = int.from_bytes(raw[offset:offset + 4], 'big')
        kind = raw[offset + 4:offset + 8]
        end = offset + 12 + size
        if zlib.crc32(raw[offset + 4:end - 4]) != int.from_bytes(raw[end - 4:end], 'big'):
            raise ValueError('Invalid PNG CRC')
        offset = end
        if kind == b'IEND':
            break
    with zipfile.ZipFile(png) as archive:
        if archive.testzip() is not None or archive.namelist() != ['sol3.txt']:
            raise ValueError('Unexpected or corrupt nested ZIP')
        (output / 'sol3.txt').write_bytes(archive.read('sol3.txt'))
    mines = solve_mines()
    if mines['text'] != 'AesCbc':
        raise ValueError('Unexpected cipher clue')
    titanic = paths['titanic'].read_bytes()
    width, height = struct.unpack_from('<ii', titanic, 18)
    pixel_end = struct.unpack_from('<I', titanic, 10)[0] + (width * 3 + 3) // 4 * 4 * abs(height)
    trailer = titanic[pixel_end:]
    (output / 'titanic-trailer.txt').write_bytes(trailer)
    prefix = b'la password es '
    if not trailer.startswith(prefix):
        raise ValueError('Missing password clue')
    password = trailer[len(prefix):]
    frozen = paths['frozen'].read_bytes()
    width, height = struct.unpack_from('<ii', frozen, 18)
    offset_frozen = struct.unpack_from('<I', frozen, 10)[0]
    stream = next(streams(frozen[offset_frozen:], width, height))[1]
    size = int.from_bytes(stream[:4], 'big')
    ciphertext = stream[4:4 + size]
    if len(ciphertext) != size:
        raise ValueError('Truncated ciphertext')
    material = hashlib.pbkdf2_hmac('sha256', password, bytes(8), 10000, 32)
    decryptor = Cipher(algorithms.AES(material[:16]), modes.CBC(material[16:])).decryptor()
    padded = decryptor.update(ciphertext) + decryptor.finalize()
    unpadder = PKCS7(128).unpadder()
    plaintext = unpadder.update(padded) + unpadder.finalize()
    parsed = parse_payload(plaintext)
    if parsed is None or parsed[2] != len(plaintext) or parsed[1] != '.wmv':
        raise ValueError('Decrypted payload is invalid')
    content, extension, consumed = parsed
    if content[:16] != bytes.fromhex('3026b2758e66cf11a6d900aa0062ce6c'):
        raise ValueError('Video is not ASF/WMV')
    target = output / ('frozen-LSB1' + extension)
    target.write_bytes(content)
    result = dict(png_end=offset, png_trailing_bytes=len(raw) - offset,
                  zip_local_header_offset=raw.find(b'PK\x03\x04', offset),
                  mines=mines, titanic_pixel_end=pixel_end, titanic_trailer=trailer.decode('ascii'),
                  cipher='AES-128-CBC', password=password.decode('ascii'),
                  ciphertext_size=size, plaintext_size=len(plaintext), padding_bytes=len(padded) - len(plaintext),
                  extracted=str(target), video_size=len(content), video_sha256=hashlib.sha256(content).hexdigest())
    (output / 'solution.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('inputs', nargs='+', type=Path)
    parser.add_argument('--output', type=Path, default=Path('output/stegoanalysis'))
    parser.add_argument('--solve-group3', action='store_true', help='Follow the validated nested clues for these four samples')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    results = [analyze(p, args.output) for p in args.inputs]
    (args.output / 'initial-analysis.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    print(json.dumps(results, indent=2))
    if args.solve_group3:
        solve_group3(args.inputs, args.output)
