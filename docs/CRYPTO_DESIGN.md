# Diseño técnico de la capa criptográfica

Estado: **propuesta en revisión (2.ª ronda). No hay código de criptografía.**

Convención de respaldo usada en todo el documento:
- **[C]** respaldado por el texto de la consigna;
- **[P]** respaldado por una prueba reproducible (comando indicado);
- **[H]** hipótesis: no está en la consigna ni se pudo comprobar con una muestra de la cátedra.

**La compatibilidad con los archivos de la cátedra NO está garantizada.** Solo se comprobó compatibilidad con OpenSSL y con vectores públicos. Las convenciones A1, A2 y A3 siguen siendo hipótesis hasta contar con una muestra cifrada real (ver §13).

Referencias: «Consigna» = `Trabajo Practico Implementacion-2026_2.pdf`; Payload = [`PAYLOAD_DESIGN.md`](PAYLOAD_DESIGN.md); decisiones previas = D-004 en [`TECHNICAL_DECISIONS.md`](TECHNICAL_DECISIONS.md); capacidad = [`LSB1_DESIGN.md`](LSB1_DESIGN.md) §5.

## 0. Evidencia empírica usada en este diseño

Antes de diseñar se hicieron comprobaciones **descartables, fuera del repositorio**, comparando la JCE de Java 21.0.10 (Corretto, proveedor SunJCE) con **OpenSSL 3.6.2** (`/opt/homebrew/bin/openssl`, `OpenSSL 3.6.2 7 Apr 2026`) y, donde se indica, con **LibreSSL 3.3.6** (`/usr/bin/openssl`, el de macOS). Todo lo marcado «verificado» se observó en esa ejecución; lo demás no. **No se probó OpenSSL 1.1.1 ni 3.0.x.**

Binario usado en cada ensayo:

| Ensayo | Java | OpenSSL 3.6.2 | LibreSSL 3.3.6 |
|---|---|---|---|
| E1 (PBKDF2, `-P`) | sí | sí | sí (§4.1) |
| E2, E3 (cifrado, 10 longitudes y casos especiales) | sí | sí | **no** |
| E12 (matriz 4 alg. × 4 modos × 6 longitudes) | sí | sí | sí |
| E13–E15 (encabezado `Salted__`, `-S`, `-K/-iv`) | — | sí | sí |
| E16, E17 (vectores públicos) | sí | sí (E16) | no |

| # | Hecho | Resultado |
|---|---|---|
| E1 | PBKDF2-HMAC-SHA256, 10.000 iteraciones, salt `00×8`, `dkLen = 32` con la contraseña `oculto` | Java = `e5fb03e4abd3d239cbb4f0aaa357240120efcd7a8c7d9986b0bf3089eff2d9b7`. `openssl enc -P` imprime `key=E5FB03E4ABD3D239CBB4F0AAA3572401` e `iv =20EFCD7A8C7D9986B0BF3089EFF2D9B7`, es decir **la misma cadena partida: primero la clave y después el IV**. |
| E2 | Cifrado completo Java frente a `openssl enc`, 4 algoritmos × 5 transformaciones × 10 longitudes de plaintext (0, 1, 7, 8, 15, 16, 17, 24, 32, 100) con contraseña no ASCII `contraseña€ñ` | **200/200 idénticos byte a byte**, y OpenSSL descifra lo que cifró Java en todos. |
| E3 | Mismo ensayo con 423 bytes y contraseña `oculto`; contraseña vacía; contraseña de 101 caracteres | 20/20 idénticos en cada uno. |
| E4 | Equivalencias JCE ↔ OpenSSL | `AES/CFB8/NoPadding` = `aes-128-cfb8`; `AES/CFB/NoPadding` = `aes-128-cfb` (feedback de 128 bits); `AES/OFB/NoPadding` = `aes-128-ofb`. Lo mismo con `DESede` y `des-ede3-*`. **CFB, CFB8 y OFB producen salidas distintas entre sí** (verificado). |
| E5 | Tamaño del ciphertext para 423 bytes de plaintext | **AES-ECB/CBC: 432. 3DES-ECB/CBC: 424.** CFB8/OFB: 423. |
| E6 | `openssl enc -pbkdf2 … -S 0000000000000000` (cifrar) | **Difieren.** OpenSSL 3.6.2: salida sin encabezado (23 bytes de entrada → 32 de salida). LibreSSL 3.3.6: antepone `Salted__` + salt = 16 bytes (→ 48 de salida, empieza con `53616c7465645f5f0000000000000000`). Ver E13. |
| E7 | Java con la contraseña incorrecta en AES-CBC (600 intentos) | 598 fallan con `BadPaddingException`; **2 pasan el padding por casualidad**. |
| E8 | CBC con ciphertext vacío | **Java no lanza error** y devuelve 0 bytes. Con 15 bytes lanza `IllegalBlockSizeException`. |
| E9 | `ECB` con `IvParameterSpec`; IV de 8 bytes en AES | Ambos lanzan `InvalidAlgorithmParameterException`. |
| E10 | NFC frente a NFD de `contraseña` | Claves **distintas**: no hay normalización Unicode. |
| E11 | Decodificación de argumentos en macOS con `LC_ALL=C` | `sun.jnu.encoding=UTF-8` incluso con `LC_ALL=C`. **No se pudo verificar en Linux** (Pampero/WSL), ver riesgo R3. |
| E12 | Java frente a OpenSSL 3.6.2 **y** LibreSSL 3.3.6: 4 algoritmos × {ECB, CBC, CFB8, OFB} × longitudes {0, 1, 15, 16, 17, 423}, contraseña `contraseña€ñ` | **96/96 idénticos** entre los tres (en LibreSSL, tras quitar el encabezado de E6). Los 16 nombres de cifrador existen en ambos binarios. |
| E13 | Manejo del encabezado `Salted__` al **descifrar** (entrada generada por Java, `-d`) | OpenSSL 3.6.2: con `-S` descifra la entrada **sin** encabezado (OK) y **falla** con la entrada con encabezado. LibreSSL: con `-S` **falla** con la entrada sin encabezado (`bad magic number`) y descifra la que lleva encabezado. **Sin `-S`, con la entrada `Salted__`+`00×8`+ciphertext, ambos descifran correctamente.** |
| E14 | `-nosalt` combinado con `-S` | Ambos ignoran `-S`: `-P` imprime `key=A7EAC0BEA12C2FBDDFAB2999B0C04045`, **otra clave** (salt vacío). `-nosalt` **no** sirve para evitar el encabezado. |
| E15 | `openssl enc -K <clave> -iv <iv>` (sin PBKDF2) | Ambos binarios: sin encabezado y ciphertext idéntico al de Java (AES-128-CBC, 100 bytes → 112). |
| E16 | Vectores PBKDF2-HMAC-SHA-256 de RFC 7914 §11 (`passwd`/`salt`/c=1/dkLen=64 y `Password`/`NaCl`/c=80000/dkLen=64) | Java coincide con el texto de la RFC y con `openssl kdf … PBKDF2` (3.6.2). |
| E17 | Vector NIST SP 800-38A F.3.7 (AES-128 **CFB8**, 18 bytes de plaintext) | Java `AES/CFB8/NoPadding` da `3b79424c9c0dd436bace9e0ed4586a4f32b9`, el valor esperado (de memoria; se transcribirá y confrontará con la norma al implementar). |

**Corrección a documentación anterior.** `PAYLOAD_DESIGN.md` afirmaba que el ejemplo de 423 bytes da 432 en 3DES. El resultado correcto (E5) es **424** (bloque de 8 bytes). Ya se corrigió allí. El código de Payload y sus tests no cambian, porque solo usan 432 como longitud arbitraria de un marco.

## 1. Lo que dice la consigna

| Ref. | Texto | Dónde | Consecuencia |
|---|---|---|---|
| C1 | «No se puede encriptar/desencriptar sin password. Si este dato no está, sólo se esteganografia.» Defaults: algoritmo y password sin modo → CBC; modo y password sin algoritmo → aes128; solo password → aes128 CBC. | §5.1, p. 3 | La capa cripto siempre recibe algoritmo, modo y contraseña ya resueltos. Los defaults ya los aplica el parser de la CLI. |
| C2 | `-a <aes128 \| aes192 \| aes256 \| 3des>`, `-m <ecb \| cfb \| ofb \| cbc>` | §5.1–5.2 | 4 algoritmos × 4 modos = 16 combinaciones. |
| C3 | Con cifrado se oculta `Tamaño cifrado \|\| encripcion(tamaño real \|\| datos \|\| extensión)`. | §5.3, p. 3–4 | La capa cripto cifra el plaintext que arma Payload y no conoce su estructura. |
| C4 | Ejemplo de 423 bytes en 3DES-CBC: «No es múltiplo de bloque así que requiere padding». | §5.3, p. 4 | Consistente con bloque de 8 (423 mod 8 = 7). No da la longitud resultante (ver E5). |
| C5 | «Si el archivo a ocultar requiriera padding usar el padding por defecto de openssl que es PKCS5.» | §5.4, p. 5 | PKCS#5/#7 en los modos que requieren padding. |
| C6 | «El archivo que se encripta es el que se quiere ocultar. Se encripta completo […] cabecera incluida.» | §5.4, p. 5 | Se cifran todos los bytes de entrada sin interpretarlos. |
| C7 | «Para la generación de clave a partir de una password, deberán usar el algoritmo PBKDF2, usando SALT fijo 0x0000000000000000 (8 bytes en 0).» | §5.4, p. 5 | Salt de 8 bytes cero, constante. |
| C8 | «Para modos de feedback considerar una cantidad de 8 bits si es CFB y 128 si es OFB.» | §5.4, p. 5 | CFB → CFB8; OFB → feedback de bloque completo (128 bits en AES). |
| C9 | «El algoritmo 3DES corresponde a Triple Des, con tres claves distintas (des_ede3 en openssl).» | §5.4, p. 5 | Clave de 24 bytes. |
| C10 | «El algoritmo PBKDF2 retorna siempre una cadena de la cual hay que separar key e iv. Para dicho algoritmo, usar SHA256 y 10000 iteraciones.» | §5.4, p. 5 | HMAC-SHA-256, 10.000 iteraciones. **Una sola cadena** que se parte en clave e IV (ver A3). |
| C11 | Tabla «Equivalencias OpenSSL / JCE para modos de Feedback». Primera columna = familia (`CFB`, `OFB`), segunda = OpenSSL, tercera = JCE. Filas: `EVP_aes_128_cfb()` ↔ «AES/CFB/NoPadding» *(corresponde a 128 bits de feedback)*; `EVP_aes_128_cfb1()` **NO tiene**; `EVP_aes_128_cfb8()` ↔ "AES/CFB8/NoPadding" *(corresponde a 8 bits de feedback)*; `EVP_aes_128_ofb()` ↔ «AES/OFB/NoPadding» *(corresponde a 128 bits de feedback)*; «NO tiene» OFB de 1 bit; «NO tiene» "AES/OFB8/NoPadding" *(8 bits)*. | §5.4, p. 5 (líneas finales de la página) | Enumera las variantes disponibles de cada familia y su equivalencia entre OpenSSL y JCE; no elige una. Ver §2.1. |
| C12 | Debe funcionar en Pampero; «Revisar que el uso eventual de cualquier librería sea posible en ese entorno.» | §10, p. 7 | Solo JDK (SunJCE), sin dependencias externas. |

## 2. Ambigüedades y decisiones provisionales

**A1 — CFB de 8 bits o de 128 bits (la más importante).** Detalle completo en §2.1. Resumen: C8 ordena 8 bits para CFB; la tabla C11 solo enumera las variantes. No hay contradicción textual demostrable, pero sí una ambigüedad práctica.

**Decisión provisional [C + H]: CFB8**, por el texto explícito de C8 (y de D-004). Se aísla en un único punto (`CryptoMode.CFB8`), de modo que cambiar a CFB128 sea una línea. El ejemplo oficial 3DES-CFB8 se descifró correctamente; no hay una muestra oficial AES-CFB para resolver por separado esa variante.

**A2 — «OFB de 128 bits» con 3DES.** En 3DES el bloque mide 64 bits, así que un feedback de 128 bits no existe. C8 se escribió pensando en AES.

**Decisión provisional [H]:** OFB con feedback de **bloque completo**: 128 bits en AES y 64 bits en 3DES, que es lo que hacen `AES/OFB` y `DESede/OFB` en JCE y `des-ede3-ofb` en OpenSSL (E4, E12). No existe otra lectura implementable. Lo mismo vale para CFB8 en 3DES: `DESede/CFB8` = `des-ede3-cfb8` (E12), 8 bits en ambos.

**A3 — Cómo se parte la salida de PBKDF2 en clave e IV.** C10 dice «separar key e iv» sin precisar orden ni longitudes. Detalle y comandos en §4.1.

**Decisión provisional [H, con evidencia de compatibilidad con OpenSSL en [P]]:**
- Una sola derivación de `dkLen = keyBytes + ivBytes`.
- `clave = DK[0 .. keyBytes)` e `IV = DK[keyBytes .. keyBytes+ivBytes)`.
- Para ECB no hay IV: `dkLen = keyBytes`.
- Como PBKDF2 es un prefijo estable (`T1‖T2‖…`), la clave de ECB y la de CBC para el mismo algoritmo coinciden; E2 y §4.1 lo confirman.

Es la lectura que hace compatible el resultado con `openssl enc -pbkdf2`. La consigna solo dice «separar key e iv»; los ejemplos oficiales AES-128-CBC, AES-256-OFB y 3DES-CFB8 recuperaron el PNG esperado con esta partición.

### 2.1 CFB8 frente a CFB128: análisis

**Dónde aparece cada cosa (consigna, §5.4 «Otras consideraciones», p. 5):**
1. **8 bits para CFB:** viñeta «Para modos de feedback considerar una cantidad de 8 bits si es CFB y 128 si es OFB.»
2. **128 bits:** en la tabla «Equivalencias OpenSSL / JCE para modos de Feedback», fila `CFB EVP_aes_128_cfb(), “AES/CFB/NoPadding” (corresponde a 128 bits de feedback)`. Esa misma tabla incluye `EVP_aes_128_cfb8() "AES/CFB8/NoPadding" (corresponde a 8 bits de feedback)` y `EVP_aes_128_cfb1() NO tiene`.
3. Las opciones `-m <ecb | cfb | ofb | cbc>` están en §5.1 (p. 2) y §5.2 (p. 3). La consigna **nunca** escribe `cfb8` como valor de `-m`.
4. La consigna no incluye en ningún lugar un ejemplo de entrada y salida con `-m cfb` ni un vector de prueba.

**¿Contradicción real?** No es demostrable. Hay dos lecturas posibles:
- **Lectura A (la adoptada):** la viñeta es la regla («`cfb` = 8 bits, `ofb` = 128 bits») y la tabla es un **diccionario de equivalencias** entre OpenSSL y JCE para cada variante que existe. La primera columna (`CFB`, `OFB`) es la *familia* y agrupa tres filas CFB (cfb, cfb1, cfb8) y tres OFB. La tabla dice cómo se llama el cfb de 8 bits en ambos mundos, que es justo lo que la regla exige.
- **Lectura B:** quien lea la primera fila de la familia `CFB` puede concluir que «CFB» = `AES/CFB` = 128 bits. Solo es compatible con la viñeta si se considera la viñeta un error.

La lectura A hace coherentes las dos citas sin descartar ninguna; la B obliga a ignorar una frase explícita. **La ambigüedad práctica persiste** porque la consigna no incluye el valor de un vector de referencia, ni indica qué hizo el programa de la cátedra.

**Impacto de elegir mal:**
- El ciphertext sería **distinto**: con el mismo clave e IV solo coincide el primer byte y el resto diverge (verificado con `openssl enc -aes-128-cfb8` y `-aes-128-cfb`, `-K E5FB03E4ABD3D239CBB4F0AAA3572401 -iv 20EFCD7A8C7D9986B0BF3089EFF2D9B7`, entrada de 40 ceros: `1f2225c3…` frente a `1f4c71ac…`).
- Un archivo de la cátedra cifrado con `-m cfb` se extraería como basura. CFB no detecta errores de contraseña (ver §7.1): fallaría recién el parseo de Payload.
- Nuestros archivos con `-m cfb` no se podrían extraer con el programa de la cátedra.
- **No cambian** el tamaño del ciphertext (`C = P`, sin padding) ni la capacidad, ni ningún otro modo.
- El costo de corregirlo es una constante en `CryptoMode` y los tests de A1 (control negativo CFB8 ≠ CFB128).

**Qué falta:** un archivo BMP real de la cátedra con `-m cfb` (y la contraseña) o su salida de referencia. Hasta entonces la decisión es **provisional [C + H]**.

**A4 — Codificación de la contraseña.** La consigna no la define.

**Decisión provisional:**
- UTF-8 sin normalización Unicode (E10).
- Es lo que ocurre en OpenSSL, que recibe los bytes de `argv` tal cual, y en `PBKDF2WithHmacSHA256`, que codifica `char[]` en UTF-8.
- E2 lo verificó con `contraseña€ñ` y con contraseñas de 101 caracteres.
- Una contraseña con sustitutos Unicode desparejados no tiene codificación UTF-8 válida: la capa cripto la rechaza con `InvalidCryptoParametersException`.

**A5 — Contraseña vacía.** La CLI hoy acepta `-pass ""` (D-008, decisión 2, ambigüedad registrada). PBKDF2 y OpenSSL funcionan con contraseña vacía (E3).

**Decisión provisional:** la capa cripto **no** la rechaza; esa política es de la CLI y se mantiene lo ya decidido.

**A6 — IV y salt.** La consigna no define dónde se guarda el IV y no hay un campo para él. Se deriva junto con la clave (A3); no se guarda ni se genera aleatorio. Consecuencia, a mencionar en el informe: con salt e IV fijos, el cifrado es **determinista**: la misma contraseña y el mismo plaintext producen siempre el mismo ciphertext. Es una limitación inherente a la consigna.

**A7 — Padding de CFB8/OFB.** La consigna habla de PKCS5 «si requiriera padding» (C5) y la tabla indica `NoPadding` para CFB/OFB (C11). OpenSSL no rellena en CFB/OFB (E5: 423 → 423).

**Decisión:** ECB/CBC con PKCS5; CFB8/OFB sin relleno. Esto cierra la ambigüedad A4 de `LSB1_DESIGN.md` y A6 de `PAYLOAD_DESIGN.md`. La fórmula de capacidad `CapacityMath` ya contempla ambos casos.

**A8 — Tamaño del ejemplo de la consigna.** La consigna no da el valor final de «423 + padding». El valor depende del bloque: **432 con AES y 424 con 3DES** (E5). Cualquier suposición de 432 para 3DES es errónea.

**A9 — Detección de contraseña incorrecta.** Resumen; el detalle está en §7.1. Sin MAC (restricción explícita: no se modifica el formato), la capa cripto no puede garantizar la detección de una contraseña incorrecta.

**A10 — Ciphertext vacío en ECB/CBC.** Java lo acepta y devuelve 0 bytes (E8), pero un ciphertext con PKCS5 nunca es vacío.

**Decisión:** la capa cripto lo rechaza con `InvalidCiphertextException`. En CFB8/OFB un ciphertext vacío descifra a vacío (y el plaintext vacío cifra a vacío), pero Payload ya rechaza `C = 0`.

## 3. Parámetros exactos

### Algoritmos

| CLI (`-a`) | JCA | Clave | Bloque | IV | OpenSSL |
|---|---|---|---|---|---|
| `aes128` | `AES` | 16 | 16 | 16 | `aes-128-*` |
| `aes192` | `AES` | 24 | 16 | 16 | `aes-192-*` |
| `aes256` | `AES` | 32 | 16 | 16 | `aes-256-*` |
| `3des` | `DESede` | 24 | 8 | 8 | `des-ede3-*` (ECB: `des-ede3`) |

### Modos

| CLI (`-m`) | JCA (modo/padding) | Padding | IV | OpenSSL |
|---|---|---|---|---|
| `ecb` | `ECB/PKCS5Padding` | sí | no | `-ecb` |
| `cbc` | `CBC/PKCS5Padding` | sí | sí | `-cbc` |
| `cfb` | `CFB8/NoPadding` | no | sí | `-cfb8` (A1) |
| `ofb` | `OFB/NoPadding` | no | sí | `-ofb` (A2) |

La transformación completa es siempre `ALGORITMO/MODO/PADDING`. **Nunca** se usa `Cipher.getInstance("AES")` (por defecto sería `AES/ECB/PKCS5Padding`).

### Bytes derivados por PBKDF2 (A3)

| Algoritmo | ECB (`dkLen`) | Con IV (`dkLen`) |
|---|---|---|
| aes128 | 16 | 32 |
| aes192 | 24 | 40 |
| aes256 | 32 | 48 |
| 3des | 24 | 32 |

### Tamaño del ciphertext `C` para un plaintext de `P` bytes

- ECB/CBC: `C = B · (⌊P/B⌋ + 1)` con `B = 16` (AES) u `8` (3DES). Siempre hay al menos un bloque.
- CFB8/OFB: `C = P`.

| P | AES (B=16) | 3DES (B=8) | CFB8/OFB |
|---|---|---|---|
| 0 | 16 | 8 | 0 |
| 1 | 16 | 8 | 1 |
| 7 | 16 | 8 | 7 |
| 8 | 16 | 16 | 8 |
| 15 | 16 | 16 | 15 |
| 16 | 32 | 24 | 16 |
| 17 | 32 | 24 | 17 |
| 423 | **432** | **424** | 423 |
| 432 | 448 | 440 | 432 |

(Verificados con OpenSSL para P = 0, 8, 16 y 423; los demás son la fórmula.)

## 4. Derivación de claves

**Parámetros fijos (C7, C10):** PBKDF2 con HMAC-SHA-256, 10.000 iteraciones, salt = 8 bytes `0x00`, `dkLen` según §3.

**Implementación:** `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` con `new PBEKeySpec(char[] password, byte[8] salt, 10000, dkLen * 8)`. El último argumento está en **bits**, no en bytes.

Detalles:
- `PBEKeySpec` codifica `char[]` en UTF-8 (A4, E2).
- La contraseña entra como `char[]`: se copia, se borra con `PBEKeySpec.clearPassword()` y `Arrays.fill`, y la cadena derivada se borra tras partirla.
- Los bytes derivados se mantienen en un objeto `DerivedKey` cerrable (`AutoCloseable`) que los pone a cero al cerrarse.
- Se ponen a cero **todos** los arreglos sensibles: la cadena derivada original, los arreglos internos de `DerivedKey` al cerrarse, y **también las copias temporales** devueltas por `DerivedKey.keyBytes()` e `ivBytes()` (y los `SecretKeySpec`/`IvParameterSpec` en la medida en que el JDK lo permita) en cuanto el llamador termina de usarlas (`finally`).
- El borrado es una buena práctica: no se garantiza porque la `String` original de la CLI sigue en memoria.
- Alternativa descartada: implementar PBKDF2 a mano sobre `Mac("HmacSHA256")`. No aporta nada porque la CLI ya entrega una `String` y se duplicaría código estándar.
- Los parámetros de la consigna **no** se reemplazan por valores modernos.

### 4.1 Convención clave+IV: justificación y comandos

**Separación de conceptos.** Hay dos pasos independientes, y se validan por separado:
1. **Derivación** (PBKDF2): contraseña → cadena de bytes `DK`. No involucra ningún cifrador.
2. **Cifrado** (AES/3DES + modo): `clave`, `IV` y plaintext → ciphertext.

**La convención** [H, compatible con OpenSSL por [P]]: `DK = PBKDF2-HMAC-SHA256(password, salt=00×8, c=10000, dkLen=keyBytes+ivBytes)`; `clave = DK[0..keyBytes)`; `IV = DK[keyBytes..keyBytes+ivBytes)`; en ECB `dkLen = keyBytes` y no hay IV.

**Por qué es la de OpenSSL:** `openssl enc -pbkdf2` pide a PBKDF2 `key_len + iv_len` bytes y los reparte en ese orden. La consigna (C10) dice que PBKDF2 «retorna siempre una cadena de la cual hay que separar key e iv» y pide «des_ede3 en openssl» y `openssl` como referencia de padding (C5): coincide con ese comportamiento. Que la cátedra lo haya hecho así sigue siendo una hipótesis.

**Comandos exactos de la verificación** (contraseña `oculto`; los dos binarios dieron la misma salida):

```
openssl enc -aes-128-cbc -pbkdf2 -iter 10000 -md sha256 -S 0000000000000000 -pass pass:oculto -P
openssl enc -aes-192-cbc …  -P     openssl enc -aes-256-cbc …  -P
openssl enc -des-ede3-cbc … -P     openssl enc -aes-128-ecb …  -P     openssl enc -des-ede3 … -P
```

| Algoritmo/modo | `key` | `iv` |
|---|---|---|
| aes-128-ecb | `E5FB03E4ABD3D239CBB4F0AAA3572401` | (no imprime) |
| aes-128-cbc | `E5FB03E4ABD3D239CBB4F0AAA3572401` | `20EFCD7A8C7D9986B0BF3089EFF2D9B7` |
| aes-192-cbc | `E5FB03E4ABD3D239CBB4F0AAA357240120EFCD7A8C7D9986` | `B0BF3089EFF2D9B74D908F992BE5FDDF` |
| aes-256-cbc | `E5FB03E4ABD3D239CBB4F0AAA357240120EFCD7A8C7D9986B0BF3089EFF2D9B7` | `4D908F992BE5FDDF985C7B0C567655B6` |
| des-ede3 (ECB) | `E5FB03E4ABD3D239CBB4F0AAA357240120EFCD7A8C7D9986` | (no imprime) |
| des-ede3-cbc / -ofb | `E5FB03E4ABD3D239CBB4F0AAA357240120EFCD7A8C7D9986` | `B0BF3089EFF2D9B7` |

Se observa que la cadena es siempre la misma, `E5FB03E4…` seguida de `20EFCD7A…`, `B0BF3089…`, `4D908F99…`, y que cada algoritmo toma un prefijo distinto: clave primero, IV a continuación, y el IV de AES-192 empieza donde termina su clave de 24 bytes.

**Lado Java** (mismo resultado para `dkLen = 32`): `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec("oculto".toCharArray(), new byte[8], 10000, 256)).getEncoded()` = `e5fb03e4abd3d239cbb4f0aaa357240120efcd7a8c7d9986b0bf3089eff2d9b7`.

**Derivación frente a cifrado:**
- La derivación se valida con `-P` (no cifra nada) y con los vectores RFC 7914 §11 (E16), idénticos en Java y `openssl kdf` (3.6.2).
- El cifrado se valida de forma independiente con `openssl enc -K <clave> -iv <iv>` (E15), que no ejecuta PBKDF2 ni escribe encabezado.
- El extremo a extremo `openssl enc -pbkdf2 …` (E2, E12) integra ambos pasos.
- Si fallara solo la derivación o solo el cifrado, los dos niveles anteriores indicarían cuál.

**Qué NO prueba esto:** que la cátedra haya derivado clave e IV de esta manera. Solo prueba compatibilidad con OpenSSL.

## 5. Padding, IV y diferencias entre proveedores

**Configuración en Java:**
- ECB: `cipher.init(mode, new SecretKeySpec(key, jcaName))`. **Sin** `IvParameterSpec` (E9).
- CBC, CFB8, OFB: `cipher.init(mode, key, new IvParameterSpec(iv))` con `iv` de 16 bytes (AES) u 8 (3DES). Un tamaño equivocado lanza `InvalidAlgorithmParameterException` (E9).
- `doFinal` una sola vez por operación. La entrada no se modifica y el resultado es un arreglo nuevo.
- Cada operación crea su propio `Cipher`: no hay estado compartido, así que la clase es segura entre hilos.

**Diferencias entre proveedores y trampas conocidas:**
- **Solo se usa SunJCE**, el proveedor por defecto del JDK (C12). El comportamiento de otros proveedores (por ejemplo Bouncy Castle) **no se verificó** y no se usa.
- `PKCS5Padding` en SunJCE para bloques de 8 y 16 bytes equivale a PKCS#7, como OpenSSL (E2). No existe `PKCS7Padding` en SunJCE.
- `CFB` sin número es **CFB de tamaño de bloque** (128 en AES, 64 en 3DES), no CFB8 (E4). Por eso la transformación es siempre `CFB8`.
- `OFB` sin número es de bloque completo (E4); `OFB8` existe en JCE, pero la consigna no lo usa.
- La tabla de la consigna es la que menciona «AES/CFB/NoPadding» como 128 bits (C11).
- Java 21 no impone límite de longitud de clave (AES-256 funciona; `Cipher.getMaxAllowedKeyLength("AES")` devuelve `Integer.MAX_VALUE`).
- `DESede` con 24 bytes no exige paridad ni descarta claves débiles, igual que OpenSSL (E2 lo confirma en la práctica).

## 6. Arquitectura propuesta

Paquete nuevo `ar.edu.itba.stegobmp.crypto`. **Depende solo del JDK.** No importa `cli`, `payload`, `bmp` ni `steganography`. No se reutilizan los enums de `cli` (`CipherAlgorithm`, `CipherMode`): la capa de integración los traduce con una correspondencia trivial. Por eso hay enums propios, con otro nombre para no confundirlos.

| Tipo | Responsabilidad |
|---|---|
| `enum CryptoAlgorithm` | `AES_128`, `AES_192`, `AES_256`, `TRIPLE_DES`. Expone `jcaName()`, `keyBytes()`, `blockBytes()` (y por tanto el tamaño de IV). |
| `enum CryptoMode` | `ECB`, `CBC`, `CFB8`, `OFB`. Expone `jcaMode()`, `padding()`, `usesIv()`, `isPadded()`. **Único punto** donde se decide A1 y A2. |
| `record CipherSpec(CryptoAlgorithm, CryptoMode)` | Combina ambos. `transformation()` (p. ej. `AES/CBC/PKCS5Padding`), `ivBytes()` (0 en ECB), `derivedBytes()` (A3) y `ciphertextLength(long plaintextLength)` (fórmula de §3, con `long` y `Math.addExact`). Lo usa la capacidad (`CapacityMath`) sin tocar JCE. |
| `final class DerivedKey implements AutoCloseable` | Contiene `key` e `iv`. `keyBytes()`/`ivBytes()` devuelven copias; **quien las recibe debe ponerlas a cero tras usarlas**; `close()` pone a cero los arreglos internos. Fábrica `of(byte[] key, byte[] iv)` para vectores NIST sin PBKDF2. |
| `interface KeyDerivation` | `DerivedKey derive(char[] password, CipherSpec spec) throws CryptoException`. |
| `final class Pbkdf2KeyDerivation implements KeyDerivation` | PBKDF2WithHmacSHA256, 10.000 iteraciones, salt `00×8`; parte la cadena según A3. Es la única clase que conoce esos parámetros. |
| `final class SymmetricCipher` | Estático y sin estado. `byte[] encrypt(CipherSpec, DerivedKey, byte[])` y `byte[] decrypt(...)`. Es **el único código que llama a JCE**, igual para AES y 3DES (los diferencia el `CipherSpec`). Traduce excepciones de JCE a las del dominio. |
| `interface PayloadCipher` | `byte[] encrypt(byte[] plaintext) throws CryptoException` y `byte[] decrypt(byte[] ciphertext) throws CryptoException`. Es el contrato definido en `PAYLOAD_DESIGN.md` §6. Lo único que conocerá la integración. |
| `final class PasswordPayloadCipher implements PayloadCipher` | Combina `CipherSpec`, contraseña y `KeyDerivation` (inyectable, por defecto `Pbkdf2KeyDerivation`). Deriva y cierra la clave en cada operación (≈ ms) y delega en `SymmetricCipher`. Expone `ciphertextLength(long)`. |

Dependencias: `PasswordPayloadCipher → {CipherSpec, KeyDerivation, SymmetricCipher, DerivedKey}`; `SymmetricCipher → {CipherSpec, DerivedKey}`; `Pbkdf2KeyDerivation → {CipherSpec, DerivedKey}`. Sin ciclos.

**Evitar duplicación AES/3DES:** no hay clases por algoritmo ni por modo. Toda la diferencia está en los datos de los enums y de `CipherSpec`.

**Recursos y errores:**
- No hay flujos ni archivos. Lo único sensible son los `byte[]`/`char[]` de la contraseña y de la clave (se borran en `finally`/`close()`).
- Las excepciones de JCE nunca se propagan: se envuelven en las del dominio, conservando la causa.
- Los mensajes **nunca** incluyen la contraseña, la clave ni el IV.

**Independencia:**
- Payload serializa, crypto transforma bytes, la esteganografía los transporta. Ninguna conoce a las otras.
- La integración hará `frameCiphertext(cipher.encrypt(serializeFile(f)))` al ocultar y `parseFile(cipher.decrypt(readCiphertextBlock(src)))` al extraer.

## 7. Errores

Jerarquía de excepciones **comprobadas** con base común `CryptoException`:

| Excepción | Cuándo | Mensaje (ejemplo) |
|---|---|---|
| `InvalidCryptoParametersException` | Algoritmo, modo, contraseña o arreglo `null`; contraseña con sustitutos desparejados; IV o clave de longitud incorrecta recibida en `DerivedKey.of`; combinación no soportada en el entorno. | «Parámetros de cifrado inválidos: la contraseña contiene caracteres que no se pueden codificar en UTF-8.» |
| `KeyDerivationException` | Falla de PBKDF2 (algoritmo no disponible en el runtime, parámetros rechazados). | «No se pudo derivar la clave: PBKDF2WithHmacSHA256 no está disponible en este entorno.» |
| `EncryptionException` | Cualquier falla de JCE al cifrar. | «Error al cifrar con AES/CBC/PKCS5Padding.» |
| `DecryptionException` | Falla de JCE al descifrar que no es de padding ni de longitud. | «Error al descifrar.» |
| `InvalidPaddingException extends DecryptionException` | `BadPaddingException`. | «Relleno inválido al descifrar: la contraseña, el algoritmo o el modo pueden ser incorrectos, o los datos estar dañados.» |
| `InvalidCiphertextException extends DecryptionException` | Ciphertext vacío en ECB/CBC; longitud que no es múltiplo de `B` en ECB/CBC. | «El ciphertext mide 15 bytes y no es múltiplo del tamaño de bloque (16) de AES/CBC.» |

Notas:
- ECB/CBC/CFB8/OFB **no autentican**. No se agrega MAC ni otro formato (§7.1).
- Un fallo de integridad con CFB8/OFB solo se manifiesta en Payload (`parseFile`), nunca aquí.
- Capacidad insuficiente sigue siendo `CapacityExceededException` (esteganografía). Un error cripto no incluye tamaños del portador.

### 7.1 Sin autenticación: qué se puede y qué no se puede detectar

La consigna fija el formato `Tamaño cifrado ‖ Enc(…)` (C3) y los modos ECB, CBC, CFB y OFB (C2). Ninguno incluye autenticación, y **no se agrega MAC, etiqueta ni campo alguno**.

1. **ECB, CBC, CFB8 y OFB no autentican el ciphertext.** Nada en el flujo permite saber *criptográficamente* si la contraseña, el algoritmo, el modo o los datos son los correctos.
2. **ECB/CBC:** un padding inválido detecta *algunos* errores. Con contraseña incorrecta, el último bloque descifra a bytes pseudoaleatorios y el padding resulta válido por casualidad con probabilidad ≈ 1/256 (≈ 0,4 %). Medición [P]: en 600 intentos con AES-CBC, 598 dieron `BadPaddingException` y 2 pasaron. Cuando pasa, la salida es basura sin error de la capa cripto. Un padding válido **no demuestra** que la contraseña sea correcta.
3. **CFB8 y OFB:** no hay padding ni nada que verificar. **No pueden detectar una contraseña incorrecta criptográficamente**: descifran siempre y devuelven bytes (basura, si la clave es incorrecta), siempre con la misma longitud que el ciphertext.
4. **Maleabilidad (efecto de modificar un bit del ciphertext):**
   - **OFB:** el keystream no depende del ciphertext, así que se invierte **únicamente** ese bit del plaintext y nada más.
   - **CFB8:** el bit correspondiente del plaintext se invierte, y además el byte de ciphertext alterado entra en el registro de realimentación: los bytes de plaintext siguientes quedan corruptos (aparentemente aleatorios) hasta que el byte alterado sale del registro (**1 + tamaño de bloque** bytes en total: 17 con AES, 9 con 3DES). Luego el descifrado se resincroniza. El atacante controla el bit del primer byte, no los siguientes.
   - **CBC:** el bloque afectado se corrompe por completo y en el bloque siguiente se invierte exactamente ese bit (modificable de forma predecible).
   - **ECB:** solo se corrompe el bloque afectado.
   Esto es inherente a los modos exigidos y no se corrige.
5. **El parseo estricto de Payload ayuda, pero no es un MAC.** `parseFile` rechaza estructuras inválidas (tamaño incoherente, extensión mal formada, ausencia de NUL, bytes sobrantes); eso detecta la mayoría de los casos de basura, pero **no es una garantía de integridad**: una modificación que conserve una estructura válida no se detecta, y con probabilidad pequeña pero no nula la basura puede parecer válida.
6. **Consecuencia para la integración:** ante `InvalidPaddingException` o `PayloadFormatException` luego de descifrar, el mensaje al usuario debe decir que la contraseña, el algoritmo o el modo pueden ser incorrectos, **o** que los datos están dañados. Nunca debe afirmar «contraseña incorrecta» con certeza, ni «contraseña correcta» cuando el parseo funcione.
7. Fuera de alcance: autenticación, IV aleatorio, derivación con salt variable.

## 8. Compatibilidad e interoperabilidad con OpenSSL

Se proponen cuatro niveles. Los dos primeros no dependen de que haya OpenSSL instalado.

**Nivel 1 — Vectores públicos conocidos (unitarios, siempre se ejecutan).**
- **PBKDF2-HMAC-SHA-256 [P]:** RFC 7914 §11, «Test Vectors for PBKDF2 with HMAC-SHA-256» (y no la parte scrypt del RFC). Aunque el RFC trata de scrypt, esa sección da vectores explícitos de PBKDF2-HMAC-SHA-256 que se usan para verificar la primitiva. La especificación del algoritmo es RFC 8018 (PKCS #5 v2.1; RFC 2898 es su predecesor), que **no** incluye vectores con SHA-256 y por eso no sirve como fuente de vectores. Vectores concretos, ya comprobados en Java y `openssl kdf` (E16):
  - `P="passwd", S="salt", c=1, dkLen=64` → `55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783`.
  - `P="Password", S="NaCl", c=80000, dkLen=64` → `4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d`.
  - Estos vectores validan la primitiva, **no** los parámetros de la consigna (salt de 8 ceros, 10.000 iteraciones). Para éstos se usan los vectores propios siguientes.
- **PBKDF2 con los parámetros de la consigna:** vectores propios, producidos por dos implementaciones independientes (Java y `openssl enc -P`, §4.1): `oculto` → `e5fb03e4abd3d239cbb4f0aaa357240120efcd7a8c7d9986b0bf3089eff2d9b7` (dkLen 32) y contraseña vacía → `fa8d81b6d185646b749b94929c82cd460acf1f0c944b508114bb18c0cedc3e16`.
- **Cifradores — NIST SP 800-38A, Apéndice F (solo AES) [P/H]:** AES-128, AES-192 y AES-256 en ECB, CBC, CFB8 y OFB, con clave e IV fijos y `DerivedKey.of`, sin PBKDF2. Los valores se transcribirán de la norma al implementar y se contrastarán con OpenSSL. De momento solo se confrontó el vector AES-128 CFB8 (E17), de memoria.
- **3DES no tiene vectores NIST en SP 800-38A** (esa norma es solo de AES). 3DES queda validado **exclusivamente** con los fixtures del nivel 2 y los ensayos con OpenSSL del nivel 3.

**Cobertura de cada fuente de verdad:**

| Combinación | Vectores NIST SP 800-38A | Fixtures/ensayos OpenSSL |
|---|---|---|
| AES-128/192/256 × ECB, CBC, CFB8, OFB | sí (al implementar) | sí |
| 3DES × ECB, CBC, CFB8, OFB | **no** | **sí (única fuente)** |
| PBKDF2 con parámetros de la consigna | no | sí (`-P`) + vectores propios |
| PBKDF2 genérico | RFC 7914 §11 | `openssl kdf` |

**Nivel 2 — Fixtures generados por OpenSSL (unitarios, sin proceso externo).** Para las 16 combinaciones se guardan en `src/test/resources/crypto/` el plaintext (423 bytes con patrón determinista) y el ciphertext producido por `openssl enc … -pbkdf2 -iter 10000 -md sha256 -S 0000000000000000`. Cada fixture se acompaña de un archivo con la versión de OpenSSL, el comando exacto y el hash SHA-256 de los datos. La prueba cifra con Java y compara **byte a byte**, y luego descifra el fixture con Java. El fixture se guarda **sin** encabezado `Salted__` (normalizado al generarlo, ver nivel 3).

**Nivel 3 — Interoperabilidad en vivo (pruebas de integración `*IT`, Failsafe).** Ejecutan `openssl` con `ProcessBuilder`. Se omiten (`Assumptions`) únicamente si **no hay ningún `openssl` ejecutable**; si lo hay pero se comporta de forma inesperada, la prueba **falla** (no se omite).

*Formato de salida de `openssl enc -S` (E6, E13) y reglas estrictas del harness.* No se asume un formato por la cadena de versión: se **detecta con una sonda** y se valida de forma estricta.
1. **Sonda al inicio:** cifrar un plaintext conocido de longitud `L` (por ejemplo AES-128-CBC, 23 bytes) con `-S 0000000000000000`, y clasificar:
   - salida de longitud `esperada(L)` → dialecto `SIN_ENCABEZADO` (observado: OpenSSL 3.6.2);
   - salida de longitud `esperada(L)+16` y cuyos primeros 16 bytes sean exactamente `53616c7465645f5f` ‖ `0000000000000000` → dialecto `CON_ENCABEZADO` (observado: LibreSSL 3.3.6);
   - **cualquier otra cosa** (otra longitud, otro magic, otro salt) → la prueba **falla** con un mensaje que incluye la versión y los bytes observados.
2. **En cada cifrado:** se valida el encabezado según el dialecto detectado. En `CON_ENCABEZADO` se exigen **exactamente** esos 16 bytes y solo entonces se quitan; si el prefijo no es exacto, falla. En `SIN_ENCABEZADO` se verifica que **no** empiece por `Salted__`.
3. **En cada descifrado con OpenSSL:** se arma la entrada **con** encabezado `Salted__` ‖ `00×8` ‖ ciphertext y se descifra **sin** `-S` (con `-pbkdf2 -iter 10000 -md sha256`). Observado en ambos binarios (E13), así que no depende del dialecto.
4. **Nunca** `-nosalt` (cambia la clave, E14).
5. Si OpenSSL rechaza `-pbkdf2` (versión anterior a 1.1.1), se omite el extremo a extremo y solo se ejecutan los dos ensayos separados siguientes.

*Ensayos separados (independientes del dialecto):*
- **Derivación:** `openssl enc -<alg> -pbkdf2 -iter 10000 -md sha256 -S 0000000000000000 -pass pass:<pw> -P` y comparación de `key=`/`iv =` con la derivación Java. El formato esperado de la salida es estricto (`salt=`, `key=`, `iv =` y hexadecimal en mayúsculas; sin `iv` en ECB); cualquier otra línea falla.
- **Cifrado:** `openssl enc -<alg> -K <clave> -iv <iv>` con la clave e IV derivados en Java (E15). No produce encabezado en ninguno de los dos binarios.

*Casos del extremo a extremo:*
- Cifrar con Java y descifrar con OpenSSL.
- Cifrar con OpenSSL y descifrar con Java.
- 16 combinaciones × longitudes {0, 1, 7, 8, 15, 16, 17, 423} × contraseñas {`oculto`, `contraseña€ñ`, de 100 caracteres, vacía}.
- La contraseña se pasa a OpenSSL con `-pass pass:<pw>` (usado en todos los ensayos hechos). Pasarla por `-pass stdin` o archivo, para evitar la dependencia de la locale (R3), **no se probó**; se decidirá al implementar el harness.

**Versiones verificadas (y las que no):**

| Binario | Versión | Verificado |
|---|---|---|
| OpenSSL | 3.6.2 | E1–E4, E6, E12–E16 |
| LibreSSL | 3.3.6 | E1, E6, E12–E15 (**no** E2/E3) |
| OpenSSL | 3.0.x | **no verificado** |
| OpenSSL | 1.1.1 | **no verificado** |

No se afirma compatibilidad con OpenSSL 3.0.x ni 1.1.1. El harness no la presupone: detecta y falla si encuentra un comportamiento inesperado.

**Nivel 4 — Controles negativos (para que las pruebas sean capaces de fallar).**
- El CFB8 de Java **no** coincide con `aes-128-cfb` de OpenSSL (E4) para plaintext de más de 16 bytes.
- OFB difiere de CFB8.
- Cambiar un byte de la contraseña, el modo, el algoritmo o el IV rompe la igualdad.
- Un salt distinto de cero rompe la igualdad.
- Esto detecta errores de derivación de clave, IV, padding y tamaño de bloque.

Los ejemplos oficiales confirman la derivación y los modos para AES-128-CBC, AES-256-OFB y 3DES-CFB8. Aún no confirman OFB con 3DES, AES-CFB, ECB, otros tamaños/modos ni decisiones de codificación de contraseña; ver [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md).

## 9. Casos de prueba propuestos

### `CipherSpecTest` (unitario)
1. Los 16 pares: transformación, `ivBytes`, `derivedBytes` según §3.
2. `ciphertextLength` para P ∈ {0, 1, 7, 8, 15, 16, 17, 423, 432} en AES y 3DES (tabla de §3), y en CFB8/OFB (`= P`).
3. Límites de `long`: `ciphertextLength` cerca de `Long.MAX_VALUE` falla con una excepción aritmética explícita.
4. `CryptoMode`: solo ECB no usa IV; solo ECB y CBC tienen padding.

### `Pbkdf2KeyDerivationTest` (unitario)
5. Vectores RFC 7914 §11 (PBKDF2-HMAC-SHA-256 genérico, dos vectores de §8 nivel 1) y vectores propios con los parámetros de la consigna (`oculto` y contraseña vacía).
6. Partición clave/IV: `clave` es el prefijo y `IV` los bytes siguientes, para los cuatro algoritmos.
7. Prefijo estable: la clave de ECB coincide con la de CBC del mismo algoritmo.
8. Longitudes `dkLen` de §3.
9. Contraseñas: 1 carácter, 64, 65 y 200 caracteres (rama de HMAC con clave larga), espacios, no ASCII (`ñ`, `€`, CJK, emoji).
10. NFC y NFD de `contraseña` producen claves distintas (E10).
11. Contraseña `null` → `InvalidCryptoParametersException`; con sustituto desparejado → ídem; vacía → se acepta (A5).
12. La contraseña original (`char[]` del llamador) no se modifica; `DerivedKey.close()` pone a cero.

### `SymmetricCipherTest` (unitario, vectores NIST con `DerivedKey.of`)
13. AES-128/192/256 × ECB/CBC/CFB8/OFB: cifrar y comparar con los vectores de SP 800-38A; descifrar y recuperar.
14. 3DES-ECB/CBC/CFB8/OFB con vectores propios generados con OpenSSL.
15. Round-trip de todas las combinaciones con P ∈ {0, 1, 7, 8, 15, 16, 17, 31, 32, 33, 423, 4096} y datos aleatorios con semilla fija.
16. **Ejemplo de la consigna:** plaintext de 423 bytes (`00 00 01 9E` ‖ 414 bytes ‖ `.txt\0`, armado a mano en el test, sin importar `payload`). Con CBC: **432 bytes en AES-128/192/256 y 424 en 3DES**; ECB igual; CFB8/OFB: 423.
17. Plaintext vacío: ECB/CBC dan un bloque (16 o 8 bytes) y descifra a vacío; CFB8/OFB dan 0 bytes.
18. Plaintext de un byte, de longitud igual al bloque y de múltiplo del bloque (se añade un bloque completo de padding).
19. La entrada no se modifica y el resultado es un arreglo nuevo.
20. **Padding inválido (determinista):** se arman plaintexts de bloque con `AES/CBC/NoPadding` cuyo último byte es `0x00`, `0x11` (> bloque), o con bytes de relleno inconsistentes (`… 02 03`) y se cifran; descifrar con `SymmetricCipher` → `InvalidPaddingException`. Lo mismo para 3DES con `0x09`.
21. **Ciphertext incompatible:** longitud 0, 1, 15 y 17 en AES-ECB/CBC; longitud 0, 7 y 9 en 3DES-ECB/CBC → `InvalidCiphertextException`. En CFB8/OFB cualquier longitud se acepta.
22. **Contraseña incorrecta:** en ECB/CBC con contraseñas elegidas y fijadas que producen error de padding → `InvalidPaddingException`; en CFB8/OFB no hay excepción y el resultado difiere del original (A9).
23. IV de tamaño incorrecto o clave de longitud incorrecta → `InvalidCryptoParametersException`.
24. `CFB8` frente a `CFB` (128): un control que verifica que la salida de `CFB8` difiere de la de `AES/CFB/NoPadding` para más de 16 bytes, para proteger A1.
25. Los mensajes de error no contienen la contraseña, la clave ni el IV.

### `PasswordPayloadCipherTest` (unitario)
26. Round-trip con `PayloadCipher` para las 16 combinaciones y contraseñas de distinta longitud y no ASCII.
27. La misma instancia puede reutilizarse y usarse desde varios hilos.
28. Cifrado determinista: dos llamadas con la misma entrada dan el mismo ciphertext (A6).
29. Contraseña distinta → resultado distinto; algoritmo o modo distinto → resultado distinto.
30. Contraseña, modo y algoritmo erróneos al descifrar: se produce `InvalidPaddingException` en ECB/CBC en la mayoría de los casos (control estadístico con 100 contraseñas: al menos 90 % fallan; el criterio exacto se afina al implementar).

### `OpenSslFixtureTest` (unitario, sin proceso externo)
31. Para las 16 combinaciones: cifrar el plaintext del fixture con Java y comparar con el ciphertext generado por OpenSSL; descifrar el fixture con Java y recuperar el plaintext (nivel 2).
32. Ciphertext de OpenSSL con contraseña no ASCII y con contraseña vacía.

### `OpenSslInteropIT` (integración, se omite solo si no hay `openssl` ejecutable)
33. Java → OpenSSL y OpenSSL → Java para las 16 combinaciones × longitudes × contraseñas del nivel 3.
34. Controles negativos del nivel 4.
35. **Harness del encabezado `Salted__`:** la sonda clasifica `SIN_ENCABEZADO` / `CON_ENCABEZADO` y falla ante cualquier otro formato; pruebas unitarias del clasificador con salidas sintéticas (longitud correcta sin encabezado, con encabezado exacto, con magic distinto, con salt distinto de cero, con longitud inesperada → las tres últimas deben fallar). Se omite solo si no hay `openssl` ejecutable.
36. Ensayos separados: derivación con `-P` (formato estricto) y cifrado con `-K/-iv`.

### `CryptoPayloadCompatibilityTest` (unitario, solo en el código de prueba)
37. `serializeFile` de 414 bytes con `.txt` (423 bytes) → cifrar con AES-CBC (432) y con 3DES-CBC (424) → `frameCiphertext` (436 y 428 bytes, con `00 00 01 B0` y `00 00 01 A8`) → `readCiphertextBlock` → descifrar → `parseFile` → igual al original. Verifica que Payload y crypto encajan sin acoplarse. El adaptador de esta prueba vive en el código de prueba.

## 10. Qué queda fuera de esta etapa

- No se integra con Payload, la CLI, BMP ni la esteganografía. No hay `EmbedOperation` ni `ExtractOperation`.
- No se implementan LSB4 ni LSBI.
- No hay MAC, IV aleatorio, salt configurable ni formato adicional.
- No se modifica código existente (solo documentación: este archivo, `PROJECT_PLAN.md` y la corrección de P7 en `PAYLOAD_DESIGN.md`).

## 11. Riesgos técnicos a resolver antes de programar

| # | Riesgo | Gravedad | Mitigación |
|---|---|---|---|
| R1 | **A1 (CFB8 o CFB128).** No es una contradicción demostrable (§2.1), pero la consigna no da un vector de referencia. | Alta | Decisión provisional CFB8 [C + H]; confirmar con una muestra cifrada de la cátedra. Cambio de una línea. |
| R2 | **A3 (partición de la cadena PBKDF2)** no está explícita. Si la cátedra deriva clave e IV por separado o en otro orden, ningún cifrado será compatible. | Alta | Se usa la convención de OpenSSL (§4.1) [H]. Probar con una muestra cifrada real. |
| R3 | **Argumentos no ASCII en Linux.** En Java, `sun.jnu.encoding` sigue al locale; con `LC_ALL=C` (posible en Pampero o WSL) una contraseña como `contraseña` podría decodificarse mal **antes** de llegar a nuestro código. En macOS no ocurre (E11). | Media | Verificar en Pampero/WSL con `LC_ALL=C` y `LANG=en_US.UTF-8`; si falla, documentar la restricción o advertir en la CLI. No se puede corregir desde la capa cripto. |
| R4 | **A2 (OFB de 128 bits con 3DES)** inexistente tal como está escrito. | Media | Se usa el feedback de bloque completo (64 bits en 3DES). |
| R5 | **Detección de contraseña incorrecta** solo probabilística en ECB/CBC (E7) y nula en CFB8/OFB; sin autenticación (§7.1). | Media | Aceptado (sin MAC). Payload estricto y mensaje orientativo en la integración. |
| R6 | **Pruebas de interoperabilidad** dependen del binario: LibreSSL 3.3.6 y OpenSSL 3.6.2 difieren en el encabezado `Salted__` (E6, E13); OpenSSL 3.0.x y 1.1.1 **no verificados**. | Baja | Sonda estricta de dialecto (§8, nivel 3), ensayos separados `-P` y `-K/-iv`, y fixtures del nivel 2 para no depender del binario. Pampero no verificado. |
| R7 | **Java en Pampero** desconocido: se asume `PBKDF2WithHmacSHA256`, `DESede` y los modos `CFB8`/`OFB` de SunJCE, disponibles desde Java 8. | Baja | Verificar al entregar (etapa 11). |
| R8 | **Cifrado determinista** (A6) por consigna: salt e IV fijos. | Informativa | Mencionarlo en el informe (punto de análisis, no se modifica). |
| R9 | **Memoria**: la capa trabaja con `byte[]` completos (≤ 2 GiB − 1), como el resto del sistema. | Baja | Límite ya documentado en Payload (`FILE_TOO_LARGE`). |

## 12. Decisiones y su respaldo

Leyenda: **[C]** consigna, **[P]** prueba reproducible, **[H]** hipótesis.

| # | Decisión | Respaldo | Estado |
|---|---|---|---|
| 1 | **A1:** CFB = CFB8, aislado en `CryptoMode` | [C] viñeta p. 5 §5.4; [P] equivalencia `AES/CFB8` = `aes-128-cfb8` (E4, E12) y vector NIST F.3.7 (E17); [H] que la cátedra lo implemente así | Provisional |
| 2 | **A2:** OFB de bloque completo (128 en AES, 64 en 3DES) | [C] «128 si es OFB» p. 5; [P] `DESede/OFB` = `des-ede3-ofb` (E12); [H] 3DES no definido en la consigna | Provisional |
| 3 | **A3:** una derivación `clave ‖ IV`, clave primero; ECB solo clave | [C] «separar key e iv» (sin orden); [P] idéntica a `openssl enc -P` (§4.1); [H] orden y longitudes de la cátedra | Provisional |
| 4 | PBKDF2-HMAC-SHA-256, 10.000 iteraciones, salt 00×8 | [C] C7, C10; [P] Java = OpenSSL (E1, E12), vectores RFC 7914 §11 (E16) | **Confirmada** |
| 5 | **A4:** contraseña UTF-8 sin normalización; desparejados rechazados | [P] E2, E10; [H] no definida en la consigna; riesgo R3 en Linux | Provisional |
| 6 | **A5:** contraseña vacía aceptada en la capa cripto | [P] E3; política de la CLI (D-008) | Provisional |
| 7 | **A7:** PKCS5 en ECB/CBC; CFB8/OFB sin relleno | [C] C5 y tabla C11 («NoPadding»); [P] E5, E12 | **Confirmada** |
| 8 | **A10:** ciphertext vacío rechazado en ECB/CBC | [P] E8 (Java no lo detecta) | Decisión de diseño |
| 9 | Tamaño del ciphertext: AES 432, 3DES 424 para 423 bytes | [C] C4 (requiere padding); [P] E5 | **Confirmada** |
| 10 | Enums propios en `crypto`; `PayloadCipher` en `crypto`; solo SunJCE | Decisión de diseño | Pendiente de tu aprobación |
| 11 | Pruebas: NIST + fixtures + `*IT` con sonda estricta | §8 | Pendiente de tu aprobación |

## 13. Estado de la validación

**Confirmado por la consigna y/o pruebas (sin hipótesis sobre la cátedra):** PBKDF2 (hash, iteraciones, salt), algoritmos y tamaños de clave, PKCS5 solo en ECB/CBC, tamaños de ciphertext, equivalencia Java ↔ OpenSSL 3.6.2 y LibreSSL 3.3.6 para los 16 cifradores (E12).

**Hipótesis que solo una muestra de la cátedra puede cerrar:** A1 (CFB8), A2 (OFB en 3DES), A3 (orden y longitud de clave/IV), A4 (codificación de la contraseña). **La compatibilidad con los archivos de la cátedra no está garantizada.**

**Muestra mínima que lo resolvería:** un BMP de la cátedra con un mensaje cifrado, con la contraseña, el algoritmo y el modo, **idealmente uno por modo** (`cbc`, `ecb`, `cfb`, `ofb`) y uno con `3des`. Con el cifrado CBC se cerraría A3; con `cfb`, A1; con `ofb` y `3des`, A2.
