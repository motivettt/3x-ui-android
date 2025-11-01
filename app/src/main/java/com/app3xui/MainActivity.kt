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
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInputLayout: LinearLayout
    private lateinit var urlEditText: EditText
    private lateinit var saveUrlButton: Button
    private lateinit var clearButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // САМЫЙ ПРОСТОЙ И НАДЕЖНЫЙ ПОЛНОЭКРАННЫЙ РЕЖИМ
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        // Инициализация компонентов
        webView = findViewById(R.id.webView)
        urlInputLayout = findViewById(R.id.urlInputLayout)
        urlEditText = findViewById(R.id.urlEditText)
        saveUrlButton = findViewById(R.id.saveUrlButton)
        clearButton = findViewById(R.id.clearButton)

        // Проверяем сохраненный URL
        val savedUrl = getSavedUrl()
        if (savedUrl.isNullOrEmpty()) {
            showUrlInputScreen()
        } else {
            loadWebView(savedUrl)
        }

        // Обработчик кнопки подключения
        saveUrlButton.setOnClickListener {
            val url = urlEditText.text.toString().trim()
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                saveUrl(url)
                loadWebView(url)
            } else {
                Toast.makeText(this, "Введите URL с http:// или https://", Toast.LENGTH_LONG).show()
            }
        }

        // Обработчик кнопки очистки
        clearButton.setOnClickListener {
            clearSavedUrl()
            Toast.makeText(this, "Настройки очищены", Toast.LENGTH_SHORT).show()
            showUrlInputScreen()
        }
    }

    // Меню (три точки в углу)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_settings -> {
                showUrlInputScreen()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getSavedUrl(): String? {
        val prefs = getSharedPreferences("My3XUIPrefs", Context.MODE_PRIVATE)
        return prefs.getString("PanelUrl", null)
    }

    private fun saveUrl(url: String) {
        val prefs = getSharedPreferences("My3XUIPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("PanelUrl", url).apply()
        Toast.makeText(this, "URL сохранен!", Toast.LENGTH_SHORT).show()
    }

    private fun clearSavedUrl() {
        val prefs = getSharedPreferences("My3XUIPrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("PanelUrl").apply()
    }

    private fun showUrlInputScreen() {
        webView.visibility = View.GONE
        urlInputLayout.visibility = View.VISIBLE
        // Показываем текущий URL для редактирования
        urlEditText.setText(getSavedUrl() ?: "")
    }

    private fun loadWebView(url: String) {
        urlInputLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}