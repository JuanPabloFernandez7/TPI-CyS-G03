# Diseño e implementación de LSB4

## Alcance de la consigna

La consigna, `Trabajo Practico Implementacion-2026_2.pdf`, §5.3, p. 4, define:

> “Es una variante del anterior, donde 4 bits del mensaje se ocultan en los 4 bits menos significativos de la componente del pixel que corresponda.”

El párrafo anterior define LSB1: los componentes se recorren en el orden en que se encuentran en los píxeles. La sección BMP (§5.4, p. 5) especifica orden físico B, G, R, incluyendo el padding usado como cuerpo en este proyecto. La consigna exige además informar la capacidad máxima cuando el mensaje no cabe.

Por lo tanto, para cada byte de portador recorrido:

- se reemplazan solo sus cuatro bits menos significativos;
- se transmiten cuatro bits consecutivos del flujo;
- los bits altos del componente se conservan;
- el recorrido y la región portadora son los mismos que LSB1.

La capacidad del flujo es `floor(bodyLength / 2)` bytes: hacen falta dos bytes del cuerpo por cada byte de mensaje. Si el cuerpo tiene longitud impar, el último byte queda sin usar.

## Orden de los nibbles

La consigna no dice si los primeros cuatro bits del byte se deben almacenar en el primer componente como nibble alto (bits 7–4) o nibble bajo (bits 3–0). Esta decisión afecta compatibilidad con los archivos de evaluación y no puede confirmarse sin un vector o archivo de la cátedra.

**Decisión provisional implementada:** usar el orden de transmisión MSB-first ya adoptado por LSB1. Así, los bits 7–4 del byte de mensaje se almacenan en el primer nibble portador y los bits 3–0 en el siguiente. En ambos casos, los cuatro bits se escriben en el nibble bajo del byte de portador. El orden se controla mediante `BitOrder` y `Lsb4(BitOrder)`, de modo que se puede probar la variante inversa sin alterar la arquitectura. La CLI usa MSB-first por defecto.

Para un cuerpo con bytes iniciales `A0 50` y el byte de mensaje `AB`, el resultado de inserción es `AA 5B`: el primer nibble transmitido es `A`, luego `B`, y los nibbles altos originales (`A` y `5`) se conservan.

## Arquitectura

`Lsb4` implementa `Steganography` y solo opera sobre el cuerpo:

- `capacityBytes(int)`: `bodyLength / 2`.
- `embed(body, data)`: valida la capacidad antes de copiar o modificar; copia el cuerpo y reemplaza los nibbles bajos necesarios. No muta la entrada ni cambia bytes posteriores al mensaje.
- `newReader(body)`: lector secuencial simétrico a la inserción; `remainingBytes()` expresa capacidad restante, no longitud de mensaje.
- `BitOrder`: se reutiliza para que inserción y extracción compartan el orden del flujo.
- `Steganographies`: resuelve `LSB4` a una instancia de `Lsb4`, conectándola con `Main`, `Embedder` y `Extractor`. CLI, BMP, Payload y Crypto conservan sus contratos.

La capa no inspecciona el BMP, no conoce el formato del payload y no cifra. El flujo de EMBED/EXTRACT es el de la integración existente, sustituyendo LSB1 por LSB4.

## Pruebas

`Lsb4Test` incluye:

- vector de inserción calculado manualmente (`AB` sobre `A0 50` → `AA 5B`);
- vector de extracción con bits altos arbitrarios;
- vector para el orden alternativo LSB-first;
- round-trip de los 256 valores de byte y de datos pseudoaleatorios con semillas fijas;
- mensaje vacío;
- verificación de que solo cambian nibbles bajos de los bytes usados y el cuerpo original no se modifica;
- capacidad exacta, un byte de exceso y cuerpo de longitud impar;
- lectura fuera de rango sin consumir datos.

`EndToEndLsb4Test` cubre EMBED/EXTRACT por `Main.run`, round-trip de archivos byte por byte con y sin extensión, archivo vacío, las 16 combinaciones criptográficas, defaults, capacidad exacta e insuficiente (sin escribir salida), BMP con gap/padding/trailing y errores de BMP/payload. `PackagedJarIT` valida también el JAR ejecutable con LSB4 y AES-CBC.

El ejemplo oficial `ladoLSB4.bmp` recuperó el PNG esperado, confirmando el orden alto-primero para ese vector. La evidencia está en [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md); no garantiza compatibilidad con cualquier variante no cubierta por la muestra.
