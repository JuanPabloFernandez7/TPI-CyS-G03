# Diseño técnico de LSB1

Estado: implementado e integrado; el orden MSB-first también se validó con los ejemplos oficiales `ladoLSB1.bmp` y `ladoLSB1aes128cbc.bmp`. Evidencia en [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md).

Referencias: «Consigna» = `Trabajo Practico Implementacion-2026_2.pdf` (se cita sección y página); «Paper» = Majeed y Sulaiman, `16Vol80No2.pdf`.

## 1. Lo que dice la consigna (base del diseño)

| # | Cita (resumida) | Ubicación | Consecuencia |
|---|---|---|---|
| R1 | «insertar información sustituyendo el bit menos significativo de cada byte del archivo portador por un bit del mensaje» | §5.3.1 LSB1, p. 4 | Un bit de mensaje por byte del cuerpo; los otros 7 bits no cambian. |
| R2 | «el primer bit del mensaje a ocultar se inserta en el bit menos significativo de la primer componente del primer pixel, el segundo bit … en la segunda componente del primer pixel, el siguiente, en la tercer componente…» | §5.3.1, p. 4 | El recorrido empieza en el primer byte del cuerpo y avanza consecutivamente. |
| R3 | «para guardar un byte, se requieren 2 pixeles completos y 2 componentes del siguiente pixel» | §5.3.1, p. 4 | 1 byte de mensaje = 8 bytes consecutivos del cuerpo (8 = 2·3 + 2). Consistente con R2. |
| R4 | «el orden real en que se leen los bytes del archivo es BGR» | §4, p. 2 | «Primera componente» = primer byte físico (azul). No hay reordenamiento por canal. |
| R5 | «Para el esteganografiado usen todo el bloque completo de los pixeles + bytes de padding» | §5.4, p. 5 | El padding de fila es portador: la capacidad es `bodyLength`, no `3·ancho·alto`. |
| R6 | «Si el archivo bmp no puede albergar el archivo a ocultar completo, deberá mostrarse un mensaje de error, en el que se indique cuál es la capacidad máxima del archivo bmp portador» | §5.3.1, p. 4 | Verificar capacidad antes de modificar nada, e informar la capacidad máxima. |
| R7 | Sin cifrado: `Tamaño real \|\| datos archivo \|\| extensión`; total `4 + longitud + e`. Con cifrado: `Tamaño cifrado \|\| encripción(...)`, total `4 + tamaño del cifrado`. «Antes de ocultar el archivo propiamente dicho, con cualquiera de los algoritmos, ocultar su tamaño.» | §5.3, p. 3–4 | LSB1 oculta un flujo de bytes que ya viene armado; no conoce su estructura. |
| R8 | La extensión «debe comenzar con '.' y terminar con '\0'»; «siempre se sabe que los primeros 4 bytes (DWORD size) corresponden al tamaño». | §5.3, p. 3–4 | La extracción sin cifrado necesita leer 4 bytes, luego N, luego hasta `\0`: lectura secuencial. |
| R9 | «Los archivos que les entregaremos tendrán el tamaño guardado en forma Big Endian.» (ej.: 17.118 → `00 00 42 DE`) | §5.4, p. 5 | Formato del campo de tamaño (etapa Payload, no LSB1). |
| R10 | Con LSB1/LSB4/LSBI los archivos de la cátedra siguen «tamaño + archivo + extensión». | §6, p. 6 | Se validó la estructura con seis ejemplos oficiales; ver [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md). |
| R11 | Debe funcionar en Pampero; el programa «debe explicitar errores». | §10, p. 7 | Mensajes de error claros y específicos. |

## 2. Ambigüedades detectadas

**A1 — Orden de los bits dentro de cada byte del mensaje (crítica para compatibilidad).**
La consigna define el orden *entre* bits y componentes (R2) pero **no dice** si «el primer bit del mensaje» es el bit 7 (MSB) o el bit 0 (LSB) del primer byte. El paper tampoco lo define para bytes: su ejemplo (§4) usa la cadena de bits `1 0 0 0 1 1 1 0` sobre ocho bytes del portador y obtiene LSBs `1,0,0,0,1,1,1,0` en ese orden, pero no es un mensaje de bytes.

Decisión propuesta: **MSB primero** (el primer bit insertado es el bit 7 del primer byte).
- Es la lectura natural de «el primer bit del mensaje»: un byte se escribe y se lee de izquierda a derecha, bit 7 primero. Con esta convención, el byte `0x8E = 10001110` reproduce exactamente la secuencia del ejemplo del paper (`1,0,0,0,1,1,1,0`).
- Es la convención habitual en las implementaciones de LSB para esta consigna, pero **no puedo verificarlo con la cátedra**; es una suposición.
- Se verificará con un archivo real de la cátedra. **Ojo: el tamaño solo no alcanza como detector.** Con el orden equivocado cada byte sale con sus bits invertidos de posición; el ejemplo de la consigna `00 00 42 DE` se leería `00 00 42 7B` (17.019 en vez de 17.118), igual de plausible. El detector fiable es la extensión y el contenido: `.txt\0` leído con el orden equivocado da bytes no ASCII, y el archivo recuperado no es válido. Se probarán ambos órdenes sobre la muestra y se elegirá el que produce una extensión válida y un archivo coherente.
- Dejo un único punto de decisión (una constante/enum interna `BitOrder`, no expuesta en la CLI) para poder cambiarlo sin reescribir el algoritmo si la muestra real lo contradice.

**A2 — «Capacidad máxima» del mensaje de error (R6).** No dice en qué unidad ni qué incluye. Propuesta: informar en bytes la cantidad requerida y la capacidad máxima del flujo oculto (`4 + …`), y además el tamaño máximo de archivo que cabe con la extensión/cifrado dados, cuando sea calculable.

**A3 — Archivos sin extensión.** R8 exige «.» al inicio y `\0` al final. No dice qué hacer con un archivo sin extensión. Es tema de la etapa Payload; se registra aquí porque afecta la capacidad (E en la fórmula). No se decide ahora.

**A4 — Relleno en modos CFB/OFB.** La consigna menciona PKCS5 si hay padding (§5.4) y la tabla JCE usa `NoPadding` para CFB/OFB. En OpenSSL (EVP) los modos CFB y OFB tienen tamaño de bloque 1 y no añaden relleno; ECB/CBC sí. Afecta la fórmula de capacidad con cifrado (§6). Se confirma en la etapa criptográfica.

**A5 — Bits del portador no usados.** La consigna no dice qué contienen los bytes del cuerpo después del mensaje. Propuesta: quedan sin modificar (idénticos al original).

## 3. Modelo de bits

Notación: `C` = cuerpo del BMP (arreglo de bytes, `C[0]` es el primer byte tras el offset real, orden físico). `D` = datos a ocultar (arreglo de `N` bytes). `N_max = ⌊|C| / 8⌋`.

**Orden de recorrido (punto 2).** Se recorre `C` por índice creciente `k = 0, 1, 2, …`, exactamente en el orden físico del archivo. Eso ya es el orden B,G,R de cada píxel (R4), y los bytes de padding al final de cada fila se tratan como cualquier otro byte, porque forman parte del cuerpo (R5). El alto negativo (top-down) no cambia nada: no se reordenan filas. No se salta ningún byte, así que no hace falta saber dónde termina cada fila.

**Asignación bit → byte portador.** El bit `j` (0..7, en orden de inserción) del byte de mensaje `i` va al byte portador `k = 8·i + j`. Con MSB primero, el bit insertado es `b(i, j) = (D[i] >> (7 − j)) & 1`.

**Inserción (punto 3).** Para cada `k < 8·N`:

```
C'[k] = (C[k] & 0xFE) | b        con b ∈ {0, 1}
```

- `0xFE = 1111 1110`: `C[k] & 0xFE` pone el LSB en 0 y deja intactos los bits 7..1.
- `| b` escribe el bit del mensaje solo en la posición 0.
- Propiedades: `C'[k] & 0xFE == C[k] & 0xFE` (los siete bits altos no cambian) y `C'[k] XOR C[k] ∈ {0, 1}` (el byte cambia como máximo en ±1). Para `k ≥ 8·N`, `C'[k] == C[k]`.
- En la práctica cambia, en promedio, la mitad de los LSB (cuando el bit del mensaje difiere del LSB original).

**Extracción (punto 4).** Para `i = 0..N−1`:

```
D[i] = Σ_{j=0..7} (C[8·i + j] & 1) << (7 − j)
```

equivalente a acumular `acc = (acc << 1) | (C[k] & 1)` ocho veces. Inserción y extracción comparten la misma función de índice `k = 8·i + j` y la misma constante de orden de bits, definidas en un solo lugar; así no pueden desalinearse. Esto se verifica con tests de ida y vuelta **y** con vectores fijos calculados a mano (un test de ida y vuelta por sí solo no detecta un orden de bits equivocado pero consistente consigo mismo).

## 4. Integración con el payload (punto 5)

LSB1 no interpreta el flujo; el payload lo arma/interpreta una capa aparte (etapa 4):

- Embed: `payload = Payload.build(...)` (R7) → `lsb1.embed(body, payload)`. El flujo ocupa desde `C[0]`, comienza por el campo de 4 bytes de tamaño (R8) y sigue sin separadores. No se inventa ningún otro formato.
- Extract sin cifrado: leer 4 bytes (tamaño `S`, Big Endian, R9) → leer `S` bytes → leer bytes hasta `\0` (extensión).
- Extract con cifrado: leer 4 bytes (tamaño cifrado `T`) → leer `T` bytes → descifrar → de ahí obtener tamaño real, datos y extensión.

Como la longitud total no se conoce de antemano en el caso sin cifrado (la extensión termina en `\0`), la extracción debe ser **secuencial**: la interfaz ofrece un lector que consume bytes del flujo bajo demanda, en lugar de exigir un `count` conocido.

## 5. Capacidad (punto 6)

Sea `|C| = bodyLength = stride · |alto|`, con `stride = 4·⌈3·ancho/4⌉` (incluye padding de fila).

- Bits disponibles: `|C|`. Bytes del flujo que caben: **`N_max = ⌊|C| / 8⌋`**. Los `|C| mod 8` bytes finales no se pueden usar.
- **Sin cifrado.** Flujo = `4 + F + E`, con `F` = tamaño del archivo y `E = len(extensión) + 1` (incluye el punto inicial en `len` y el `\0` final; p. ej. `.txt\0` → E = 5). Condición: `4 + F + E ≤ N_max`, es decir **`F_max = N_max − 4 − E`**. Si `F_max < 0`, no cabe ni el encabezado.
- **Con cifrado.** Flujo = `4 + T`, con `T` el tamaño del cifrado. Condición: `4 + T ≤ N_max`. Con `P = 4 + F + E` (la estructura que se cifra) y bloque `B` (16 en AES, 8 en 3DES):
  - ECB/CBC con PKCS5/PKCS7: `T = B · (⌊P / B⌋ + 1)` (siempre se agrega al menos un byte de relleno). Por tanto `F_max = B·⌊(N_max − 4)/B⌋ − 1 − 4 − E`.
  - CFB/OFB (sin relleno, ver A4): `T = P`, entonces `F_max = N_max − 4 − 4 − E`.
- Ejemplo: BMP de 100×100 → `stride = 300`, `|C| = 30 000`, `N_max = 3 750`. Con `.txt` (E = 5): `F_max = 3 741` sin cifrar.
- Verificación previa: se calcula `N` (tamaño del flujo ya armado) y se compara con `N_max` **antes** de tocar el cuerpo; `capacityBytes(bodyLength)` solo devuelve `N_max`. Para LSB1 el borde exacto `N == N_max` debe entrar, y `N == N_max + 1` debe fallar.

## 6. Errores (punto 7)

| Situación | Dónde se detecta | Comportamiento |
|---|---|---|
| Flujo mayor que la capacidad | `embed`, antes de modificar | `CapacityExceededException(requiredBytes, capacityBytes)`; el mensaje indica ambos valores (R6, A2). No se escribe ningún archivo. |
| BMP inválido (firma, DIB, 24 bpp, compresión, truncado, desbordamientos) | `BmpReader` (ya implementado) | `BmpFormatException`; LSB1 nunca ve un BMP inválido porque recibe solo el arreglo del cuerpo. |
| Cuerpo vacío o con menos de 8 bytes | `capacityBytes` = 0 | Todo flujo no vacío supera la capacidad → `CapacityExceededException`. |
| Datos nulos | `embed` | `NullPointerException`/`IllegalArgumentException` (error de programación, no de usuario). |
| Flujo truncado al extraer (el lector pide más bytes de los que quedan en el cuerpo) | `StegoReader.readByte()` | `StegoDataException` con cuántos bytes se pedían y cuántos quedaban. |
| Tamaño leído absurdo (mayor que el flujo disponible: portador sin mensaje, algoritmo o password equivocados) | Capa Payload usando `remainingBytes()` | Error explícito **antes** de reservar memoria o seguir leyendo; no hay forma de distinguir «mensaje corrupto» de «no hay mensaje». |
| Extensión sin `\0` dentro de lo disponible | Capa Payload | Error de datos truncados. |

LSB1 nunca modifica el arreglo recibido (devuelve una copia), coherente con la inmutabilidad de `BmpImage`.

## 7. Integración con las clases BMP (punto 8)

El algoritmo trabaja solo sobre `byte[]`; no conoce rutas, headers ni `BmpImage`:

```
BmpImage image = BmpReader.read(carrierPath);          // valida formato
byte[] body    = image.copyBody();                     // orden físico, incluye padding
byte[] newBody = lsb1.embed(body, payloadBytes);       // sólo bits LSB de body[0 .. 8N-1]
BmpWriter.write(image.withBody(newBody), outPath);     // header, gap y cola idénticos

BmpImage stego = BmpReader.read(stegoPath);
StegoReader r  = lsb1.newReader(stego.copyBody());     // lectura secuencial de bytes
```

- `header()` y `bodyOffset()` no los usa el algoritmo; los usa el orquestador (futuro `EMBED/EXTRACT`) para informes de error (dimensiones, capacidad).
- `withBody` garantiza que header, bytes intermedios (offset > 54) y bytes posteriores no cambian; los tests lo comprueban a nivel de archivo completo.
- Consecuencia: LSB1 se prueba con cuerpos sintéticos sin crear BMP, y el mismo contrato (`Steganography`) servirá para LSB4 y LSBI (que necesita su propio estado para los flags).

## 8. Diseño de clases propuesto (paquete `ar.edu.itba.stegobmp.steganography`)

| Tipo | Responsabilidad |
|---|---|
| `interface Steganography` | Contrato común: `long capacityBytes(int bodyLength)`, `byte[] embed(byte[] body, byte[] data)` (devuelve cuerpo nuevo; lanza `CapacityExceededException`), `StegoReader newReader(byte[] body)`. No sabe de BMP ni de cifrado. |
| `interface StegoReader` | Lectura secuencial del flujo oculto: `int readByte()` (lanza `StegoDataException` si no queda), `byte[] readBytes(int n)`, `long remainingBytes()`. Permite al payload leer 4 bytes, luego N, luego hasta `\0` sin conocer la longitud total. |
| `final class Lsb1 implements Steganography` | Implementa R1–R3 y las fórmulas de §3 y §5. Sin estado salvo constantes. `embed` copia el cuerpo y aplica `(c & 0xFE) \| bit`. Su lector extrae `(c & 1)` y arma bytes. |
| `enum BitOrder { MSB_FIRST, LSB_FIRST }` | Punto único de decisión de A1 (uso interno; por defecto `MSB_FIRST`). Provee `bitOf(byte, j)` y `place(acc, bit, j)` para que inserción y extracción no puedan divergir. |
| `class CapacityExceededException` | Lleva `requiredBytes` y `capacityBytes` para el mensaje de R6. |
| `class StegoDataException` | Datos ocultos insuficientes o inconsistentes al extraer. |
| `final class CapacityMath` (opcional) | Fórmulas de §5 (`maxFileSize(...)`) para el mensaje de error y las pruebas; **no** bloquea `embed`, que valida el flujo real. |

Fuera de este alcance (etapas posteriores): `PayloadBuilder/Parser` (etapa 4), LSB4, LSBI, cifrado y los comandos EMBED/EXTRACT.

Aritmética: todos los índices y tamaños en `long` donde puedan crecer; `N_max·8` y `8·N` se calculan sin desbordar `int` (el cuerpo máximo es < 2 GiB, `8·N ≤ |C|`).

## 9. Casos de prueba propuestos

**Vectores fijos (calculados a mano, independientes del código):**
1. Un byte `0x8E` sobre `00110011 11101001 01101010 10101001 11011000 10001101 10001100 01101101` (cuerpo del ejemplo del paper §4): resultado esperado `…11101000`, etc., igual al «Result (Stego-image)» del paper. Verifica la asignación bit → byte portador.
2. Un byte con patrón no simétrico (`0x01` → LSBs `0,0,0,0,0,0,0,1` y `0xB2` → `1,0,1,1,0,0,1,0`) para detectar un orden invertido; los palíndromos (`0xA5`, `0x00`, `0xFF`) no lo detectan, por eso no se usan solos.
3. Bytes `0x00` y `0xFF`: LSBs todos 0 / todos 1.
4. Patrones alternados `0xAA`, `0x55` sobre un portador de todos `0xFF` y de todos `0x00`.

**Ida y vuelta:**
5. Un byte; varios bytes; 1 000 bytes aleatorios con semilla fija; todos los valores 0..255.
6. Flujo vacío (0 bytes): `embed` no cambia el cuerpo y el lector no tiene datos. (Con el formato de la consigna el flujo real nunca es vacío; es un caso límite del algoritmo.)
7. Con el flujo armado como `size(Big Endian) || datos || ".ext\0"` a mano, leído secuencialmente con `StegoReader` (sin la capa Payload final).

**Propiedades del cuerpo:**
8. Solo cambian bits autorizados: para todo `k`, `(C'[k] ^ C[k]) & 0xFE == 0`; para `k ≥ 8N`, `C'[k] == C[k]`; la cantidad de bytes distintos es ≤ `8N`. El arreglo de entrada no se modifica.
9. Cuerpo de todos 0 / todos 1 / aleatorio como portador.

**Con BMP real sintético (usando `BmpTestData`):**
10. Portador con padding (ancho 3): los bytes de padding sirven como portadores (se usan con anchos y tamaños que obligan a cruzar el final de la fila).
11. Offset > 54: header, bytes intermedios y cuerpo fuera de `[0, 8N)` idénticos en el archivo completo.
12. Bytes adicionales al final: idénticos tras `withBody`.
13. Alto negativo: el orden físico no cambia el resultado.
14. Archivo completo: `toBytes()` solo difiere en los LSB de `[bodyOffset, bodyOffset + 8N)`.

**Capacidad y errores:**
15. `N == N_max` entra y se recupera exactamente; `N == N_max + 1` → `CapacityExceededException` con ambos valores; cuerpo sin modificar y sin escritura.
16. Cuerpo de longitud no múltiplo de 8: los bytes sobrantes no se usan (`N_max = ⌊|C|/8⌋`).
17. Cuerpo vacío y de menos de 8 bytes.
18. Lectura más allá del flujo → `StegoDataException` con valores pedidos/disponibles.
19. `CapacityMath`: ejemplos de §5 (100×100, `.txt`, AES-CBC/AES-CFB/3DES) comparados con cálculo manual.

**Validación oficial:** `ladoLSB1.bmp` y `ladoLSB1aes128cbc.bmp` recuperaron el mismo PNG válido que los demás ejemplos. Esto confirma MSB-first para esos vectores, no cualquier variante de payload o portador.

## 10. Decisiones que necesito que apruebes

1. A1: bits del mensaje **MSB primero**, con `BitOrder` interno como único punto de cambio.
2. El algoritmo expone `embed(body, data)` + `StegoReader` secuencial (no `extract(body, count)`).
3. A5: los bytes del portador posteriores al mensaje no se modifican.
4. A2: el error de capacidad informa bytes requeridos y bytes máximos del flujo (y el tamaño máximo de archivo si es calculable).
5. A4 y A3 quedan para las etapas de criptografía y payload respectivamente.
