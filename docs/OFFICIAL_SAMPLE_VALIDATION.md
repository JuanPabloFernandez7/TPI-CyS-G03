# Validación con los ejemplos oficiales de la cátedra

## Alcance

Se usaron los siete BMP y `README.txt` provistos en `docs/ejemplo/`. Los originales se leyeron únicamente como entradas. Las seis extracciones se guardaron bajo `demo/official-validation-2026-11-12/`.

El README identifica `lado.bmp` como portador y confirma que cada una de las otras seis imágenes contiene una imagen PNG. Para los casos cifrados se usó la contraseña `margarita` y el algoritmo/modo especificados allí.

## Inspección BMP

Se verificaron los campos del encabezado y la longitud de los siete archivos:

| Propiedad | Valor |
|---|---:|
| Firma | `BM` |
| Tamaño de archivo | 921654 bytes |
| Offset de píxeles | 54 |
| Tamaño DIB | 40 (BITMAPINFOHEADER) |
| Dimensiones | 640 × 480 |
| Planos / profundidad | 1 / 24 bits |
| Compresión | 0 (`BI_RGB`, sin compresión) |
| Stride / cuerpo | 1920 / 921600 bytes |
| Padding, gap, trailing | ninguno |

Estos archivos cubren el formato BMP admitido, pero no ejercitan offset extendido, padding, altura negativa ni bytes trailing.

SHA-256 de los BMP originales (lectura inicial y comprobación posterior idénticas):

| Archivo | SHA-256 |
|---|---|
| `lado.bmp` | `fee3bdabe02a5d7a178955864106a923dace797bc45a642c5bfd54127b742884` |
| `ladoLSB1.bmp` | `5e08c795cca819d251c43fbd90836ffdbe168c1861ff77536f8f7f0177547a0c` |
| `ladoLSB1aes128cbc.bmp` | `e2bfee1295a4e83f8dc78bffbc33cb4c6b933d5c9b43c7341293c81ac8c64018` |
| `ladoLSB4.bmp` | `8e39d2e610abe3515ccf784f9c8c2f775fa59de67e1ae8c00915b9c8c7d31194` |
| `ladoLSBI.bmp` | `701b0eb568f5c864c203d3baf6d4162d3b00002e9f7af4a65652cda498e177c2` |
| `ladoLSBIaes256ofb.bmp` | `a5e48c2452bd5e16ebbb33b427550f49e3df009aa549999ddac21a52af1a2bd5` |
| `ladoLSBIdescfb.bmp` | `bd09b0436fd88d7a39f2fedcb97c935aa0212deff17a1afe6f8a69b0627cf154` |

## Extracciones y validación de PNG

| BMP oficial | Esteganografía | Cifrado | Resultado | PNG |
|---|---|---|---|---|
| `ladoLSB1.bmp` | LSB1 | ninguno | extraído | válido, 2480 × 1219, RGBA de 8 bits |
| `ladoLSB4.bmp` | LSB4 | ninguno | extraído | válido, 2480 × 1219, RGBA de 8 bits |
| `ladoLSBI.bmp` | LSBI | ninguno | extraído | válido, 2480 × 1219, RGBA de 8 bits |
| `ladoLSB1aes128cbc.bmp` | LSB1 | AES-128-CBC | extraído | válido, 2480 × 1219, RGBA de 8 bits |
| `ladoLSBIaes256ofb.bmp` | LSBI | AES-256-OFB | extraído | válido, 2480 × 1219, RGBA de 8 bits |
| `ladoLSBIdescfb.bmp` | LSBI | 3DES-CFB8 | extraído | válido, 2480 × 1219, RGBA de 8 bits |

Las seis salidas tienen 44886 bytes y son **idénticas byte por byte**, SHA-256:

```text
b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d
```

La validación fue más allá de la firma: se comprobaron límites y CRC de todos los chunks, `IHDR`, `IEND`, descompresión del flujo `IDAT`, tamaño de los datos de scanline y filtros PNG válidos. Además, `sips`/ImageIO decodificó cada imagen y reportó formato PNG y dimensiones 2480 × 1219.

## Comandos ejecutados

Todos se ejecutaron con el JAR empaquetado y Java 21; `-out` apunta a la carpeta de resultados, no a los originales:

```sh
J=target/stegobmp-1.0.0-SNAPSHOT.jar
SRC=docs/ejemplo
OUT=demo/official-validation-2026-11-12

java -jar "$J" -extract -p "$SRC/ladoLSB1.bmp" \
  -out "$OUT/ladoLSB1" -steg LSB1
java -jar "$J" -extract -p "$SRC/ladoLSB4.bmp" \
  -out "$OUT/ladoLSB4" -steg LSB4
java -jar "$J" -extract -p "$SRC/ladoLSBI.bmp" \
  -out "$OUT/ladoLSBI" -steg LSBI
java -jar "$J" -extract -p "$SRC/ladoLSB1aes128cbc.bmp" \
  -out "$OUT/ladoLSB1aes128cbc" -steg LSB1 \
  -a aes128 -m cbc -pass margarita
java -jar "$J" -extract -p "$SRC/ladoLSBIaes256ofb.bmp" \
  -out "$OUT/ladoLSBIaes256ofb" -steg LSBI \
  -a aes256 -m ofb -pass margarita
java -jar "$J" -extract -p "$SRC/ladoLSBIdescfb.bmp" \
  -out "$OUT/ladoLSBIdescfb" -steg LSBI \
  -a 3des -m cfb -pass margarita
```

## Decisiones confirmadas y límites

Las seis extracciones recuperaron exactamente el mismo PNG esperado por el README. En particular:

- **LSB4:** el orden implementado (nibble alto primero) es compatible con el ejemplo oficial `ladoLSB4.bmp`.
- **LSBI:** la variante implementada (payload B/G en orden físico B→G, flags en los primeros cuatro bytes y reconstrucción MSB-first) es compatible con `ladoLSBI.bmp`, `ladoLSBIaes256ofb.bmp` y `ladoLSBIdescfb.bmp`.
- **Criptografía:** los parámetros de derivación documentados en el README son compatibles en AES-128-CBC, AES-256-OFB y 3DES-CFB8. El README además proporciona los bytes derivados esperados para esos casos; el éxito de descifrado y la igualdad con el PNG de los demás ejemplos corroboran la derivación, IV, modo, framing y parsing combinados.
- **Payload y extensiones:** las seis rutas recuperaron archivos `.png` válidos con el mismo contenido.
- **BMP:** los siete ejemplos son compatibles con `BmpReader`; los hashes de los BMP fueron los mismos al inspeccionarlos antes y después de las extracciones.

No se detectó una incompatibilidad; por eso no se modificó código ni se agregó un test de corrección. La validación no prueba los otros modos criptográficos contra archivos oficiales, ni confirma todos los aspectos de la consigna, el estegoanálisis solicitado o la ejecución en Pampero. La etapa de estegoanálisis y la validación final se mantienen abiertas hasta completar esos requisitos.
