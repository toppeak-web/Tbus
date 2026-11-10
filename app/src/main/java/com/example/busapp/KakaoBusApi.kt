package com.example.busapp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class BusStopSearchItem(
    val busStopId: String,
    val displayName: String,
    val stopNumber: String,
    val direction: String
)

data class BusArrival(
    val lineId: String,
    val busNumber: String,
    val direction: String,
    val arrivalTimeSeconds: Int,
    val busStopCount: Int,
    val nextArrivalTimeSeconds: Int?,
    val nextBusStopCount: Int?,
    val vehicleNumber: String
)

data class BusStopInfo(
    val stopName: String,
    val stopNumber: String,
    val arrivals: List<BusArrival>
)

data class BusRouteStop(
    val stopId: String,
    val stopName: String,
    val order: Int,
    val isVirtual: Boolean
)

data class BusLocation(
    val sectionOrder: Int,
    val sectionDist: Int,
    val sectionOffsetDist: Int,
    val vehicleNumber: String
)

data class BusLineInfo(
    val lineId: String,
    val lineName: String,
    val routeDetailName: String,
    val busStops: List<BusRouteStop>,
    val busLocations: List<BusLocation>
)

class KakaoBusApi(
    private val client: OkHttpClient = defaultClient
) {
    fun searchBusStops(keyword: String): List<BusStopSearchItem> {
        val baseUrl = "https://search.map.kakao.com/mapsearch/search_bus.daum".toHttpUrlOrNull()
            ?: throw IOException("Invalid search URL")
        val url = baseUrl.newBuilder()
            .addQueryParameter("type", "busStop")
            .addQueryParameter("output", "json")
            .addQueryParameter("q", keyword)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Referer", "https://map.kakao.com/")
            .addHeader("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()

            val root = JSONObject(body)
            val list = root.optJSONArray("List") ?: JSONArray()
            val result = mutableListOf<BusStopSearchItem>()

            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val docId = item.optString("DOCID", "")
                if (!docId.startsWith("BS")) continue

                val name = item.optString("BUSSTOP_NAME", docId)
                val stopId = item.optString("STOP_ID", "")
                val direction = item.optString("direction", "")
                val region = listOf(
                    item.optString("RGN_D1_NAME", ""),
                    item.optString("RGN_D2_NAME", "")
                ).filter { it.isNotBlank() }.joinToString(" ")
                val display = buildString {
                    append(name)
                    append(" [")
                    append(docId)
                    append("]")
                    if (stopId.isNotBlank()) append(" ($stopId)")
                    if (direction.isNotBlank()) append(" -> $direction")
                    if (region.isNotBlank()) append(" [$region]")
                }

                result += BusStopSearchItem(
                    busStopId = docId,
                    displayName = display,
                    stopNumber = stopId,
                    direction = direction
                )
            }

            return result.distinctBy { it.busStopId }
        }
    }

    fun fetchStopInfo(busStopId: String): BusStopInfo {
        val url = "https://map.kakao.com/bus/stop.json?busstopid=$busStopId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val rawBody = response.body?.string().orEmpty()
            if (rawBody.isBlank()) throw IOException("Empty response body")

            val jsonText = stripJsonp(rawBody)
            val root = JSONObject(jsonText)
            return parseBusStopInfo(root, busStopId)
        }
    }

    fun fetchBusLineInfo(lineId: String): BusLineInfo {
        val url = "https://map.kakao.com/bus/info.json?busline=$lineId&output=json"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val rawBody = response.body?.string().orEmpty()
            if (rawBody.isBlank()) throw IOException("Empty response body")

            val jsonText = stripJsonp(rawBody)
            val root = JSONObject(jsonText)

            val busStops = mutableListOf<BusRouteStop>()
            val stopsJson = root.optJSONArray("busStops") ?: JSONArray()
            for (index in 0 until stopsJson.length()) {
                val stop = stopsJson.optJSONObject(index) ?: continue
                busStops += BusRouteStop(
                    stopId = stop.optString("id", ""),
                    stopName = stop.optString("name", ""),
                    order = stop.optInt("order", index + 1),
                    isVirtual = stop.optBoolean("virtual", false)
                )
            }

            val busLocations = mutableListOf<BusLocation>()
            val locationsJson = root.optJSONArray("busLocations") ?: JSONArray()
            for (index in 0 until locationsJson.length()) {
                val location = locationsJson.optJSONObject(index) ?: continue
                busLocations += BusLocation(
                    sectionOrder = location.optInt("sectionOrder", -1),
                    sectionDist = location.optInt("sectionDist", 0),
                    sectionOffsetDist = location.optInt("sectionOffsetDist", 0),
                    vehicleNumber = location.optString("vehicleNumber", "")
                )
            }

            return BusLineInfo(
                lineId = root.optString("id", lineId),
                lineName = root.optString("name", lineId),
                routeDetailName = root.optString("routeDetailName", ""),
                busStops = busStops.sortedBy { it.order },
                busLocations = busLocations
            )
        }
    }

    private fun parseBusStopInfo(root: JSONObject, fallbackStopId: String): BusStopInfo {
        val stopName = root.optString("name", fallbackStopId)
        val stopNumber = root.optString("id", fallbackStopId)
        val lines = root.optJSONArray("lines") ?: JSONArray()

        val arrivals = mutableListOf<BusArrival>()
        for (index in 0 until lines.length()) {
            val line = lines.optJSONObject(index) ?: continue
            val arrival = line.optJSONObject("arrival") ?: continue

            arrivals += BusArrival(
                lineId = line.optString("id", ""),
                busNumber = line.optString("name", "-"),
                direction = arrival.optString("direction", ""),
                arrivalTimeSeconds = arrival.optInt("arrivalTime", 0),
                busStopCount = arrival.optInt("busStopCount", -1),
                nextArrivalTimeSeconds = arrival.optInt("arrivalTime2", -1).takeIf { it >= 0 },
                nextBusStopCount = arrival.optInt("busStopCount2", -1).takeIf { it >= 0 },
                vehicleNumber = arrival.optString("vehicleNumber", "")
            )
        }

        return BusStopInfo(stopName = stopName, stopNumber = stopNumber, arrivals = arrivals)
    }

    internal fun stripJsonp(body: String): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed

        val firstParen = trimmed.indexOf('(')
        val lastParen = trimmed.lastIndexOf(')')
        if (firstParen >= 0 && lastParen > firstParen) {
            val candidate = trimmed.substring(firstParen + 1, lastParen).trim()
            if (candidate.startsWith("{") || candidate.startsWith("[")) return candidate
        }

        return trimmed
    }

    private companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
