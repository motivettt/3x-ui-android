package com.threex.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.view.View
import android.widget.Toast
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import android.view.animation.AnimationUtils
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInputLayout: LinearLayout
    private lateinit var urlEditText: EditText
    private lateinit var saveUrlButton: Button
    private lateinit var clearButton: Button
    private lateinit var pingButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorTextView: TextView
    private lateinit var pingCard: LinearLayout
    private lateinit var pingStatusTextView: TextView
    private lateinit var pingResultTextView: TextView
    
    private var pingJob: Job? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
                webView.goBack()
            } else {
                // Если показывается экран настроек, выходим из приложения
                if (urlInputLayout.visibility == View.VISIBLE) {
                    finish()
                } else {
                    // Если в WebView, показываем экран настроек
                    showUrlInputScreen()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Полноэкранный режим (но с сохранением интерактивности)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Важно: Убеждаемся что окно может получать сенсорные события
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        
        // КРИТИЧЕСКИ ВАЖНО: Отключаем блокировку touch событий
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)

        setContentView(R.layout.activity_main)

        // Регистрация обработчика кнопки назад
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Инициализация компонентов
        webView = findViewById(R.id.webView)
        urlInputLayout = findViewById(R.id.urlInputLayout)
        urlEditText = findViewById(R.id.urlEditText)
        saveUrlButton = findViewById(R.id.saveUrlButton)
        clearButton = findViewById(R.id.clearButton)
        pingButton = findViewById(R.id.pingButton)
        progressBar = findViewById(R.id.progressBar)
        errorTextView = findViewById(R.id.errorTextView)
        pingCard = findViewById(R.id.pingCard)
        pingStatusTextView = findViewById(R.id.pingStatusTextView)
        pingResultTextView = findViewById(R.id.pingResultTextView)

        // Инициализация WebView
        setupWebView()

        // Проверяем сохраненный URL
        val savedUrl = getSavedUrl()
        if (savedUrl.isNullOrEmpty()) {
            showUrlInputScreen()
        } else {
            loadWebView(savedUrl)
        }

        // Обработчик кнопки подключения
        saveUrlButton.setOnClickListener {
            connectToUrl()
        }

        // Обработчик кнопки пинга
        pingButton.setOnClickListener {
            startPing()
        }

        // Обработка нажатия Enter в поле URL
        urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                connectToUrl()
                true
            } else {
                false
            }
        }

        // Обработчик кнопки очистки
        clearButton.setOnClickListener {
            clearSavedUrl()
            pingCard.visibility = View.GONE
            Toast.makeText(this, "Настройки очищены", Toast.LENGTH_SHORT).show()
            showUrlInputScreen()
        }
    }

    private fun setupWebView() {
        // Критически важные настройки для работы сенсорного экрана и форм
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            
            // Настройки масштабирования и отображения
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            
            // Настройки для работы с формами и input полями
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            
            // КРИТИЧЕСКИ ВАЖНО: Включаем все что нужно для работы touch событий
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            textZoom = 100
            
            // Настройки для лучшей работы с сенсорным экраном
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(false)
            
            // Отключаем множественные окна
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            
            // Настройки для современных веб-приложений
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false
        }

        // КРИТИЧЕСКИ ВАЖНО: Делаем WebView полностью интерактивным
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isClickable = true
        webView.isLongClickable = true
        webView.isEnabled = true
        
        // Убеждаемся что WebView не блокирует события
        webView.setOnLongClickListener(null)
        
        // НЕ запрашиваем фокус здесь - он будет запрошен после загрузки страницы

        // Обработка прогресса загрузки и файлов
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                    errorTextView.visibility = View.GONE
                    // Убеждаемся что WebView имеет фокус после загрузки
                    webView.requestFocus()
                }
            }

            // Поддержка выбора файлов в формах
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<android.net.Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Возвращаем false, чтобы использовать стандартный обработчик
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Загружаем все URL внутри WebView
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                handleLoadError(errorCode, description ?: "Неизвестная ошибка")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                errorTextView.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                
                // Упрощенный JavaScript код - НЕ блокирует события, только включает touch
                val jsCode = """
                    javascript:(function() {
                        // Убеждаемся что touch события работают на всем документе
                        if (document.body) {
                            document.body.style.touchAction = 'auto';
                            document.body.style.webkitUserSelect = 'text';
                            document.body.style.userSelect = 'text';
                        }
                        
                        // Убираем блокировки pointer events у всех элементов
                        var allElements = document.querySelectorAll('*');
                        for (var i = 0; i < allElements.length; i++) {
                            var el = allElements[i];
                            if (el.style.pointerEvents === 'none') {
                                el.style.pointerEvents = 'auto';
                            }
                        }
                    })();
                """.trimIndent()
                
                // Выполняем JavaScript и даем фокус WebView после небольшой задержки
                webView.postDelayed({
                    try {
                        webView.evaluateJavascript(jsCode, null)
                        // Даем фокус WebView после выполнения JavaScript
                        webView.requestFocus()
                    } catch (e: Exception) {
                        // Игнорируем ошибки выполнения JavaScript
                        webView.requestFocus()
                    }
                }, 300)
            }
        }
        
        // УБРАН обработчик touch событий - он может блокировать взаимодействие с WebView
        // WebView сам должен обрабатывать все touch события
        
        // Убеждаемся что клавиатура будет показываться автоматически при фокусе на input полях
        // Это работает благодаря правильным настройкам в манифесте (windowSoftInputMode)
    }

    private fun handleLoadError(errorCode: Int, description: String) {
        progressBar.visibility = View.GONE
        errorTextView.visibility = View.VISIBLE
        
        val errorMessage = when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> "Не удалось найти сервер. Проверьте URL и подключение к интернету."
            WebViewClient.ERROR_CONNECT -> "Не удалось подключиться к серверу. Проверьте доступность панели."
            WebViewClient.ERROR_TIMEOUT -> "Превышено время ожидания. Сервер не отвечает."
            -2 -> "Ошибка SSL сертификата. Возможно, используется самоподписанный сертификат."
            else -> "Ошибка загрузки: $description"
        }
        
        errorTextView.text = errorMessage
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun connectToUrl() {
        var url = urlEditText.text.toString().trim()
        
        if (url.isEmpty()) {
            Toast.makeText(this, "Введите URL", Toast.LENGTH_SHORT).show()
            return
        }

        // Автоматическое добавление http:// если протокол не указан
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }

        // Улучшенная валидация URL
        if (!isValidUrl(url)) {
            Toast.makeText(this, "Некорректный URL. Пример: http://192.168.1.1:2053", Toast.LENGTH_LONG).show()
            return
        }

        // Проверка интернет-соединения
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Нет подключения к интернету", Toast.LENGTH_LONG).show()
            return
        }

        saveUrl(url)
        loadWebView(url)
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            java.net.URL(url)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Показываем кнопку "Обновить" только когда WebView видим
        val reloadItem = menu.findItem(R.id.menu_reload)
        reloadItem?.isVisible = webView.visibility == View.VISIBLE
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_settings -> {
                showUrlInputScreen()
                return true
            }
            R.id.menu_reload -> {
                if (webView.visibility == View.VISIBLE) {
                    webView.reload()
                    Toast.makeText(this, "Обновление страницы...", Toast.LENGTH_SHORT).show()
                }
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
        errorTextView.visibility = View.GONE
        progressBar.visibility = View.GONE
        
        // Показываем текущий URL для редактирования
        urlEditText.setText(getSavedUrl() ?: "")
        
        // Анимации при показе экрана
        urlInputLayout.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
        urlEditText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        pingButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in))
        saveUrlButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in))
        clearButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in))
        
        // Автофокус на поле ввода
        urlEditText.requestFocus()
        urlEditText.selectAll()
        
        // Показываем клавиатуру с задержкой для плавности
        urlEditText.postDelayed({
            showKeyboard()
        }, 300)
        
        // Обновляем меню
        invalidateOptionsMenu()
    }

    private fun loadWebView(url: String) {
        // Скрываем экран настроек
        urlInputLayout.visibility = View.GONE
        errorTextView.visibility = View.GONE
        
        // Скрываем клавиатуру только для нашего EditText
        hideKeyboard()
        
        // КРИТИЧЕСКИ ВАЖНО: Делаем WebView видимым и активным
        webView.visibility = View.VISIBLE
        webView.bringToFront() // Убеждаемся что WebView на переднем плане
        
        // Обновляем меню
        invalidateOptionsMenu()

        // Загружаем URL
        webView.loadUrl(url)
        
        // Убеждаемся что WebView получает фокус после загрузки
        // Это позволит показывать клавиатуру при фокусе на input полях внутри WebView
        webView.postDelayed({
            webView.requestFocus()
            // Принудительно активируем WebView
            webView.isEnabled = true
            // Важно: не показываем клавиатуру здесь, она покажется автоматически при фокусе на input
        }, 300)
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(urlEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlEditText.windowToken, 0)
    }

    // Управление жизненным циклом WebView
    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        hideKeyboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        pingJob?.cancel()
        webView.destroy()
    }
    
    // Функция для начала пинга
    private fun startPing() {
        var url = urlEditText.text.toString().trim()
        
        if (url.isEmpty()) {
            Toast.makeText(this, "Введите URL для пинга", Toast.LENGTH_SHORT).show()
            return
        }

        // Автоматическое добавление http:// если протокол не указан
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }

        // Улучшенная валидация URL
        if (!isValidUrl(url)) {
            Toast.makeText(this, "Некорректный URL", Toast.LENGTH_SHORT).show()
            return
        }

        // Останавливаем предыдущий пинг если он запущен
        pingJob?.cancel()

        // Показываем карточку с анимацией
        pingCard.visibility = View.VISIBLE
        pingCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in))
        
        pingStatusTextView.text = "Проверка соединения..."
        pingResultTextView.text = ""
        
        // Начинаем ping в отдельном потоке
        pingJob = CoroutineScope(Dispatchers.IO).launch {
            performPing(url)
        }
    }
    
    // Выполнение HTTP ping (Android не поддерживает ICMP без root)
    private suspend fun performPing(targetUrl: String) {
        val pingResults = mutableListOf<Long>()
        var successCount = 0
        var failCount = 0
        
        repeat(5) { iteration ->
            try {
                val startTime = System.currentTimeMillis()
                val url = URL(targetUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "HEAD"
                    setRequestProperty("User-Agent", "3X-UI-Android-App")
                    instanceFollowRedirects = false
                }
                
                val responseCode = connection.responseCode
                val endTime = System.currentTimeMillis()
                val latency = endTime - startTime
                
                connection.disconnect()
                
                if (responseCode in 200..499 || responseCode == 302 || responseCode == 301) {
                    pingResults.add(latency)
                    successCount++
                    
                    withContext(Dispatchers.Main) {
                        updatePingResults(iteration + 1, latency, true, null)
                    }
                } else {
                    failCount++
                    withContext(Dispatchers.Main) {
                        updatePingResults(iteration + 1, 0, false, "HTTP $responseCode")
                    }
                }
            } catch (e: IOException) {
                failCount++
                withContext(Dispatchers.Main) {
                    updatePingResults(iteration + 1, 0, false, e.message ?: "Ошибка подключения")
                }
            } catch (e: Exception) {
                failCount++
                withContext(Dispatchers.Main) {
                    updatePingResults(iteration + 1, 0, false, e.message ?: "Неизвестная ошибка")
                }
            }
            
            if (iteration < 4) {
                delay(1000) // Пауза 1 секунда между пингами
            }
        }
        
        // Показываем итоговую статистику
        withContext(Dispatchers.Main) {
            showPingSummary(pingResults, successCount, failCount)
        }
    }
    
    private fun updatePingResults(packetNumber: Int, latency: Long, success: Boolean, error: String?) {
        val statusText = if (success) {
            "✅ Пакет $packetNumber: ${latency}ms"
        } else {
            "❌ Пакет $packetNumber: $error"
        }
        
        pingResultTextView.append("$statusText\n")
        
        // Прокрутка вниз
        pingResultTextView.post {
            val scrollAmount = pingResultTextView.layout?.getLineTop(pingResultTextView.lineCount) ?: 0
            pingResultTextView.scrollTo(0, scrollAmount)
        }
    }
    
    private fun showPingSummary(results: List<Long>, successCount: Int, failCount: Int) {
        if (results.isEmpty()) {
            pingStatusTextView.text = "❌ Не удалось подключиться"
            pingStatusTextView.setTextColor(0xFFFF5252.toInt())
            return
        }
        
        val min = results.minOrNull() ?: 0
        val max = results.maxOrNull() ?: 0
        val avg = results.average().toLong()
        
        val successRate = (successCount * 100) / 5
        
        pingStatusTextView.text = "📊 Статистика: Успешно $successCount/$5 ($successRate%)"
        pingStatusTextView.setTextColor(0xFF4CAF50.toInt())
        
        pingResultTextView.append("\n━━━━━━━━━━━━━━━━━━━━\n")
        pingResultTextView.append("📈 Минимум: ${min}ms\n")
        pingResultTextView.append("📊 Среднее: ${avg}ms\n")
        pingResultTextView.append("📉 Максимум: ${max}ms\n")
        pingResultTextView.append("🎯 Успешных: $successCount | Потеряно: $failCount")
    }

    // Сохранение и восстановление состояния WebView
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }
}
