# Exercises HPC

Gruppenmitglieder:
* Andreas Burger (se15m020)
* Teresa Melchart (se15m027)
* Johanna Strutzenberger (se15m006)

## Exercise 1 - Image Rotation (gel�st)
*at.fhtw.hpc.exercise1.ImageRotation*

Bei der Aufgabe 1 hatten wir keine Probleme. Wir verwenden zur �bergabe in den Kernel keine Arrays, sondern das cl_image_format.

## Exercise 2 - Scan (gel�st)
*at.fhtw.hpc.exercise2.ScanComparer*

Bei der Aufgabe 2 war die gr��te Herausforderung der Umgang mit den gro�en Arrays beim Work-Efficient Scan. Schlussendlich konnten wir diese aber l�sen.

## Exercise 3 - Scan Application (gel�st)
Wir haben den Radix-Sort gew�hlt. In der Abgabe sind 2 L�sungen:
1. *at.fhtw.hpc.exercise3.Radixsort*

Funktioniert mit gro�en Arrays (> 1 Mio), verwendet jedoch Java Objekte zwischen den Kernels.

2. *at.fhtw.hpc.exercise3.RadixSortUsingBuffers*

�bergibt cl_mem Objekte von Kernel zu Kernel, funktioniert allerdings nur mit kleinen Arrays (im Versuch <= 512).

Leider ging es sich zeitlich nicht mehr aus, eine L�sung, die die Buffer verwendet und auch f�r gro�e Arrays funktioniert, zu erarbeiten.