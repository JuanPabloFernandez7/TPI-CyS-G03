# stegobmp

Implementación del trabajo práctico de esteganografía de Criptografía y Seguridad (72.04), ITBA.

## Requisitos

- JDK 21
- Maven

## Compilar

```sh
mvn clean package
```

## Ejecutar

```sh
java -jar target/stegobmp-1.0.0-SNAPSHOT.jar --help
```

## Tests

```sh
mvn test            # pruebas unitarias
mvn clean verify    # unitarias + interoperabilidad OpenSSL + pruebas sobre el JAR empaquetado
```

## Estado

EMBED y EXTRACT funcionan con LSB1, LSB4 y LSBI, con y sin cifrado (AES-128/192/256, 3DES; ECB, CBC, CFB8, OFB). Se aceptan BMP de 24 bits sin compresión con DIB V3, V4 o V5, preservando el encabezado y usando el offset real. V5 se agregó para extraer la muestra `frozen.bmp` del grupo.

Se resolvieron las cuatro muestras del grupo 3: `roma.bmp` (LSBI, PDF), `hugo4.bmp` (LSB4, PNG con ZIP y Buscaminas), `titanic.bmp` (contraseña añadida al final) y `frozen.bmp` (LSB1 + AES-128-CBC, WMV). El [informe de estegoanálisis](docs/ESTEGOANALISIS_G03.md) incluye el procedimiento, las diez cuestiones del punto 7, hashes, comparación experimental y comandos de reproducción. Las salidas se generan en `output/stegoanalysis/` y no se versionan.

```sh
python -m pip install -r tools/requirements-analysis.txt
python tools/stego_analysis.py --solve-group3 /ruta/grupo3/roma.bmp /ruta/grupo3/titanic.bmp /ruta/grupo3/frozen.bmp /ruta/grupo3/hugo4.bmp
python tools/validate_group3.py /ruta/grupo3
```

Python y sus bibliotecas son herramientas auxiliares para reproducir el análisis; el ejecutable Java no las necesita. La CLI extrae los casos LSB indicando el método; el script auxiliar descubre candidatos y recupera el texto posterior a los píxeles de Titanic. No hay un subcomando `-analyze` en el JAR.

Quedan por comprobar la ejecución real con Java 21 en WSL/Pampero y el convenio LSBI en imágenes con padding de fila, ausente en las muestras del grupo. Códigos de salida: 0 éxito/ayuda, 1 error de operación, 2 error de uso, 3 reservado. Plan: [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md); diseño LSBI: [`docs/LSBI_DESIGN.md`](docs/LSBI_DESIGN.md); diseño LSB4: [`docs/LSB4_DESIGN.md`](docs/LSB4_DESIGN.md).
