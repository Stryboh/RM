package org.stryboh.rm

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class MainActivity : AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        if (client != null) { // Check if the client is initialized
            if (haveAuthorization) {
                //Log.d("TDLib", "Already authorized, setting TDLib parameters")
                setTdlibParameters()
            } else {
                //Log.d("TDLib", "Not authorized, getting authorization state")
                client?.send(TdApi.GetAuthorizationState(), AuthorizationRequestHandler())
            }
        }
    }

    var client: Client? = null
    private var haveAuthorization = false
    private val authorizationLock: Lock = ReentrantLock()
    private val gotAuthorization: Condition = authorizationLock.newCondition()

    // TDLib API credentials (replace with actual values)
    private val apiId: Int = api_id // Replace with your actual api_id
    private val apiHash: String = "api_hash" // Replace with your actual api_hash

    private lateinit var phoneNumberLauncher: ActivityResultLauncher<Intent>
    private lateinit var codeLauncher: ActivityResultLauncher<Intent>
    private lateinit var passwordLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize launchers for authentication
        initializeLaunchers()

        // Initialize TDLib client
        initializeClient()

        setContentView(R.layout.layout)

        val removeChatsButton: Button = findViewById(R.id.remove_chats_button)
        val saveButton: Button = findViewById(R.id.save_chats_list_button)
        val chatsList: EditText = findViewById(R.id.editTextChatsList)
        val switchScreenButton: Button = findViewById(R.id.switch_screen_button_1)
        var chatIds = loadChatsFromFile()
        chatsList.setText(chatIds.joinToString("\n"))  // Join with newline for better readability

        removeChatsButton.setOnClickListener {
            chatIds = loadChatsFromFile() // Reload chat IDs from file
            //Log.d("TDLib", "Starting removeChats function")
            removeChats(chatIds)
        }

        switchScreenButton.setOnClickListener {
            val intent = Intent(this, FilesActivity::class.java)
            startActivity(intent)
        }

        saveButton.setOnClickListener {
            val chats = chatsList.text.toString()
            if (chats.isNotEmpty()) {
                try {
                    val outputStream: FileOutputStream = openFileOutput("chats_list.txt", Context.MODE_PRIVATE)
                    outputStream.write(chats.toByteArray())
                    outputStream.close()
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                    // Optionally, display a success message to the user
                } catch (e: Exception) {
                    // Handle exceptions (e.g., file not found, permission denied)
                    e.printStackTrace()
                    // Optionally, display an error message to the user
                }
            }
        }
    }

    private fun initializeLaunchers() {
        phoneNumberLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val phoneNumber = result.data?.getStringExtra("phoneNumber")
                phoneNumber?.let {
                    //Log.d("TDLib", "Phone number entered: $it")
                    client?.send(TdApi.SetAuthenticationPhoneNumber(it, null), AuthorizationRequestHandler())
                }
            }
        }

        codeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val code = result.data?.getStringExtra("code") // ? . getStringExtra( " authCode" )
                code?.let {
                    //Log.d("TDLib", "Authentication code entered: $code, $it")
                    //client?.send()
                    client?.send(TdApi.CheckAuthenticationCode(code), AuthorizationRequestHandler())
                } ?: run {
                    //Log.e("TDLib", "Authentication code is null")
                }
            } else {
                //Log.e("TDLib", "Authentication code activity result is not OK")
            }
        }

        passwordLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val password = result.data?.getStringExtra("password")
                password?.let {
                    //Log.d("TDLib", "Password entered: $it")
                    client?.send(TdApi.CheckAuthenticationPassword(password), AuthorizationRequestHandler())
                } ?: run {
                    //Log.e("TDLib", "Password is null")
                }
            } else {
                //Log.e("TDLib", "Password activity result is not OK")
            }
        }
    }

    private fun initializeClient() {
        setTdlibParameters()
        client = Client.create({ result ->
            if (result is TdApi.UpdateAuthorizationState) {
                //Log.d("TDLib", "Authorization state update: ${result.authorizationState} ")
                handleAuthorizationState(result.authorizationState)
            } else {
                //Log.d("TDLib", "Received unexpected update: $result")
            }
        }, null, null)

        // Set TDLib parameters after initializing the client
        //setTdlibParameters()
    }

    private fun handleAuthorizationState(authorizationState: TdApi.AuthorizationState?) {
        when (authorizationState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                //Log.d("TDLib", "AuthorizationState: WaitTdlibParameters")
                Toast.makeText(this, "AuthorizationState: WaitTdlibParameters", Toast.LENGTH_SHORT).show()
                setTdlibParameters() // Ensure TDLib parameters are set properly.
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                //Log.d("TDLib", "AuthorizationState: WaitPhoneNumber")
                //Log.d("TDLib", "Now loading showPhoneNumberInputDialog")
                showPhoneNumberInputDialog()
                //showPhoneNumberActivity() // Launch phone number input activity.
            }
            is TdApi.AuthorizationStateWaitCode -> {
                //Log.d("TDLib", "AuthorizationState: WaitCode")
                //if falls - change dialog to activity
                showCodeInputDialog() // Launch code input activity.
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                //Log.d("TDLib", "AuthorizationState: WaitPassword")
                //if falls - change dialog to activity
                showPasswordInputDialog() // Launch password input activity.
            }
            is TdApi.AuthorizationStateReady -> {
                //Log.d("TDLib", "AuthorizationState: Ready")
                haveAuthorization = true
                authorizationLock.lock()
                try {
                    gotAuthorization.signal()
                } finally {
                    authorizationLock.unlock()
                }
            }
            else -> {
                //Log.e("TDLib", "Unsupported authorization state: $authorizationState")
            }
        }
    }

    private inner class AuthorizationRequestHandler : Client.ResultHandler {
        override fun onResult(result: TdApi.Object?) {
            when (result) {
                is TdApi.Error -> {
                    //Log.e("TDLib", "Authorization error: ${result.message}")
                    Toast.makeText(this@MainActivity, "Authorization error: ${result.message}", Toast.LENGTH_SHORT).show()
                    Toast.makeText(this@MainActivity, "Trying to set TdLib parameters", Toast.LENGTH_SHORT).show()
                    setTdlibParameters()
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    //Log.d("TDLib", "AuthorizationState: WaitPhoneNumber")
                    showPhoneNumberInputDialog()  // Transition to password input
                }
                is TdApi.AuthorizationStateWaitCode -> {
                    //Log.d("TDLib", "AuthorizationState: WaitCode")
                    showCodeInputDialog()  // Transition to password input
                }
                is TdApi.AuthorizationStateWaitPassword -> {
                    //Log.d("TDLib", "AuthorizationState: WaitPassword")
                    showPasswordInputDialog()  // Transition to password input
                }
                is TdApi.AuthorizationStateReady -> {
                    //Log.d("TDLib", "AuthorizationState: Ready")
                    haveAuthorization = true
                }
                is TdApi.Ok -> {
                    //Log.d("TDLib", "Authorization action successful")
                }
                else -> {
                    //Log.e("TDLib", "Unexpected authorization result: $result")
                    setTdlibParameters()
                }
            }
        }
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------
    //Auth
    //--------------------------------------------------------------------------------------------------------------------------------------------
    private fun setTdlibParameters() {
        val encryptionKey = loadEncryptionKey() ?: generateAndSaveEncryptionKey() // Load or generate key
        val parameters = TdApi.SetTdlibParameters().apply {
            useTestDc = false // Production environment
            databaseDirectory = filesDir.absolutePath + "/tdlib"
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = true
            apiId = this@MainActivity.apiId
            apiHash = this@MainActivity.apiHash
            systemLanguageCode = "en"
            deviceModel = android.os.Build.MODEL
            systemVersion = android.os.Build.VERSION.RELEASE
            applicationVersion = "1.0"
            databaseEncryptionKey = encryptionKey
        }

        client?.send(parameters) { result ->
            if (result is TdApi.Ok) {
                //Log.d("TDLib", "TDLib parameters set successfully.")
                Toast.makeText(this@MainActivity, "TDLib parameters set successfully", Toast.LENGTH_SHORT).show()

            } else {
                //Log.e("TDLib", "Failed to set TDLib parameters: $result")
                Toast.makeText(this@MainActivity, "Failed to set TDLib parameters: $result", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPhoneNumberInputDialog() {
        //Log.d("TDLib", "Now loading showPhoneNumberInputDialog")

        runOnUiThread { // Run the dialog code on the main thread
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Enter Phone Number")

            val input = EditText(this)
            builder.setView(input)

            builder.setPositiveButton("OK") { dialog, _ ->
                val phoneNumber = input.text.toString()
                if (phoneNumber.isNotEmpty()) {
                    client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), AuthorizationRequestHandler())
                }
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            //Log.d("TDLib", "Now showing builder")

            builder.show()
            //Log.d("TDLib", "Was it shown?")
        }
    }

    private fun showCodeInputDialog() {
        //Log.d("TDLib", "Now loading showCodeInputDialog")

        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Enter Code")

            val input = EditText(this)
            builder.setView(input)

            builder.setPositiveButton("OK") { dialog, _ ->
                val code = input.text.toString()
                if (code.isNotEmpty()) {
                    client?.send(TdApi.CheckAuthenticationCode(code), AuthorizationRequestHandler())
                }
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }

            //Log.d("TDLib", "Now showing builder")
            builder.show()
            //Log.d("TDLib", "Was it shown?")
        }
    }

    private fun showPasswordInputDialog() {
        //Log.d("TDLib", "Now loading showPasswordInputDialog")

        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Enter Password")

            val input = EditText(this)
            builder.setView(input)

            builder.setPositiveButton("OK") { dialog, _ ->
                val password = input.text.toString()
                if (password.isNotEmpty()) {
                    client?.send(TdApi.CheckAuthenticationPassword(password), AuthorizationRequestHandler())
                }
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }

            //Log.d("TDLib", "Now showing builder")
            builder.show()
            //Log.d("TDLib", "Was it shown?")
        }
    }
    //--------------------------------------------------------------------------------------------------------------------------------------------

    //Encryption needed to authenticate
    //--------------------------------------------------------------------------------------------------------------------------------------------
    private fun generateAndSaveEncryptionKey(): ByteArray {
        val key = ByteArray(32) // 256-bit key
        SecureRandom().nextBytes(key)
        saveEncryptionKey(key)
        return key
    }

    private fun saveEncryptionKey(key: ByteArray) {
        val sharedPreferences = getSharedPreferences("tdlib_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("encryption_key", Base64.encodeToString(key, Base64.DEFAULT)).apply()
    }

    private fun loadEncryptionKey(): ByteArray? {
        val sharedPreferences = getSharedPreferences("tdlib_prefs", Context.MODE_PRIVATE)
        val keyString = sharedPreferences.getString("encryption_key", null)
        return keyString?.let { Base64.decode(it, Base64.DEFAULT) }
    }
    //--------------------------------------------------------------------------------------------------------------------------------------------

    //Chats processing
    //--------------------------------------------------------------------------------------------------------------------------------------------
    private fun loadChatsFromFile(): ArrayList<String> {
        return try {
            val inputStream: FileInputStream = openFileInput("chats_list.txt")
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

    private fun removeChats(chatNames: ArrayList<String>) {
        try {
            Toast.makeText(this@MainActivity, "Setting TdLib Parameters", Toast.LENGTH_SHORT).show()
            setTdlibParameters()
        }
        finally{

        }
        Log.d("TDLib", "Starting removeChats function")
        if (chatNames.isNotEmpty()) {
            for (chatName in chatNames) {
                Log.d("TDLib", "Finding chat ID for: $chatName")
                getChatIdByName(chatName) { chatId ->
                    if (chatId != 0L) {
                        Log.d("TDLib", "Removing chat with ID: $chatId")
                        client?.send(TdApi.LeaveChat(chatId)) { result ->
                            when (result?.constructor) {
                                TdApi.Ok.CONSTRUCTOR -> {
                                    //Log.d("TDLib", "Left chat $chatId successfully")
                                    Toast.makeText(this@MainActivity, "Left chat $chatId successfully", Toast.LENGTH_SHORT).show()
                                    // Handle success (e.g., update UI)
                                }
                                TdApi.Error.CONSTRUCTOR -> {
                                    Toast.makeText(this@MainActivity, "Error leaving chat $chatId: ${(result as TdApi.Error).message} ", Toast.LENGTH_SHORT).show()

                                    //Log.e("TDLib", "Error leaving chat $chatId: ${(result as TdApi.Error).message} ")
                                    // Handle error (e.g., display error message)
                                }
                                else -> {
                                    //Log.e("TDLib", "Unexpected result leaving chat $chatId: $result")
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Chat not found: $chatName", Toast.LENGTH_SHORT).show()
                        //Log.e("TDLib", "Chat not found: $chatName")
                    }
                }
            }
        } else {
            Toast.makeText(this@MainActivity, "No chats to remove", Toast.LENGTH_SHORT).show()
            //Log.d("TDLib", "No chats to remove")
        }
    }

    private fun getChatIdByName(chatName: String, callback: (Long) -> Unit) {
        client?.send(TdApi.SearchChatsOnServer(chatName, 1)) { result ->
            when (result?.constructor) {
                TdApi.Chats.CONSTRUCTOR -> {
                    val chats = result as TdApi.Chats
                    if (chats.chatIds.isNotEmpty()) {
                        callback(chats.chatIds[0])
                    } else {
                        callback(0) // Chat not found
                    }
                }
                TdApi.Error.CONSTRUCTOR -> {
                    //Log.e("TDLib", "Error searching for chat: ${(result as TdApi.Error).message} ")
                    callback(0) // Chat not found
                }
                else -> {
                    //Log.e("TDLib", "Unexpected result searching for chat: $result")
                    callback(0) // Chat not found
                }
            }
        }
    }
    //--------------------------------------------------------------------------------------------------------------------------------------------
}