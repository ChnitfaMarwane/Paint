# Java Paint Application

A 2D drawing application built with Java Swing. 

## Features
* Drawing tools: Pencil, Line, Rectangle, Oval, and Eraser.
* Flood Fill: Uses a Breadth-First Search (BFS) algorithm.
* Undo System: Uses a Stack to revert changes (Ctrl+Z).
* Clipboard: Copy and paste using the OS clipboard (Ctrl+C / Ctrl+V).
* File I/O: Open and save images in PNG format.

## Project Files
* PaintApp.java: Main GUI, menus, and toolbars.
* CanvasPanel.java: Mouse events, graphics rendering, and algorithms.
* PaintApp.jar: Executable file.

## How to Run

Run the executable JAR:
```java -jar PaintApp.jar```

Or compile and run from source:
```javac PaintApp.java CanvasPanel.java```
```java PaintApp```