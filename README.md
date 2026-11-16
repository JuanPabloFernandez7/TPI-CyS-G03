# stegobmp — guía de uso

Programa Java para ocultar y extraer archivos en imágenes BMP con LSB1, LSB4
o LSBI, con cifrado opcional. Para la estructura y explicación clase por
clase, consultar [Arquitectura](docs/ARQUITECTURA.md).

## 1. Requisitos

- JDK 21, con `java` y `javac` disponibles.
- Maven.
- Para ejecutar la prueba de interoperabilidad completa: OpenSSL. Si no está
  instalado, esa prueba se omite; los vectores criptográficos incluidos
  siguen verificándose.

Comprobar el entorno:

```sh
java -version
javac -version
mvn -version
openssl version
```

Maven puede necesitar acceso a sus dependencias y plugins la primera vez.
El programa Java no requiere bibliotecas externas en tiempo de ejecución.

Se verificó en Pampero con Java 21.0.13 y Java 27, usando OpenSSL 3.6.5
y compilando con `--release 21`. En ambos runtimes, `mvn clean verify`
pasó con 494 pruebas unitarias y 116 de integración, sin fallos ni
omisiones. Con Java 27 también se verificaron round-trips cifrados con los
tres métodos y las extracciones de Roma, Hugo y Frozen; Titanic se
inspeccionó externamente.

## 2. Compilar y verificar

Desde la raíz del proyecto:

```sh
mvn clean verify
```

El comando compila, ejecuta las pruebas unitarias, genera el JAR y ejecuta
las pruebas de integración. Revisar `BUILD SUCCESS` y también la cantidad
de pruebas omitidas. El ejecutable queda en:

```text
target/stegobmp-1.0.0-SNAPSHOT.jar
```

Solo compilación y empaquetado: `mvn clean package`. Solo pruebas unitarias:
`mvn test`.

## 3. Consultar ayuda

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar --help
```

También se acepta `-h`. Las opciones y valores distinguen mayúsculas de
minúsculas. Se usa `java -jar` como lanzador; no se instala automáticamente
un comando llamado `stegobmp`.

## 4. Ocultar un archivo

Primero, preparar el archivo a ocultar y el BMP portador. El BMP debe ser V3,
V4 o V5, de 24 bits y sin compresión. Después ejecutar:

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -embed -in mensaje.txt -p portador.bmp \
  -out imagen-con-mensaje.bmp -steg LSB1
```

Esto crea `imagen-con-mensaje.bmp` con el contenido y la extensión de
`mensaje.txt`. Cambiar `LSB1` por `LSB4` o `LSBI` para elegir otro método.

Con cifrado AES-128-CBC:

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -embed -in mensaje.txt -p portador.bmp \
  -out imagen-cifrada.bmp -steg LSBI \
  -a aes128 -m cbc -pass "contraseña"
```

El portador de entrada se lee sin modificarlo, siempre que se indique una
ruta de salida diferente. **Las salidas existentes pueden sobrescribirse**:
comprobar la ruta antes de ejecutar.

## 5. Extraer el archivo

Usar el mismo método y, si corresponde, la misma contraseña, algoritmo y modo:

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -extract -p imagen-con-mensaje.bmp \
  -out recuperado -steg LSB1
```

La salida será `recuperado.txt`: el programa agrega a `-out` la extensión
guardada. Para el ejemplo cifrado:

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -extract -p imagen-cifrada.bmp \
  -out recuperado -steg LSBI \
  -a aes128 -m cbc -pass "contraseña"
```

Para comprobar que el resultado coincide con el original:

```sh
cmp mensaje.txt recuperado.txt
```

`cmp` no imprime nada si los archivos son iguales. La extracción no detecta
automáticamente el método ni el cifrado. Una contraseña incorrecta o datos
alterados no siempre se detectan porque el formato no incluye autenticación.

## 6. Opciones

| Opción | Función |
|---|---|
| `-embed` | Ocultar; requiere `-in`, `-p`, `-out` y `-steg`. |
| `-extract` | Extraer; requiere `-p`, `-out` y `-steg`. |
| `-in archivo` | Archivo que se va a ocultar; solo en EMBED. |
| `-p imagen.bmp` | BMP portador. |
| `-out ruta` | BMP de salida en EMBED; base del nombre recuperado en EXTRACT. |
| `-steg LSB1`, `LSB4` o `LSBI` | Método de esteganografía. |
| `-a aes128`, `aes192`, `aes256` o `3des` | Cifrado opcional. |
| `-m ecb`, `cbc`, `cfb` o `ofb` | Modo de cifrado opcional. |
| `-pass contraseña` | Activa el cifrado con la contraseña indicada. |

Sin `-pass` no se cifra, aunque se hayan indicado `-a` o `-m`. Con contraseña
se usan AES-128 y CBC por defecto para los parámetros omitidos. CFB usa
feedback de 8 bits; OFB usa el tamaño de bloque del algoritmo. La contraseña
puede aparecer en el historial o en la lista de procesos: evitar contraseñas
reales en equipos compartidos.

Las extensiones deben usar ASCII imprimible y no contener separadores de
ruta. Un archivo sin extensión se recupera sin sufijo. El programa carga los
archivos en memoria y rechaza BMP mayores a `2 GiB - 1`; la memoria
disponible puede imponer un límite menor.

## 7. Errores y capacidad

- `0`: éxito o ayuda.
- `1`: error de operación, por ejemplo archivo inexistente, BMP incompatible,
  capacidad insuficiente o payload inválido.
- `2`: argumentos inválidos; se muestra la sintaxis.
- `3`: reservado para una estrategia no implementada; no corresponde a
  ninguno de los tres métodos actuales.

La capacidad indicada en un error es la capacidad en bytes del **flujo
oculto**, incluidos los metadatos; el tamaño máximo del archivo de entrada
es menor y depende de la extensión y, cuando se usa, del cifrado.

## 8. Ejemplo oficial

Con el JAR compilado, crear una carpeta para la salida:

```sh
mkdir -p resultados
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar \
  -extract -p docs/docs_catedra/ejemplo/ladoLSBIdescfb.bmp \
  -out resultados/oficial -steg LSBI \
  -a 3des -m cfb -pass margarita
sha256sum resultados/oficial.png
```

El SHA-256 esperado es
`b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d`.
En macOS se puede usar `shasum -a 256` en lugar de `sha256sum`.
`mvn clean verify` también extrae y decodifica los seis ejemplos oficiales.

## 9. Análisis externo opcional

Las herramientas Python son independientes de la CLI Java. Requieren Python 3,
sus dependencias y acceso a los cuatro BMP del grupo, que pueden no estar
incluidos en una copia del repositorio:

```sh
ENV_DIR=$(mktemp -d)
python3 -m venv "$ENV_DIR"
. "$ENV_DIR/bin/activate"
python -m pip install -r tools/requirements-analysis.txt
python -m unittest discover -s tools -p 'test_*.py'
python tools/stego_analysis.py --solve-group3 \
  docs/docs_catedra/grupo3/roma.bmp docs/docs_catedra/grupo3/hugo4.bmp \
  docs/docs_catedra/grupo3/titanic.bmp docs/docs_catedra/grupo3/frozen.bmp
python tools/validate_group3.py docs/docs_catedra/grupo3
```

El segundo comando de análisis compara los resultados con el JAR compilado y
requiere ejecutar primero el primero. Las salidas quedan en
`output/stegoanalysis/`, ignorado por Git; no se modifican los originales.
Para evitar crear `.venv` dentro de la entrega se puede crear el entorno
virtual fuera del repositorio, como en el ejemplo.

`python tools/render_analysis_report.py` produce opcionalmente un PDF del
documento de arquitectura. Acepta `--input archivo.md --output archivo.pdf`
para otro documento Markdown sencillo; no genera automáticamente el informe
completo del TP.
