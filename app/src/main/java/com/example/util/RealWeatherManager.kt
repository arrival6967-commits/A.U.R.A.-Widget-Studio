package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DailyForecastItem(
    val dayName: String,
    val dateStr: String,
    val maxTemp: String,
    val minTemp: String,
    val conditionText: String,
    val iconName: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("dayName", dayName)
        put("dateStr", dateStr)
        put("maxTemp", maxTemp)
        put("minTemp", minTemp)
        put("conditionText", conditionText)
        put("iconName", iconName)
    }

    companion object {
        fun fromJson(json: JSONObject): DailyForecastItem = DailyForecastItem(
            dayName = json.optString("dayName", "Завтра"),
            dateStr = json.optString("dateStr", ""),
            maxTemp = json.optString("maxTemp", "+15°"),
            minTemp = json.optString("minTemp", "+8°"),
            conditionText = json.optString("conditionText", "Ясно"),
            iconName = json.optString("iconName", "WbSunny")
        )
    }
}

data class RealWeatherData(
    val locationName: String,
    val temperature: String,
    val apparentTemperature: String,
    val conditionText: String,
    val iconName: String,
    val humidityPct: Int,
    val windKmH: Double,
    val forecast3Day: List<DailyForecastItem>,
    val lastUpdatedMillis: Long
)

object RealWeatherManager {

    private const val TAG = "RealWeatherManager"
    private const val PREFS_NAME = "aura_real_weather_cache"
    private const val CACHE_EXPIRY_MS = 20 * 60 * 1000L // 20 minutes

    @Volatile
    private var cachedInMemory: RealWeatherData? = null

    fun getCachedOrFallback(context: Context): RealWeatherData {
        cachedInMemory?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val locationName = prefs.getString("loc", "Москва") ?: "Москва"
        val temp = prefs.getString("temp", "+16°C") ?: "+16°C"
        val appTemp = prefs.getString("app_temp", "+14°C") ?: "+14°C"
        val cond = prefs.getString("cond", "Ясно") ?: "Ясно"
        val icon = prefs.getString("icon", "WbSunny") ?: "WbSunny"
        val hum = prefs.getInt("hum", 54)
        val wind = prefs.getFloat("wind", 11.5f).toDouble()
        val forecastJsonStr = prefs.getString("forecast_json", null)
        val updated = prefs.getLong("updated", System.currentTimeMillis())

        val forecastList = if (!forecastJsonStr.isNullOrBlank()) {
            parseForecastJson(forecastJsonStr)
        } else {
            createDefault3DayForecast()
        }

        val data = RealWeatherData(
            locationName = locationName,
            temperature = temp,
            apparentTemperature = appTemp,
            conditionText = cond,
            iconName = icon,
            humidityPct = hum,
            windKmH = wind,
            forecast3Day = forecastList,
            lastUpdatedMillis = updated
        )
        cachedInMemory = data
        return data
    }

    suspend fun fetchRealWeather(context: Context, force: Boolean = false): RealWeatherData = withContext(Dispatchers.IO) {
        val currentCached = getCachedOrFallback(context)
        val now = System.currentTimeMillis()
        if (!force && (now - currentCached.lastUpdatedMillis < CACHE_EXPIRY_MS)) {
            return@withContext currentCached
        }

        try {
            val (lat, lon, detectedCity) = resolveDeviceLocation(context)
            val apiUrl = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto"

            val jsonString = httpGet(apiUrl, timeoutMs = 8000)
            val root = JSONObject(jsonString)
            val current = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")

            val curTemp = current.getDouble("temperature_2m")
            val curAppTemp = current.getDouble("apparent_temperature")
            val weatherCode = current.getInt("weather_code")
            val humidity = current.optInt("relative_humidity_2m", 55)
            val wind = current.optDouble("wind_speed_10m", 10.0)

            val condition = wmoToConditionRussian(weatherCode)
            val icon = wmoToIconName(weatherCode)

            val tempStr = formatTemp(curTemp)
            val appTempStr = formatTemp(curAppTemp)

            val dailyDates = daily.getJSONArray("time")
            val dailyMax = daily.getJSONArray("temperature_2m_max")
            val dailyMin = daily.getJSONArray("temperature_2m_min")
            val dailyCodes = daily.getJSONArray("weather_code")

            val ruLocale = Locale.forLanguageTag("ru")
            val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayFormatter = SimpleDateFormat("EEEE", ruLocale)
            val shortDateFormatter = SimpleDateFormat("d MMM", ruLocale)

            val forecastItems = mutableListOf<DailyForecastItem>()
            val count = minOf(3, dailyDates.length())
            for (i in 0 until count) {
                val rawDate = dailyDates.getString(i)
                val maxT = dailyMax.getDouble(i)
                val minT = dailyMin.getDouble(i)
                val code = dailyCodes.getInt(i)

                val dayName = when (i) {
                    0 -> "Сегодня"
                    1 -> "Завтра"
                    else -> try {
                        val parsed = dateParser.parse(rawDate)
                        if (parsed != null) dayFormatter.format(parsed).replaceFirstChar { it.uppercase() } else "День $i"
                    } catch (e: Exception) {
                        "День $i"
                    }
                }
                val shortDate = try {
                    val parsed = dateParser.parse(rawDate)
                    if (parsed != null) shortDateFormatter.format(parsed) else rawDate
                } catch (e: Exception) {
                    rawDate
                }

                forecastItems.add(
                    DailyForecastItem(
                        dayName = dayName,
                        dateStr = shortDate,
                        maxTemp = formatTemp(maxT),
                        minTemp = formatTemp(minT),
                        conditionText = wmoToConditionRussian(code),
                        iconName = wmoToIconName(code)
                    )
                )
            }

            val result = RealWeatherData(
                locationName = detectedCity,
                temperature = tempStr,
                apparentTemperature = appTempStr,
                conditionText = condition,
                iconName = icon,
                humidityPct = humidity,
                windKmH = wind,
                forecast3Day = forecastItems,
                lastUpdatedMillis = now
            )

            // Cache to SharedPreferences
            saveToPrefs(context, result)
            cachedInMemory = result
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch live weather from Open-Meteo", e)
            currentCached
        }
    }

    private fun saveToPrefs(context: Context, data: RealWeatherData) {
        val forecastArr = JSONArray()
        data.forecast3Day.forEach { forecastArr.put(it.toJson()) }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("loc", data.locationName)
            .putString("temp", data.temperature)
            .putString("app_temp", data.apparentTemperature)
            .putString("cond", data.conditionText)
            .putString("icon", data.iconName)
            .putInt("hum", data.humidityPct)
            .putFloat("wind", data.windKmH.toFloat())
            .putString("forecast_json", forecastArr.toString())
            .putLong("updated", data.lastUpdatedMillis)
            .apply()
    }

    fun parseForecastJson(jsonStr: String): List<DailyForecastItem> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<DailyForecastItem>()
            for (i in 0 until arr.length()) {
                list.add(DailyForecastItem.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            createDefault3DayForecast()
        }
    }

    fun serializeForecastJson(list: List<DailyForecastItem>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun createDefault3DayForecast(): List<DailyForecastItem> {
        return listOf(
            DailyForecastItem("Сегодня", "30 окт", "+16°", "+8°", "Ясно", "WbSunny"),
            DailyForecastItem("Завтра", "31 окт", "+18°", "+9°", "Переменная облачность", "Cloud"),
            DailyForecastItem("Вс", "1 ноя", "+15°", "+7°", "Небольшой дождь", "Grain")
        )
    }

    @SuppressLint("MissingPermission")
    private fun resolveDeviceLocation(context: Context): Triple<Double, Double, String> {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var bestLocation: Location? = null

        if (locationManager != null) {
            try {
                val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                for (provider in providers) {
                    if (locationManager.isProviderEnabled(provider)) {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null) {
                            if (bestLocation == null || loc.time > bestLocation.time) {
                                bestLocation = loc
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.d(TAG, "Location permission not granted yet, using fallback", e)
            }
        }

        if (bestLocation != null) {
            val cityName = reverseGeocodeCity(context, bestLocation.latitude, bestLocation.longitude)
            return Triple(bestLocation.latitude, bestLocation.longitude, cityName)
        }

        // Timezone based default location coordinates
        val tzId = TimeZone.getDefault().id
        return when {
            tzId.contains("Moscow", ignoreCase = true) -> Triple(55.7558, 37.6173, "Москва")
            tzId.contains("Petersburg", ignoreCase = true) -> Triple(59.9343, 30.3351, "Санкт-Петербург")
            tzId.contains("Yekaterinburg", ignoreCase = true) -> Triple(56.8389, 60.6057, "Екатеринбург")
            tzId.contains("Novosibirsk", ignoreCase = true) -> Triple(55.0084, 82.9357, "Новосибирск")
            tzId.contains("Vladivostok", ignoreCase = true) -> Triple(43.1155, 131.8855, "Владивосток")
            tzId.contains("Almaty", ignoreCase = true) -> Triple(43.2220, 76.8512, "Алматы")
            tzId.contains("Minsk", ignoreCase = true) -> Triple(53.9045, 27.5615, "Минск")
            tzId.contains("Kyiv", ignoreCase = true) -> Triple(50.4501, 30.5234, "Киев")
            tzId.contains("London", ignoreCase = true) -> Triple(51.5074, -0.1278, "Лондон")
            tzId.contains("New_York", ignoreCase = true) -> Triple(40.7128, -74.0060, "Нью-Йорк")
            tzId.contains("Tokyo", ignoreCase = true) -> Triple(35.6762, 139.6503, "Токио")
            else -> Triple(55.7558, 37.6173, "Москва")
        }
    }

    private fun reverseGeocodeCity(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.forLanguageTag("ru"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val city = addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.subAdminArea
                ?: addresses?.firstOrNull()?.adminArea
            city?.ifBlank { "Мое местоположение" } ?: "Мое местоположение"
        } catch (e: Exception) {
            "Мое местоположение"
        }
    }

    private fun formatTemp(celsius: Double): String {
        val rounded = Math.round(celsius).toInt()
        return if (rounded > 0) "+$rounded°C" else "$rounded°C"
    }

    private fun wmoToConditionRussian(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45, 48 -> "Туман"
        51, 53, 55 -> "Легкая морось"
        56, 57 -> "Ледяная морось"
        61 -> "Небольшой дождь"
        63 -> "Умеренный дождь"
        65 -> "Сильный ливень"
        66, 67 -> "Ледяной дождь"
        71 -> "Небольшой снег"
        73 -> "Снегопад"
        75 -> "Сильный снегопад"
        77 -> "Снежная крупа"
        80, 81, 82 -> "Ливневый дождь"
        85, 86 -> "Метель"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> "Ясно"
    }

    private fun wmoToIconName(code: Int): String = when (code) {
        0, 1 -> "WbSunny"
        2, 3 -> "Cloud"
        45, 48 -> "Cloud"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Grain"
        71, 73, 75, 77, 85, 86 -> "AcUnit"
        95, 96, 99 -> "Thunderstorm"
        else -> "WbSunny"
    }

    private fun httpGet(urlStr: String, timeoutMs: Int): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AuraWidgetAI/1.0 (Android)")

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode from $urlStr")
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        conn.disconnect()
        return sb.toString()
    }
}
