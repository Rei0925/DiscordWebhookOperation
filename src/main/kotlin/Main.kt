package com.github.Rei0925

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.nio.charset.Charset
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

// ✅ Webhook情報を管理するデータクラス
@Serializable
data class Webhook(var username: String, val url: String, var icon: String? = null)

// JSONの設定をグローバルに1回だけ作成
val jsonFormatter = Json { prettyPrint = true }

fun main() = runBlocking {
    val client = HttpClient(CIO)
    val webhooks = loadWebhooks()

    while (true) {
        println("\n===== Discord Webhook CUI =====")
        println("1. Webhookを登録")
        println("2. メッセージを送信")
        println("3. 履歴を表示")
        println("4. Webhookの名前を変更")
        println("5. 終了")
        print("選択番号: ")

        when (readlnOrNull()?.toIntOrNull()) {
            1 -> registerWebhook(webhooks)
            2 -> sendMessage(client, webhooks)
            3 -> showHistory()
            4 -> changeName(webhooks)
            5 -> {
                println("終了します。")
                break
            }
            else -> println("無効な選択です。1～5を選んでください。")
        }
    }

    client.close()
}

// ✅ Webhook読み込み
fun loadWebhooks(): MutableList<Webhook> {
    val file = File("webhooks.json")
    if (!file.exists()) {
        // 初期値として空のwebhooksを設定
        file.writeText("""{"webhooks": []}""")
    }

    try {
        val jsonString = file.readText()  // Charsetを指定せず、システムのデフォルトを使用

        // JSONが空や不正な場合に対応
        if (jsonString.isBlank() || jsonString == "null") {
            println("webhooks.jsonが空または無効です。新しいファイルを作成します。")
            return mutableListOf()  // 空のリストを返す
        }

        // JSONの解析
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val list = json["webhooks"]?.jsonArray?.map {
            Webhook(
                it.jsonObject["username"]?.jsonPrimitive?.content ?: "無名",
                it.jsonObject["url"]?.jsonPrimitive?.content ?: "",
                it.jsonObject["icon"]?.jsonPrimitive?.content
            )
        }?.toMutableList() ?: mutableListOf()

        return list
    } catch (e: Exception) {
        println("JSONの読み込みに失敗しました: ${e.message}")
        return mutableListOf()  // 失敗した場合は空のリストを返す
    }
}

fun registerWebhook(webhooks: MutableList<Webhook>) {
    val scanner = Scanner(System.`in`)

    print("Webhook名: ")
    val username = scanner.nextLine()

    print("Webhook URL: ")
    val url = scanner.nextLine()

    webhooks.add(Webhook(username, url))

    // インデントを有効にしたJSONを保存
    val json = jsonFormatter.encodeToJsonElement(mapOf("webhooks" to webhooks))
    File("webhooks.json").writeText(json.toString())  // インデント付きで保存

    println("Webhookを登録しました！")
}

suspend fun sendMessage(client: HttpClient, webhooks: List<Webhook>) {
    if (webhooks.isEmpty()) {
        println("Webhookが登録されていません")
        return
    }

    // 登録済みWebhookの表示
    println("\n登録済みWebhook:")
    webhooks.forEachIndexed { index, webhook ->
        println("${index + 1}. ${webhook.username} (${webhook.url})")
    }

    print("\n送信するWebhookの番号を選択: ")
    val index = readlnOrNull()?.toIntOrNull()?.minus(1)

    if (index == null || index !in webhooks.indices) {
        println("無効な番号です")
        return
    }

    val webhook = webhooks[index]

    val scanner = Scanner(System.`in`)
    print("タイトル: ")
    val title = scanner.nextLine()

    print("説明: ")
    val description = scanner.nextLine()

    print("色 (HEX): ")
    val colorHex = scanner.nextLine().removePrefix("#")
    val color = Integer.parseInt(colorHex, 16)

    // フッター用のカスタム名前と時間を追加
    print("フッター名: ")
    val footerName = scanner.nextLine()

    // 現在の日時をZonedDateTimeに変換
    val timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)

    val payload = buildJsonObject {
        put("username", webhook.username)
        put("embeds", buildJsonArray {
            add(buildJsonObject {
                put("title", title)
                put("description", description)
                put("color", color)
                put("footer", buildJsonObject {
                    put("text", footerName) // フッターのテキスト
                })
                put("timestamp", timestamp)  // 正しいタイムスタンプ形式を使用
            })
        })
        webhook.icon?.let {
            put("avatar_url", it)
        }
    }

    // UTF-8でエンコードして送信
    client.post(webhook.url) {
        contentType(ContentType.Application.Json)
        setBody(payload.toString().toByteArray(Charset.forName("UTF-8")))  // UTF-8にエンコード
    }

    println("送信完了！")

    // 履歴に保存
    saveHistory(title, description, colorHex)
}

// 履歴保存時にUTF-8で保存
fun saveHistory(title: String, description: String, color: String) {
    val file = File("history.json")
    val history = if (file.exists()) {
        Json.parseToJsonElement(file.readText(Charset.forName("UTF-8"))).jsonArray.toMutableList()
    } else {
        mutableListOf()
    }

    val newEntry = buildJsonObject {
        put("title", title)
        put("description", description)
        put("color", color)
        put("timestamp", System.currentTimeMillis())
    }

    history.add(newEntry)

    val json = buildJsonArray {
        history.forEach { add(it) }
    }

    file.writeText(json.toString(), Charset.forName("UTF-8"))
}

// ✅ 履歴表示
fun showHistory() {
    val file = File("history.json")
    if (!file.exists()) {
        println("履歴がありません")
        return
    }

    val history = Json.parseToJsonElement(file.readText(Charset.forName("UTF-8"))).jsonArray
    println("\n===== 履歴 =====")
    history.forEachIndexed { index, entry ->
        val obj = entry.jsonObject
        println("${index + 1}. ${obj["title"]!!.jsonPrimitive.content} - ${obj["description"]!!.jsonPrimitive.content} (色: #${obj["color"]!!.jsonPrimitive.content})")
    }
}

// ✅ Webhookの名前とアイコンを変更
fun changeName(webhooks: MutableList<Webhook>) {
    if (webhooks.isEmpty()) {
        println("登録されているWebhookがありません。")
        return
    }

    // 登録済みWebhookの表示
    println("\n登録済みWebhook:")
    webhooks.forEachIndexed { index, webhook ->
        println("${index + 1}. ${webhook.username} (${webhook.url})")
    }

    print("\n名前を変更したいWebhookの番号を選択: ")
    val index = readlnOrNull()?.toIntOrNull()?.minus(1)

    if (index == null || index !in webhooks.indices) {
        println("無効な番号です")
        return
    }

    val webhook = webhooks[index]
    val scanner = Scanner(System.`in`)

    print("新しい名前を入力: ")
    val newName = scanner.nextLine()

    println("変更後のアイコンを指定しますか？")
    println("1. 画像を選択（現在のディレクトリ内にあるPNGファイル）")
    println("2. アイコンなし")
    print("選択番号: ")

    val iconChoice = readlnOrNull()?.toIntOrNull()

    when (iconChoice) {
        1 -> {
            val imageFiles = File(".").listFiles { file -> file.extension == "png" }?.toList() ?: emptyList()

            if (imageFiles.isEmpty()) {
                println("アイコン用の画像ファイルが見つかりませんでした。")
            } else {
                println("\n選択可能な画像ファイル:")
                imageFiles.forEachIndexed { i, file -> println("${i + 1}. ${file.name}") }
                print("\n選択する番号を入力: ")
                val iconIndex = readlnOrNull()?.toIntOrNull()?.minus(1)

                if (iconIndex != null && iconIndex in imageFiles.indices) {
                    webhook.icon = imageFiles[iconIndex].absolutePath
                    println("アイコンを変更しました。")
                } else {
                    println("無効な番号です。アイコンなしに設定します。")
                }
            }
        }
        2 -> {
            webhook.icon = null
            println("アイコンを削除しました。")
        }
        else -> {
            println("無効な選択です。アイコンなしに設定します。")
        }
    }

    // Webhookの名前変更
    webhook.username = newName

    val json = Json.encodeToJsonElement(mapOf("webhooks" to webhooks))
    File("webhooks.json").writeText(json.toString(), Charset.forName("UTF-8"))

    println("Webhookの名前を変更しました！")
}
