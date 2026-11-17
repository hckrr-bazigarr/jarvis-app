package com.example.jarvis                                                                            │
│                                                                                                        │
│  import android.Manifest                                                                               │
│  import android.app.Activity                                                                           │
│  import android.content.Intent                                                                         │
│  import android.content.pm.PackageManager                                                              │
│  import android.net.Uri                                                                                │
│  import android.os.Bundle                                                                              │
│  import android.speech.RecognizerIntent                                                                │
│  import android.speech.tts.TextToSpeech                                                                │
│  import android.widget.Button                                                                          │
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
│      private lateinit var tv: TextView                                                                 │
│      private val REQ_VOICE = 100                                                                       │
│      private val REQ_MIC = 200                                                                         │
│                                                                                                        │
│      override fun onCreate(savedInstanceState: Bundle?) {                                              │
│          super.onCreate(savedInstanceState)                                                            │
│          setContentView(R.layout.activity_main)                                                        │
│                                                                                                        │
│          tv = findViewById(R.id.tv)                                                                    │
│          val btn = findViewById<Button>(R.id.btn)                                                      │
│                                                                                                        │
│          tts = TextToSpeech(this) { status ->                                                          │
│              if (status == TextToSpeech.SUCCESS) {                                                     │
│                  tts.language = Locale("hi", "IN")                                                     │
│                  speak("Jarvis ready hai. Bolo kya karna hai?")                                        │
│              }                                                                                         │
│          }                                                                                             │
│                                                                                                        │
│          btn.setOnClickListener {                                                                      │
│              if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)             │
│                  != PackageManager.PERMISSION_GRANTED                                                  │
│              ) {                                                                                       │
│                  ActivityCompat.requestPermissions(                                                    │
│                      this,                                                                             │
│                      arrayOf(Manifest.permission.RECORD_AUDIO),                                        │
│                      REQ_MIC                                                                           │
│                  )                                                                                     │
│              } else {                                                                                  │
│                  listen()                                                                              │
│              }                                                                                         │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      override fun onRequestPermissionsResult(                                                          │
│          requestCode: Int,                                                                             │
│          permissions: Array<out String>,                                                               │
│          grantResults: IntArray                                                                        │
│      ) {                                                                                               │
│          super.onRequestPermissionsResult(requestCode, permissions, grantResults)                      │
│          if (requestCode == REQ_MIC && grantResults.isNotEmpty()                                       │
│              && grantResults[0] == PackageManager.PERMISSION_GRANTED                                   │
│          ) {                                                                                           │
│              listen()                                                                                  │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun listen() {                                                                            │
│          val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {                         │
│              putExtra(                                                                                 │
│                  RecognizerIntent.EXTRA_LANGUAGE_MODEL,                                                │
│                  RecognizerIntent.LANGUAGE_MODEL_FREE_FORM                                             │
│              )                                                                                         │
│              putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")                                        │
│              putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo...")                                        │
│          }                                                                                             │
│          startActivityForResult(intent, REQ_VOICE)                                                     │
│      }                                                                                                 │
│                                                                                                        │
│      override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {                 │
│          super.onActivityResult(requestCode, resultCode, data)                                         │
│                                                                                                        │
│          if (requestCode == REQ_VOICE && resultCode == Activity.RESULT_OK && data != null) {           │
│              val text = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)                   │
│                  ?.get(0) ?: ""                                                                        │
│                                                                                                        │
│              tv.text = text                                                                            │
│              handleCommand(text)                                                                       │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun handleCommand(cmd: String) {                                                          │
│          val c = cmd.lowercase(Locale.getDefault())                                                    │
│          val emotion = detectEmotion(c)                                                                │
│                                                                                                        │
│          when {                                                                                        │
│              c.contains("gaana") || c.contains("song") -> {                                            │
│                  val query = c.substringAfter("gaana")                                                 │
│                      .substringAfter("song")                                                           │
│                      .trim()                                                                           │
│                      .ifBlank { "aaj blue" }                                                           │
│                                                                                                        │
│                  playYouTube(query)                                                                    │
│                  speak("Gaana chala raha hoon: $query")                                                │
│              }                                                                                         │
│                                                                                                        │
│              c.contains("whatsapp") -> {                                                               │
│                  val number = Regex("(\\d{10,12})").find(c)?.value ?: "919876543210"                   │
│                  val msg = c.substringAfter("bhejo")                                                   │
│                      .substringAfter(number)                                                           │
│                      .trim()                                                                           │
│                      .ifBlank { "Hi" }                                                                 │
│                                                                                                        │
│                  openWhatsApp(number, msg)                                                             │
│                  speak("WhatsApp khol raha hoon. Send button dabao.")                                  │
│              }                                                                                         │
│                                                                                                        │
│              c.contains("time") || c.contains("samay") -> {                                            │
│                  val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())            │
│                  speak("Abhi $time baje hain")                                                         │
│              }                                                                                         │
│                                                                                                        │
│              else -> {                                                                                 │
│                  if (emotion == "negative") {                                                          │
│                      speak("Lagta hai tum thoda pareshan ho. Main sun raha hoon.")                     │
│                  } else if (emotion == "positive") {                                                   │
│                      speak("Arre badhiya! Mood accha lag raha hai.")                                   │
│                  } else {                                                                              │
│                      speak("Sorry, samajh nahi aaya. Dobara bolo.")                                    │
│                  }                                                                                     │
│              }                                                                                         │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun detectEmotion(text: String): String {                                                 │
│          val positive = listOf("happy", "khush", "accha", "badhiya", "pyaar", "love", "great",         │
│  "thank")                                                                                              │
│          val negative = listOf("sad", "dukhi", "gussa", "angry", "bura", "hate", "pareshan",           │
│  "tension")                                                                                            │
│                                                                                                        │
│          val score = positive.count { text.contains(it) } - negative.count { text.contains(it) }       │
│                                                                                                        │
│          return when {                                                                                 │
│              score > 0 -> "positive"                                                                   │
│              score < 0 -> "negative"                                                                   │
│              else -> "neutral"                                                                         │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun playYouTube(query: String) {                                                          │
│          val intent = Intent(Intent.ACTION_SEARCH).apply {                                             │
│              setPackage("com.google.android.youtube")                                                  │
│              putExtra("query", query)                                                                  │
│              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)                                                   │
│          }                                                                                             │
│          try {                                                                                         │
│              startActivity(intent)                                                                     │
│          } catch (e: Exception) {                                                                      │
│              startActivity(                                                                            │
│                  Intent(                                                                               │
│                      Intent.ACTION_VIEW,                                                               │
│                      Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")    │
│                  )                                                                                     │
│              )                                                                                         │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun openWhatsApp(number: String, msg: String) {                                           │
│          val url = "https://wa.me/$number?text=${Uri.encode(msg)}"                                     │
│          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))                                       │
│          try {                                                                                         │
│              startActivity(intent)                                                                     │
│          } catch (e: Exception) {                                                                      │
│              speak("WhatsApp install nahi hai ya link open nahi ho raha")                              │
│          }                                                                                             │
│      }                                                                                                 │
│                                                                                                        │
│      private fun speak(text: String) {                                                                 │
│          tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")                                     │
│      }                                                                                                 │
│                                                                                                        │
│      override fun onDestroy() {                                                                        │
│          tts.shutdown()                                                                                │
│          super.onDestroy()                                                                             │
│      }                                                                                                 │
│  }
