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

EMBED y EXTRACT funcionan con LSB1, LSB4 y LSBI, con y sin cifrado (AES-128/192/256, 3DES; ECB, CBC, CFB8, OFB). Los ejemplos oficiales confirmaron LSB4 alto-primero, LSBI B/G en orden B→G y los casos cifrados AES-128-CBC, AES-256-OFB y 3DES-CFB8; ver [`docs/OFFICIAL_SAMPLE_VALIDATION.md`](docs/OFFICIAL_SAMPLE_VALIDATION.md). Esta validación no completa el estegoanálisis ni la entrega final. Códigos de salida: 0 éxito/ayuda, 1 error de operación, 2 error de uso, 3 algoritmo no implementado (ninguno de los algoritmos CLI actuales). Plan: [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md); diseño LSBI: [`docs/LSBI_DESIGN.md`](docs/LSBI_DESIGN.md); diseño LSB4: [`docs/LSB4_DESIGN.md`](docs/LSB4_DESIGN.md).
