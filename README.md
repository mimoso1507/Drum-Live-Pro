# Drum Live Pro

App de batería con pads para **Android** (nativa, Kotlin) y **Windows** (`.exe`, Kotlin/JVM). Próxima versión: navegador.

## Funciones
- Pads con íconos automáticos según el instrumento (más de 80 íconos originales) y mapeo por **nota MIDI 0‑127**.
- Agregar / quitar filas de pads (hasta 8 filas de 4).
- **Kits SF2**: 13 baterías incluidas (GeneralUser GS) + importar tus propios `.sf2`. Botón **+** para crear un kit personalizado con nombre, color, ícono y mapeo libre (cada pad puede usar cualquier preset de cualquier SF2).
- **DrumPad AutoPlay** sincronizado con el compás y el tempo: toca un pad para subir su intensidad (6 niveles), mantén pulsado para apagarlo.
- **Grabación** en loop con overdub, deshacer toma, metrónomo con cuenta previa, **cuantización** (1/4 a 1/64 + tresillos, swing y fuerza), compás independiente (2/4, 3/4, 4/4, 5/4, 6/8, 7/8, 12/8…).
- Exportar a **MIDI** (canal 10) y **WAV**.
- Panel **FX**: Distorsión (Band Width, Low Pass, Intensity), Reverb (Time, High Freq, Mix), Eco (Delay, Feedback, Dry/Wet), cada uno con interruptor y «Restablecer». Slider rápido de reverb.
- **Vista de batería virtual** generada automáticamente desde los pads (si hay más pads, dibuja más piezas).
- **MIDI USB y Bluetooth LE** con botón **Aprender**: pulsa Aprender, toca un pad y golpea la tecla del controlador.

## Versión Windows
- Se abre **a pantalla completa y siempre en horizontal** (si el monitor está vertical, la app se muestra en una franja apaisada). **ESC** sale de pantalla completa, **F11** vuelve.
- Mismo motor de audio, SF2, kits, AutoPlay, grabación, cuantización, FX, temas y vista de batería que en Android (el código compartido vive en `app/src/main/java`; `desktop/` agrega la interfaz de escritorio).
- Se toca con el mouse (clic derecho en un pad = editarlo), con el **teclado** (filas `1 2 3 4` / `Q W E R` / `A S D F` / `Z X C V`…; Mayúsculas = golpe fuerte; espacio = reproducir/detener) o con un **controlador MIDI USB** (con «Aprender»).
- Arrastra archivos `.sf2` a la ventana para importarlos. Las exportaciones MIDI/WAV van a `Documentos\DrumLivePro`.

## Compilar
El APK se compila automáticamente con GitHub Actions en cada push a `main` (pestaña *Actions* o la última release (el archivo se llama `Drum-Live-Pro-vX.Y.apk`)).
Localmente: `./gradlew assembleDebug` (Android SDK 35, JDK 17).

Windows: el workflow *Windows EXE* compila `desktop/` y lo empaqueta con `jpackage` (instalador `Drum-Live-Pro-vX.Y-Windows-Setup.exe` y versión portable `.zip`) en la release `windows-vX.Y`. Localmente: `./gradlew -p desktop run`.

## Créditos
Sonidos de batería incluidos: extraídos de *GeneralUser GS v2* de S. Christian Collins (licencia de uso libre, ver `tools/`).
`tools/sf2drums.py` extrae los kits (banco 128) de un SF2 completo.
