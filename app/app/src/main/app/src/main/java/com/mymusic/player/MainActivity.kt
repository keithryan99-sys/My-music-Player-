
package com.mymusic.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: android.net.Uri
)

class MainActivity : Activity() {

    private val songs = mutableListOf<Song>()
    private var currentIndex = -1
    private var shuffle = false
    private var player: MediaPlayer? = null

    private lateinit var status: TextView
    private lateinit var songList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var playButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF101827.toInt())
        }

        val title = TextView(this).apply {
            text = "♫  MY MUSIC"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 18)
        }
        root.addView(title)

        status = TextView(this).apply {
            text = "Your music, your way"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFB9C9E5.toInt())
            setPadding(8, 8, 8, 16)
        }
        root.addView(status)

        songList = ListView(this)
        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf<String>()
        )
        songList.adapter = adapter
        songList.setBackgroundColor(0xFF101827.toInt())
        songList.setOnItemClickListener { _, _, position, _ ->
            playSong(position)
        }

        root.addView(
            songList,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        fun addButton(label: String, action: () -> Unit): Button {
            val button = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            }
            controls.addView(
                button,
                LinearLayout.LayoutParams(0, 58, 1f)
            )
            return button
        }

        addButton("Previous") {
            if (songs.isNotEmpty()) {
                playSong(
                    if (currentIndex <= 0) songs.lastIndex
                    else currentIndex - 1
                )
            }
        }

        playButton = addButton("Play") {
            if (player?.isPlaying == true) {
                player?.pause()
                playButton.text = "Play"
            } else if (player != null) {
                player?.start()
                playButton.text = "Pause"
            } else if (songs.isNotEmpty()) {
                playSong(if (currentIndex >= 0) currentIndex else 0)
            }
        }

        addButton("Next") { nextSong() }
        root.addView(controls)

        val modes = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        fun modeButton(label: String, action: () -> Unit) {
            modes.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            })
        }

        modeButton("Play in order") {
            shuffle = false
            status.text = "Playing in order"
        }

        modeButton("Shuffle") {
            shuffle = true
            status.text = "Shuffle enabled"
        }

        root.addView(modes)
        setContentView(root)

        requestMusicPermission()
    }

    private fun requestMusicPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED) {
            scanMusic()
        } else {
            requestPermissions(arrayOf(permission), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )
        if (requestCode == 100 &&
            grantResults.firstOrNull() ==
            PackageManager.PERMISSION_GRANTED) {
            scanMusic()
        } else {
            status.text = "Allow music access to find your songs"
        }
    }

    private fun scanMusic() {
        songs.clear()

        val collection =
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )
            val titleColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )
            val artistColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(titleColumn)
                    ?: "Unknown title"
                val artist = cursor.getString(artistColumn)
                    ?: "Unknown artist"

                val uri =
                    android.content.ContentUris.withAppendedId(
                        collection, id
                    )

                songs.add(Song(id, name, artist, uri))
            }
        }

        adapter.clear()
        adapter.addAll(
            songs.map { "${it.title}\n${it.artist}" }
        )
        adapter.notifyDataSetChanged()
        status.text = "${songs.size} songs found"
    }

    private fun playSong(index: Int) {
        if (index !in songs.indices) return

        currentIndex = index
        val song = songs[index]

        player?.release()
        player = MediaPlayer()

        try {
            player?.setDataSource(this, song.uri)
            player?.setOnPreparedListener { mp ->
                mp.start()
                playButton.text = "Pause"
                status.text = "Now playing: ${song.title}"
            }
            player?.setOnCompletionListener {
                nextSong()
            }
            player?.setOnErrorListener { _, _, _ ->
                status.text = "Unable to play this song"
                true
            }
            status.text = "Loading: ${song.title}"
            player?.prepareAsync()
        } catch (e: Exception) {
            status.text = "Could not open this song"
            player?.release()
            player = null
        }
    }

    private fun nextSong() {
        if (songs.isEmpty()) return

        val next = if (shuffle && songs.size > 1) {
            var candidate: Int
            do {
                candidate = songs.indices.random()
            } while (candidate == currentIndex)
            candidate
        } else {
            (currentIndex + 1) % songs.size
        }

        playSong(next)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
