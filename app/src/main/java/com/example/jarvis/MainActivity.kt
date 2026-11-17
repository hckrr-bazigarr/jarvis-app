│  package com.example.jarvis                                                                            │
│                                                                                                        │
│  import android.Manifest                                                                               │
│  import android.content.Intent                                                                         │
│  import android.content.pm.PackageManager                                                              │
│  import android.net.Uri                                                                                │
│  import android.os.Build                                                                               │
│  import android.os.Bundle                                                                              │
│  import android.speech.RecognitionListener                                                             │
│  import android.speech.RecognizerIntent                                                                │
│  import android.speech.SpeechRecognizer                                                                │
│  import android.speech.tts.TextToSpeech                                                                │
│  import android.telephony.SmsManager                                                                   │
│  import android.view.Gravity                                                                           │
│  import android.widget.Button                                                                          │
│  import android.widget.LinearLayout                                                                    │
│  import android.widget.TextView                                                                        │
│  import androidx.appcompat.app.AppCompatActivity                                                       │
│  import androidx.core.app.ActivityCompat                                                               │
│  import androidx.core.content.ContextCompat                                                            │
│  import java.text.SimpleDateFormat                                                                     │
│  import java.util.Date                                                                                 │
│  import java.util.Locale                                                                               │
│                                                                                                        │
│  class MainActivity : AppCompatActivity() {                                                            │
│                                                                                                        │
│      private lateinit var tts: TextToSpeech                                                            │
│      private lateinit var recognizer: SpeechRecognizer                                                 │
│      private lateinit var statusText: TextView                                                         │
│      private var ttsReady = false                                                                      │
│      private var emotion = "neutral"                                                                   │
│                                                                                                        │
│      override fun onCreate(savedInstanceState: Bundle?) {                                              │
│          super.onCreate(savedInstanceState)                                                            │
│                                                                                                        │
│          statusText = TextView(this).apply {                                                           │
│              textSize = 22f                                                                            │
│              setPadding(48, 140, 48, 48)                                                               │
│              text = "Jarvis ready. Tap the mic and speak."                                             │
│          }                                                                                             │
│          val micButton = Button(this).apply {                                                          │
│              text = "🎤 Speak"                                                                         │
│              setOnClickListener { startListening() }                                                   │
│          }                                                                                             │
│          setContentView(LinearLayout(this).apply {                                                     │
│              orientation = LinearLayout.VERTICAL                                                       │
│              gravity = Gravity.CENTER                                                                  │
│              addView(statusText)                                                                       │
│              addView(micButton)                                                                        │
│          })                                                                                            │
│                                                                                                        │
│          requestPermissions()                                                                          │
│                                                                                                        │
│          tts = TextToSpeech(this) { status ->                                                          │
│              if (status == TextToSpeech.SUCCESS) {                                                     │
│                  tts.language = Locale.US          // Hindi voice chahiye to: Locale("hi","IN")        │
│                  ttsReady = true                                                                       │
│                  speak("Systems online. Bolo, kya karna hai?")                                         │
│              }                                                                                         │
│          }                                                                                             │
│                                                                                                        │
│          recognizer = SpeechRecognizer.createSpeechRecognizer(this)                                    │
│          recognizer.setRecognitionListener(listener)                                                   │
│      }                                                                                                 │
│                                                                                                        │
│      private val listener = object : RecognitionListener {                                             │
│          override fun onReadyForSpeech(params: Bundle?) {}                                             │
│          override fun onBeginningOfSpeech() { statusText.text = "Listening..." }                       │
│          override fun onRmsChanged(rmsdB: Float) {}                                                    │
│          override fun onBufferReceived(buffer: ByteArray?) {}                                          │
│          override fun onEndOfSpeech() {}                                                               │
│          override fun onError(error: Int) { statusText.text = "Samajh nahi aaya, phir bolo." }         │
│          override fun onResults(results: Bundle?) {                                                    │
│              val cmd = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)               │
│                  ?.get(0) ?: return                                                                    │
│              statusText.text = "You: $cmd"                                                             │
│              handleCommand(cmd)                                                                        │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun startListening() {                                                                    │
│          val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {                         │
│              putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,                                           │
│                  RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)                                            │
│              putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN") // Hindi mix ke liye "hi-IN"           │
│          }                                                                                             │
│          recognizer.startListening(intent)                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun handleCommand(cmd: String) {                                                          │
│          val c = cmd.lowercase().trim()                                                                │
│          emotion = detectEmotion(c)                                                                    │
│                                                                                                        │
│          when {                                                                                        │
│              c.contains("play") || c.contains("gaana") || c.contains("song") ||                        │
│              c.contains("music") -> {                                                                  │
│                  val q = c.replace("play", "").replace("gaana", "")                                    │
│                      .replace("song", "").replace("music", "").replace("please", "").trim()            │
│                  playOnYoutube(q)                                                                      │
│              }                                                                                         │
│              c.contains("send sms") || c.contains("text to") -> parseAndSendSms(c)                     │
│              c.contains("whatsapp") -> parseAndSendWhatsapp(c)                                         │
│              c.contains("time") -> respond("Time hai " +                                               │
│                  SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()))                      │
│              c.contains("date") || c.contains("day") -> respond("Aaj hai " +                           │
│                  SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()))            │
│              c.contains("open youtube") -> openApp("com.google.android.youtube")                       │
│              c.contains("open whatsapp") -> openApp("com.whatsapp")                                    │
│              c.contains("hello") || c.contains("hey") || c.contains("hi ") -> respond(greeting())      │
│              c.contains("thank") -> respond("Hamesha yahin hoon.")                                     │
│              else -> webSearch(c)                                                                      │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      // ---------- Emotion detection (keyword based v1) ----------                                     │
│      private fun detectEmotion(t: String): String {                                                    │
│          val sad = listOf("sad", "dukhi", "udas", "tired", "lonely", "depressed")                      │
│          val angry = listOf("angry", "gussa", "hate", "annoyed", "furious")                            │
│          val happy = listOf("happy", "khush", "excited", "great", "awesome", "love")                   │
│          return when {                                                                                 │
│              sad.any { t.contains(it) } -> "sad"                                                       │
│              angry.any { t.contains(it) } -> "angry"                                                   │
│              happy.any { t.contains(it) } -> "happy"                                                   │
│              else -> "neutral"                                                                         │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun greeting(): String = when (emotion) {                                                 │
│          "sad" -> "Lagta hai mood off hai. Kuch play karun?",                                          │
│          "angry" -> "Ek deep breath lo. Calming gaana laga doon?",                                     │
│          "happy" -> "Badhiya! Aaj kya karna hai?",                                                     │
│          else -> "Hello, batao kya karun?"                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      // ---------- Actions ----------                                                                  │
│      private fun playOnYoutube(query: String) {                                                        │
│          if (query.isBlank()) { respond("Kaunsa gaana? naam batao."); return }                         │
│          val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)                 │
│          startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))                                     │
│          respond("$query laga raha hoon YouTube pe")                                                   │
│      }                                                                                                 │
│                                                                                                        │
│      private fun parseAndSendSms(cmd: String) {                                                        │
│          // bolo: "send sms to 9876543210 saying I'm on my way"                                        │
│          val number = Regex("\\d{10}").find(cmd)?.value                                                │
│          val msg = cmd.replace(Regex("send sms to \\d{10}"), "")                                       │
│              .replace("saying", "").replace("that", "").trim()                                         │
│          if (number == null || msg.isBlank()) {                                                        │
│              respond("Aise bolo: send sms to 9876543210 saying hello"); return                         │
│          }                                                                                             │
│          if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)                     │
│              != PackageManager.PERMISSION_GRANTED) {                                                   │
│              ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 2)         │
│              respond("Pehle SMS permission do"); return                                                │
│          }                                                                                             │
│          try {                                                                                         │
│              val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)                             │
│                  SmsManager.getInstance(this) else SmsManager.getDefault()                             │
│              sms.sendTextMessage(number, null, msg, null, null)                                        │
│              respond("$number ko message bhej diya")                                                   │
│          } catch (e: Exception) {                                                                      │
│              respond("SMS nahi gaya: ${e.message}")                                                    │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun parseAndSendWhatsapp(cmd: String) {                                                   │
│          // bolo: "whatsapp 9876543210 I'll be late"                                                   │
│          val number = Regex("\\d{10}").find(cmd)?.value                                                │
│          val msg = cmd.replace(Regex("whatsapp \\d{10}"), "")                                          │
│              .replace("message", "").replace("saying", "").trim()                                      │
│          if (number == null) { respond("Aise bolo: whatsapp 9876543210 hello"); return }               │
│          val url = "https://wa.me/91$number?text=" + Uri.encode(msg) // +91 = India                    │
│          startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))                                     │
│          respond("WhatsApp khol raha hoon. Send tu dabaye ga.")                                        │
│      }                                                                                                 │
│                                                                                                        │
│      private fun openApp(pkg: String) {                                                                │
│          try {                                                                                         │
│              startActivity(packageManager.getLaunchIntentForPackage(pkg)!!)                            │
│              respond("App khol raha hoon")                                                             │
│          } catch (e: Exception) { respond("App install nahi hai") }                                    │
│      }                                                                                                 │
│                                                                                                        │
│      private fun webSearch(query: String) {                                                            │
│          startActivity(Intent(Intent.ACTION_VIEW,                                                      │
│              Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))                       │
│          respond("$query search kar raha hoon")                                                 
