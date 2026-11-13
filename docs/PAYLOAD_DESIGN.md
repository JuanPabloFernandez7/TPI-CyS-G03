# Diseño técnico de Payload

Estado: **ajustes del usuario aplicados; implementado en el paquete `payload`.**

Referencias: «Consigna» = `Trabajo Practico Implementacion-2026_2.pdf` (se cita sección y página); LSB1 = [`LSB1_DESIGN.md`](LSB1_DESIGN.md); decisión previa = D-003 en [`TECHNICAL_DECISIONS.md`](TECHNICAL_DECISIONS.md).

## 1. Lo que dice la consigna (base del diseño)

| Ref. | Texto | Dónde | Consecuencia |
|---|---|---|---|
| P1 | «Oculte un archivo cualquiera en un archivo de extensión “.bmp”» | §3, p. 1 | No se puede rechazar un archivo por su contenido, tamaño (salvo capacidad) ni por no tener extensión sin dejarlo documentado. |
| P2 | Sin cifrado: «Tamaño real \|\| datos archivo \|\| extensión»; total `4 + longitud archivo + e` | §5.3, p. 3 | El flujo oculto sin cifrado es exactamente esa estructura. |
| P3 | «La extensión debe comenzar con ‘.’ y terminar con ‘\0’.» Ejemplos: «.png», «.jpg», «.txt», «.html» | §5.3, p. 3 | `e` = longitud de la extensión incluidos el punto y el NUL (p. ej. `.txt\0` → 5). |
| P4 | «Siempre se sabe que los primeros 4 bytes (DWORD size) corresponden al tamaño de archivo.» «Siempre se sabe que después de los n bytes del archivo viene un punto y los n ascii de la extensión, terminando en ‘\0’.» | §5.3, p. 3 | Lectura secuencial: 4 bytes, `n` bytes, y luego bytes hasta el NUL. La extensión es ASCII. |
| P5 | Con cifrado: «Tamaño cifrado \|\| encripcion(tamaño real \|\| datos archivo \|\| extensión)»; total `4 + tamaño del cifrado` | §5.3, p. 3–4 | Flujo = prefijo de 4 bytes con la longitud del ciphertext + ciphertext. El plaintext es la misma estructura de P2. |
| P6 | «Una vez hecha la desencripción se obtiene el tamaño real del archivo, se lee esa cantidad de bytes y se toma la extensión (hasta el ‘\0’).» | §5.3, p. 4 | Tras descifrar se vuelve a aplicar la lectura de P2 sobre el plaintext. |
| P7 | Ejemplo: `saco.txt` de 414 bytes con 3DES-CBC: se encripta `414 \|\| datos \|\| ".txt\0"` = 4 + 414 + 5 = 423 bytes; «requiere padding»; se oculta `(423+padding) \|\| encripcion(...)`, o sea `4 + (423 + padding)` bytes. | §5.3, p. 4 | Vector de prueba oficial: tamaño cifrado ≠ tamaño real. **Corrección (verificada con OpenSSL 3.6, ver `CRYPTO_DESIGN.md`):** con PKCS5, 423 bytes dan **432 en AES-CBC/ECB (bloque 16)** y **424 en 3DES-CBC/ECB (bloque 8)**; la consigna no da la cifra. |
| P8 | «Los archivos que les entregaremos tendrán el tamaño guardado en forma Big Endian.» 17.118 → `00 00 42 DE` | §5.4, p. 5 | Campo de tamaño en Big Endian explícito. |
| P9 | «El archivo que se encripta es el que se quiere ocultar. Se encripta completo. Es decir, aún si fuera un bmp, se encripta desde el byte 0 hasta el último, cabecera incluida.» | §5.4, p. 5 | `data` son los bytes del archivo sin tocar; ninguna interpretación de su formato. |
| P10 | Si requiere padding, «el padding por defecto de openssl que es PKCS5» | §5.4, p. 5 | El padding pertenece a la capa criptográfica; Payload no lo ve. |
| P11 | Ejemplo de extracción: `-extract … -out "mensaje1"` para recuperar `mensaje1.txt` | §5.2, p. 3 | Indica que `-out` no lleva extensión y que el programa añade la recuperada (ver A2). |
| P12 | Los archivos de la cátedra con LSB1/LSB4/LSBI siguen «tamaño + archivo + extensión». | §6, p. 6 | Se validaron seis ejemplos oficiales; alcance y evidencia en [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md). |
| P13 | «El programa debe explicitar errores.» | §10, p. 7 | Mensajes específicos para cada fallo del payload. |

## 2. Ambigüedades y decisiones provisionales

**A1 — Archivos sin extensión (punto 1 del pedido).** P3 exige que la extensión empiece con `.` y termine con NUL, y P1 pide aceptar «un archivo cualquiera», pero la consigna no dice qué escribir si el archivo no tiene extensión.

Alternativas:
- (a) Rechazar el archivo. Agrega una restricción que la consigna no pide.
- (b) Escribir solo `\0`. Viola «debe comenzar con ‘.’».
- (c) Escribir `.\0`: un punto seguido del terminador. Cumple literalmente P3 y se puede deshacer sin ambigüedad.

**Decisión provisional: (c).** Una extensión vacía se codifica como `2E 00` (`e = 2`).
- Al extraer, `.\0` significa «sin extensión»: el archivo se escribe con el nombre de `-out` tal cual.
- El lector es estricto: un NUL en la primera posición de la extensión se rechaza como extensión mal formada.
- Si una muestra real de la cátedra contradice esto, cambia una sola línea (aceptar `\0` solo).

**A2 — Cómo se combina `-out` con la extensión recuperada (P11).** El ejemplo sugiere `ruta = -out + extensión`.

**Decisión provisional (para la etapa de integración, no se implementa ahora):** concatenar siempre, sin detectar si `-out` ya termina igual. `FileExtension.appendTo(String)` ofrece esa operación. Queda anotada para revisarla en integración.

**A3 — Qué cuenta como «extensión» al ocultar.** La consigna da ejemplos pero no una regla.

**Decisión provisional:**
- La extensión es el sufijo del nombre de archivo (sin directorios) desde el **último** `.`. Es la regla de `strrchr(nombre, '.')`, probablemente la usada por la cátedra.
- Si no hay `.`, o el `.` es el último carácter, no hay extensión (A1).
- Un archivo oculto como `.bashrc` tiene extensión `.bashrc`, sin tratamiento especial.
- Se conservan las mayúsculas y minúsculas. `a.tar.gz` tiene extensión `.gz`.
- Alfabeto permitido (ajuste del usuario): ASCII imprimible `0x20–0x7E` salvo `/` y `\`. Se prohíben el NUL, los controles, los bytes no ASCII y los separadores de ruta. Se admite el espacio (`.tar gz` es válida). Razones:
  - P4 dice «ascii».
  - Los separadores evitarían una ruta de salida inesperada al concatenar (A2).
- Una extensión fuera de ese alfabeto, por ejemplo `.café`, **se rechaza al ocultar con un error claro**, en lugar de truncarse. No hay límite de longitud adicional a la capacidad del portador.

**A4 — Signo del campo de tamaño.** «DWORD» es un entero de 32 bits sin signo.

**Decisión provisional:**
- Al leer se interpreta como sin signo en un `long`.
- Se acepta solo `0 ≤ valor ≤ 2³¹−1`, porque en Java el archivo debe caber en un `byte[]`. Cualquier valor mayor se informa como «tamaño inválido».
- En la práctica un valor así también excede cualquier portador razonable.

**A5 — Bytes sobrantes tras el NUL (ajuste del usuario).** La consigna no dice nada. Se distinguen dos situaciones:

**Decisión provisional:**
- `readFile(ByteSource)` (lectura sobre el portador) se **detiene en el NUL** e ignora el resto de los bytes del portador: el cuerpo siempre tiene más capacidad que el mensaje, así que lo que sigue no pertenece al payload.
- `parseFile(byte[] plaintext)` (plaintext descifrado, que mide exactamente lo que se cifró) es **estricto**: cualquier byte posterior al NUL se rechaza con `TRAILING_BYTES`. Ayuda a detectar password, algoritmo o modo incorrectos.
- Si una muestra real de la cátedra contradice la versión estricta, el cambio es una sola comprobación.

**A6 — Tamaño del cifrado.** Es solo la longitud del ciphertext (P5). Que sea múltiplo del bloque (ECB/CBC) o igual al plaintext (CFB/OFB) lo valida la capa criptográfica, no Payload.
- Ya está registrada como A4 en `LSB1_DESIGN.md`: PKCS5 frente a no relleno en CFB/OFB.
- Payload no depende de esa decisión.

**A7 — Archivo vacío.** La consigna no lo excluye (P1) y la estructura lo admite (`size = 0`).

**Decisión provisional:** se acepta. `00 00 00 00 || .txt\0` es válido, con y sin cifrado.

## 3. Formato (resumen)

Notación: `F` = longitud del archivo, `E` = longitud de la extensión codificada (punto + caracteres + NUL, mínimo 2), `u32be(x)` = 4 bytes Big Endian.

```
estructura(archivo) = u32be(F) || datos(F bytes) || extensión(E bytes)     longitud 4 + F + E

sin cifrado:  flujo = estructura
con cifrado:  flujo = u32be(C) || Enc(estructura)     C = longitud del ciphertext
```

`F ≠ C` en general. En el ejemplo P7 con AES-CBC, `F = 414`, `estructura = 423`, `C = 432` y el flujo mide `4 + 432 = 436` bytes (con 3DES-CBC sería `C = 424` y el flujo `428`):

| Campo | Bytes |
|---|---|
| Tamaño real | `00 00 01 9E` (414) |
| Tamaño cifrado | `00 00 01 B0` (432, AES; en 3DES sería `00 00 01 A8` = 424) |

**Observación de diseño:** sin cifrado el flujo coincide con la estructura. Por eso hacen falta solo dos primitivas de serialización: la estructura, y el marco `u32be(C) || bytes`.

**Qué debe contener el plaintext antes de cifrarse (punto 2).** Exactamente la estructura completa `u32be(F) || datos || extensión` (P5, P6). El cifrado de la etapa 8 recibe ese arreglo y devuelve el ciphertext.

**Cómo se reconstruye el archivo (punto 2).** Se descifra el ciphertext (la capa criptográfica ya quitó el padding si lo hubo) y se aplica `parseFile` al plaintext: se lee `F`, `F` bytes y la extensión hasta el NUL.

## 4. Arquitectura

Paquete nuevo `ar.edu.itba.stegobmp.payload`. **Depende solo del JDK**: no importa `steganography`, `bmp`, `cli` ni `crypto`. Quien conecta las capas es la futura capa de operaciones (§6).

| Clase | Responsabilidad |
|---|---|
| `final class HiddenFile` | Valor inmutable: `byte[]` de datos y `FileExtension`. `dataLength()` (como `long`) y `copyData()`. `equals`/`hashCode` por contenido. No lee ni escribe en disco. |
| `final class FileExtension` | Valor inmutable. Fábricas `none()`, `of(String)` (p. ej. `".txt"`) y `fromFileName(String)` (regla A3). `encodedLength()`, `toBytes()` (con punto y NUL), `isEmpty()`, `text()` y `appendTo(String)`. Valida el alfabeto. |
| `interface ByteSource` | Lectura secuencial mínima propia de Payload: `readByte()`, `readBytes(int n)` y `remainingBytes()`. `remainingBytes()` es capacidad de lectura restante, no longitud del mensaje, igual que en `StegoReader`. Lanza `PayloadFormatException` (reason `TRUNCATED`) si faltan bytes. |
| `final class ByteArraySource implements ByteSource` | Fuente sobre un `byte[]`. Sirve para parsear el plaintext descifrado y para los tests. |
| `final class PayloadCodec` | Estático y sin estado. Es el único lugar que conoce el formato (ver métodos abajo). |
| `class PayloadFormatException extends Exception` | Excepción comprobada con `Reason` (enum) y mensaje en español. |

Métodos de `PayloadCodec`:
- `byte[] serializeFile(HiddenFile)`: devuelve la estructura (`4 + F + E`). Valida el desbordamiento con `long`.
- `HiddenFile readFile(ByteSource)`: lectura secuencial desde un flujo sin cifrado. Consume solo lo necesario.
- `HiddenFile readFile(ByteSource)`: lectura secuencial desde un flujo sin cifrado. Consume solo lo necesario y se detiene en el NUL (A5).
- `HiddenFile parseFile(byte[] plaintext)`: `readFile` sobre el arreglo y luego rechaza con `TRAILING_BYTES` cualquier byte posterior al NUL (A5, estricto).
- `byte[] frameCiphertext(byte[] ciphertext)`: devuelve `u32be(C) || ciphertext`. Rechaza un ciphertext vacío (`CIPHERTEXT_SIZE_ZERO`).
- `byte[] readCiphertextBlock(ByteSource)`: lee `u32be(C)`, valida `C` (distinto de 0, `≤ 2³¹−1` y `≤ remainingBytes()`) **antes de reservar memoria** y luego lee `C` bytes. `C` nunca se confunde con el tamaño real `F` del archivo.
- `long plainStreamLength(HiddenFile)` y `long framedLength(long ciphertextLength)`: longitudes para calcular capacidad. Se pueden probar sin reservar arreglos enormes.

`PayloadFormatException.Reason`:
- `TRUNCATED`
- `SIZE_INVALID`
- `SIZE_EXCEEDS_AVAILABLE`
- `CIPHERTEXT_SIZE_ZERO`
- `EXTENSION_MISSING_DOT`
- `EXTENSION_INVALID_CHAR`
- `EXTENSION_UNTERMINATED`
- `TRAILING_BYTES`
- `FILE_TOO_LARGE`

**Separación de responsabilidades:**
- Payload serializa y deserializa, pero no cifra, no recorre portadores y no accede a disco.
- Steganography transporta bytes, sin saber qué significan.
- Crypto transforma `byte[] → byte[]`, sin saber del formato.
- Nada de esto se mezcla.

## 5. Validaciones y errores (punto 4)

| Situación | Cuándo | Reason | Mensaje (ejemplo) |
|---|---|---|---|
| Menos de 4 bytes disponibles para el tamaño | Leer | `TRUNCATED` | «Payload truncado: no hay 4 bytes para el tamaño (disponibles: 2).» |
| Tamaño ≥ 2³¹ (negativo si se leyera con signo) | Leer | `SIZE_INVALID` | «Tamaño inválido: 4294967295.» |
| Tamaño mayor que lo que queda por leer | Leer | `SIZE_EXCEEDS_AVAILABLE` | «El tamaño declarado (N) excede los bytes disponibles (M). Payload inconsistente.» |
| Tamaño del cifrado = 0, o ciphertext vacío al enmarcar | Leer / escribir | `CIPHERTEXT_SIZE_ZERO` | «Tamaño cifrado inválido: 0.» |
| Bytes posteriores al NUL en `parseFile` (plaintext) | Parsear | `TRAILING_BYTES` | «Hay N bytes después de la extensión.» |
| Faltan bytes tras los datos (ni punto ni NUL) | Leer | `TRUNCATED` | «Payload truncado: falta la extensión.» |
| Primer byte de la extensión distinto de `.` | Leer | `EXTENSION_MISSING_DOT` | «La extensión debe comenzar con '.' (se leyó 0xNN).» |
| Byte de extensión fuera de `0x20–0x7E` o separador de ruta | Leer | `EXTENSION_INVALID_CHAR` | «Carácter inválido 0xNN en la extensión.» |
| Se acaban los bytes antes del NUL | Leer | `EXTENSION_UNTERMINATED` | «La extensión no termina en NUL.» |
| `4 + F + E` o `4 + C` desbordan `Integer.MAX_VALUE` | Escribir | `FILE_TOO_LARGE` | «El archivo es demasiado grande para el formato.» |
| Extensión del nombre fuera del alfabeto | Ocultar | `EXTENSION_INVALID_CHAR` | «La extensión '.café' contiene caracteres no ASCII.» |

Notas:
- `PayloadFormatException` es un error de formato, no de capacidad. La capacidad insuficiente la sigue informando `CapacityExceededException` (LSB1).
- Hay validación previa de tamaños antes de reservar memoria: un tamaño corrupto no provoca `OutOfMemoryError`.
- Con cifrado, la capa de integración añade la pista «¿password, algoritmo o modo incorrectos?» cuando falla la lectura del plaintext (en esa etapa).
- Todos los cálculos intermedios van en `long`, con `Math.addExact`/`multiplyExact` (como en el lector BMP).
- Con un orden de bits incorrecto en LSB1 (ver A1 de `LSB1_DESIGN.md`) es probable que el payload falle con `EXTENSION_MISSING_DOT` o `EXTENSION_INVALID_CHAR`. Eso ayuda a detectarlo con las muestras reales.

## 6. Integración futura (punto 6)

Payload y `Steganography` no se conocen. La futura capa de operaciones (`EmbedOperation`/`ExtractOperation`) los conecta.

**Adaptador.** Una clase mínima en esa capa implementa `ByteSource` sobre `StegoReader`. Delega en `readByte`/`readBytes`/`remainingBytes` y traduce `StegoDataException` a `PayloadFormatException` (`TRUNCATED`). Se mantiene fuera de `payload` para que ese paquete no importe `steganography`.

**EMBED:**
1. `HiddenFile f = new HiddenFile(leerArchivo(-in), FileExtension.fromFileName(nombre))`.
2. Sin `-pass`: `flujo = PayloadCodec.serializeFile(f)`.
3. Con `-pass`: `flujo = PayloadCodec.frameCiphertext(cipher.encrypt(PayloadCodec.serializeFile(f)))`.
4. `steganography.embed(body, flujo)`: la capacidad se valida allí (`CapacityExceededException`).
5. `image.withBody(...)` y `BmpWriter`.

**EXTRACT:**
1. `reader = steganography.newReader(body)` y `src = new StegoByteSource(reader)`.
2. Sin `-pass`: `f = PayloadCodec.readFile(src)`.
3. Con `-pass`: `c = PayloadCodec.readCiphertextBlock(src)`, `plain = cipher.decrypt(c)` y `f = PayloadCodec.parseFile(plain)`.
4. Se escribe `f.extension().appendTo(-out)` con `f.copyData()`.

**Interfaz que necesitará la etapa de cifrado.** Un contrato propio, definido en esa etapa:
```
interface PayloadCipher {
    byte[] encrypt(byte[] plaintext) throws CryptoException;
    byte[] decrypt(byte[] ciphertext) throws CryptoException;   // devuelve el plaintext sin padding
}
```
- Mapea a `-a`, `-m` y `-pass`.
- Es una función `byte[] → byte[]` sin saber de tamaños ni extensiones.
- El padding PKCS5 (o su ausencia en CFB/OFB) y la validación de longitud de bloque quedan dentro del cifrador.

**Compatibilidad con LSB4 y LSBI.** El flujo es el mismo: dependen solo de que el reader entregue bytes. LSBI guarda sus 4 bits de patrón dentro de su propia implementación, de forma transparente para Payload.

**Capacidad.** Para la fórmula de `CapacityMath`, `E = extension.encodedLength()` (5 para `.txt`, 2 sin extensión).

## 7. Casos de prueba propuestos

Todos son unitarios y con vectores calculados a mano, salvo el 24, que es de compatibilidad con LSB1.

### Serialización (`PayloadCodecTest`)
1. **Un byte:** datos `41`, `.txt` → `00 00 00 01 41 2E 74 78 74 00`.
2. **Archivo vacío con extensión:** `00 00 00 00 2E 74 78 74 00`; `readFile` devuelve datos vacíos y `.txt`.
3. **Sin extensión:** datos `01 02` → `00 00 00 02 01 02 2E 00`; `readFile` devuelve `FileExtension.none()`.
4. **Archivo vacío sin extensión:** `00 00 00 00 2E 00`.
5. **Ejemplo de la consigna 17.118:** archivo de 17.118 ceros con `.png`: los 4 primeros bytes son `00 00 42 DE`; longitud `4 + 17118 + 5 = 17127`.
6. **Orden de bytes (endianness):** `F = 256` → `00 00 01 00`, no `00 01 00 00`. También `F = 255`, `65535`, `65536` y `2²⁴`.
7. **Ejemplo 414 con `.txt`:** la estructura mide 423 y empieza con `00 00 01 9E`.
8. **Marco cifrado:** `frameCiphertext` de 432 bytes → los primeros 4 son `00 00 01 B0` y el total es 436. Con 432 bytes distintos de cero, comparar el contenido byte a byte.
9. **Extensiones de distinta longitud:** `.c`, `.txt`, `.html`, `.jpeg` y una de 255 caracteres. `E` es `len + 1`, hay un único NUL final y la lectura devuelve el mismo texto.
10. **Datos con 0x00 y 0x2E:** los datos contienen NUL y puntos y la lectura no confunde sus bytes con el terminador. Los datos se leen por tamaño, no por búsqueda de NUL.
11. **Datos arbitrarios:** los 256 valores de byte, y 10.000 bytes aleatorios con semilla fija, ida y vuelta.
12. **Round-trip con y sin extensión:** `parseFile(serializeFile(f))` es igual a `f` para extensiones `.txt`, `.gz` y ninguna.

### Lectura, truncamiento e inconsistencias
13. **Flujo de menos de 4 bytes** (0, 1, 3 bytes): `TRUNCATED`.
14. **Datos truncados:** tamaño 10 con solo 9 bytes disponibles: `SIZE_EXCEEDS_AVAILABLE`.
15. **Tamaño que cabe pero falta la extensión:** `TRUNCATED`.
16. **Capacidad exacta:** el flujo calza justo con los bytes disponibles; se lee sin error y `remainingBytes() == 0`.
17. **Un byte de menos:** el mismo flujo sin su último byte (el NUL): `EXTENSION_UNTERMINATED`.
18. **Primer byte de extensión distinto de `.`:** `EXTENSION_MISSING_DOT`. Se prueba también `00` solo (rechazo de A1).
19. **Carácter inválido** (`0x00` interior, `0x07`, `0xC3`, `/`, `\`): `EXTENSION_INVALID_CHAR`. El espacio (`0x20`) es válido.
20. **Tamaño inválido:** `80 00 00 00` (2³¹) y `FF FF FF FF`: `SIZE_INVALID`; `7F FF FF FF` sin datos suficientes: `SIZE_EXCEEDS_AVAILABLE`. No se reserva memoria.
21. **Sin sobre-lectura:** tras `readFile`, `remainingBytes()` es el original menos `4 + F + E`.
22. **Tamaño de cifrado:** `00 00 00 00` → `CIPHERTEXT_SIZE_ZERO`; `C` mayor que lo disponible → `SIZE_EXCEEDS_AVAILABLE`; `C` exacto se lee completo.
23. **Sobrantes** (A5): `readFile` sobre un flujo con bytes después del NUL los ignora y `remainingBytes()` los conserva; `parseFile` sobre el mismo contenido lanza `TRAILING_BYTES`. Un plaintext exacto se acepta.
23b. **Marco cifrado:** `frameCiphertext(new byte[0])` lanza `CIPHERTEXT_SIZE_ZERO`; `readCiphertextBlock` con `C = 0x7FFFFFFF` y pocos bytes disponibles lanza `SIZE_EXCEEDS_AVAILABLE` sin reservar memoria.

### Compatibilidad
24. **Payload + LSB1:** el flujo `serializeFile` se oculta con `Lsb1` en un `BmpImage` sintético, se lee con un adaptador definido en el test y se compara con el original. Verifica que `ByteSource` encaja con `StegoReader`. El adaptador vive en el código de prueba, no en `main`.

### `FileExtension` y longitudes
25. **`fromFileName`:**

| Nombre | Resultado |
|---|---|
| `mensaje1.txt` | `.txt` |
| `a.tar.gz` | `.gz` |
| `archivo` | ninguna |
| `archivo.` | ninguna |
| `.bashrc` | `.bashrc` |
| `foto.JPG` | `.JPG` (se conservan las mayúsculas) |
| `a.b c` | `.b c` (válida: el espacio está permitido) |
| `a.tar gz` | `.tar gz` (válida) |
| `a.café` | inválida (no ASCII) |

26. **`appendTo`:** `mensaje1` + `.txt` → `mensaje1.txt`; con extensión vacía devuelve `mensaje1`.
27. **Límites:**
    - `4 + F + E` con `F` cerca de `Integer.MAX_VALUE`: `FILE_TOO_LARGE`. Se prueba con `plainStreamLength(long, int)`, sin reservar arreglos enormes.
    - `framedLength` con `C` cercano a `Integer.MAX_VALUE`: error.
    - `HiddenFile.dataLength()` devuelve un `long`.
28. **Longitud del flujo = `4 + F + E`** para `F` de 0 a 300 y extensiones de 0 a 6 caracteres.

## 8. Fuera de alcance de esta etapa

- Cifrado (PBKDF2, AES, 3DES, modos y padding).
- LSB4 y LSBI.
- Lectura/escritura de archivos del usuario y cualquier cambio en el CLI.
- El adaptador `StegoByteSource` y `EmbedOperation`/`ExtractOperation` (etapa de integración).
- El código existente de `bmp`, `cli` y `steganography` no se modifica.

## 9. Decisiones (ajustadas por el usuario)

1. **A1:** extensión vacía como `.\0`, con lector estricto que rechaza `\0` solo.
2. **A3:** la extensión es el sufijo desde el último `.`, también para dotfiles. Alfabeto ASCII `0x20–0x7E` sin `/` ni `\`; se rechaza lo demás al ocultar.
3. **A4:** tamaño leído como `u32` y aceptado solo hasta `2³¹−1`.
4. **A5:** `readFile` se detiene en el NUL e ignora el resto del portador; `parseFile` es estricto y rechaza bytes posteriores (`TRAILING_BYTES`).
5. **A2:** al extraer, `ruta = -out + extensión`, siempre. Se aplica en integración.
6. **Arquitectura:** `payload` define su propio `ByteSource` y no importa `steganography`; el adaptador se escribe en la etapa de integración.
7. **Marco cifrado:** `frameCiphertext` rechaza ciphertext vacío; `readCiphertextBlock` valida `C` antes de reservar memoria; `F` y `C` se mantienen separados.
