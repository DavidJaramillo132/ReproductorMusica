package com.david.reproductordemusica

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



class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var posicion = 0

    private lateinit var songTitle: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var seekBar: Slider
    private lateinit var imagenAlbum: ImageView


    private val canciones = mutableListOf<Uri>()
    private val nombres = mutableListOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private val actualizarSeekBar = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    seekBar.value  = it.currentPosition.toFloat()
                    currentTime.text = formatearTiempo(it.currentPosition)
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        songTitle = findViewById(R.id.songTitle)
        currentTime = findViewById(R.id.tiempoActual)
        totalTime = findViewById(R.id.tiempoTotal)
        seekBar = findViewById(R.id.seekBar)
        imagenAlbum = findViewById(R.id.imagenMusicView)


        val btnPlay = findViewById<ImageButton>(R.id.btnPlay)
        val btnPause = findViewById<ImageButton>(R.id.btnPause)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)


        // Verificar permisos según la versión de Android
        val permiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permiso), 1)
        } else {
            cargarMusicaDelDispositivo()
        }

        btnPlay.setOnClickListener {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    handler.post(actualizarSeekBar)
                }
            }
        }

        btnPause.setOnClickListener {
            mediaPlayer?.takeIf { it.isPlaying }?.pause()
        }

        btnNext.setOnClickListener {
            if (canciones.isNotEmpty()) {
                posicion = (posicion + 1) % canciones.size
                reproducirCancion()
            }
        }

        btnPrev.setOnClickListener {
            if (canciones.isNotEmpty()) {
                posicion = (posicion - 1 + canciones.size) % canciones.size
                reproducirCancion()
            }
        }

        seekBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) mediaPlayer?.seekTo(value.toInt())
        }


    }

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

        if (canciones.isNotEmpty()) {
            posicion = 0
            reproducirCancion()
        } else {
            songTitle.text = "No se encontraron canciones."
        }
    }

    private fun reproducirCancion() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, canciones[posicion])
        mediaPlayer?.let {
            songTitle.text = nombres[posicion]
            seekBar.valueTo = it.duration.toFloat()
            totalTime.text = formatearTiempo(it.duration)
            seekBar.value = 0f
            it.start()
            handler.post(actualizarSeekBar)

            // Mostrar imagen del álbum si existe
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


    private fun formatearTiempo(millis: Int): String {
        val min = TimeUnit.MILLISECONDS.toMinutes(millis.toLong())
        val sec = TimeUnit.MILLISECONDS.toSeconds(millis.toLong()) % 60
        return String.format("%d:%02d", min, sec)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cargarMusicaDelDispositivo()
        } else {
            songTitle.text = "Permiso denegado."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        handler.removeCallbacks(actualizarSeekBar)
    }
}
