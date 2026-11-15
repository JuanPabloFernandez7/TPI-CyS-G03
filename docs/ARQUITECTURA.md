# Arquitectura y funcionamiento de stegobmp

Este documento describe las capas, sus clases y el recorrido de los datos. La
guía para compilar y usar el programa está en [README.md](../README.md). Al
final se conservan los resultados del estegoanálisis para el informe del TP;
no sustituyen ese informe completo.

## Estructura general

```text
src/main/java/ar/edu/itba/stegobmp/
  Main.java        Entrada, ayuda, errores y despacho
  cli/             Argumentos y configuración validada
  app/             Coordinación de ocultamiento y extracción
  bmp/             Lectura, validación y escritura del BMP
  payload/         Formato del archivo oculto
  crypto/          PBKDF2 y cifrado simétrico
  steganography/   LSB1, LSB4, LSBI y capacidad
src/test/          Pruebas unitarias, integración y vectores
tools/             Herramientas Python auxiliares de análisis
docs/docs_catedra/ Consigna, paper, apuntes y muestras
pom.xml            Compilación, empaquetado y pruebas Maven
```

La aplicación Java no depende de las herramientas Python. Cada capa recibe
datos de la anterior sin necesitar conocer los detalles de las otras.

## Recorrido paso a paso

### Ocultamiento

1. `Main` invoca `ArgumentParser`, obtiene una configuración validada y elige
   la estrategia indicada por `-steg`.
2. `Embedder` lee el archivo de entrada y `BmpReader` valida el portador y su
   geometría. La imagen conserva el encabezado completo, el offset real y
   los bytes posteriores al cuerpo.
3. `PayloadCodec` forma `tamaño real || archivo || extensión || NUL`. Ambos
   campos de tamaño del formato son de cuatro bytes big endian.
4. Si hay contraseña, `PasswordPayloadCipher` deriva clave e IV y cifra ese
   contenido; se antepone el tamaño del texto cifrado. Sin contraseña no se
   cifra, incluso cuando se indican `-a` o `-m`.
5. Se verifica la capacidad del flujo completo, incluidos sus metadatos y el
   padding de cifrado cuando corresponda.
6. La estrategia reemplaza los bits portadores del cuerpo y `BmpWriter`
   escribe el resultado, conservando el resto del BMP.

### Extracción

1. Se valida el BMP y se crea un lector para el algoritmo indicado.
2. Sin cifrado se lee tamaño, contenido y extensión. Con cifrado se lee el
   tamaño del ciphertext y se descifra con los parámetros indicados.
3. Se valida el payload; `Extractor` escribe `-out` más la extensión
   recuperada. La operación no detecta automáticamente el algoritmo.

## Clases, interfaces y enums

### Entrada y CLI

| Clase o tipo | Responsabilidad |
|---|---|
| `Main` | Entrada del programa, ayuda, códigos de salida y ejecución. |
| `ArgumentParser` | Reconoce y valida opciones, valores y defaults. |
| `CommandOptions` | Configuración validada e inmutable de una ejecución. |
| `EncryptionSettings` | Algoritmo, modo y contraseña cuando hay cifrado. |
| `Operation` | Enum de operaciones EMBED y EXTRACT. |
| `StegAlgorithm` | Enum y conversión de LSB1, LSB4 y LSBI. |
| `CipherAlgorithm` | Enum de algoritmos aceptados por la CLI. |
| `CipherMode` | Enum de modos aceptados por la CLI. |
| `UsageException` | Error de argumentos o combinaciones inválidas. |

### Aplicación

| Clase | Responsabilidad |
|---|---|
| `Embedder` | Coordina lectura, payload, cifrado, capacidad e inserción. |
| `Extractor` | Coordina lectura, extracción, descifrado y escritura. |
| `CipherSpecs` | Traduce los valores CLI a especificaciones criptográficas. |
| `Steganographies` | Selecciona la implementación de esteganografía. |
| `StegoReaderSource` | Adapta un lector esteganográfico a `ByteSource`. |
| `OperationException` | Error de operación comunicado por `Main`. |

### BMP

| Clase | Responsabilidad |
|---|---|
| `BmpReader` | Valida firma, DIB V3/V4/V5, dimensiones, planos, profundidad, compresión, offset y cuerpo completo. |
| `BmpHeader` | Geometría, stride, cantidad de filas y tamaño del cuerpo. |
| `BmpImage` | Conserva los bytes originales y permite reemplazar solo el cuerpo. |
| `BmpWriter` | Escribe la imagen resultante. |
| `BmpFormatException` | Describe un BMP inválido o no soportado. |

Solo se aceptan imágenes de 24 bits sin compresión. Se recorre el cuerpo en
orden físico B/G/R, con padding por fila. Se admiten alturas negativas;
el orden físico no se invierte. El lector conserva los bytes del encabezado
extendido y los que estén después de los píxeles, pero no interpreta todos
los metadatos propios de V4/V5.

### Payload

| Clase o tipo | Responsabilidad |
|---|---|
| `HiddenFile` | Contenido binario y extensión del archivo oculto. |
| `FileExtension` | Validación, codificación ASCII y reconstrucción del sufijo. |
| `PayloadCodec` | Serialización y lectura de tamaños, datos y extensión. |
| `ByteSource` | Contrato de lectura secuencial de bytes. |
| `ByteArraySource` | Implementación de `ByteSource` sobre un arreglo. |
| `PayloadFormatException` | Error de framing o contenido oculto inválido. |

Sin cifrar: `u32be(F) || archivo[F] || extensión || 00`. Cifrado:
`u32be(C) || cifrado(u32be(F) || archivo || extensión || 00)`. `F` es el
tamaño original y `C` el del ciphertext.

### Criptografía

| Clase o tipo | Responsabilidad |
|---|---|
| `CryptoAlgorithm` | Algoritmos, nombres JCE y tamaños de clave/bloque. |
| `CryptoMode` | Modos, padding y uso de IV. |
| `CipherSpec` | Combinación de algoritmo y modo y longitudes derivadas. |
| `KeyDerivation` | Contrato de derivación desde una contraseña. |
| `Pbkdf2KeyDerivation` | PBKDF2-HMAC-SHA256, 10.000 iteraciones y ocho bytes de salt cero. |
| `DerivedKey` | Clave e IV; limpia sus arreglos al cerrarse. |
| `PayloadCipher` | Contrato de cifrado y descifrado de bytes. |
| `PasswordPayloadCipher` | Deriva clave/IV y coordina las operaciones con contraseña. |
| `SymmetricCipher` | Inicializa JCE y ejecuta la transformación simétrica. |
| `CryptoException` | Clase base para errores criptográficos. |
| `EncryptionException` | Error al cifrar. |
| `DecryptionException` | Error al descifrar. |
| `InvalidCiphertextException` | Ciphertext incompatible con la transformación. |
| `InvalidCryptoParametersException` | Parámetros criptográficos inválidos. |
| `InvalidPaddingException` | Padding inválido al descifrar. |
| `KeyDerivationException` | Error en PBKDF2. |

Se admiten AES-128/192/256 y 3DES de tres claves; ECB y CBC usan
PKCS5Padding, CFB8 y OFB no agregan padding. OFB usa el tamaño de bloque del
algoritmo: 128 bits para AES y 64 bits para 3DES. No hay autenticación:
una contraseña incorrecta o una modificación no siempre son detectables.

### Esteganografía

| Clase o tipo | Responsabilidad |
|---|---|
| `Steganography` | Contrato de capacidad, inserción y lector. |
| `StegoReader` | Lectura secuencial de bytes ocultos. |
| `Lsb1` | Inserta un bit del flujo por byte del cuerpo. |
| `Lsb4` | Inserta cuatro bits por byte del cuerpo, nibble alto primero. |
| `Lsbi` | Inserta en B/G y padding con inversión por patrón. |
| `CarrierLayout` | Geometría, selección de bytes LSBI y capacidad. |
| `BitOrder` | Orden de bits; se usa MSB primero. |
| `CapacityMath` | Fórmulas informativas de capacidad para LSB1. |
| `CapacityExceededException` | Flujo mayor que la capacidad del portador. |
| `StegoDataException` | Lectura más allá de la capacidad disponible. |

LSB1 y LSB4 recorren todos los bytes del cuerpo, incluido padding. LSBI
reserva los primeros cuatro bytes físicos para flags `00, 01, 10, 11` y usa
como datos los B/G reales y todos los bytes de padding restantes; omite R
para datos. Un R dentro del prefijo sí puede contener un flag. Los patrones
se obtienen de los bits 2 y 1 y se invierte si hubo más cambios que no
cambios; en empate no se invierte.

Para ancho `W`, filas `H`, padding por fila `D` y `R` bytes elegibles entre
los primeros cuatro bytes físicos, la capacidad LSBI en bytes de flujo es
`floor((H * (2 * W + D) - R) / 8)`. Es capacidad del flujo, no necesariamente
del archivo original, porque este también requiere metadatos y posiblemente
cifrado. Los BMP LSBI con padding creados con la variante anterior que lo
omitía pueden no ser compatibles.

## Implementación y validación por etapas

1. CLI y configuración independiente del resto del sistema.
2. Lector/escritor BMP y geometría de filas.
3. Payload, LSB1 y flujos completos de ocultamiento/extracción.
4. Derivación, cifrado y compatibilidad con OpenSSL.
5. LSB4 y LSBI, con vectores propios y padding.
6. Compatibilidad con los seis ejemplos oficiales y análisis de las cuatro
   muestras del grupo; se amplió la lectura a V4/V5.
7. Pruebas del JAR, regresiones y documentación de uso.

Las pruebas incluyen vectores manuales, casos inválidos, round-trips,
compatibilidad criptográfica con OpenSSL y los seis PNG oficiales. Las
muestras oficiales disponibles no tienen padding, por lo que la variante
LSBI con padding está validada con pruebas propias, no con un vector externo.
La validación en Pampero y el informe completo del punto 7 siguen pendientes.

## Herramientas auxiliares

- `stego_analysis.py`: obtiene candidatos, analiza las cuatro muestras del
  grupo y reproduce la cadena de pistas. Los candidatos son evidencia, no una
  demostración infalible de que haya un archivo oculto.
- `validate_group3.py`: compara extracciones Java con las independientes y
  realiza un experimento con el mismo archivo y los tres métodos.
- `test_stego_analysis.py`: prueba el recorrido LSBI con padding.
- `render_analysis_report.py`: convierte un subconjunto de Markdown en PDF;
  no genera ni completa por sí solo el informe académico.
- `requirements-analysis.txt`: dependencias de estas herramientas, no del JAR.

# Estegoanálisis de las imágenes del grupo 3

## Alcance

Análisis de los cuatro archivos de la cátedra, según el punto 6 de la
consigna. Se trabajó con los originales como entrada y las salidas en
`demo/grupo3-analisis/`. No se modificaron los originales. Las muestras y
salidas no forman parte de este documento.

| Portador | Método identificado | Contenido recuperado |
|---|---|---|
| `roma.bmp` | LSBI, sin cifrar | PDF de 6.407 bytes con una pista |
| `hugo4.bmp` | LSB4, sin cifrar | PNG de 42.095 bytes con un ZIP añadido |
| `titanic.bmp` | Texto añadido al final del BMP | Pista de 22 bytes con la contraseña |
| `frozen.bmp` | LSB1, AES-128-CBC | WMV de 2.041.764 bytes |

La aplicación recuperó el PDF y el PNG desde los BMP originales. Originalmente
el video se extrajo independientemente y con una copia adaptada a V3. El lector
ahora acepta V5, por lo que puede extraerlo directamente del original. El
texto añadido a Titanic se recupera mediante análisis externo.

El estegoanálisis de §6 se documenta como investigación técnica externa. La
CLI conserva `-embed` y `-extract` sin detección automática genérica.

## Procedimiento y evidencias

### 1. Identificación inicial

Se leyeron los encabezados BMP y los primeros bytes reconstruidos para cada
algoritmo, con el tamaño de cuatro bytes en big endian. Un tamaño plausible
por sí solo no prueba la presencia de datos: se contrastó con la firma, el
formato, la extensión y la estructura completa del resultado.

| Portador | DIB / offset | Prueba decisiva |
|---|---|---|
| `roma.bmp` | 40 / 54 | LSBI: `00 00 19 07`, seguido de `%PDF-1.5`; flags `0010` |
| `hugo4.bmp` | 40 / 54 | LSB4: `00 00 a4 6f`, seguido de la firma PNG |
| `frozen.bmp` | 124 / 138 | LSB1: `00 1f 27 b0`, tamaño cifrado 2.041.776, múltiplo de 16 |
| `titanic.bmp` | 40 / 54 | `bfSize` = 2.359.350; longitud real = 2.359.372, con 22 bytes adicionales |

### 2. `roma.bmp`: LSBI y PDF

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -extract -p docs/docs_catedra/grupo3/roma.bmp \
  -out demo/grupo3-analisis/roma -steg LSBI
```

El PDF tiene una página, es legible y dice que se cambie la extensión del PNG
a ZIP y se descomprima. Su SHA-256 es
`2c736b542e214c38ead02f132152e887cb9aba1d32be970caec2924f1897b60b`.

### 3. `hugo4.bmp`: LSB4, PNG y ZIP anidado

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -extract -p docs/docs_catedra/grupo3/hugo4.bmp \
  -out demo/grupo3-analisis/hugo4 -steg LSB4
```

El PNG decodifica como imagen RGBA de 413 × 302. Sus seis chunks tienen CRC
válido; los datos `IDAT` se descomprimieron y tienen el tamaño esperado. El
PNG termina en el offset 41.756 y allí comienza un ZIP de 339 bytes; los
bytes añadidos permiten que el archivo siga siendo un PNG válido. El ZIP
contiene `sol3.txt` (260 bytes), cuya integridad también se verificó.
El ZIP puede obtenerse sin modificar el PNG así:

```sh
dd if=demo/grupo3-analisis/hugo4.png \
  of=demo/grupo3-analisis/hugo4-anexo.zip bs=1 skip=41756
unzip -t demo/grupo3-analisis/hugo4-anexo.zip
unzip -p demo/grupo3-analisis/hugo4-anexo.zip sol3.txt
```

El texto indica que cada mina representa un 1, que cada fila forma una letra
ASCII empezando por `01`, y que así se obtiene el algoritmo y modo de 128 bits.
También indica que la contraseña está en otro archivo y que hay un WMV cifrado.
La imagen contiene casillas sin abrir: no alcanza con contar solo las
banderas. Los números adyacentes y el contador de tres minas restantes
permiten completar las tres minas faltantes.

| Fila | Seis bits de minas | ASCII con prefijo `01` | Carácter |
|---|---|---|---|
| 1 | `000001` | `01000001` | A |
| 2 | `100101` | `01100101` | e |
| 3 | `110011` | `01110011` | s |
| 4 | `000011` | `01000011` | C |
| 5 | `100010` | `01100010` | b |
| 6 | `100011` | `01100011` | c |

El resultado es `AesCbc`; junto con los 128 bits de la pista se interpreta
como AES-128-CBC. El resultado se confirmó al descifrar y validar el WMV.
SHA-256 del PNG:
`e5e5b47e6f25b3a104a0416618e4068b60dea88efb2f606a081a0dd9d7ed6940`.
SHA-256 de `sol3.txt`:
`4e9ddeaa9ba0fd9d64ddd257659ff19bea7a755113324344ff21ac140af135d9`.

### 4. `titanic.bmp`: datos añadidos al archivo

El cuerpo de píxeles termina en el offset 2.359.350, igual al `bfSize`
declarado. Los 22 bytes que siguen son el texto ASCII
`la password es exitoso`. Se recuperó como `titanic.txt`.

Este método no sustituye bits de píxeles: agrega datos después del BMP.
La imagen puede seguir viéndose normal, pero un análisis del tamaño declarado,
el fin del cuerpo o una búsqueda de texto revela fácilmente la información.
No hay cifrado en esta pista; por eso el método no protege su contenido.

```sh
tail -c 22 docs/docs_catedra/grupo3/titanic.bmp
```

### 5. `frozen.bmp`: LSB1, AES-128-CBC y video

El original es BMP de 24 bits, sin compresión, 4096 × 3072, DIB V5 de
124 bytes y offset de píxeles 138. Su cuerpo no tiene padding por fila.
Los primeros 32 bits LSB1 codifican un tamaño cifrado de 2.041.776 bytes.
Usando contraseña `exitoso`, PBKDF2-HMAC-SHA256, 10.000 iteraciones y salt
de ocho bytes cero, se descifró con AES-128-CBC. El plaintext contiene:

```text
00 1f 27 a4 || 2.041.764 bytes de archivo || ".wmv\0"
```

El contenido empieza con la firma ASF `30 26 b2 75 8e 66 cf 11 ...`;
se reconoció como WMV y se decodificaron completamente video y audio con
FFmpeg. Dura aproximadamente 53,64 s, video WMV3 de 320 × 240 y audio WMAv2.
FFmpeg emitió una advertencia de timestamps no monótonos al escribir la
salida de prueba, sin fallar la decodificación.

Con soporte V5, la aplicación extrae el video directamente del original:

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -extract -p docs/docs_catedra/grupo3/frozen.bmp \
  -out demo/grupo3-analisis/frozen-original \
  -steg LSB1 -a aes128 -m cbc -pass exitoso
```

La extracción directa se comparó con la independiente y coincide byte por
byte; ambas tienen el SHA-256 indicado más abajo. El archivo original no se
modificó.

**Adaptación usada antes de agregar V5:** se hizo una copia y se cambió
únicamente el byte en offset 14, de `0x7c` a `0x28`: así el tamaño DIB pasa
de 124 a 40. Se conservaron el offset 138 y todos los bytes de píxeles; los
84 bytes restantes del encabezado original pasan a ser gap para el lector.
Esto fue una adaptación específica de esta muestra, no una conversión general
de BMP V5. Actualmente ya no es necesaria; el original permanece intacto.

```sh
python3 - <<'PY'
from pathlib import Path
import struct
source = Path("docs/docs_catedra/grupo3/frozen.bmp").read_bytes()
data = bytearray(source)
assert struct.unpack_from("<I", data, 14)[0] == 124
assert struct.unpack_from("<I", data, 10)[0] == 138
struct.pack_into("<I", data, 14, 40)
Path("demo/grupo3-analisis/frozen-copia-v3.bmp").write_bytes(data)
PY
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -extract -p demo/grupo3-analisis/frozen-copia-v3.bmp \
  -out demo/grupo3-analisis/frozen-aplicacion \
  -steg LSB1 -a aes128 -m cbc -pass exitoso
```

El resultado de la aplicación fue idéntico byte por byte al obtenido
independientemente. SHA-256:
`16b76f7a8b57c1666c1715e4ff32e1deb50c01ab3d1486bffd3ff150264d50e4`.
Visualmente el fragmento muestra un mensaje codificado en el tejido de un
telar. La identificación exacta de la película y del mensaje requiere una
revisión del fragmento completo para el punto 7 del informe.

## Comparación experimental de los tres métodos

Se ocultó el mismo PNG de 42.095 bytes en `lado.bmp`, sin cifrado. Los tres
resultados permitieron recuperar exactamente el archivo original. La
capacidad indicada es la del flujo, no la del archivo neto; el flujo usado
ocupó 42.104 bytes. El portador no tiene padding.

| Método | Capacidad del flujo | Componentes modificados | MSE | PSNR |
|---|---:|---:|---:|---:|
| LSB1 | 115.200 bytes | 167.289 | 0,181520 | 55,542 dB |
| LSB4 | 460.800 bytes | 78.451 | 5,911781 | 40,414 dB |
| LSBI | 76.799 bytes | 166.710 | 0,180892 | 55,557 dB |

En este caso LSB4 modificó menos componentes, pero cada componente pudo
cambiar hasta 15 unidades, frente a una unidad en LSB1 y LSBI; por eso
tiene mayor error cuadrático. Sobre las mismas posiciones B/G de LSBI, LSB
sin inversión habría modificado 166.730 componentes de datos y LSBI modificó
166.707, más tres flags. Esto ilustra el criterio de inversión para este
caso; no garantiza que LSBI sea mejor en toda imagen o métrica.

## Integridad de los originales

Los siguientes SHA-256 coincidieron antes y después del análisis:

```text
af290697ee3bfa74b3b6b6ff85d29b08e109f9700ef44474a0c2c3e5fc55f227  frozen.bmp
492d0d2e608cec2af4cabdda2783274e84d8e4fc9b3180511ef83196164bab04  hugo4.bmp
583a43fb5cb7483310519926aaac11939c59a82ebc995e4f456d0a51e19a070c  roma.bmp
3d282a1c4c0d650d9fa4c71a8ce013186f7fd233389fbec76bbf0b54cb9227d4  titanic.bmp
```

## Pendientes para la entrega

- Explicar el análisis externo y distinguir la adaptación anterior de la
  extracción directa ahora posible con el lector V5.
- Integrar estos resultados y la comparación en el informe completo del punto
  7; falta en particular estudiar el video con más detalle.
- Validar compilación y ejecución en Pampero y revisar la documentación de
  uso y los demás requisitos de entrega.
