package com.test4x.app

import com.google.gson.Gson
import com.turo.pushy.apns.ApnsClientBuilder
import com.turo.pushy.apns.util.ApnsPayloadBuilder
import com.turo.pushy.apns.util.SimpleApnsPushNotification
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import org.slf4j.LoggerFactory
import spark.Filter
import spark.Request
import spark.Spark
import java.util.concurrent.ConcurrentHashMap


class Bark {
    private val logger = LoggerFactory.getLogger("Bark-Java")

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Bark().run(
                args.getOrNull(0)?.toInt()
                    ?: (System.getenv("port")?.toInt())
            )
        }
    }

    fun run(port: Int? = 7777) {
        Spark.port(port ?: 7777)
        Spark.after(Filter { req, res ->
            res.type("application/json; charset=utf-8")
        })
        Spark.get("ping", ping, gson::toJson)
        Spark.post("ping", ping, gson::toJson)
        Spark.get("register", register, gson::toJson)
        Spark.post("register", register, gson::toJson)
        Spark.get(":key/:body", index, gson::toJson)
        Spark.post(":key/:body", index, gson::toJson)
        Spark.get(":key/:title/:body", index, gson::toJson)
        Spark.post(":key/:title/:body", index, gson::toJson)
        Spark.get(":key/:category/:title/:body", index, gson::toJson)
        Spark.post(":key/:category/:title/:body", index, gson::toJson)
    }

    fun postPush(
        category: String?,
        title: String?,
        body: String?,
        deviceToken: String?,
        params: Map<String, Any> = emptyMap()
    ): Boolean {
        val builder = ApnsPayloadBuilder()
            .setAlertTitle(title)
            .setAlertBody(body)
            .setCategoryName("myNotificationCategory")
            .setSound("1107")
        params.forEach { t, u -> builder.addCustomProperty(t, u) }
        val payload = builder
            .setBadgeNumber(1)
            .buildWithDefaultMaximumLength()

        val pushNotification = SimpleApnsPushNotification(deviceToken, "me.fin.bark", payload)
        val notificationResponse = apnsClient.sendNotification(pushNotification).get()
        return if (notificationResponse.isAccepted) {
            true
        } else {
            logger.error(notificationResponse.rejectionReason)
            false
        }
    }

    interface Repo {
        fun putKeyAndDevice(key: String, device: String)
        fun getDeviceByKey(key: String): String?
    }

    val apnsClient = ApnsClientBuilder()
        .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
        .setClientCredentials(ClassLoader.getSystemResourceAsStream("cert-20200229.p12"), "bp")
        .build()

    val gson = Gson()

    val repo = System.getenv("redis.host")?.let { host ->
        object : Repo {
            val sync: RedisCommands<String, String>

            init {
                logger.info("use redis")
                val builder = RedisURI.Builder.redis(host, System.getenv("redis.port")?.toInt() ?: 6379)
                System.getenv("redis.auth")?.let {
                    builder.withPassword(it)
                }
                val client = RedisClient.create(builder.build())
                val connection = client.connect()
                sync = connection.sync()
            }

            override fun putKeyAndDevice(key: String, device: String) {
                sync.hset("device", key, device)
            }

            override fun getDeviceByKey(key: String): String? {
                return sync.hget("device", key)
            }
        }
    } ?: object : Repo {

        val map = ConcurrentHashMap<String, String>()

        init {
            logger.info("use hashMap")
        }

        override fun putKeyAndDevice(key: String, device: String) {
            map[key] = device
        }

        override fun getDeviceByKey(key: String): String? = map[key]

    }


    val ping: (Request, spark.Response) -> Response<Map<String, String>> = { req, res ->
        Response("pong", mapOf("version" to "1.0.0"))
    }


    val register: (Request, spark.Response) -> Response<Map<String, String>> = { req, res ->
        val deviceToken = req.queryParams("devicetoken")
        val key = req.queryParams("key")
        repo.putKeyAndDevice(key, deviceToken)
        logger.info("注册成功 $deviceToken")
        Response("注册成功", mapOf("key" to key))
    }


    val index: (Request, spark.Response) -> Response<Map<String, String>> = { req, res ->
        val key: String = req.params(":key")
        val category: String? = req.params(":category")
        val title: String? = req.params(":title")
        val body: String? = req.params(":body")
        val deviceToken = repo.getDeviceByKey(key)
        val argMaps = req.queryMap().toMap().mapValues { it.value[0] }
        if (postPush(category, title, body, deviceToken, argMaps)) {
            Response("")
        } else {
            Response("", code = 400)
        }

    }


    data class Response<T>(val message: String, val data: T? = null, val code: Int = 200)

}
