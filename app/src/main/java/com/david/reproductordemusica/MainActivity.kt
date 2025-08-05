// Paquete principal de la app
package com.david.reproductordemusica

// Importación de clases necesarias para permisos, controles, multimedia y UI
import android.Manifest
import com.google.android.material.slider.Slider
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import android.widget.ImageView
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory

// Clase principal que controla la actividad
class MainActivity : AppCompatActivity() {

    // Reproductor de audio
    private var mediaPlayer: MediaPlayer? = null

    // Índice de la canción que se está reproduciendo
    private var posicion = 0

    // Elementos visuales del layout
    private lateinit var songTitle: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var seekBar: Slider
    private lateinit var imagenAlbum: ImageView

    // Listas para almacenar las canciones (URIs) y sus nombres
    private val canciones = mutableListOf<Uri>()
    private val nombres = mutableListOf<String>()

    // Handler para actualizar el progreso de la canción en la UI
    private val handler = Handler(Looper.getMainLooper())

    // Tarea que actualiza el seekBar y tiempo actual cada 500ms mientras se reproduce
    private val actualizarSeekBar = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    seekBar.value = it.currentPosition.toFloat()
                    currentTime.text = formatearTiempo(it.currentPosition)
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    // Método que se ejecuta cuando inicia la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Vinculación de los elementos del layout con variables
        songTitle = findViewById(R.id.songTitle)
        currentTime = findViewById(R.id.tiempoActual)
        totalTime = findViewById(R.id.tiempoTotal)
        seekBar = findViewById(R.id.seekBar)
        imagenAlbum = findViewById(R.id.imagenMusicView)

        val btnPlay = findViewById<ImageButton>(R.id.btnPlay)
        val btnPause = findViewById<ImageButton>(R.id.btnPause)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)

        // Verificación de permisos según la versión de Android
        val permiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        // Si no tiene permisos, los pide. Si ya los tiene, carga la música
        if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permiso), 1)
        } else {
            cargarMusicaDelDispositivo()
        }

        // Botón Play
        btnPlay.setOnClickListener {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    handler.post(actualizarSeekBar)
                }
            }
        }

        // Botón Pause
        btnPause.setOnClickListener {
            mediaPlayer?.takeIf { it.isPlaying }?.pause()
        }

        // Botón siguiente
        btnNext.setOnClickListener {
            if (canciones.isNotEmpty()) {
                posicion = (posicion + 1) % canciones.size
                reproducirCancion()
            }
        }

        // Botón anterior
        btnPrev.setOnClickListener {
            if (canciones.isNotEmpty()) {
                posicion = (posicion - 1 + canciones.size) % canciones.size
                reproducirCancion()
            }
        }

        // Mover el seekBar manualmente
        seekBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) mediaPlayer?.seekTo(value.toInt())
        }
    }

    // Método que carga la música desde el almacenamiento del dispositivo
    private fun cargarMusicaDelDispositivo() {
        canciones.clear()
        nombres.clear()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proyeccion = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val seleccion = "${MediaStore.Audio.Media.MIME_TYPE} LIKE ?"
        val seleccionArgs = arrayOf("audio/%")

        val cursor: Cursor? = contentResolver.query(
            uri, proyeccion, seleccion, seleccionArgs, null
        )

        // Recorre el cursor y guarda cada canción encontrada
        cursor?.use {
            val idIndex = it.getColumnIndex(MediaStore.Audio.Media._ID)
            val nameIndex = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val nombre = it.getString(nameIndex)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                canciones.add(contentUri)
                nombres.add(nombre)
            }
        }

        // Si hay canciones, reproduce la primera
        if (canciones.isNotEmpty()) {
            posicion = 0
            reproducirCancion()
        } else {
            songTitle.text = "No se encontraron canciones."
        }
    }

    // Método que reproduce una canción basada en la posición actual
    private fun reproducirCancion() {
        mediaPlayer?.release() // Libera el reproductor anterior si existía
        mediaPlayer = MediaPlayer.create(this, canciones[posicion]) // Crea nuevo MediaPlayer

        mediaPlayer?.let {
            // Actualiza los textos e inicia la canción
            songTitle.text = nombres[posicion]
            seekBar.valueTo = it.duration.toFloat()
            totalTime.text = formatearTiempo(it.duration)
            seekBar.value = 0f
            it.start()
            handler.post(actualizarSeekBar)

            // Intenta recuperar la imagen del álbum
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, canciones[posicion])
                val art = retriever.embeddedPicture
                if (art != null) {
                    val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                    imagenAlbum.setImageBitmap(bitmap)
                } else {
                    imagenAlbum.setImageResource(R.drawable.ic_launcher_foreground) // imagen por defecto
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }

        } ?: run {
            songTitle.text = "Error al cargar canción."
        }
    }

    // Formatea los milisegundos a formato mm:ss
    private fun formatearTiempo(millis: Int): String {
        val min = TimeUnit.MILLISECONDS.toMinutes(millis.toLong())
        val sec = TimeUnit.MILLISECONDS.toSeconds(millis.toLong()) % 60
        return String.format("%d:%02d", min, sec)
    }

    // Manejo del resultado del permiso solicitado
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cargarMusicaDelDispositivo()
        } else {
            songTitle.text = "Permiso denegado."
        }
    }

    // Se llama cuando se destruye la actividad para liberar recursos
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        handler.removeCallbacks(actualizarSeekBar)
    }
}
