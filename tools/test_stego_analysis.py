import unittest
import zlib

from stego_analysis import png_end_offset, streams


class LsbiPaddingTest(unittest.TestCase):
    def test_padding_is_used_in_physical_order(self):
        cases = [
            (1, [4, 5, 7, 8, 9, 11, 12, 13]),
            (2, [4, 6, 7, 8, 9, 11, 12, 14]),
            (3, [4, 6, 7, 9, 10, 11, 12, 13]),
        ]
        for width, positions in cases:
            with self.subTest(width=width):
                stride = (width * 3 + 3) // 4 * 4
                body = bytearray(stride * 8)
                for position in (positions[0], positions[2], positions[5], positions[7]):
                    body[position] = 1
                extracted = dict(streams(body, width, 8))['LSBI']
                self.assertEqual(0xA5, extracted[0])

    def test_padding_in_reserved_prefix_is_a_flag(self):
        body = bytearray(4 * 5)
        body[3] = 1
        positions = [4, 5, 7, 8, 9, 11, 12, 13]
        for position, bit in zip(positions, [1, 0, 1, 0, 0, 1, 0, 1]):
            body[position] = 6 | (bit ^ 1)
        self.assertEqual(0xA5, dict(streams(body, 1, 5))['LSBI'][0])


class PngEndTest(unittest.TestCase):
    def test_end_and_invalid_chunks(self):
        iend = b'\0\0\0\0IEND' + zlib.crc32(b'IEND').to_bytes(4, 'big')
        png = b'\x89PNG\r\n\x1a\n' + iend
        self.assertEqual(len(png), png_end_offset(png + b'ZIP'))
        for invalid in [b'not a PNG', png[:-1], png[:8], png[:-1] + b'\0']:
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    png_end_offset(invalid)


if __name__ == '__main__':
    unittest.main()
