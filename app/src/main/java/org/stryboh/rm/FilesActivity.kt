package org.stryboh.rm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

class FilesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.files_layout)

        val saveFileListButton: Button = findViewById(R.id.save_file_list_button)
        val removeTelegramButton: Button = findViewById(R.id.remove_telegram_button)
        val removeFilesButton: Button = findViewById(R.id.remove_files_button)
        val filesList: EditText = findViewById(R.id.editTextFilesList)
        val switchScreenButton: Button = findViewById(R.id.switch_screen_button_2)
        var filesIds = loadChatsFromFile()
        filesList.setText(filesIds.joinToString("\n"))

        saveFileListButton.setOnClickListener {
            val files = filesList.text.toString()
            if (files.isNotEmpty()) {
                try {
                    val outputStream: FileOutputStream = openFileOutput("files_list.txt", Context.MODE_PRIVATE)
                    outputStream.write(files.toByteArray())
                    outputStream.close()
                    // Optionally, display a success message to the user
                } catch (e: Exception) {
                    // Handle exceptions (e.g., file not found, permission denied)
                    e.printStackTrace()
                    // Optionally, display an error message to the user
                }
            }
        }
        removeFilesButton.setOnClickListener {
            removeFiles()
        }

        removeTelegramButton.setOnClickListener {
            Log.d("FilesActivity", "Remove telegram button clicked")
            Runtime.getRuntime().exec("su -c pm uninstall --user 0 org.telegram.messenger")
            Toast.makeText(this, "Telegram deleted successfully.", Toast.LENGTH_SHORT).show()
        }

        switchScreenButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
    private fun removeFiles() {
        val removalList = File(filesDir, "files_list.txt")
        try {
            if (removalList.exists()) {
                FileInputStream(removalList).use { fis ->
                    InputStreamReader(fis).use { isr ->
                        isr.buffered().forEachLine { line ->
                            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -rf ${line.trim()}"))
                            process.waitFor()
                        }
                    }
                }
                Toast.makeText(this, "Deleted successfully.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Save file first", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "An error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadChatsFromFile(): ArrayList<String> {
        return try {
            val inputStream: FileInputStream = openFileInput("files_list.txt")
            val chats = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            val chatList = chats.trim().splitToSequence("\n").map { it.trim() }.toCollection(ArrayList())
            chatList
        } catch (e: Exception) {
            // Handle exceptions (e.g., file not found)
            e.printStackTrace()
            ArrayList() // Return an empty ArrayList in case of an exception
        }
    }
}