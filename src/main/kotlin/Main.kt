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

// 色名とHEXコードの対応表
val colorMap = mapOf(
    "red" to 0xFF0000,
    "blue" to 0x0000FF,
    "green" to 0x00FF00,
    "yellow" to 0xFFFF00,
    "orange" to 0xFFA500,
    "purple" to 0x800080,
    "pink" to 0xFFC0CB,
    "gold" to 0xFFD700,
    "white" to 0xFFFFFF,
    "black" to 0x000000,
    "gray" to 0x808080
)


// JSONの設定をグローバルに1回だけ作成
val jsonFormatter = Json { prettyPrint = true }

fun main() = runBlocking {
    ensureUtf8()// 起動時にコードページをチェックして変換
    val client = HttpClient(CIO)
    val webhooks = loadWebhooks()

    while (true) {
        println("\n===== Discord Webhook CUI =====")
        println("1. Webhookを登録")
        println("2. メッセージを送信")
        println("3. 履歴を表示")
        println("4. Webhookの名前を変更")
        println("5. Webhookを削除")
        println("6. 終了")
        print("選択番号: ")

        when (readlnOrNull()?.toIntOrNull()) {
            1 -> registerWebhook(webhooks)
            2 -> sendMessage(client, webhooks)
            3 -> showHistory()
            4 -> changeName(webhooks)
            5 -> deleteWebhook(webhooks)
            6 -> {
                println("終了します。")
                break
            }
            else -> println("無効な選択です。1～6を選んでください。")
        }
    }

    client.close()
}
//ウェブフックを削除
fun deleteWebhook(webhooks: MutableList<Webhook>) {
    if (webhooks.isEmpty()) {
        println("削除するWebhookがありません。")
        return
    }

    // Webhook一覧を表示
    println("\n削除するWebhookを選択:")
    webhooks.forEachIndexed { index, webhook ->
        println("${index + 1}. ${webhook.username} (${webhook.url})")
    }

    print("\n削除するWebhookの番号を選択: ")
    val index = readlnOrNull()?.toIntOrNull()?.minus(1)

    if (index == null || index !in webhooks.indices) {
        println("無効な番号です。")
        return
    }

    // 削除の確認
    val webhook = webhooks[index]
    print("本当に削除しますか？ (${webhook.username}) [y/N]: ")
    val confirm = readlnOrNull()?.lowercase()

    if (confirm == "y") {
        webhooks.removeAt(index)
        println("${webhook.username} を削除しました！")

        // JSONに保存
        val json = jsonFormatter.encodeToJsonElement(mapOf("webhooks" to webhooks))
        File("webhooks.json").writeText(json.toString())

    } else {
        println("削除をキャンセルしました。")
    }
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

    println("説明を入力 (終了するには '.' のみを入力):")
    val descriptionBuilder = StringBuilder()

    while (true) {
        val line = scanner.nextLine()
        if (line == ".") break  // 終了キー
        descriptionBuilder.appendLine(line)  // 行を追加
    }

    val description = descriptionBuilder.toString().trim()

    // ✅ 色の入力と再試行処理
    var color: Int? = null
    while (color == null) {
        print("色名 or HEX: ")
        val colorInput = scanner.nextLine().trim().lowercase()

        color = when {
            colorInput.startsWith("#") -> {
                // HEXコードの場合
                runCatching { Integer.parseInt(colorInput.removePrefix("#"), 16) }
                    .getOrNull()
            }
            colorMap.containsKey(colorInput) -> {
                // 色名の場合
                colorMap[colorInput]
            }
            else -> null
        }

        if (color == null) {
            println("\n無効な色名またはHEXコードです。以下の色から選択してください:")

            // ✅ 色リストを表示
            colorMap.forEach { (name, code) ->
                println("$name → #${code.toString(16).uppercase()}")
            }
        }
    }

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
                    put("text", footerName)
                })
                put("timestamp", timestamp)
            })
        })
        webhook.icon?.let {
            put("avatar_url", it)
        }
    }

    // UTF-8でエンコードして送信
    client.post(webhook.url) {
        contentType(ContentType.Application.Json)
        setBody(payload.toString().toByteArray(Charset.forName("UTF-8")))
    }

    println("送信完了！")

    // 履歴に保存（色コードは大文字表示）
    saveHistory(title, description, color.toString(16).uppercase())
}

// 履歴保存時にUTF-8で保存
fun saveHistory(title: String, description: String, color: String) {
    val file = File("history.json")

    // ファイルが存在しない場合は新規作成
    if (!file.exists()) {
        try {
            file.createNewFile()  // ファイルを作成
            println("history.json を新規作成しました")
        } catch (e: Exception) {
            println("ファイル作成に失敗しました: ${e.message}")
            return
        }
    }

    val history = if (file.exists()) {
        Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonArray.toMutableList()
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

    file.writeText(json.toString(), Charsets.UTF_8)
    print("履歴を保存しました！")
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
