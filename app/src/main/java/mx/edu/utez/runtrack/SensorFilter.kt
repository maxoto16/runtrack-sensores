package mx.edu.utez.runtrack

import kotlin.math.abs
import kotlin.math.sqrt

/** Lectura ya filtrada que se muestra en pantalla. */
data class Reading(val x: Float, val y: Float, val z: Float, val timestampNs: Long) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

/**
 * Control y filtrado de eventos de un sensor de 3 ejes.
 *
 * Aplica, en este orden:
 *  1. Límite de frecuencia: descarta eventos que llegan antes de [minIntervalMs].
 *  2. Filtro paso bajo (suavizado) para quitar el ruido del sensor.
 *  3. Descarte de duplicados: si la lectura casi no cambió respecto a la última
 *     aceptada (menos de [minDelta]), no se vuelve a publicar.
 *
 * Es una clase de Kotlin puro (sin dependencias de Android) para poder probarla fácil.
 */
class SensorFilter(
    private val minIntervalMs: Long = 100L,
    private val alpha: Float = 0.25f,
    private val minDelta: Float = 0.05f
) {
    var received = 0L; private set
    var accepted = 0L; private set
    var discardedByRate = 0L; private set
    var discardedByDuplicate = 0L; private set

    private var lastAcceptedNs = Long.MIN_VALUE
    private var last: Reading? = null
    private var sx = 0f
    private var sy = 0f
    private var sz = 0f
    private var primed = false

    /** Devuelve la lectura filtrada, o null si el evento se descarta. */
    fun process(timestampNs: Long, x: Float, y: Float, z: Float): Reading? {
        received++

        // 1) Control de frecuencia
        if (lastAcceptedNs != Long.MIN_VALUE &&
            (timestampNs - lastAcceptedNs) < minIntervalMs * 1_000_000L
        ) {
            discardedByRate++
            return null
        }

        // 2) Suavizado (paso bajo exponencial)
        if (!primed) {
            sx = x; sy = y; sz = z; primed = true
        } else {
            sx += alpha * (x - sx)
            sy += alpha * (y - sy)
            sz += alpha * (z - sz)
        }

        // 3) Duplicados
        val prev = last
        if (prev != null &&
            abs(sx - prev.x) < minDelta &&
            abs(sy - prev.y) < minDelta &&
            abs(sz - prev.z) < minDelta
        ) {
            discardedByDuplicate++
            return null
        }

        val r = Reading(sx, sy, sz, timestampNs)
        last = r
        lastAcceptedNs = timestampNs
        accepted++
        return r
    }

    fun reset() {
        received = 0; accepted = 0; discardedByRate = 0; discardedByDuplicate = 0
        lastAcceptedNs = Long.MIN_VALUE; last = null; primed = false
    }
}

/**
 * Detecta cambios de dirección del corredor usando la velocidad angular en el
 * eje Z del giroscopio (rad/s). Un giro cuenta cuando la velocidad supera el
 * umbral y respeta un tiempo de espera para no contar el mismo giro varias veces.
 */
class TurnDetector(
    private val thresholdRadS: Float = 1.2f,
    private val cooldownMs: Long = 600L
) {
    var turns = 0; private set
    private var lastTurnNs = Long.MIN_VALUE

    /** @return +1 giro a la izquierda, -1 a la derecha, 0 si no hay giro nuevo. */
    fun onGyroZ(timestampNs: Long, wz: Float): Int {
        if (abs(wz) < thresholdRadS) return 0
        if (lastTurnNs != Long.MIN_VALUE && (timestampNs - lastTurnNs) < cooldownMs * 1_000_000L) return 0
        lastTurnNs = timestampNs
        turns++
        return if (wz > 0) 1 else -1
    }

    fun reset() { turns = 0; lastTurnNs = Long.MIN_VALUE }
}
