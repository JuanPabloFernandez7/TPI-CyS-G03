# stegobmp — Módulos en profundidad y estrategia de testing

Complemento de [`IMPLEMENTATION_OVERVIEW.md`](IMPLEMENTATION_OVERVIEW.md). Aquí se explica **qué es y por qué existe cada carpeta** (paquete) y **qué tipos de pruebas se hacen**. El detalle por clase está en el documento general.

Todo el código vive en `src/main/java/ar/edu/itba/stegobmp/`; los tests replican la misma estructura en `src/test/java/ar/edu/itba/stegobmp/`.

---

## Parte A — Las carpetas

### Idea central: capas independientes

El problema se dividió en piezas que **no se conocen entre sí**. Cada una resuelve una sola pregunta y solo depende del JDK:

| Carpeta | Pregunta que responde | Entrada → salida |
|---|---|---|
| `cli` | ¿Qué pidió el usuario? | `String[]` → `CommandOptions` |
| `bmp` | ¿Cómo está armada la imagen? | archivo `.bmp` ↔ `BmpImage` |
| `steganography` | ¿Cómo se esconden bits en los píxeles? | cuerpo de la imagen + bytes ↔ cuerpo modificado |
| `payload` | ¿Qué bytes exactos se esconden? | archivo + extensión ↔ flujo de bytes |
| `crypto` | ¿Cómo se cifra y descifra ese flujo? | `byte[]` ↔ `byte[]` |
| `app` | ¿En qué orden se conectan las piezas? | `CommandOptions` → archivo de salida |
| `Main` | ¿Cómo se comunica con el usuario? | argumentos → código de salida y mensajes |

Ventajas de esta separación:
- Cada capa se probó y aprobó por separado antes de pasar a la siguiente.
- LSBI vive en `steganography`; `app` le entrega `CarrierLayout` para seleccionar B/G y omitir padding. `payload`, `crypto`, `bmp` y `cli` no incorporan lógica específica de LSBI.
- El cifrado puede probarse contra OpenSSL sin involucrar imágenes, y el formato del payload sin involucrar cifrado.

---

### `cli/` — la interfaz de línea de comandos

**Qué es:** el traductor entre lo que escribe el usuario y una configuración que el resto del programa entiende. Es puramente sintáctico y semántico a nivel de argumentos: no abre archivos ni sabe qué es un BMP.

**Qué hace:**
- Reconoce las nueve opciones de la consigna (`-embed -extract -in -p -out -steg -a -m -pass`).
- Valida: argumentos desconocidos, opciones repetidas, valores faltantes, combinaciones inválidas (por ejemplo `-in` en extract, o embed y extract juntos) y opciones obligatorias ausentes. Cada error dice cuál es el argumento problemático.
- Aplica los **defaults de cifrado** de la consigna (solo `-pass` → AES-128 CBC; algoritmo sin modo → CBC; modo sin algoritmo → AES-128; sin `-pass` → sin cifrado).
- Produce el texto de ayuda.
- Un valor de `-pass` que empieza con `-` se interpreta como contraseña, no como otra opción.

**Qué entrega:** un `CommandOptions` inmutable. El cifrado se representa con `Optional<EncryptionSettings>`: vacío significa "no cifrar", de modo que el resto del programa no necesita repetir las reglas de defaults.

**Por qué así:** si el parser decidiera además cómo ocultar o cifrar, no podría probarse sin archivos ni claves. Al devolver solo datos, se prueba con listas de strings.

---

### `bmp/` — el formato de imagen

**Qué es:** lectura, validación y escritura de BMP V3 de 24 bits sin compresión, que es lo único que admite la consigna.

**Qué hace:**
- Valida la firma `BM`, la versión del encabezado, los 24 bits por píxel, la ausencia de compresión y la coherencia de offsets y tamaños. Si algo no corresponde, falla con un mensaje claro en lugar de leer basura.
- Interpreta los campos en **little endian**.
- Divide el archivo en cuatro zonas: **cabecera**, **gap** (bytes entre la cabecera y el inicio de los píxeles, si el offset lo deja), **cuerpo** (los píxeles, incluyendo el **padding** al final de cada fila para que mida un múltiplo de 4 bytes) y **trailing** (bytes después de los píxeles).
- Permite reemplazar solo el cuerpo y reconstruir el archivo con todo lo demás **idéntico byte a byte**.

**Por qué importa el cuerpo con padding:** LSB1 y LSB4 recorren los bytes del cuerpo, padding incluido. LSBI selecciona solo componentes B/G y omite padding para datos; si un byte de padding cae dentro de los primeros cuatro bytes físicos, puede alojar un flag obligatorio. Separar las cuatro zonas garantiza que ocultar datos nunca altere cabecera, gap ni trailing.

**Qué no hace:** no conoce LSB ni payload. Solo entrega y recibe un arreglo de bytes de píxeles.

---

### `steganography/` — los algoritmos de ocultamiento

**Qué es:** la lógica de esconder y leer bits dentro del cuerpo de la imagen. Incluye LSB1, LSB4 y LSBI.

**Qué hace:**
- Define la interfaz `Steganography` (capacidad, incrustar, abrir lector), con overloads de contexto para algoritmos que necesitan geometría. Es el **punto de extensión** común.
- `Lsb1`: guarda 1 bit del mensaje en el bit menos significativo de cada byte del cuerpo, en orden físico. Capacidad `⌊bytes del cuerpo / 8⌋`.
- `Lsb4`: guarda 4 bits del mensaje en el nibble menos significativo de cada byte del cuerpo. Usa dos bytes de portador por byte de mensaje; capacidad `⌊bytes del cuerpo / 2⌋`. El nibble alto del mensaje se transmite primero como decisión provisional explícita.
- `Lsbi`: guarda el flujo en B/G, en orden físico B→G, con flags en los primeros cuatro bytes. Calcula la inversión por patrón con la regla estricta `changed > unchanged`; salta R y padding.
- `CarrierLayout`: traduce ancho, filas y stride a posiciones de componentes B/G sin que `steganography` dependa de `bmp`.
- El orden de bits y nibbles está aislado en `BitOrder`; puede probarse la variante inversa con vectores independientes. LSBI usa MSB-first por defecto.
- Calcula capacidades máximas (`CapacityMath`) y distingue los errores de capacidad insuficiente de los de lectura fuera de rango.

**Qué no hace:** no sabe dónde termina el mensaje. Entrega un flujo secuencial de bytes; la capa `payload` decide hasta dónde leer.

---

### `payload/` — el formato de lo que se oculta

**Qué es:** la definición exacta de los bytes que viajan dentro de la imagen, independiente del algoritmo LSB y del cifrado.

**Formato (consigna):**
- Sin cifrado: `tamaño real (4 bytes, big endian) ‖ contenido ‖ extensión` donde la extensión empieza con `.` y termina con un byte NUL (`.txt\0`).
- Con cifrado: `tamaño del ciphertext (4 bytes, big endian) ‖ ciphertext`, donde el plaintext cifrado es la estructura anterior completa.

**Qué hace:**
- Serializa y deserializa (`PayloadCodec`), tanto leyendo de una fuente secuencial (se detiene en el NUL, ignorando lo que sobre en la imagen) como analizando un plaintext ya descifrado (estricto: rechaza bytes de más).
- Valida y modela la extensión (`FileExtension`), incluyendo archivos sin extensión.
- Distingue siempre el **tamaño real del archivo** del **tamaño del ciphertext** (con padding son distintos).
- Detecta y nombra los errores: payload truncado, tamaños inválidos o excedidos, extensión mal formada, NUL ausente, bytes sobrantes.
- Valida el tamaño declarado **antes** de reservar memoria, para que un tamaño enorme y falso no cause un fallo por memoria.
- Calcula longitudes (`plainStreamLength`, `framedLength`) para que la capa `app` verifique capacidad sin serializar.

**Qué no hace:** no cifra ni sabe de píxeles. Lee de una abstracción `ByteSource`.

---

### `crypto/` — el cifrado

**Qué es:** el cifrado y descifrado de `byte[]` con una contraseña, compatible con `openssl enc -pbkdf2`.

**Qué hace:**
- Soporta AES-128/192/256 y 3DES con los modos ECB, CBC, CFB8 y OFB (16 combinaciones).
- **Deriva la clave** con PBKDF2-HMAC-SHA256, 10.000 iteraciones y salt fijo de 8 bytes en cero. Deriva `clave ‖ IV` en una sola operación (clave primero), igual que OpenSSL; en ECB no hay IV.
- Usa padding PKCS5 en ECB/CBC y ninguno en CFB8/OFB, por lo que el tamaño del ciphertext depende del modo (por ejemplo, 423 bytes de plaintext dan 432 en CBC y 423 en CFB8/OFB).
- Concentra todo el uso de JCA en `SymmetricCipher`: una sola lógica para AES y 3DES, una instancia de `Cipher` nueva por operación.
- Borra de memoria claves, IV y copias temporales cuando termina.
- Define excepciones específicas: parámetros inválidos, derivación, cifrado, descifrado, padding inválido y ciphertext incompatible. Los mensajes y `toString()` nunca incluyen contraseñas, claves ni IV.

**Qué no hace:** no agrega MAC, IV aleatorio ni formato propio (la consigna no los pide). Por eso **no autentica**: una contraseña incorrecta se detecta solo de forma probabilística en ECB/CBC (por el padding) y no de forma criptográfica en CFB8/OFB.

**Decisiones provisionales** aisladas en esta carpeta: CFB de 8 bits, OFB de bloque completo con 3DES, la partición clave/IV y la codificación UTF-8 de la contraseña.

---

### `app/` — la integración

**Qué es:** el único lugar donde se conectan todas las capas. Contiene el **orden de las operaciones**, nada más.

**Embed:** leer archivo → leer BMP → armar el archivo oculto → calcular capacidad (con o sin padding según el modo) → serializar → cifrar y enmarcar si corresponde → incrustar en el cuerpo → escribir el BMP. Si la capacidad no alcanza, falla antes de escribir.

**Extract:** leer BMP → leer el payload del portador → descifrar si corresponde → analizar estrictamente → escribir `-out` + extensión recuperada, solo después de validar todo.

**Piezas auxiliares:** un adaptador entre el lector esteganográfico y la fuente de bytes del payload, un traductor de enums de CLI a enums de crypto, una fábrica de algoritmos y una excepción con mensajes para el usuario. `Embedder` y `Extractor` crean el `CarrierLayout` a partir de `BmpHeader`.

**Por qué es aparte:** si los contratos de las capas cambian, el impacto queda acotado a esta carpeta. Y es la única que sabe, por ejemplo, que ECB/CBC ocupan más bytes que CFB8/OFB.

---

### `Main` — el punto de entrada

**Qué es:** el límite entre el programa y el sistema operativo.

**Qué hace:** invoca al parser, decide qué ejecutar y traduce el resultado a **códigos de salida** y mensajes:

| Código | Significado |
|---|---|
| 0 | éxito o ayuda (el éxito no imprime nada) |
| 1 | error de operación: archivo inexistente, BMP inválido, capacidad insuficiente, payload corrupto, fallo de descifrado |
| 2 | error de uso (argumentos) |
| 3 | algoritmo válido pero todavía no implementado |

Los errores van a stderr con el prefijo `Error:`. La lógica real está en `run(...)`, que es la que invocan los tests para no depender de un proceso aparte.

---

## Parte B — Tipos de pruebas

La validación de LSBI con Amazon Corretto 21.0.10 y OpenSSL 3.6.2 ejecutó 482 pruebas unitarias y 109 de integración, sin fallos ni omisiones. La convención es: `*Test` corre en la fase `test` (Surefire) y `*IT` corre en `verify` (Failsafe), después de empaquetar el JAR. Una etapa se considera terminada solo cuando sus pruebas pasan; compilar no alcanza.

### B.1 Pruebas unitarias por capa

Cada capa se prueba **aislada**, sin depender de las otras.

| Capa | Qué se verifica |
|---|---|
| `cli` | Cada opción válida; defaults de cifrado (los tres casos); desconocidos, duplicados, faltantes; combinaciones inválidas; ayuda y códigos de salida; contraseñas que empiezan con `-`; grafía exacta de los valores. |
| `bmp` | Lectura de un BMP válido sintético; rechazo de firma errónea, otras profundidades de color, compresión, offsets incoherentes y archivos truncados; padding de filas; gap y trailing; escritura que conserva todo byte a byte. |
| `steganography` | Vectores calculados **a mano** para LSB1, LSB4 y LSBI; los cuatro patrones, inversión y empate, almacenamiento/recuperación de flags; capacidad exacta e insuficiente; preservación de R/padding y bytes no usados; lecturas fuera de rango; `CapacityMath` contra fuerza bruta. |
| `payload` | Vectores calculados a mano; round-trip con y sin extensión; extensiones de distinta longitud; archivo vacío; truncados; tamaños negativos o inconsistentes; NUL ausente; bytes sobrantes (`readFile` los ignora y `parseFile` los rechaza); alfabeto de la extensión (`.tar gz` válida, `.café` rechazada); límites de longitud. |
| `crypto` | Round-trip de las 16 combinaciones; longitudes de plaintext 0, 1, un bloque, múltiplos y no múltiplos (incluyendo 423); tamaño exacto del ciphertext; contraseñas cortas, largas y no ASCII; padding inválido y ciphertext incompatible; controles negativos que muestran que CFB8 ≠ CFB128; reutilización de una misma instancia y uso concurrente; que nada secreto aparezca en mensajes. |
| `app` | El adaptador `StegoReader` → `ByteSource`, el mapeo de enums de CLI a crypto (`cfb` → CFB8) y la fábrica de algoritmos. |
| `Main` | Códigos de salida y mensajes sin lanzar un proceso. |

### B.2 Vectores conocidos (pruebas contra referencias externas)

Evitan que el código "se pruebe contra sí mismo":
- **PBKDF2-HMAC-SHA256:** vectores publicados, y los parámetros exactos de la consigna (10.000 iteraciones, salt de 8 bytes en cero).
- **NIST:** vectores oficiales de AES para los modos y tamaños que cubren.
- **Fixtures de OpenSSL** (`src/test/resources/crypto/`): archivos cifrados con `openssl enc -pbkdf2 -iter 10000 -md sha256 -S 0000000000000000`, para todos los algoritmos y modos, con contraseña ASCII, no ASCII y vacía. Un `MANIFEST.txt` registra la versión de OpenSSL y los comandos, y el SHA-256 de cada fixture. Se verifica que Java produce exactamente esos bytes y que los descifra.

### B.3 Interoperabilidad con OpenSSL (integración, `OpenSslInteropIT`)

Prueba real, con procesos externos:
- **Java cifra → OpenSSL descifra**, y **OpenSSL cifra → Java descifra**, para todos los algoritmos y modos (97 pruebas).
- Un harness reconoce estrictamente el formato de salida de OpenSSL: si aparece un encabezado inesperado, la prueba **falla** en lugar de ignorarlo.
- Si OpenSSL no está instalado, estas pruebas se omiten; si está instalado y se comporta distinto de lo esperado, fallan.
- Esta ejecución se corrió con OpenSSL 3.6.2; ejecuciones previas también pasaron con LibreSSL 3.3.6. No se afirma compatibilidad con 3.0.x porque no se verificó.

Detecta errores de derivación de clave, de IV, de padding y de tamaño de bloque, que son los que rompen la compatibilidad con la cátedra.

### B.3 bis Pruebas de contrato entre capas

- `CryptoPayloadCompatibilityTest` verifica que `crypto` cumple lo que `payload` necesita (por ejemplo: la estructura de 423 bytes da un ciphertext de 432 bytes en CBC) sin que un paquete importe al otro en el código de producción.
- `PayloadLsb1CompatibilityTest` verifica que un payload serializado viaja intacto por LSB1.

### B.4 Pruebas de integración end-to-end (`EndToEndLsb1Test`, `EndToEndLsb4Test`, `EndToEndLsbiTest`)

Ejecutan el flujo completo (`Main.run`) con BMP sintéticos y archivos temporales, sin mocks:
- Embed + extract sin cifrado y con las 16 combinaciones; defaults de CLI; `-a`/`-m` sin `-pass` (no cifra).
- `EndToEndLsb4Test` verifica round-trip sin cifrado y con las 16 combinaciones, defaults, capacidad exacta/insuficiente y preservación de BMP con gap/padding/trailing.
- `EndToEndLsbiTest` verifica round-trip sin cifrado, archivo vacío y extensionless, capacidad exacta/insuficiente, BMP top-down con padding/gap/trailing, corrupción y las 16 combinaciones criptográficas.
- Verificación **byte a byte** del archivo recuperado, incluyendo archivo vacío, sin extensión y con distintas extensiones.
- **Capacidad exacta y capacidad insuficiente**, incluyendo las diferencias por padding (ejemplos calculados: con AES-CBC se necesitan 36 bytes enmarcados donde CFB8 necesita menos).
- BMP con padding de filas, gap y trailing: todo fuera de los LSB queda idéntico, y los bytes posteriores al mensaje no se tocan.
- Que el BMP de entrada y el archivo de entrada no se modifiquen.
- Errores: archivo inexistente, "no es un BMP", BMP de otra profundidad, salida no escribible, imagen sin datos ocultos, payload corrupto armado a mano, ciphertext truncado, algoritmo o modo equivocado.
- Contraseña incorrecta: en ECB/CBC se detecta en la gran mayoría de los casos (no en todos, por diseño del padding); en CFB8/OFB nunca se detecta criptográficamente, y se verifica que **nunca se recupere el archivo original**.
- Que la contraseña no aparezca en los mensajes de error.

### B.5 Pruebas del JAR empaquetado (`PackagedJarIT`, fase `verify`)

Lanzan `java -jar` como proceso aparte con los comandos de la consigna: ayuda, error de uso (2), error de operación (1) y round-trips reales para LSB1, LSB4 y LSBI con cifrado y sin él (10 tests en total). Prueban lo que realmente entregamos (el JAR), no solo las clases.

### B.6 Pruebas de ida y vuelta de BMP (`BmpRoundTripIT`)

Leer y volver a escribir un BMP no debe alterar un solo byte.

### B.7 Datos de prueba

- `BmpTestData`: generador de BMP sintéticos (ancho, alto, gap, trailing) con contenido pseudoaleatorio de **semilla fija**, para que los fallos sean reproducibles.
- `CryptoTestData` y `OpenSslHarness`: utilitarios para vectores y para invocar OpenSSL.
- No se usan resultados aleatorios sin semilla fija.

### B.8 Lo que las pruebas **no** pueden garantizar

El suite automatizado se complementa con la validación externa de seis BMP oficiales, detallada en [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md). Esos ejemplos confirman LSB1/LSB4/LSBI y tres combinaciones cifradas, pero no cubren todos los modos, algoritmos ni decisiones (por ejemplo, UTF-8, contraseña vacía, OFB con 3DES o `.\0` para archivos sin extensión). La validación del estegoanálisis completo y del entorno de entrega sigue pendiente.

---

## Cómo ejecutar

```
export JAVA_HOME=<JDK 21>
mvn clean test      # unitarias
mvn clean verify    # unitarias + integración (incluye el JAR y OpenSSL)
mvn clean verify -Dstegobmp.openssl=/usr/bin/openssl   # usar otro binario de OpenSSL (p. ej. LibreSSL)
```
