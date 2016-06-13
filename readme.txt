Gruppenmitglieder:
Andreas Burger (se15m020)
Teresa Melchart ()
Johanna Strutzenberger ()

Image Rotation(gelöst): at.fhtw.hpc.exercise1.ImageRotation
Bei der Aufgabe 1 hatten wir keine Probleme. Wir verwenden zur Übergabe in den Kernel keine Arrays, sondern das cl_image_format.

Scan (gelöst): at.fhtw.hpc.exercise2.ScanComparer
Bei der Aufgabe 2 war die größte Herausforderung der Umgang mit den großen Arrays beim Work-Efficient Scan. Schlussendlich konnten wir diese aber lösen.

Scan Application (gelöst): at.fhtw.hpc.exercise3.RadixSort
Wir haben den Radix-Sort gewählt. Allerdings konnten keine cl_mem Objekte aus dem Kernel zurückgegeben werden. Das Problem war, dass es ab eine gewissen Größe das Ergebnis nicht mehr korrekt war.