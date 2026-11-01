package mx.edu.utez.runtrack

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

/**
 * RunTrack – gestión de sensores (acelerómetro y giroscopio).
 *
 * Flujo:
 *  1. onCreate: verifica qué sensores existen en el dispositivo/emulador.
 *  2. onResume: registra los listeners (habilita la escucha).
 *  3. onSensorChanged: filtra eventos y actualiza la pantalla en tiempo real.
 *  4. onPause: da de baja los listeners para no gastar batería en segundo plano.
 */
class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Un filtro por sensor (cada uno con su propio estado).
    private val accelFilter = SensorFilter(minIntervalMs = 100L, alpha = 0.25f, minDelta = 0.05f)
    private val gyroFilter = SensorFilter(minIntervalMs = 100L, alpha = 0.30f, minDelta = 0.02f)
    private val turns = TurnDetector()

    // Contadores del modo "sin filtro" para comparar el impacto.
    private var rawAccel = 0L
    private var rawGyro = 0L
    private var filterEnabled = true
    private var startMs = 0L

    private lateinit var tvAvail: TextView
    private lateinit var tvAccel: TextView
    private lateinit var tvGyro: TextView
    private lateinit var tvTurns: TextView
    private lateinit var tvMetrics: TextView
    private lateinit var swFilter: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAvail = findViewById(R.id.tvAvail)
        tvAccel = findViewById(R.id.tvAccel)
        tvGyro = findViewById(R.id.tvGyro)
        tvTurns = findViewById(R.id.tvTurns)
        tvMetrics = findViewById(R.id.tvMetrics)
        swFilter = findViewById(R.id.swFilter)

        // 1) Disponibilidad de sensores
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        showAvailability()

        swFilter.isChecked = true
        swFilter.setOnCheckedChangeListener { _, checked ->
            filterEnabled = checked
            resetCounters()
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetCounters() }
    }

    private fun showAvailability() {
        val all = sensorManager.getSensorList(Sensor.TYPE_ALL).size
        val sb = StringBuilder("Sensores totales en el equipo: $all\n")
        sb.append(describe("Acelerómetro", accelerometer)).append('\n')
        sb.append(describe("Giroscopio", gyroscope))
        tvAvail.text = sb.toString()
    }

    private fun describe(label: String, s: Sensor?): String =
        if (s == null) "✖ $label: NO disponible"
        else "✔ $label: ${s.name} (consumo ${s.power} mA, rango ${s.maximumRange}, " +
            "retardo mín. ${s.minDelay} µs)"

    // 2) Habilitar escucha
    override fun onResume() {
        super.onResume()
        startMs = SystemClock.elapsedRealtime()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    // 4) Liberar recursos cuando la app sale de primer plano
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // 3) Captura en tiempo real
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                rawAccel++
                val r = if (filterEnabled) accelFilter.process(event.timestamp, event.values[0], event.values[1], event.values[2])
                else Reading(event.values[0], event.values[1], event.values[2], event.timestamp)
                if (r != null) {
                    tvAccel.text = String.format(
                        "X: %6.2f   Y: %6.2f   Z: %6.2f\n|a| = %.2f m/s²",
                        r.x, r.y, r.z, r.magnitude
                    )
                    updateMetrics()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                rawGyro++
                val wz = event.values[2]
                val dir = turns.onGyroZ(event.timestamp, wz)
                val r = if (filterEnabled) gyroFilter.process(event.timestamp, event.values[0], event.values[1], wz)
                else Reading(event.values[0], event.values[1], wz, event.timestamp)
                if (r != null) {
                    tvGyro.text = String.format(
                        "X: %6.2f   Y: %6.2f   Z: %6.2f  rad/s",
                        r.x, r.y, r.z
                    )
                }
                if (dir != 0) {
                    val lado = if (dir > 0) "izquierda ⟲" else "derecha ⟳"
                    tvTurns.text = "Cambios de dirección: ${turns.turns}\nÚltimo giro: $lado"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no se requiere */ }

    private fun resetCounters() {
        accelFilter.reset(); gyroFilter.reset(); turns.reset()
        rawAccel = 0; rawGyro = 0
        startMs = SystemClock.elapsedRealtime()
        tvTurns.text = "Cambios de dirección: 0"
        updateMetrics()
    }

    // 6) Impacto en rendimiento: eventos recibidos vs. publicados en la interfaz
    private fun updateMetrics() {
        val secs = ((SystemClock.elapsedRealtime() - startMs) / 1000.0).coerceAtLeast(1.0)
        val rawTotal = rawAccel + rawGyro
        val uiTotal = if (filterEnabled) accelFilter.accepted + gyroFilter.accepted else rawTotal
        val saved = if (rawTotal > 0) 100.0 * (rawTotal - uiTotal) / rawTotal else 0.0
        tvMetrics.text = String.format(
            "Tiempo: %.0f s\nEventos del sensor: %d (%.1f/s)\nActualizaciones de pantalla: %d (%.1f/s)\n" +
                "Descartados por frecuencia: %d\nDescartados por duplicado: %d\nReducción de trabajo: %.0f %%",
            secs, rawTotal, rawTotal / secs, uiTotal, uiTotal / secs,
            accelFilter.discardedByRate + gyroFilter.discardedByRate,
            accelFilter.discardedByDuplicate + gyroFilter.discardedByDuplicate,
            saved
        )
    }
}
