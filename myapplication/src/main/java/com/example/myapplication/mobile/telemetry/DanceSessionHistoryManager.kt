package com.example.myapplication.mobile.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestor de persistencia local para el historial de sesiones de Just Dance.
 * Almacena resúmenes biométricos en SharedPreferences en formato JSON.
 */
class DanceSessionHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<DanceSessionSummary>>(emptyList())
    val history: StateFlow<List<DanceSessionSummary>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    /**
     * Carga el historial persistido al iniciar.
     */
    fun loadHistory() {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<DanceSessionSummary>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val zonesMap = mutableMapOf<HeartRateZone, Long>()
                if (obj.has("zones")) {
                    val zonesObj = obj.getJSONObject("zones")
                    for (key in zonesObj.keys()) {
                        try {
                            val zone = HeartRateZone.valueOf(key)
                            zonesMap[zone] = zonesObj.getLong(key)
                        } catch (_: Exception) {}
                    }
                }

                list.add(
                    DanceSessionSummary(
                        songTitle = obj.optString("songTitle", "Coreografía Just Dance"),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        totalCaloriesKcal = obj.optDouble("totalCaloriesKcal", 0.0).toFloat(),
                        avgHeartRateBpm = obj.optDouble("avgHeartRateBpm", 0.0).toFloat(),
                        maxHeartRateBpm = obj.optDouble("maxHeartRateBpm", 0.0).toFloat(),
                        minHeartRateBpm = obj.optDouble("minHeartRateBpm", 0.0).toFloat(),
                        averageDanceEnergyG = obj.optDouble("averageDanceEnergyG", 0.0).toFloat(),
                        timeInZonesSeconds = zonesMap,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            Log.e("DanceTelemetry", "Error parsing history JSON", e)
        }
    }

    /**
     * Guarda una nueva sesión completada en almacenamiento local.
     */
    fun saveSession(summary: DanceSessionSummary) {
        val current = _history.value.toMutableList()
        current.add(0, summary) // Insertar al principio (más reciente primero)
        _history.value = current

        try {
            val jsonArray = JSONArray()
            for (s in current) {
                val obj = JSONObject().apply {
                    put("songTitle", s.songTitle)
                    put("durationSeconds", s.durationSeconds)
                    put("totalCaloriesKcal", s.totalCaloriesKcal.toDouble())
                    put("avgHeartRateBpm", s.avgHeartRateBpm.toDouble())
                    put("maxHeartRateBpm", s.maxHeartRateBpm.toDouble())
                    put("minHeartRateBpm", s.minHeartRateBpm.toDouble())
                    put("averageDanceEnergyG", s.averageDanceEnergyG.toDouble())
                    put("timestamp", s.timestamp)

                    val zonesObj = JSONObject()
                    s.timeInZonesSeconds.forEach { (zone, seconds) ->
                        zonesObj.put(zone.name, seconds)
                    }
                    put("zones", zonesObj)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
            Log.i("DanceTelemetry", "Session saved! Total saved sessions: ${current.size}")
        } catch (e: Exception) {
            Log.e("DanceTelemetry", "Error saving session to SharedPreferences", e)
        }
    }

    /**
     * Limpia todo el historial guardado.
     */
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        _history.value = emptyList()
    }

    companion object {
        private const val PREFS_NAME = "jd_dance_history_prefs"
        private const val KEY_HISTORY = "dance_sessions_history_json"
    }
}
