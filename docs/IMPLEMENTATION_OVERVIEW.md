# stegobmp — Resumen de la implementación

Documento de síntesis del trabajo realizado hasta la integración end-to-end de LSBI: qué hace cada módulo y cada clase, cómo se relacionan y cómo se cubre cada punto de la consigna. Los detalles y justificaciones están en los documentos de diseño referenciados (`PROJECT_PLAN.md`, `TECHNICAL_DECISIONS.md`, `LSBI_DESIGN.md`, `LSB1_DESIGN.md`, `PAYLOAD_DESIGN.md`, `CRYPTO_DESIGN.md`).

> Explicación en profundidad de cada carpeta y de la estrategia de pruebas: [`MODULES_AND_TESTING.md`](MODULES_AND_TESTING.md).

## 1. Qué es el programa

`stegobmp` es una CLI en Java 21 (Maven, sin dependencias de runtime) que **oculta** un archivo dentro de un BMP de 24 bits sin compresión y lo **extrae** después, con cifrado opcional (AES-128/192/256 y 3DES; ECB, CBC, CFB, OFB) derivado de una contraseña.

Estado: CLI, BMP, LSB1, LSB4, LSBI, Payload, Criptografía e integración end-to-end implementados. LSB4 alto-primero y LSBI B→G/MSB-first fueron confirmados con ejemplos oficiales; la validación total del TP sigue pendiente.

```
stegobmp -embed   -in archivo -p portador.bmp -out salida.bmp -steg LSB1|LSB4|LSBI [-a aes128|aes192|aes256|3des] [-m ecb|cbc|cfb|ofb] [-pass pw]
stegobmp -extract           -p portador.bmp -out salida       -steg LSB1|LSB4|LSBI [-a ...] [-m ...] [-pass pw]
```

## 2. Arquitectura por capas

```
                        Main
                         │  (códigos de salida, mensajes)
        ┌────────────────┼──────────────────┐
        ▼                ▼                  ▼
      cli/             app/  ──────────────────────────────┐
 (ArgumentParser)   Embedder / Extractor                   │
 → CommandOptions     │   │    │    │                      │
                      ▼   ▼    ▼    ▼                      │
                   bmp/ payload/ crypto/ steganography/    │
                                                           │
 app/ es la única capa que conoce a todas las demás ───────┘
```

Regla de diseño: **cada capa es independiente de las demás** (`payload`, `crypto`, `steganography` y `bmp` solo dependen del JDK). Solo `app/` las conecta. Esto permitió probar y aprobar cada etapa por separado.

### Flujo EMBED
```
archivo ─► HiddenFile ─► PayloadCodec.serializeFile ─► [sin cifrado]            ─┐
                                                      └► PasswordPayloadCipher   │
                                                          .encrypt ─► frameCiphertext ─┤
                                                                                       ▼
BmpReader ─► BmpImage.copyBody + CarrierLayout ─► Steganography.embed(cuerpo, flujo, layout) ─► BmpImage.withBody ─► BmpWriter
```
Antes de cifrar se calcula la capacidad necesaria y se compara contra `capacityBytes`; si no alcanza no se escribe nada.

### Flujo EXTRACT
```
BmpReader ─► copyBody + CarrierLayout ─► Steganography.newReader ─► StegoReaderSource (ByteSource)
   sin cifrado: PayloadCodec.readFile ───────────────────────────────┐
   con cifrado: readCiphertextBlock ─► decrypt ─► parseFile (estricto)┤
                                                                      ▼
                                   escribir  <-out> + extensión recuperada
```
El archivo de salida se escribe solo después de validar todo.

## 3. Módulos y clases

### 3.1 `cli` — parser de argumentos (consigna: sintaxis de línea de comandos)
Convierte `String[]` en una configuración validada e inmutable, sin ninguna lógica de EMBED/EXTRACT.

| Clase | Responsabilidad |
|---|---|
| `ArgumentParser` | Reconoce `-embed -extract -in -p -out -steg -a -m -pass`. Detecta argumentos desconocidos, duplicados, valores faltantes, combinaciones inválidas (`-in` en extract, ambas operaciones, etc.) y opciones obligatorias ausentes. Acepta los valores con la grafía exacta de la consigna. Un valor de `-pass` que empieza con `-` se toma como contraseña. Genera el texto de ayuda. |
| `CommandOptions` | Record inmutable: operación, entrada, portador, salida, algoritmo de esteganografía y `Optional<EncryptionSettings>`. |
| `EncryptionSettings` | Algoritmo, modo y contraseña ya con defaults aplicados. |
| `Operation`, `StegAlgorithm`, `CipherAlgorithm`, `CipherMode` | Enums de la CLI. |
| `UsageException` | Error de uso (código de salida 2). |

**Defaults de la consigna:** solo `-pass` → AES-128 CBC; algoritmo + pass sin modo → CBC; modo + pass sin algoritmo → AES-128; sin `-pass` → no se cifra (incluso si se dieron `-a`/`-m`; interpretación documentada).

### 3.2 `bmp` — lectura y escritura de BMP (consigna: BMP V3, 24 bits, sin compresión)
| Clase | Responsabilidad |
|---|---|
| `BmpReader` | Lee y valida: firma `BM`, cabecera V3, 24 bpp, sin compresión, offset de píxeles, tamaños; little endian. Rechaza formatos no soportados con un error claro. |
| `BmpHeader` | Offset de píxeles, ancho y alto. |
| `BmpImage` | Modelo inmutable. Separa cuatro zonas: cabecera, *gap* entre cabecera y píxeles, **cuerpo** (píxeles incluyendo el padding de filas a múltiplos de 4 bytes) y *trailing*. `copyBody()` / `withBody()` permiten modificar solo el cuerpo; `toBytes()` reconstruye el archivo. |
| `BmpWriter` | Escribe la imagen conservando byte a byte todo lo que no es cuerpo. |
| `BmpFormatException` | Formato inválido o no soportado. |

El cuerpo incluye el padding de filas porque el enunciado indica que se recorre el arreglo de bytes de la imagen tal cual está en el archivo.

### 3.3 `steganography` — LSB1, LSB4 y LSBI
| Clase | Responsabilidad |
|---|---|
| `Steganography` | Interfaz común. Conserva métodos body-only para LSB1/LSB4 y ofrece overloads que reciben `CarrierLayout` para algoritmos que necesitan la geometría BMP. |
| `Lsb1` | Un bit del mensaje por byte del cuerpo, en el bit menos significativo, recorriendo los bytes en orden físico. Capacidad `⌊cuerpo/8⌋`. Solo cambia los LSB de los bytes necesarios; el resto queda intacto. |
| `Lsb4` | Cuatro bits consecutivos del mensaje en el nibble bajo de cada byte del cuerpo; usa dos bytes de portador por byte de mensaje. Capacidad `⌊cuerpo/2⌋`; conserva el nibble alto y el resto del cuerpo. Nibble alto primero, confirmado con el ejemplo oficial. |
| `Lsbi` | Usa B y G de píxeles en orden físico B→G; omite R y padding. Reserva los primeros cuatro bytes físicos para flags LSB1 de los patrones `00,01,10,11`; invierte por patrón solo si `changed > unchanged`. Usa `BitOrder.DEFAULT` para reconstruir bytes. |
| `CarrierLayout` | Describe ancho, filas almacenadas y stride; permite localizar B/G y padding sin acoplar la capa a `BmpImage`. |
| `StegoReader` | Lectura secuencial del flujo oculto (`read`, `remainingBytes`). |
| `BitOrder` | Orden de transmisión compartido (MSB primero por defecto); permite probar variantes explícitas. |
| `CapacityMath` | Cálculo del tamaño máximo de archivo por capacidad, con y sin padding de bloque. |
| `CapacityExceededException`, `StegoDataException` | Capacidad insuficiente (con bytes requeridos y disponibles); lectura más allá del cuerpo. |

### 3.4 `payload` — formato del contenido oculto (consigna: estructura de lo que se oculta)
Independiente del algoritmo de esteganografía y de la criptografía.

Formato sin cifrado: `tamaño real (4 bytes BE) || contenido || extensión ('.xxx' + 0x00)`.
Formato con cifrado: `tamaño del ciphertext (4 bytes BE) || ciphertext`, donde el plaintext cifrado es la estructura completa anterior (tamaño + contenido + extensión).

| Clase | Responsabilidad |
|---|---|
| `HiddenFile` | Contenido + extensión del archivo a ocultar. |
| `FileExtension` | Valida y representa la extensión (empieza con `.`, ASCII imprimible sin `/` ni `\`, termina en NUL); `fromFileName` toma el sufijo desde el último `.`; `appendTo(path)` la agrega al nombre de salida. Archivo sin extensión se codifica como `.\0`. |
| `PayloadCodec` | `serializeFile`, `readFile(ByteSource)` (secuencial, se detiene en el NUL), `parseFile(byte[])` (estricto: rechaza bytes sobrantes), `frameCiphertext`, `readCiphertextBlock` (valida el tamaño declarado antes de reservar memoria), `plainStreamLength`, `framedLength`. |
| `ByteSource`, `ByteArraySource` | Abstracción de fuente de bytes secuencial (y su implementación en memoria). |
| `PayloadFormatException` | Error con motivo explícito: `TRUNCATED`, `SIZE_INVALID`, `SIZE_EXCEEDS_AVAILABLE`, `CIPHERTEXT_SIZE_ZERO`, `EXTENSION_MISSING_DOT`, `EXTENSION_INVALID_CHAR`, `EXTENSION_UNTERMINATED`, `TRAILING_BYTES`, `FILE_TOO_LARGE`. |

Se distinguen siempre el tamaño real del archivo y el tamaño del ciphertext (en modos con padding son distintos).

### 3.5 `crypto` — cifrado por contraseña (consigna: algoritmos, modos y derivación de clave)
Trabaja solo con `byte[]`; no conoce Payload, BMP ni esteganografía.

| Clase | Responsabilidad |
|---|---|
| `CryptoAlgorithm`, `CryptoMode`, `CipherSpec` | Algoritmos AES-128/192/256 y 3DES; modos ECB, CBC, CFB8, OFB. `CipherSpec` valida la combinación, indica longitud de clave/IV/bloque, si hay padding y calcula `ciphertextLength(n)`. |
| `KeyDerivation` / `Pbkdf2KeyDerivation` | PBKDF2-HMAC-SHA256, 10.000 iteraciones, salt fijo de 8 bytes en cero; se derivan `clave ‖ IV` en una sola operación (clave primero), como `openssl enc -pbkdf2`. Contraseña en UTF-8. |
| `DerivedKey` | Material derivado (clave + IV); `AutoCloseable`, se pone a cero al cerrar, sin exponer secretos en `toString()`. |
| `SymmetricCipher` | Único lugar con JCA (`javax.crypto`): una instancia de `Cipher` nueva por operación (sin compartir estado), `PKCS5Padding` en ECB/CBC, `NoPadding` en CFB8/OFB, sin IV en ECB. Una sola lógica para AES y 3DES. |
| `PayloadCipher` | Interfaz `encrypt(byte[])`, `decrypt(byte[])`, `ciphertextLength(long)`. |
| `PasswordPayloadCipher` | Implementación: deriva la clave, usa `SymmetricCipher` y borra los arreglos sensibles (incluidas las copias temporales). |
| `CryptoException` y subclases | `InvalidCryptoParametersException`, `KeyDerivationException`, `EncryptionException`, `DecryptionException` → `InvalidPaddingException`, `InvalidCiphertextException`. |

Sin MAC ni autenticación (no los pide la consigna): una contraseña incorrecta se detecta con alta probabilidad en ECB/CBC por el padding, pero **no** de forma criptográfica en CFB8/OFB.

### 3.6 `app` — integración (consigna: flujo completo EMBED/EXTRACT)
| Clase | Responsabilidad |
|---|---|
| `Embedder` | Orquesta EMBED: leer archivo y BMP, armar `HiddenFile`, calcular capacidad (considerando padding), serializar, cifrar y enmarcar si corresponde, incrustar, escribir el BMP. |
| `Extractor` | Orquesta EXTRACT: leer BMP, recuperar el payload, descifrar si corresponde, parsear estrictamente y escribir `-out` + extensión. |
| `StegoReaderSource` | Adapta `StegoReader` a `ByteSource` (lectura fuera de rango → `TRUNCATED`). |
| `CipherSpecs` | Traduce enums de la CLI a los de `crypto` (`cfb` → CFB8). |
| `Steganographies` | Fábrica de LSB1, LSB4 y LSBI. |
| `OperationException` | Error de operación con mensaje para el usuario. |

### 3.7 `Main`
Parsea; si hay error de uso imprime el mensaje y sale con 2; si piden ayuda imprime y sale con 0; si no ejecuta `Embedder`/`Extractor`. Un `OperationException` imprime `Error: …` en stderr y sale con 1. El éxito es silencioso. El código 3 queda reservado para un algoritmo que la CLI acepte pero no esté implementado.

| Código | Significado |
|---|---|
| 0 | éxito / ayuda |
| 1 | error de operación (archivo inexistente, BMP inválido, capacidad insuficiente, payload corrupto, error de descifrado) |
| 2 | error de uso |
| 3 | algoritmo aceptado pero aún no implementado |

## 4. Cumplimiento de la consigna

| Requisito | Dónde / cómo |
|---|---|
| Opciones `-embed -extract -in -p -out -steg -a -m -pass` | `ArgumentParser`, `ArgumentParserTest` (70 tests) |
| Defaults de cifrado (solo pass → AES-128 CBC, etc.) | `ArgumentParser` + `EndToEndLsb1Test` (defaults reales) |
| BMP V3 24 bits sin compresión; padding de filas | `BmpReader`, `BmpImage`; tests con padding, gap y trailing |
| LSB1 | `Lsb1`, `Lsb1Test` con vectores calculados a mano |
| Estructura `tamaño ‖ contenido ‖ extensión\0` | `PayloadCodec`, `PayloadCodecTest` |
| Con cifrado: `tamaño del cifrado ‖ cifrado(estructura completa)` | `PayloadCodec.frameCiphertext` + `Embedder` |
| Extensión con `.` y terminador NUL; recuperar el archivo con su extensión | `FileExtension`, `Extractor` |
| AES-128/192/256, 3DES; ECB, CBC, CFB8, OFB | `CipherSpec`, `SymmetricCipher`; 16 combinaciones en `PasswordPayloadCipherTest` y pruebas end-to-end |
| PBKDF2-HMAC-SHA256, 10.000 iter., salt de 8 bytes en cero | `Pbkdf2KeyDerivation`, vectores PBKDF2 |
| Compatibilidad con OpenSSL | `OpenSslInteropIT` (97 tests, Java ↔ OpenSSL en ambos sentidos; OpenSSL 3.6.2 y LibreSSL 3.3.6) |
| Ejemplo de la consigna: 414 bytes + `.txt` = 423 bytes, ciphertext de 432 | test dedicado en crypto y capacidad en `EndToEndLsb1Test` |
| LSB1, LSB4 y LSBI | Implementaciones y vectores manuales (`Lsb1Test`, `Lsb4Test`, `LsbiTest`); LSBI además en `EndToEndLsbiTest`. |
| Ejecución con el JAR real | `PackagedJarIT`, incluidos round-trips LSBI con y sin cifrado |
| Capacidad insuficiente | `Embedder` valida antes de escribir; tests de capacidad exacta y −1 |
| LSB4 | Implementada; nibble alto primero confirmado con `ladoLSB4.bmp`. |
| LSBI | `Lsbi`, vectores manuales, integración CLI y JAR; B→G/MSB-first confirmados con tres ejemplos oficiales. |

## 5. Pruebas

- **Unitarias** (`mvn clean test`, Surefire): 482 tests — CLI 70, BMP 52, esteganografía 47, Payload 39, Crypto 191 (incluye vectores NIST/PBKDF2), integración `app` 6, end-to-end LSB1/LSB4/LSBI 73 y `MainTest` 4.
- **Integración** (`mvn clean verify`, Failsafe): 109 tests — `OpenSslInteropIT` 97, `PackagedJarIT` 10, `BmpRoundTripIT` 2.
- Resultado: 0 fallos, 0 omitidos.
- Runtime verificado: Amazon Corretto 21.0.10 y OpenSSL 3.6.2.
- Si OpenSSL no está instalado, sus tests se omiten; si está instalado y se comporta de forma inesperada, fallan.

## 6. Decisiones provisionales y ambigüedades abiertas

La consigna no las resuelve; están aisladas en el código y documentadas. Algunas decisiones ya se contrastaron con ejemplos oficiales; el detalle y el alcance exacto están en [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md). No todas las combinaciones cuentan con un vector oficial.

1. **CFB8 vs CFB128:** se implementa CFB de 8 bits; el ejemplo oficial 3DES-CFB8 fue validado.
2. **OFB con 3DES:** bloque completo de 8 bytes; no hay muestra oficial de 3DES-OFB.
3. **Partición clave/IV:** una sola derivación PBKDF2 de `clave ‖ IV` (convención de OpenSSL); tres combinaciones oficiales fueron validadas.
4. **Contraseña:** UTF-8 sin normalización; la contraseña vacía se acepta.
5. **Archivo sin extensión:** se codifica `.\0`.
6. **Nombre de salida:** `-out` + extensión recuperada, siempre concatenadas.
7. **Orden de bits dentro del byte (LSB1):** MSB primero; LSB1 y LSBI se contrastaron con ejemplos oficiales.
8. **`-a`/`-m` sin `-pass`:** no se cifra.
9. **Contraseña incorrecta:** no es detectable criptográficamente en CFB8/OFB y solo probabilísticamente en ECB/CBC.
10. **LSB4:** nibble alto primero confirmado con `ladoLSB4.bmp`.
11. **Criptografía oficial:** se validaron AES-128-CBC, AES-256-OFB y 3DES-CFB8; los demás casos oficiales no están provistos.

## 7. Próximos pasos

La compatibilidad de seis ejemplos oficiales está documentada. Próximo: completar los requisitos de estegoanálisis y validar el proyecto en Pampero o entorno equivalente; no se declara finalizado el TP.
