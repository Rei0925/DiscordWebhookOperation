package com.github.Rei0925

import java.io.BufferedReader
import java.io.InputStreamReader

fun ensureUtf8() {
    try {
        // 現在のコードページを取得
        val process = ProcessBuilder("chcp").start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readLine()

        // "現在のコード ページ: 932" をチェック
        if (output.contains("932")) {
            println("コードページがShift-JIS (932) です。UTF-8に変更します...")

            // UTF-8に変更
            ProcessBuilder("chcp", "65001").inheritIO().start().waitFor()
            println("UTF-8に変更完了 ✅")
        } else {
            println("既にUTF-8が設定されています。")
        }
    } catch (e: Exception) {
        println("コードページの変更に失敗しました: ${e.message}")
    }
}