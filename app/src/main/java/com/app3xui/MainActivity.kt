package com.threex.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.view.View
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    // Ключи для сохранения данных
    private val PREFS_NAME = "My3XUIPrefs"
    private val URL_KEY = "PanelUrl"

    private lateinit var webView: WebView
    private lateinit var urlInputLayout: LinearLayout
    private lateinit var urlEditText: EditText
    private lateinit var saveUrlButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация компонентов по ID
        webView = findViewById(R.id.webView)
        urlInputLayout = findViewById(R.id.urlInputLayout)
        urlEditText = findViewById(R.id.urlEditText)
        saveUrlButton = findViewById(R.id.saveUrlButton)

        // 1. Проверяем, есть ли сохраненный URL
        val savedUrl = getSavedUrl()

        if (savedUrl.isNullOrEmpty()) {
            // Если URL нет, показываем экран ввода
            showUrlInputScreen()
        } else {
            // Если URL есть, сразу загружаем WebView
            loadWebView(savedUrl)
        }

        // 2. Устанавливаем обработчик нажатия кнопки "Сохранить"
        saveUrlButton.setOnClickListener {
            val enteredUrl = urlEditText.text.toString().trim()
            if (enteredUrl.isNotEmpty() &&
                (enteredUrl.startsWith("http://") || enteredUrl.startsWith("https://"))) {
                saveUrl(enteredUrl)
                loadWebView(enteredUrl)
            } else {
                Toast.makeText(this, "Пожалуйста, введите полный URL-адрес с http:// или https://", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Получение сохраненного URL из SharedPreferences
    private fun getSavedUrl(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(URL_KEY, null)
    }

    // Сохранение URL в SharedPreferences
    private fun saveUrl(url: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(URL_KEY, url).apply()
        Toast.makeText(this, "URL сохранен!", Toast.LENGTH_SHORT).show()
    }

    // Переключение на экран ввода URL
    private fun showUrlInputScreen() {
        webView.visibility = View.GONE
        urlInputLayout.visibility = View.VISIBLE
    }

    // Настройка и загрузка WebView
    private fun loadWebView(url: String) {
        // Скрываем экран ввода и показываем WebView
        urlInputLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE

        // Настройка WebView
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true

        // Загрузка URL
        webView.loadUrl(url)
    }

    // Обработка кнопки "Назад" (для работы внутри WebView)
    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}