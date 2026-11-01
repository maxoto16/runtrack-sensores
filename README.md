# RunTrack – Gestión de sensores (AVS.03.03.01)

App Android (Kotlin) para la asignatura Desarrollo de Aplicaciones Móviles.
Captura y filtra en tiempo real el **acelerómetro** y el **giroscopio** y detecta cambios de dirección del corredor.

## Cómo abrirla en Android Studio
1. `git clone https://github.com/maxoto16/runtrack-sensores`
2. File → Open → selecciona la carpeta `runtrack-sensores` y espera el Gradle Sync.
3. Ejecuta (▶) en un emulador o en un celular con depuración USB.

En el emulador, los sensores se simulan en ⋯ → Virtual sensors.

## Qué hace
- Verifica qué sensores existen (`SensorManager.getDefaultSensor`).
- Registra los listeners en `onResume` y los libera en `onPause`.
- Filtra eventos (`SensorFilter`): límite de frecuencia, suavizado paso bajo y descarte de duplicados.
- Detecta giros con el giroscopio (`TurnDetector`).
- Interruptor de filtro y panel de métricas para comparar el impacto en rendimiento.

Requisitos: Android Studio reciente, JDK 17, minSdk 24.
