
package com.example.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private lateinit var recognizer: SpeechRecognizer
    private lateinit var statusText: TextView

    private var ttsReady = false
    private var emotion = "neutral"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var pendingSmsNumber = ""
    private var pendingSmsMessage = ""

    companion object {
        private const val PERMISSION_REQUEST = 101
        private const val SMS_PERMISSION_REQUEST = 102

        private const val GEMINI_MODEL = "gemini-3.8-flash"

        private const val GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"
    }

    // ---------- Jarvis Personality ----------

    private val SYSTEM_PROMPT = """
        You are Jarvis, a highly advanced, witty, and helpful AI assistant inspired by Iron Man's J.A.R.V.I.S.

        Rules:
        - Keep answers short and conversational, usually 1-3 sentences.
        - Detect the user's mood and respond with matching tone.
        - Be comforting if sad, calm if angry, energetic if happy.
        - Be charming, intelligent and loyal without overdoing it.
        - For coding questions, provide exact code or commands.
        - Do not use markdown or emojis.
        - Reply naturally in the language used by the user.
        - Support Hindi, English and Hinglish.
    """.trimIndent()

    // ---------- Activity Setup ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusText = TextView(this).apply {
            textSize = 22f
            setPadding(48, 140, 48, 48)
            text = "Jarvis. Tap the mic and speak."
        }

        val micButton = Button(this).apply {
            text = "Speak"
            setOnClickListener {
                startListening()
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(statusText)
                addView(micButton)
            }
        )

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale("en", "IN"))

                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

                if (ttsReady) {
                    speak("Systems online. Main sun raha hoon, bolo.")
                }
            }
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer.setRecognitionListener(listener)
        } else {
            respond("Speech recognition is not available on this device.")
        }

        requestPermissions()
    }

    // ---------- Permissions ----------

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == SMS_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                sendSmsNow()
            } else {
                respond("SMS permission denied.")
            }
            return
        }

        if (requestCode == PERMISSION_REQUEST) {
            val audioGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

            if (audioGranted) {
                respond("Microphone permission granted. Ready.")
            } else {
                respond("Microphone permission is required.")
            }
        }
    }

    // ---------- Speech Recognition ----------

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {
            statusText.text = "Listening..."
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            statusText.text = "Processing..."
        }

        override fun onError(error: Int) {
            statusText.text = "Samajh nahi aaya, phir bolo."
        }

        override fun onResults(results: Bundle?) {
            val cmd = results
                ?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                ?.firstOrNull() ?: return

            statusText.text = "You: $cmd"
            handleCommand(cmd)
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListening() {
        if (!::recognizer.isInitialized) {
            respond("Speech recognition unavailable.")
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions()
            return
        }

        try {
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "en-IN"
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "en-IN"
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
            }

            recognizer.startListening(intent)

        } catch (e: Exception) {
            respond("Unable to start listening.")
        }
    }

    // ---------- Command Router ----------

    private fun handleCommand(cmd: String) {

        val c = cmd.lowercase(Locale.ROOT).trim()

        emotion = detectEmotion(c)

        when {

            c.startsWith("play ") ||
            c.startsWith("play song") ||
            c.contains("gaana") ||
            c.contains("song") ||
            c.contains("music") -> {

                val q = c
                    .replace("play", "")
                    .replace("gaana", "")
                    .replace("song", "")
                    .replace("music", "")
                    .replace("please", "")
                    .trim()

                playOnYoutube(q)
            }

            c.startsWith("send sms") ||
            c.contains("text to") -> {
                parseAndSendSms(cmd)
            }

            c.contains("whatsapp") -> {
                parseAndSendWhatsapp(cmd)
            }

            c.contains("what time") ||
            c == "time" ||
            c.contains("current time") -> {

                respond(
                    "Time hai " +
                    SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(Date())
                )
            }

            c.contains("date") ||
            c.contains("what day") -> {

                respond(
                    "Aaj hai " +
                    SimpleDateFormat(
                        "EEEE, d MMMM yyyy",
                        Locale.getDefault()
                    ).format(Date())
                )
            }

            c.contains("open youtube") -> {
                openApp("com.google.android.youtube")
            }

            c.contains("open whatsapp") -> {
                openApp("com.whatsapp")
            }

            c.contains("open chrome") -> {
                openApp("com.android.chrome")
            }

            c.startsWith("search ") -> {
                webSearch(c.removePrefix("search").trim())
            }

            else -> {
                askAI(cmd)
            }
        }
    }

    // ---------- Emotion Detection ----------

    private fun detectEmotion(t: String): String {

        val sad = listOf(
            "sad", "dukhi", "udas", "tired",
            "lonely", "depressed", "thak"
        )

        val angry = listOf(
            "angry", "gussa", "hate",
            "annoyed", "furious", "bakwas"
        )

        val happy = listOf(
            "happy", "khush", "excited",
            "great", "awesome", "love", "badhiya"
        )

        return when {
            sad.any { t.contains(it) } -> "sad"
            angry.any { t.contains(it) } -> "angry"
            happy.any { t.contains(it) } -> "happy"
            else -> "neutral"
        }
    }

    // ---------- Gemini AI ----------

    private fun askAI(prompt: String) {

        respond("Soch raha hoon...")

        val key = BuildConfig.GEMINI_API_KEY

        if (key.isBlank()) {
            respond("API key set nahi hai. local.properties mein daalo.")
            return
        }

        executor.execute {

            try {

                val fullPrompt =
                    "$SYSTEM_PROMPT\n\nUser mood: $emotion\nUser: $prompt"

                val json = JSONObject().apply {

                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")

                                put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "text",
                                            fullPrompt
                                        )
                                    )
                                )
                            }
                        )
                    )

                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("maxOutputTokens", 300)
                            put("temperature", 0.7)
                        }
                    )
                }

                val body = json.toString()
                    .toRequestBody(
                        "application/json".toMediaType()
                    )

                val request = Request.Builder()
                    .url("$GEMINI_URL?key=$key")
                    .post(body)
                    .build()

                http.newCall(request).execute().use { response ->

                    val responseBody = response.body?.string()
                        ?: throw Exception("Empty server response")

                    if (!response.isSuccessful) {
                        throw Exception(
                            "Gemini API error ${response.code}: $responseBody"
                        )
                    }

                    val result = JSONObject(responseBody)

                    val candidates = result.optJSONArray("candidates")
                        ?: throw Exception("No AI response available")

                    if (candidates.length() == 0) {
                        throw Exception("AI returned no answer")
                    }

                    val text = candidates
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .optString("text", "")

                    if (text.isBlank()) {
                        throw Exception("Empty AI answer")
                    }

                    mainHandler.post {
                        respond(text.trim())
                    }
                }

            } catch (e: Exception) {

                mainHandler.post {
                    respond(
                        "Gemini se connect nahi ho paya. " +
                        (e.message ?: "Please try again.")
                    )
                }
            }
        }
    }

    // ---------- Text To Speech ----------

    private fun respond(message: String) {

        mainHandler.post {

            statusText.text = message
            speak(message)
        }
    }

    private fun speak(message: String) {

        if (ttsReady) {
            tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "JARVIS_RESPONSE"
            )
        }
    }

    // ---------- YouTube Music ----------

    private fun playOnYoutube(query: String) {

        val searchQuery = if (query.isBlank()) {
            "music"
        } else {
            query
        }

        val url = Uri.parse(
            "https://www.youtube.com/results?search_query=" +
                Uri.encode(searchQuery)
        )

        val intent = Intent(Intent.ACTION_VIEW, url)

        try {
            startActivity(intent)
            respond("YouTube par search kar raha hoon.")
        } catch (e: Exception) {
            respond("YouTube open nahi ho raha.")
        }
    }

    // ---------- SMS Parsing ----------

    private fun parseAndSendSms(command: String) {

        val pattern = Regex(
            """(?:send sms|text)\s+to\s+(\+?\d{7,15})\s+(?:message\s+)?(.+)""",
            RegexOption.IGNORE_CASE
        )

        val match = pattern.find(command)

        if (match == null) {
            respond(
                "Bolo: send sms to number message hello"
            )
            return
        }

        pendingSmsNumber = match.groupValues[1]
        pendingSmsMessage = match.groupValues[2].trim()

        if (pendingSmsMessage.isBlank()) {
            respond("SMS message empty hai.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm SMS")
            .setMessage(
                "Number: $pendingSmsNumber\n\n" +
                "Message: $pendingSmsMessage\n\n" +
                "Kya main SMS send kar doon?"
            )
            .setPositiveButton("Send") { _, _ ->

                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    sendSmsNow()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.SEND_SMS),
                        SMS_PERMISSION_REQUEST
                    )
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                respond("SMS cancel kar diya.")
            }
            .show()
    }

    private fun sendSmsNow() {

        try {

            val smsManager = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(
                pendingSmsMessage
            )

            smsManager.sendMultipartTextMessage(
                pendingSmsNumber,
                null,
                parts,
                null,
                null
            )

            respond("SMS send request submit kar di hai.")

        } catch (e: Exception) {

            respond(
                "SMS send nahi hua: ${e.message}"
            )
        }
    }

    // ---------- WhatsApp ----------

    private fun parseAndSendWhatsapp(command: String) {

        val pattern = Regex(
            """(?:whatsapp|message)\s+(?:to\s+)?(\+?\d{7,15})\s+(?:message\s+)?(.+)""",
            RegexOption.IGNORE_CASE
        )

        val match = pattern.find(command)

        if (match == null) {
            respond(
                "Bolo: whatsapp to number message hello"
            )
            return
        }

        val number = match.groupValues[1]
        val message = match.groupValues[2].trim()

        if (message.isBlank()) {
            respond("WhatsApp message empty hai.")
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(
                Intent.EXTRA_TEXT,
                message
            )
            putExtra(
                "jid",
                number.replace("+", "") + "@s.whatsapp.net"
            )
        }

        try {
            startActivity(intent)
            respond("WhatsApp open kar raha hoon. Message check karke send karo.")
        } catch (e: Exception) {
            respond("WhatsApp open nahi ho paya.")
        }
    }

    // ---------- Open Applications ----------

    private fun openApp(packageName: String) {

        try {

            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent != null) {
                startActivity(launchIntent)
                respond("Application opening.")
            } else {
                respond("Application installed nahi hai.")
            }

        } catch (e: Exception) {
            respond("Application open nahi ho paya.")
        }
    }

    // ---------- Web Search ----------

    private fun webSearch(query: String) {

        if (query.isBlank()) {
            respond("Kya search karna hai?")
            return
        }

        val url = Uri.parse(
            "https://www.google.com/search?q=" +
                Uri.encode(query)
        )

        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, url)
            )
            respond("Searching the web.")
        } catch (e: Exception) {
            respond("Search open nahi ho paya.")
        }
    }

    // ---------- Lifecycle ----------

    override fun onDestroy() {

        if (::recognizer.isInitialized) {
            recognizer.destroy()
        }

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        executor.shutdownNow()

        super.onDestroy()
    }
}
