# Diseño de LSBI para BMP V3

## 1. Fuentes y alcance

Fuentes principales:

1. Consigna, `Trabajo Practico Implementacion-2026_2.pdf`:
   - §4, p. 2: BMP V3; el cuerpo se lee en orden físico B, G, R.
   - §5.3, pp. 4–5: definición de LSBI y registro de los cuatro patrones.
   - §5.4, p. 5: tamaño Big Endian y parámetros criptográficos.
2. Mohammed Abdul Majeed y Rossilawati Sulaiman, “An Improved LSB Image Steganography Technique using bit-inverse in 24 bit colour image”, JATIT, Vol. 80, No. 2, 20 Oct. 2015:
   - §5, PDF p. 4 / página impresa 344: criterio de patrones, conteo de modificaciones y recuperación.
   - §6, PDF pp. 4–5 / páginas impresas 344–345: pseudocódigo propuesto.

La consigna es normativa para este TP. Se usa el paper para completar los detalles del algoritmo que la consigna remite a él, excepto donde la consigna especifica una representación diferente.

## 2. Consigna frente al paper y resolución de D-005

| Aspecto | Consigna | Paper | ¿Contradicción? | Interpretación para este TP e impacto |
|---|---|---|---|---|
| Canales para los bits del secreto | §5.3, p. 4 remite al método del paper. §4, p. 2 exige recorrer bytes BMP en orden físico B, G, R. | Resumen, PDF p. 1; §5, p. 4; §6, pp. 4–5: aplica inversión e inserción de datos a los canales Green y Blue; Red se deja como ruido. | No respecto del conjunto de canales: la consigna remite al paper y el orden físico puede aplicarse al subconjunto elegido. | Usar solo B y G para datos, filtrando el cuerpo en orden físico: por píxel B primero, luego G; saltar R. La mención global BGR no obliga a almacenar secreto en los tres canales. |
| Canales para los cuatro flags | §5.3, p. 5: almacenar los 4 bits con LSB1 normal en los primeros cuatro bytes consecutivos; explícitamente indica que no se invierten y que se usan R, G y B. | §5, p. 4: hasta cuatro patrones requieren flags en una ubicación específica, sin fijar una codificación BMP concreta. | No: la consigna especifica cómo resolver la ubicación que el paper deja abierta. | Los LSB de los primeros cuatro bytes del cuerpo guardan, en orden, flags para patrones `00`, `01`, `10`, `11`. Estas posiciones se excluyen del flujo de datos. |
| Orden de recorrido de datos | §4, p. 2: bytes físicos B, G, R en ese orden. | §6, p. 4: pseudocódigo dice “For each colour from green and blue”, que enumera G antes que B. | Sí, si se interpreta el orden del pseudocódigo del paper como orden obligatorio del flujo. | Prevalece el orden explícito de la consigna: se filtran los bytes físicos y se usan B y G en ese orden. Esto determina exactamente qué bit va a qué componente y se usa igual al extraer. |
| Canales rojos | La consigna fija el orden BGR; no dice explícitamente que Red porte los bits del secreto en LSBI. | Resumen y §5 dicen que Red no se usa para ocultar el mensaje y actúa como ruido. | No, al aplicar la referencia que la consigna exige leer. | Los bytes R no portan datos ni se alteran, salvo que uno de los primeros cuatro bytes del cuerpo sea un byte R que contiene un flag según lo manda la consigna. |
| Definición de patrón | §5.3, p. 4: cuatro patrones de los bits segundo y tercero menos significativos. §5.3, p. 5 los ordena `00`, `01`, `10`, `11`. | §5, p. 4: usa los bits segundo y tercero desde el final; ejemplo clasifica A, B y D como `10`, C como `01`. §6, p. 4 lista los cuatro patrones. | No sustantiva. | Se toma el par `(bit 2, bit 1)` del byte, es decir `pattern = (value >>> 1) & 0b11`, con el bit 2 como primer carácter del nombre. Así, `10001100` es `10`, como en el ejemplo del paper. |
| Cuándo invertir | §5.3, p. 4 remite al algoritmo LSBI del paper, no da otra regla de conteo. | §5, p. 4, pasos 4–5: comparar por patrón y complementar LSB cuando los componentes cambiados son más que los no cambiados. | No; el criterio lo completa el paper. | Contar, en los componentes de datos de cada patrón, `changed` cuando el LSB original difiere del bit secreto y `unchanged` en caso contrario. Invertir todos los LSB de ese patrón solo si `changed > unchanged`; empate significa no invertir. Minimiza cambios sin alterar los bits del patrón. |
| Orden de los flags | §5.3, p. 5: orden explícito `00`, `01`, `10`, `11`; ejemplo flags `1010`. | §5, p. 4 solo pide guardar el estado de hasta cuatro patrones. | No. | Flags en orden `00`, `01`, `10`, `11`. Ejemplo `1010` significa invertir `00` y `10`. |
| Padding de fila | §4, p. 2 define el cuerpo y el orden físico, pero no describe explícitamente el uso del padding para LSBI. | El paper habla de canales de color/píxeles y no trata padding BMP. | No es una contradicción textual; es una omisión de ambas fuentes. | El padding no es canal de color y se omite para datos. Los bytes de padding se preservan salvo que estén entre los primeros cuatro bytes físicos y almacenen un flag exigido por la consigna. Requiere conocer ancho, filas y stride, por lo que se agrega `CarrierLayout`. |
| Altura top-down | No detalla LSBI para altura negativa. | No trata BMP top-down. | No. | Procesar filas en el orden físico de almacenamiento; la altura negativa cambia la orientación visual, no el orden de los bytes. |
| Bit order del secreto | La consigna exige orden físico de componentes, pero no define si los bits de cada byte del secreto se toman MSB-first o LSB-first en LSBI. | El paper dice que se extraen y alinean LSBs para reconstruir los bits; no fija con claridad una convención de byte más allá de sus ejemplos. | Ambigüedad real, no contradicción esencial. | Usar `BitOrder.DEFAULT` (MSB-first), igual que LSB1/LSB4 y el formato de bytes existente. Se mantiene aislado para validar con material oficial. |

### Conclusión sobre D-005

La diferencia de canales no bloquea una implementación fundamentada. Para el contenido, la consigna remite explícitamente al paper, cuyo algoritmo usa Green y Blue y reserva Red como ruido. La exigencia de la consigna de orden físico se satisface recorriendo BGR y filtrando Red: para cada píxel se usa B y luego G. Para los flags prevalece la especificación más concreta de la consigna: los primeros cuatro bytes físicos, usando LSB1 y sin inversión.

La enumeración G→B del pseudocódigo del paper sí difiere del orden físico B→G exigido por la consigna. Se adopta B→G por prioridad normativa. No se requiere consultar a la cátedra para elegir esta implementación, aunque la compatibilidad con sus archivos solo podrá confirmarse con una muestra.

## 3. Representación de patrones y criterio de inversión

Para un valor de componente de 8 bits `v`, el patrón es:

```text
pattern(v) = (v >> 1) & 0b11
bit 1 del índice = bit 2 de v = tercer bit menos significativo
bit 0 del índice = bit 1 de v = segundo bit menos significativo
```

| Índice | Texto | Condición en `v` |
|---:|:---:|---|
| 0 | `00` | bits 2 y 1 son 0, 0 |
| 1 | `01` | bits 2 y 1 son 0, 1 |
| 2 | `10` | bits 2 y 1 son 1, 0 |
| 3 | `11` | bits 2 y 1 son 1, 1 |

Para cada bit del payload asignado a una componente B/G:

```text
pattern = pattern(originalComponent)
changed[pattern]   += (originalComponent.LSB != secretBit)
unchanged[pattern] += (originalComponent.LSB == secretBit)
```

La bandera es `invert[pattern] = changed[pattern] > unchanged[pattern]`. Si hay empate o no hubo posiciones de ese patrón, no se invierte. Es una comparación estricta, no `>=`.

Primero se aplica la sustitución LSB ordinaria. Luego, si `invert[pattern]` es true, se complementa el LSB de **cada componente de datos** de ese patrón. La inversión no cambia el patrón, pues los bits 2 y 1 permanecen intactos. El LSB final queda `secretBit XOR invert[pattern]`.

## 4. Flags y extracción

La consigna (§5.3, p. 5) fija cuatro flags precediendo al secreto. Se escriben como cuatro bits LSB1 sin inversión en los LSB de `body[0]` a `body[3]`:

```text
body[0].LSB = flag[00]
body[1].LSB = flag[01]
body[2].LSB = flag[10]
body[3].LSB = flag[11]
```

Se conservan los otros siete bits de esos bytes. Estos cuatro bytes físicos se reservan y no se usan como componentes del payload, aunque alguno sea B o G. La secuencia de datos comienza después de esos bytes: al recorrer el cuerpo, se seleccionan las posiciones B/G que estén en píxeles y cuyo índice físico sea al menos 4.

Al extraer:

1. Leer los LSB de los primeros cuatro bytes físicos para recuperar `flag[00..11]`.
2. Recorrer las mismas posiciones B/G, en el mismo orden físico.
3. Para cada componente calcular el patrón con los bits 2 y 1 (no modificados).
4. Recuperar el bit secreto como `component.LSB XOR flag[pattern]`.
5. Agrupar ocho bits con el `BitOrder` configurado para reconstruir cada byte.

Por lo tanto, la extracción necesita solo el BMP stego y los metadatos estructurales del BMP (ancho, cantidad de filas y stride). No consulta ni necesita el portador original.

## 5. Recorrido BMP, padding y bytes preservados

LSBI necesita distinguir canales de color y padding; una secuencia plana de bytes no basta cuando el ancho produce padding. Se introduce `CarrierLayout` en `steganography`, con ancho en píxeles, filas almacenadas y stride. `Embedder` y `Extractor` lo construyen a partir del `BmpHeader`.

Para cada fila física:

```text
pixel bytes: B, G, R, B, G, R, ...
padding:     omitido por LSBI
```

- Flags: primeros cuatro bytes del cuerpo sin importar el canal.
- Payload: solo B y G de píxeles completos, después del prefijo de flags; orden físico B luego G.
- R: no se altera, excepto si uno de los primeros cuatro bytes físicos es R y almacena un flag.
- Padding: nunca transporta datos. Se preserva, salvo que un byte de padding esté dentro del prefijo físico de cuatro bytes y su LSB deba guardar un flag según la consigna.
- Header, gap y trailing: permanecen fuera del cuerpo y se conservan por el modelo BMP.
- Top-down/bottom-up: se recorre el cuerpo en el orden de bytes recibido, sin invertir filas.

LSB1 y LSB4 no usan `CarrierLayout` para su operación; conservan exactamente su comportamiento actual. Se agrega un overload contextual al contrato para no acoplar la capa de esteganografía a `BmpImage`.

## 6. Capacidad útil

Sean:

- `W`: ancho en píxeles;
- `H`: cantidad de filas almacenadas (`abs(height)` BMP);
- `S`: stride de fila en bytes;
- `P = W * 3`: bytes de píxeles por fila;
- `R`: cantidad de bytes B/G entre los primeros cuatro bytes físicos del cuerpo:

```text
R = count(i in [0, min(4, H*S)) such that
          row = i / S,
          col = i % S,
          col < P and col % 3 is 0 or 1)
```

Hay `2*W*H` componentes portadores B/G en total. Quitando los B/G reservados para flags y agrupando ocho bits por byte:

```text
capacityBytes = floor((2*W*H - R) / 8)
```

Padding y canal R no aportan capacidad. Los cuatro flags consumen cuatro bytes físicos de prefijo, no cuatro bytes de payload. `embed` valida `data.length <= capacityBytes` antes de copiar o modificar el cuerpo. Si no alcanza, informa bytes de payload requeridos y capacidad máxima útil.

## 7. Pseudocódigo

### Inserción

```text
embed(body, data, layout):
    validar que body.length == layout.rows * layout.stride
    candidates = posiciones B/G de píxeles, índice físico >= 4, en orden de archivo
    si data.length > floor(candidates.count / 8): error de capacidad

    changed[4] = 0; unchanged[4] = 0
    result = copia(body)
    k = 0
    para cada byte d en data:
        para bitIndex = 0..7 según BitOrder:
            i = candidates[k++]
            p = (body[i] >> 1) & 3             // del portador original
            b = bit(d, bitIndex)
            result[i].LSB = b
            si body[i].LSB != b: changed[p]++ ; si no: unchanged[p]++

    para p = 0..3:
        invert[p] = changed[p] > unchanged[p]  // empate: false

    para cada i entre candidates usados:
        p = (body[i] >> 1) & 3
        si invert[p]: result[i].LSB ^= 1

    para p = 0..3:
        result[p].LSB = invert[p]              // prefijo LSB1; no se invierte
    devolver result
```

### Extracción

```text
reader(body, layout):
    invert[p] = body[p].LSB para p = 0..3
    candidates = mismas posiciones B/G, índice físico >= 4, orden físico

readByte():
    value = 0
    repetir 8 veces según BitOrder:
        i = siguiente candidate
        p = (body[i] >> 1) & 3
        secretBit = body[i].LSB XOR invert[p]
        colocar secretBit en value
    devolver value
```

`remainingBytes` es `floor(candidatesRemaining / 8)`, no la longitud del archivo oculto. El lector no reserva un arreglo mayor al número solicitado y falla antes de leer más allá de la capacidad.

## 8. Ejemplos binarios reversibles

En ambos ejemplos, `CarrierLayout(width=2, rows=5, stride=8)` produce 40 bytes de cuerpo (6 bytes BGR por fila y 2 de padding). Los primeros cuatro índices son flags. Los candidatos B/G para datos comienzan en índices `4, 8, 9, 11, 12, 16, 17, 19, ...`; R y el padding se omiten. Orden del mensaje MSB-first.

### Ejemplo 1: se invierte el patrón `10`

Mensaje `FF` = bits `1 1 1 1 1 1 1 1`. Los ocho primeros componentes candidatos tienen valor `04` (`bits 2,1 = 10`, LSB original 0). Para `10`: changed=8, unchanged=0, luego `invert[10]=1`. Los otros flags son 0.

```text
flags por patrón 00 01 10 11: 0 0 1 0
LSBs de body[0..3] antes → después: 0 0 0 0 → 0 0 1 0
LSB tras sustitución ordinaria en los ocho candidatos: 1 1 1 1 1 1 1 1
LSB después de aplicar invert[10]:                   0 0 0 0 0 0 0 0
valores candidatos 04 04 04 04 04 04 04 04 → 04 04 04 04 04 04 04 04
```

La extracción lee flags `0010`. Cada candidato es patrón `10`; `LSB 0 XOR flag 1 = 1`, reconstruyendo `FF`.

### Ejemplo 2: empate, no se invierte

Mensaje `00` = ocho bits cero. Valores originales de los candidatos, en orden:

```text
02 03 00 00 00 00 00 00
```

Los dos primeros tienen patrón `01`: LSBs `0,1`, ambos comparados contra mensaje `0`, dan changed=1 y unchanged=1. Por empate estricto, `invert[01]=0`. Los seis restantes son patrón `00` y no cambian (changed=0, unchanged=6). Patrones `10` y `11` no aparecen, por lo que tampoco se invierten.

```text
flags por patrón 00 01 10 11: 0 0 0 0
valores de los ocho candidatos antes: 02 03 00 00 00 00 00 00
valores después de sustitución:       02 02 00 00 00 00 00 00
valores después de inversión:         02 02 00 00 00 00 00 00
```

La extracción obtiene `02.LSB XOR 0 = 0`, `02.LSB XOR 0 = 0` y seis ceros: reconstruye `00`. El patrón `01` sigue identificable porque los bits 2 y 1 no se modificaron.

## 9. Payload, cifrado y casos límite

`Lsbi` implementa el mismo contrato byte-secuencial que `Lsb1`/`Lsb4`; no interpreta el payload. Los flags son metadatos propios del transporte LSBI y preceden al flujo:

```text
BMP: flags[00,01,10,11] (4 LSBs en 4 bytes) || bits del flujo de Payload en B/G
flujo sin cifrado: u32be(F) || fileBytes || extension || NUL
flujo con cifrado: u32be(C) || ciphertext
```

La capa `Embedder` serializa/cifra igual que antes; el lector LSBI devuelve los bytes del flujo sin exponer los flags a `PayloadCodec`. Todas las combinaciones Crypto quedan por ello soportadas sin cambios en Payload ni Crypto.

Casos cubiertos por los tests:

- geometría artificial con menos de cuatro bytes para flags (un BMP válido tiene al menos cuatro bytes de cuerpo);
- payload vacío (flujo esteganográfico de longitud cero) y archivo vacío (payload válido no vacío);
- todos los patrones, patrón ausente, cambio siempre/no cambio y empates;
- capacidad exacta y un byte más;
- filas sin padding, con padding, ancho 1 y altura negativa;
- portador original inmutable; bits superiores y bytes R/padding no usados preservados fuera del prefijo de flags obligatorio;
- lectura por lotes, lectura secuencial y sobrelectura;
- BMP con gap/trailing;
- cifrado sin cifrado y las 16 combinaciones;
- LSB1/LSB4 y compatibilidad del JAR sin regresiones.

## 10. Decisiones y límites de compatibilidad

**Derivadas de requisitos explícitos:** flags en los primeros cuatro bytes físicos; flags en orden `00,01,10,11`, LSB1 sin inversión; formato Payload existente; orden físico BMP BGR.

**Completadas con el paper:** datos en G/B; patrones desde bits 2 y 1; inversión del LSB cuando las modificaciones superan las no modificaciones; recuperar con las mismas banderas.

**Decisiones de integración para este TP:** filtrar B/G en orden físico B→G por encima del orden G→B del pseudocódigo del paper; excluir padding porque no representa un canal; contar solo componentes empleados por los bits del flujo; tie=no invertir; MSB-first por consistencia con `BitOrder.DEFAULT`.

Las decisiones de orden B→G y MSB-first recuperaron el mismo PNG válido de tres ejemplos oficiales, claros y cifrados. La evidencia está en [`OFFICIAL_SAMPLE_VALIDATION.md`](OFFICIAL_SAMPLE_VALIDATION.md). Esto confirma los vectores disponibles, no todas las imágenes de evaluación ni combinaciones no incluidas.
