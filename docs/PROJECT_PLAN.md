# Plan del proyecto stegobmp

## Alcance y fuentes

Implementar `stegobmp` en uno de los lenguajes permitidos por la consigna: C con OpenSSL o Java 21. El programa deberá ocultar y extraer archivos en BMP V3 de 24 bits sin compresión, soportar LSB1, LSB4 y LSBI, y ofrecer el cifrado opcional y el análisis requeridos por la cátedra.

La fuente principal es `Trabajo Practico Implementacion-2026_2.pdf`. Se consultaron también el paper `16Vol80No2.pdf` y los apuntes/manuales suministrados sobre OpenSSL, JCA y PBKDF2. En caso de conflicto, la consigna prevalece, salvo ambigüedades que puedan afectar la compatibilidad; esas se resolverán antes de implementar el comportamiento afectado.

## Arquitectura propuesta

La arquitectura separa responsabilidades y mantiene las reglas del formato independientes del CLI:

- **CLI:** parseo estricto, validación de opciones, defaults y mensajes de error.
- **BMP:** lectura y validación del encabezado, acceso al cuerpo en orden físico, preservación del encabezado y escritura sin alteraciones ajenas.
- **Payload:** serialización y parsing de tamaño, contenido, extensión y terminador; manejo explícito de Big Endian.
- **Esteganografía:** contrato común de inserción/extracción y estrategias LSB1, LSB4 y LSBI; cálculo de capacidad.
- **Criptografía:** PBKDF2, derivación de clave/IV y cifrado simétrico con las combinaciones exigidas.
- **Análisis:** procedimientos para probar métodos sobre las muestras de la cátedra y documentar evidencia, sin inventar resultados.
- **Pruebas:** unitarias por módulo, pruebas de ida y vuelta e integración con archivos controlados.

Si se elige Java, una posible organización es `cli`, `bmp`, `payload`, `steganography`, `crypto`, `analysis` y `common` bajo `src/main/java`, con pruebas bajo `src/test/java`. Si se elige C, se conservarán las mismas fronteras en módulos `.c/.h`, con una compilación reproducible y OpenSSL. El detalle del empaquetado se decide junto con el lenguaje.

## Etapas y criterios de salida

0. **Configuración inicial — completa:** Maven, Java 21 y JAR ejecutable.
1. **CLI — completa:** parser estricto, defaults, ayuda y códigos de salida (`ArgumentParserTest`, `MainTest`).
2. **BMP — completa localmente:** BMP V3 de 24 bits sin compresión, offset real, stride/padding, top-down/bottom-up, preservación de gap/trailing (`BmpReaderTest`, `BmpRoundTripIT`). Los siete BMP oficiales inspeccionados son compatibles con el lector, aunque no cubren padding, offset extendido ni trailing.
3. **LSB1 — completa e integrada:** inserción/extracción secuencial, capacidad y orden provisional MSB-first (`Lsb1Test`, `Lsb1BmpTest`, `EndToEndLsb1Test`, JAR).
4. **Payload — completa e integrada:** serialización, parsing estricto, extensión, marco cifrado y validaciones (`PayloadCodecTest`, `PayloadLsb1CompatibilityTest`, flujos end-to-end).
5. **Criptografía — completa e integrada:** AES/3DES, los cuatro modos, PBKDF2, limpieza de secretos y pruebas Java ↔ OpenSSL (`SymmetricCipherTest`, `OpenSslInteropIT`).
6. **Integración funcional LSB1 — completa:** CLI + BMP + Payload + Crypto + esteganografía; capacidad, errores, defaults y JAR (`EndToEndLsb1Test`, `PackagedJarIT`).
7. **LSB4 — implementada, integrada y validada con ejemplo oficial:** el vector `ladoLSB4.bmp` confirmó nibble alto primero.
8. **LSBI — implementada, integrada y validada con ejemplos oficiales:** `ladoLSBI.bmp`, `ladoLSBIaes256ofb.bmp` y `ladoLSBIdescfb.bmp` confirmaron la variante B→G/MSB-first y los casos cifrados indicados. Diseño y evidencia en [`LSBI_DESIGN.md`](LSBI_DESIGN.md) y [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md).
9. **Estegoanálisis — parcial:** se validaron seis ejemplos oficiales de extracción, pero esto no equivale a completar el análisis de todas las imágenes y requisitos experimentales de la consigna.
10. **Validación final y entrega — parcial:** completar las combinaciones que no cubren los ejemplos oficiales, terminar los requisitos de estegoanálisis y validar en Pampero o entorno equivalente.

Cada etapa se considera terminada solo al pasar sus criterios de prueba; compilar por sí solo no alcanza. La validación oficial aquí registrada se limita a los seis ejemplos enumerados y no equivale a certificar todos los requisitos o el estegoanálisis.

## Riesgos y dependencias

- Los ejemplos oficiales disponibles confirman seis casos de extracción; aún faltan los demás requisitos de estegoanálisis y la validación en Pampero o entorno equivalente.
- El PDF de la consigna expresa la versión Java como “21.03”; el proyecto usa Java 21 y se validó con Corretto 21.0.10. Si la cátedra usa esa cadena de versión literalmente, requiere confirmación.
- La consigna no determina el orden de nibbles de LSB4 ni el orden de componentes B/G de LSBI; los ejemplos oficiales disponibles confirman las variantes implementadas, sin cubrir todas las posibles imágenes.
- Para LSBI, la consigna y el paper difieren en el orden de enumeración de canales (B→G físico frente a G→B en el pseudocódigo). Los ejemplos oficiales disponibles son compatibles con la decisión B→G basada en el orden físico explícito de la consigna.
- Los ejemplos genéricos de los apuntes criptográficos no sustituyen los parámetros concretos fijados en la consigna.
- La CLI documentada no define claramente un subcomando separado para análisis automático; se verificará el alcance requerido al integrar la funcionalidad.

## Estado actual

- Lenguaje confirmado: Java 21.
- Auditoría del repositorio ejecutada con Corretto 21.0.10 y OpenSSL 3.6.2: antes de LSB4, `mvn clean verify` pasó con 434 unitarias y 106 de integración, sin fallos ni omisiones.
- Estado luego de implementar LSB4: `mvn clean test` pasó con 450 unitarias y `mvn clean verify` con 450 unitarias + 107 de integración, sin fallos ni omisiones.
- El orden LSB4 alto-primero se confirmó con `ladoLSB4.bmp`.
- LSBI B→G y MSB-first se confirmaron con tres muestras oficiales; otras variantes siguen sujetas a verificación.
- Validación LSBI: Corretto 21.0.10, OpenSSL 3.6.2; `mvn clean test` ejecutó 482 pruebas (0 fallos/omisiones) y `mvn clean verify` ejecutó 482 unitarias + 109 de integración (0 fallos/omisiones). Incluye 97 pruebas de interoperabilidad OpenSSL y 10 ejecuciones del JAR.
- Los seis payloads oficiales se extrajeron y validaron como PNG byte-idénticos; los resultados y hashes están en [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md). El estegoanálisis general y la validación en Pampero siguen pendientes.
