# Exercises HPC

Gruppenmitglieder:
* Andreas Burger (se15m020)
* Teresa Melchart (se15m027)
* Johanna Strutzenberger (se15m006)

## Exercise 1 - Image Rotation (gelöst)
*at.fhtw.hpc.exercise1.ImageRotation*

Bei der Aufgabe 1 hatten wir keine Probleme. Wir verwenden zur Übergabe in den Kernel keine Arrays, sondern das cl_image_format.

## Exercise 2 - Scan (gelöst)
*at.fhtw.hpc.exercise2.ScanComparer*

Bei der Aufgabe 2 war die größte Herausforderung der Umgang mit den großen Arrays beim Work-Efficient Scan. Schlussendlich konnten wir diese aber lösen.

## Exercise 3 - Scan Application (gelöst)
Wir haben den Radix-Sort gewählt. In der Abgabe sind 2 Lösungen:
1. *at.fhtw.hpc.exercise3.Radixsort*

Funktioniert mit großen Arrays (> 1 Mio), verwendet jedoch Java Objekte zwischen den Kernels.

2. *at.fhtw.hpc.exercise3.RadixSortUsingBuffers*

Übergibt cl_mem Objekte von Kernel zu Kernel, funktioniert allerdings nur mit kleinen Arrays (im Versuch <= 512).

Leider ging es sich zeitlich nicht mehr aus, eine Lösung, die die Buffer verwendet und auch für große Arrays funktioniert, zu erarbeiten.