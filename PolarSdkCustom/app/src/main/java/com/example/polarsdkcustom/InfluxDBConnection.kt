package com.example.polarsdkcustom

import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.WriteApiBlocking
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import com.influxdb.exceptions.MethodNotAllowedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class InfluxDBConnection {

    companion object {
        private const val url = "https://us-east-1-1.aws.cloud2.influxdata.com" // URL base sem /orgs
        private const val token = "tM8pfkFHXFUktDTvoaubIz5NjPAtkG_LfDCdqbb3wxalXhoBWVwF1ABiZ4TV5oyKaVjcru8cGfffEZLaExwwMA=="
        private const val org = "Telemedicine - IC"
        private const val bucket = "polarInfo"
    }

    private val influxDBClient: InfluxDBClient = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket)
    private val writeApi: WriteApiBlocking = influxDBClient.writeApiBlocking

    suspend fun writeData(measurement: String, field: String, value: Int, tag: String = "default_tag"){
        try {
            withContext(Dispatchers.IO) {
                val point = Point(measurement)
                    .addTag("location", tag)
                    .addField(field, value)
                    .time(Instant.now(), WritePrecision.NS)
                writeApi.writePoint(point)
            }
        } catch (e: MethodNotAllowedException) {
            println("Erro: Método não permitido - Verifique o endpoint e o método HTTP")
            e.printStackTrace()
        } catch (e: Exception) {
            println("Erro inesperado: ${e.message}")
            e.printStackTrace()
        }
    }

    fun closeConnection() {
        influxDBClient.close()
    }
}