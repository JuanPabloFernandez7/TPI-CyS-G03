# Registro de decisiones técnicas

## D-001 — Lenguaje y runtime

- **Estado:** confirmado por el usuario.
- **Alternativas permitidas:** C con OpenSSL; Java 21.
- **Decisión:** Java 21.
- **Fundamento:** la consigna acepta explícitamente Java 21; JCA incluye las primitivas criptográficas necesarias, y el manejo de arreglos, archivos y errores reduce riesgos de memoria frente a C. La estructura modular propuesta también facilita las pruebas de ida y vuelta.
- **Trade-offs:** C ofrece acceso directo a OpenSSL y puede resultar natural para el entorno Linux, pero aumenta la carga de manejo de memoria, tamaños y errores. Java requiere comprobar los nombres y parámetros exactos de los proveedores JCA, especialmente CFB8, 3DES y PBKDF2.
- **Verificación local:** están instalados Amazon Corretto 21.0.10 y OpenJDK 21.0.2; el `java`/`javac` predeterminado es OpenJDK 26. También hay Maven, clang 17 y OpenSSL 3.6.2. Se debe seleccionar explícitamente JDK 21 para compilar y probar si se confirma Java.
- **Límite de verificación:** no se inspeccionó Pampero. No se afirma que allí estén instalados Java 21 ni una versión específica de OpenSSL.
- **Próximo paso:** validar build con JDK 21 y continuar con la etapa CLI.

## D-002 — BMP cubierto por el alcance

- **Estado:** establecido por la consigna.
- **Decisión:** soportar BMP V3 de 24 bits por píxel y sin compresión; respetar el offset real de píxeles, orden físico BGR y padding de filas, usar los bytes de padding como portadores y no alterar el encabezado.
- **Validación requerida:** round-trip de lectura/escritura byte por byte y pruebas con filas que tengan y no tengan padding.
- **Base:** `Trabajo Practico Implementacion-2026_2.pdf`, secciones 4 y 5.4.

## D-003 — Formato y endianess del payload

- **Estado:** establecido por la consigna, sujeto a pruebas de compatibilidad.
- **Decisión:** sin cifrado, ocultar tamaño real, contenido y extensión terminada en NUL; con cifrado, ocultar tamaño cifrado seguido por el cifrado de la estructura anterior. Leer/escribir el tamaño de forma explícita en Big Endian para compatibilidad con los archivos de la cátedra.
- **Validación requerida:** archivos binarios y vacíos, extensiones y comparación byte por byte.
- **Base:** `Trabajo Practico Implementacion-2026_2.pdf`, secciones 5.3 y 5.4.

## D-004 — Parámetros criptográficos

- **Estado:** fijado por la consigna.
- **Decisión:** PBKDF2 con SHA-256, 10.000 iteraciones y salt fijo de ocho bytes cero; derivar y separar clave e IV según el algoritmo. Ofrecer AES-128/192/256 y 3DES de tres claves, con ECB/CBC/CFB de 8 bits/OFB de 128 bits cuando se especifique password.
- **Implicancia de implementación:** en Java no se supondrá que `AES/CFB/NoPadding` es CFB8; se verificará la transformación de 8 bits. Se probará cada transformación y proveedor en el runtime objetivo. ECB no consume IV, aunque la derivación y el tratamiento del material deben seguir la convención necesaria para interoperar con la cátedra.
- **Base:** `Trabajo Practico Implementacion-2026_2.pdf`, sección 5.4, contrastada con los apuntes JCA/OpenSSL y PBKDF2. Prevalecen los parámetros de la consigna sobre ejemplos genéricos.

## D-005 — Diferencia de alcance en LSBI

- **Estado:** confirmada con los ejemplos oficiales `ladoLSBI.bmp`, `ladoLSBIaes256ofb.bmp` y `ladoLSBIdescfb.bmp`; ver [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md).
- **Consigna:** exige recorrer los bytes BMP en orden físico B, G, R y fija los cuatro flags en los primeros cuatro bytes físicos, en orden `00, 01, 10, 11`, codificados con LSB1 sin inversión.
- **Paper:** el payload LSBI usa B/G y deja R como ruido; los patrones se forman con los bits segundo y tercero menos significativos. Su pseudocódigo enumera G antes que B.
- **Decisión:** se usan exclusivamente los componentes B/G, filtrados en orden físico B→G por píxel, prevaleciendo el orden BGR explícito de la consigna sobre el orden G→B del pseudocódigo. Los flags siguen literalmente la consigna. El padding no se considera canal de datos y se preserva, salvo que alguno de sus bytes esté en el prefijo físico de cuatro bytes reservado para flags; se agregó `CarrierLayout` para que LSBI conozca ancho, filas y stride sin depender de `BmpImage`.
- **Inversión:** para cada patrón se cuentan los cambios y no cambios entre las posiciones que transportan el flujo; se invierte si y solo si `changed > unchanged` (empate: no invertir). La extracción recupera `stegoLSB XOR flag[pattern]`.
- **Orden de bits:** MSB-first, consistente con `BitOrder.DEFAULT`; la consigna/paper no fijan inequívocamente este orden por byte.
- **Límite:** B→G, flags e interpretación MSB-first recuperaron el PNG oficial en tres ejemplos LSBI. Esto confirma esos vectores, no toda combinación o variante no probada.
- **Observación:** el paper enumera una vez `00, 10, 10, 11`; su ejemplo y pseudocódigo sustentan el conjunto `00, 01, 10, 11`, también indicado por la consigna. Se interpreta como errata tipográfica.

## D-006 — Estegoanálisis

- **Estado:** pendiente de muestras.
- **Decisión:** no inferir ni documentar resultados experimentales hasta tener los cuatro archivos provistos por la cátedra. Se conservarán hipótesis y evidencia separadas.
- **Base:** consigna, sección 6.

## D-008 — Parser de CLI

- **Estado:** implementado y probado (`ArgumentParserTest`, `MainTest`).
- **Decisión:** `ArgumentParser` produce un `CommandOptions` inmutable (operación, paths, `StegAlgorithm`, `Optional<EncryptionSettings>`) sin conocer BMP/payload/cripto. Valores de `-a`, `-m` y `-steg` se aceptan con la grafía exacta de la consigna (`aes128`, `cbc`, `LSBI`...). El valor que sigue a una opción se toma siempre como valor (un password puede empezar con `-`). Códigos de salida: 0 ayuda, 2 error de uso, 3 reservado para algoritmos aún no implementados.
- **Decisiones confirmadas por el usuario (revisión de la etapa CLI):**
  1. `-a`/`-m` sin `-pass`: **no se cifra, solo se esteganografía, sin error.** Se basa en la *interpretación* de la consigna §5.1 ("No se puede encriptar/desencriptar sin password. Si este dato no está, sólo se esteganografia"); la consigna no dice explícitamente si `-a`/`-m` sin `-pass` es un error.
  2. `-pass ""`: se mantiene aceptado (password vacío, con cifrado). **Ambigüedad abierta:** la consigna no define el password vacío; revisar si surge evidencia de la cátedra.
  3. Valores con la grafía exacta de la consigna (`aes128`, `3des`, `cbc`, `LSB1`, `LSBI`...); no se aceptan variantes de mayúsculas.
  4. Un password que empieza con `-` (incluso igual a otra opción, p. ej. `-a`) se toma siempre como valor de `-pass`, porque el valor que sigue a una opción con valor se consume sin interpretarlo. Cubierto por tests.
- **Ambigüedades sin resolver:**
  1. `-a`/`-m` sin `-pass`: ver decisión 1 (interpretación, no texto explícito).
  2. `-pass ""` (vacío): ver decisión 2.
  3. Las comillas tipográficas y guiones largos (–in) del PDF no se interpretan; se exige `-in`.
  4. Código de salida y formato de error no están especificados.
- **Verificación:** `mvn test` (unitarios, sin procesos externos) y `mvn clean verify` (agrega `PackagedJarIT`, que ejecuta el JAR empaquetado vía Failsafe).

## D-009 — Lector/escritor BMP

- **Estado:** implementado y probado (`BmpReaderTest`, `BmpRoundTripIT`).
- **Decisiones confirmadas por el usuario:** offset ≥ 54 aceptado usando el offset real y preservando bytes intermedios; bytes posteriores al cuerpo preservados y no usados como portadores; archivo menor a `offset + bodySize` rechazado; `biSizeImage` ignorado; ancho ≤ 0 y alto 0 rechazados; alto negativo (top-down) aceptado con `abs(height)` y recorrido físico.
- **Validaciones:** firma `BM`, DIB = 40, planos = 1, 24 bpp, compresión = 0. Campos uint32 leídos como `long`; `stride`, `bodySize` y `offset + bodySize` en `long` con `Math.multiplyExact/addExact` (desborda con ancho y alto cercanos a 2^31 → "fuera de rango").
- **Decisiones no cubiertas por la consigna:**
  1. Offset < 54 rechazado (solaparía el DIB).
  2. El campo `bfSize` (bytes 2..5) y los campos reservados no se validan; se preservan.
  3. Archivos > 2 GiB − 1 rechazados (se cargan en un `byte[]`).
  4. La paleta/resolución/colores importantes del DIB se ignoran y se preservan.
  5. Un offset mayor a 54 no se interpreta (los bytes intermedios pueden ser paleta u otros datos).
- **Representación:** `BmpImage` conserva el archivo completo (`byte[]`) más `bodyOffset`/`bodyLength`; `copyBody()`, `gapBytes()`, `trailingBytes()` devuelven copias y `withBody()` devuelve otra imagen con solo el cuerpo reemplazado.

## D-010 — LSB1

- **Estado:** implementado, probado y compatible con el ejemplo oficial `ladoLSB1.bmp` y el ejemplo AES-128-CBC `ladoLSB1aes128cbc.bmp` (ver [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md)).
- **Decisión:** el bit `j` del byte `i` del mensaje se guarda en el LSB del byte `8i+j` del cuerpo, MSB primero. La consigna no especifica el orden dentro del byte; `BitOrder` es el único punto de configuración. Se validó con `ladoLSB1.bmp` y `ladoLSB1aes128cbc.bmp`.
- **Recorrido:** orden físico del cuerpo (B, G, R), incluyendo el padding de filas (consigna §5.4); el modelo BMP no se invierte aunque la altura sea negativa.
- **Interfaz:** `Steganography.embed(body, data)` devuelve un cuerpo nuevo (no muta la entrada) y `newReader(body)` entrega un `StegoReader` secuencial. `remainingBytes()` es la capacidad de lectura restante del cuerpo (`⌊bodyLength/8⌋` menos lo leído), no la longitud del mensaje; la capa Payload determina el final y valida.
- **Capacidad:** `⌊bodyLength/8⌋` bytes de flujo; los bytes posteriores al mensaje y el resto no múltiplo de 8 quedan intactos. `CapacityMath` calcula el tamaño máximo de archivo (sin cifrado: N−4−E; con padding de bloque B: B·⌊(N−4)/B⌋−1−4−E; sin padding: N−8−E), verificado contra fuerza bruta.
- **Errores:** `CapacityExceededException` (bytes requeridos y capacidad máxima) y `StegoDataException` (lectura más allá del cuerpo; una lectura fallida no consume).
- **Pruebas de orden:** vectores calculados a mano (ejemplo del paper, `0x01`, `0xB2`, `0xAA`, `0x55`, flujo `00 00 42 DE`) independientes del round-trip; los bytes palíndromos no sirven para detectar el orden.
- **Pendientes de otras etapas:** archivos sin extensión (Payload); padding de CFB/OFB vs ECB/CBC (Crypto).

## D-011 — Payload

- **Estado:** implementado y probado (`PayloadCodecTest`, `FileExtensionTest`, `PayloadLsb1CompatibilityTest`); diseño en [`PAYLOAD_DESIGN.md`](PAYLOAD_DESIGN.md).
- **Formato:** `u32be(F) || datos || extensión\0`; con cifrado `u32be(C) || ciphertext`, con `F` (tamaño real) y `C` (tamaño del cifrado) siempre distintos. El plaintext a cifrar es la estructura completa.
- **Decisiones provisionales (no cubiertas por la consigna):**
  1. Archivo sin extensión: se codifica `.\0`; el lector rechaza un `\0` solo.
  2. Extensión = sufijo desde el último `.` del nombre sin directorios; alfabeto ASCII `0x20–0x7E` salvo `/` y `\`; el resto se rechaza al ocultar.
  3. Tamaño `u32` aceptado hasta `2³¹−1`.
  4. `readFile` se detiene en el NUL e ignora lo que sigue en el portador; `parseFile` (plaintext) rechaza bytes posteriores (`TRAILING_BYTES`).
  5. `-out` + extensión recuperada, siempre concatenadas (se aplica en integración).
  6. `frameCiphertext` rechaza ciphertext vacío; `readCiphertextBlock` valida `C` antes de reservar memoria.
- **Independencia:** `payload` solo depende del JDK; el adaptador `StegoReader → ByteSource` queda para integración (en los tests existe solo como código de prueba).

## D-007 — Build inicial

- **Estado:** implementado.
- **Decisión:** Maven sin dependencias de runtime, compilación con `maven.compiler.release=21` y JAR ejecutable con `ar.edu.itba.stegobmp.Main` como punto de entrada.
- **Fundamento:** empaquetado común y reproducible desde terminal; no sumar bibliotecas hasta que una etapa lo requiera.
- **Verificación:** `mvn clean package` con Corretto 21.0.10; JAR `--help` retorna 0 y un comando pendiente retorna 2.

## D-012 — Integración end-to-end LSB1

- **Estado:** implementado y probado (`EndToEndLsb1Test`, `StegoReaderSourceTest`, `CipherSpecsTest`, `PackagedJarIT`).
- **Estructura (`ar.edu.itba.stegobmp.app`):** `Embedder` y `Extractor` orquestan BMP → Payload → Crypto → Steganography sin duplicar lógica; `StegoReaderSource` adapta `StegoReader` a `ByteSource` (`StegoDataException` → `PayloadFormatException(TRUNCATED)`); `CipherSpecs` mapea los enums de la CLI a los de crypto (`CFB` → CFB8); `Steganographies` conecta los algoritmos implementados; `OperationException` lleva el mensaje para el usuario.
- **Orden:** sin cifrado `serializeFile → embed`; con cifrado `serializeFile → encrypt → frameCiphertext → embed`; extracción inversa, con `parseFile` estricto tras descifrar.
- **Capacidad:** se calcula antes de cifrar y de escribir (`plainStreamLength`, o `framedLength(ciphertextLength(...))` para considerar padding en ECB/CBC y no en CFB8/OFB). Si no alcanza no se escribe la salida.
- **Salida:** el archivo se escribe solo tras validar todo; `-out` + extensión recuperada, siempre concatenadas (A2 de `PAYLOAD_DESIGN.md`). Éxito silencioso.
- **Códigos de salida:** 0 éxito/ayuda, 1 error de operación (archivo inexistente, BMP inválido, capacidad, payload corrupto, contraseña incorrecta), 2 uso, 3 reservado para un algoritmo todavía no implementado.
- **Contraseña incorrecta:** ECB/CBC la detectan por padding solo con alta probabilidad (no siempre); CFB8/OFB no pueden detectarla criptográficamente, solo por estructura inválida del payload. No hay MAC (fuera de la consigna). Los mensajes no incluyen la contraseña.
- **Auditoría de crypto (observaciones menores, no bloqueantes):** `SecretKeySpec` conserva una copia interna de la clave que no se puede borrar; `PBEKeySpec`/`destroy()` es de mejor esfuerzo; la contraseña llega como `String` desde la CLI y no puede borrarse; `PasswordPayloadCipher` re-deriva la clave en cada operación (milisegundos).
- **Validación oficial parcial:** AES-128-CBC, AES-256-OFB y 3DES-CFB8 con `margarita` recuperaron el mismo PNG oficial; ver [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md). Sigue pendiente validar otros modos/algoritmos con archivos de la cátedra, así como UTF-8, contraseña vacía y `.\0` sin extensión contra muestras oficiales.

## D-013 — LSB4: agrupamiento y orden de nibbles

- **Estado:** implementado, probado y validado con el ejemplo oficial `ladoLSB4.bmp` (ver [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md)).
- **Consigna:** §5.3, p. 4: “4 bits del mensaje se ocultan en los 4 bits menos significativos de la componente del pixel que corresponda.” El párrafo anterior define que las componentes se recorren según su orden físico; §5.4 establece B, G, R.
- **Implementación:** por cada byte de mensaje se usan dos bytes consecutivos del cuerpo. En cada uno se reemplaza solo el nibble bajo, conservando el nibble alto. Capacidad `floor(bodyLength / 2)`; si el cuerpo es impar, el último byte queda sin usar.
- **Decisión:** los cuatro bits más significativos del byte de mensaje van primero al primer nibble de portador; los cuatro menos significativos van al segundo. Se sigue el orden MSB-first de LSB1. La consigna no especifica qué mitad va primero; esta variante recuperó el PNG de `ladoLSB4.bmp`.
- **Aislamiento:** `Lsb4(BitOrder)` permite seleccionar el otro orden en pruebas; la CLI utiliza `BitOrder.DEFAULT`. `Steganographies` conecta LSB4 con el flujo existente sin cambiar contratos de BMP, Payload, Crypto o CLI.
- **Pruebas:** vector manual `AB` sobre bytes de portador `A0 50` produce `AA 5B`, vector de extracción, modo LSB-first, valores de byte, capacidad, preservación de bits, end-to-end y JAR.

## D-014 — Validación contra los ejemplos oficiales BMP

- **Estado:** seis de seis payloads extraídos y validados como PNG.
- **Material:** portador `lado.bmp`, seis BMP con mensajes y `README.txt` en `docs/ejemplo/`.
- **Resultados:** las seis extracciones produjeron archivos PNG byte-idénticos (2480 × 1219, RGBA, SHA-256 `b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d`); se verificaron chunks/CRC, `IDAT`/zlib y decodificación con ImageIO (`sips`).
- **Cobertura oficial:** LSB1 claro y AES-128-CBC; LSB4 claro; LSBI claro, AES-256-OFB y 3DES-CFB8.
- **BMP:** los siete archivos son BMP V3 de 24 bits sin compresión, 640×480, offset 54, stride 1920 y sin padding/gap/trailing. Los hashes originales permanecieron iguales después de leerlos.
- **Conclusión:** no se requirieron correcciones. La compatibilidad comprobada se limita a estas combinaciones y muestras; no completa por sí sola el estegoanálisis ni la validación final.
