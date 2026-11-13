# stegobmp — Guía de uso y funcionamiento paso a paso

Qué recibe el programa, qué hace internamente y qué devuelve. Estado actual: **LSB1, LSB4 y LSBI implementados e integrados**. Los ejemplos oficiales confirmaron LSB4 alto-primero, LSBI B→G/MSB-first y tres combinaciones cifradas; ver [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md). La validación de otras combinaciones y del resto de requisitos de entrega sigue pendiente.

Documentos relacionados: [`IMPLEMENTATION_OVERVIEW.md`](IMPLEMENTATION_OVERVIEW.md) (clases y consigna), [`MODULES_AND_TESTING.md`](MODULES_AND_TESTING.md) (carpetas y pruebas).

---

## 1. Qué recibe y qué devuelve

Solo argumentos de línea de comandos y archivos en disco. No lee stdin ni guarda estado entre ejecuciones.

| Operación | Entradas | Salida |
|---|---|---|
| `-embed` | `-in` archivo a ocultar, `-p` BMP portador, `-out` BMP resultado, `-steg`, opcionalmente `-a -m -pass` | un BMP nuevo en `-out` |
| `-extract` | `-p` BMP con mensaje, `-out` nombre base, `-steg`, opcionalmente `-a -m -pass` | un archivo `-out` + extensión original |

Valores: `-steg LSB1|LSB4|LSBI`, `-a aes128|aes192|aes256|3des`, `-m ecb|cbc|cfb|ofb`.

El éxito no imprime nada. El resultado se comunica con el **código de salida**; los errores van a stderr con el prefijo `Error:`.

| Código | Significado |
|---|---|
| 0 | éxito o ayuda |
| 1 | error de operación (archivo inexistente, BMP inválido, capacidad insuficiente, payload corrupto, fallo de descifrado) |
| 2 | error de uso (argumentos) |
| 3 | algoritmo válido pero no implementado |

## 2. Uso rápido

```bash
mvn clean package -DskipTests
J=target/stegobmp-1.0.0-SNAPSHOT.jar

# sin cifrado
java -jar $J -embed   -in archivo.txt -p foto.bmp -out resultado.bmp -steg LSB1
java -jar $J -extract -p resultado.bmp -out recuperado -steg LSB1            # genera recuperado.txt

# con cifrado
java -jar $J -embed   -in archivo.txt -p foto.bmp -out resultado.bmp -steg LSB1 -a aes128 -m cbc -pass secreto
java -jar $J -extract -p resultado.bmp -out recuperado -steg LSB1 -a aes128 -m cbc -pass secreto

# LSB4 con los defaults de cifrado (AES-128 + CBC)
java -jar $J -embed   -in archivo.txt -p foto.bmp -out resultado4.bmp -steg LSB4 -pass secreto
java -jar $J -extract -p resultado4.bmp -out recuperado4 -steg LSB4 -pass secreto

# LSBI sin cifrado
java -jar $J -embed   -in archivo.txt -p foto.bmp -out resultado-lsbi.bmp -steg LSBI
java -jar $J -extract -p resultado-lsbi.bmp -out recuperado-lsbi -steg LSBI
```

El portador debe ser un BMP de 24 bits sin compresión. Para convertir una imagen en macOS: `sips -s format bmp foto.png --out foto.bmp`.

La extracción debe repetir el mismo `-steg`, `-a`, `-m` y `-pass` que el embed: nada de eso se guarda en la imagen.

Para ejecutar una batería de casos con una carpeta por caso: `demo/run.sh` (recrea `demo/out/` en cada pasada; acepta un filtro, por ejemplo `demo/run.sh aes256`).

## 3. Reglas de cifrado por defecto

| Argumentos | Resultado |
|---|---|
| sin `-pass` (incluso con `-a`/`-m`) | no se cifra; solo esteganografía |
| solo `-pass` | AES-128 + CBC |
| `-a` + `-pass`, sin `-m` | el algoritmo indicado + CBC |
| `-m` + `-pass`, sin `-a` | AES-128 + el modo indicado |

El caso de `-a`/`-m` sin `-pass` es una interpretación de la consigna, documentada.

## 4. Paso 0: `Main` y el parser (`cli`)

1. `ArgumentParser` valida: argumentos desconocidos, duplicados, valores faltantes, combinaciones inválidas (por ejemplo `-in` en extract) y opciones obligatorias ausentes. Ante un error imprime qué falló y sale con 2.
2. Un valor de `-pass` que empieza con `-` se toma como contraseña.
3. Aplica los defaults de cifrado y entrega un `CommandOptions` inmutable.
4. LSB1, LSB4 y LSBI están disponibles. `Steganographies` selecciona la implementación pedida.

## 5. EMBED paso a paso

`-embed -in archivo.txt -p foto.bmp -out resultado.bmp -steg LSB1 -a aes128 -m cbc -pass secreto`

1. **Leer el archivo** (`Embedder`). Se arma un `HiddenFile` con el contenido y la extensión (desde el último `.` del nombre; `.txt`). Si no existe: `Error`, código 1.
2. **Leer el BMP** (`BmpReader`): valida firma `BM`, 24 bits, sin compresión. Lo separa en cabecera, gap, cuerpo (píxeles con el padding de filas) y trailing.
3. **Verificar capacidad antes de hacer nada**:
   - LSB1: capacidad = `floor(bytes del cuerpo / 8)`;
   - LSB4: capacidad = `floor(bytes del cuerpo / 2)`;
   - LSBI: capacidad = `floor((2 * ancho * filas - R) / 8)`, donde `R` son las componentes B/G incluidas en los primeros cuatro bytes físicos reservados para flags;
   - sin cifrado se necesitan `4 + F + len(extensión) + 1` bytes;
   - con cifrado se necesitan `4 + tamaño del ciphertext`, que depende del modo: ECB/CBC agregan padding; CFB8/OFB no.
   Si no alcanza: `Error: El portador no tiene capacidad suficiente: se necesitan N bytes y la capacidad máxima es M bytes.` No se escribe nada.
4. **Serializar** (`PayloadCodec`): `tamaño real (4 bytes BE) ‖ contenido ‖ ".txt" + 0x00`.
5. **Cifrar, si hay `-pass`** (`PasswordPayloadCipher`): deriva `clave ‖ IV` con PBKDF2-HMAC-SHA256 (10.000 iteraciones, salt de 8 bytes en cero), cifra la estructura completa del paso 4 y la enmarca como `tamaño del ciphertext (4 bytes BE) ‖ ciphertext`.
6. **Incrustar** (`Lsb1`, `Lsb4` o `Lsbi`):
   - LSB1: cada bit del flujo, MSB primero, va al bit menos significativo de un byte consecutivo del cuerpo.
   - LSB4: cada grupo de cuatro bits va al nibble bajo de un byte consecutivo; el nibble alto del mensaje va primero (confirmado con el ejemplo oficial `ladoLSB4.bmp`).
   - LSBI: escribe los cuatro flags en el LSB de los primeros cuatro bytes; transporta datos en B/G de píxeles (B→G), excluye R y padding, e invierte patrones cuando `changed > unchanged`.
   Los bytes posteriores al mensaje no se tocan.
7. **Escribir** (`BmpWriter`): cabecera, gap y trailing quedan idénticos byte a byte; solo cambia el cuerpo. El archivo resultante pesa lo mismo que el original.

Ejemplo sin cifrado, archivo de 5 bytes con extensión `.txt`:
```
00 00 00 05 | 68 6f 6c 61 0a | 2e 74 78 74 00        (14 bytes = 112 bits → 112 bytes del cuerpo)
```
Con la estructura completa de 423 bytes (414 + 4 + 5) el ciphertext mide 432 en CBC y 423 en CFB8/OFB.

## 6. EXTRACT paso a paso

`-extract -p resultado.bmp -out recuperado -steg LSB1 -a aes128 -m cbc -pass secreto`

1. **Leer el BMP** y abrir el lector del algoritmo indicado sobre el cuerpo. LSBI lee primero sus flags y usa ancho, filas y stride para recorrer B/G.
2. **Leer el payload** (`PayloadCodec` sobre `StegoReaderSource`):
   - sin cifrado: 4 bytes de tamaño F, F bytes de contenido y la extensión hasta el NUL; se detiene ahí e ignora el resto;
   - con cifrado: 4 bytes de tamaño C, se valida que C no exceda lo disponible **antes** de reservar memoria, y se leen C bytes de ciphertext.
3. **Descifrar** (solo con `-pass`): misma derivación de clave e IV; se obtiene `tamaño ‖ contenido ‖ extensión`, que se parsea de forma estricta (rechaza bytes sobrantes).
4. **Escribir** `-out` + extensión (`recuperado.txt`) **solo después de validar todo**. Un archivo sin extensión sale exactamente como `-out`.

## 7. Diagrama

```
EMBED:   archivo ─► [tamaño|datos|.ext\0] ─► (cifrar + [tamaño C|ciphertext]) ─► LSB1/LSB4/LSBI sobre el cuerpo del BMP ─► resultado.bmp
EXTRACT: BMP ─► LSB1/LSB4/LSBI lee bits ─► [tamaño|datos|.ext\0] o [tamaño C|ciphertext] ─► (descifrar) ─► <out>.ext
```

## 8. Errores y límites

| Situación | Comportamiento |
|---|---|
| Extraer algo cifrado sin `-pass`, o una imagen sin mensaje | `Error` (código 1); sin `-pass` el mensaje sugiere usarlo |
| Contraseña, algoritmo o modo incorrectos en ECB/CBC | casi siempre `No se pudo descifrar el contenido` por padding inválido (código 1); **no** es 100 % seguro |
| Contraseña incorrecta en CFB/OFB | no se puede detectar al descifrar; falla solo si la estructura resultante es inválida; nunca recupera el original |
| Archivo mayor que la capacidad | código 1 y no se escribe la salida |
| Portador muy pequeño | capacidad LSB1 = `floor(bodyLength/8)`; LSB4 = `floor(bodyLength/2)`; LSBI excluye flags, R y padding |
| Portador que no es BMP 24 bits sin compresión | código 1 |

No hay MAC ni hash de integridad: la consigna no los pide, por lo que el programa no puede asegurar que lo extraído sea "el archivo correcto".

## 9. Pendientes y supuestos sin validar

Ninguno fue contrastado con una muestra real de la cátedra: orden de nibbles LSB4 alto primero, CFB8 frente a CFB128, OFB con 3DES, partición clave/IV, contraseña en UTF-8, contraseña vacía aceptada, orden de bits MSB primero, `.\0` para archivos sin extensión y `-out` + extensión concatenados.
