package io.github.nexalloy.keygen

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputType
import android.util.Base64
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var alias: EditText
    private lateinit var commonName: EditText
    private lateinit var validityYears: EditText
    private lateinit var generateButton: Button
    private lateinit var resultSection: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var filePassword: EditText
    private lateinit var filePasswordConfirm: EditText

    private var keystore: GeneratedKeystore? = null
    private var pendingExport: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps the passwords out of screenshots and the recent-apps preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this).apply {
            addView(content)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(scroll)

        content.addView(heading("NexAlloy signing key"))
        content.addView(text(
            "Generates an RSA 4096 app signing key, the same as keytool -genkeypair, " +
                "and saves it to Bitwarden. Nothing leaves the phone unless you save or copy it."
        ))
        alias = content.field("Key alias (KEYSTORE_ALIAS)", "nexalloynext")
        commonName = content.field("Certificate name", "NexAlloyNext")
        validityYears = content.field("Valid for (years)", "100", InputType.TYPE_CLASS_NUMBER)
        generateButton = content.button("Generate keystore") { generate() }

        resultSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        content.addView(resultSection)
        resultText = text("").apply { setTextIsSelectable(true) }
        resultSection.addView(resultText)

        resultSection.addView(heading("1. Back up to Bitwarden"))
        resultSection.addView(text(
            "Saves a password-protected Bitwarden export with one secure note: the notes are the " +
                "KEYSTORE secret, and the fields hold the passwords and alias. Import it at " +
                "vault.bitwarden.com → Tools → Import data, format \"Bitwarden (json)\", " +
                "then delete the file. The file password is only for this import."
        ))
        filePassword = resultSection.field(
            "File password", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        filePasswordConfirm = resultSection.field(
            "Repeat file password", "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        resultSection.button("Save Bitwarden export…") { saveBitwardenExport() }

        resultSection.addView(heading("2. Add the GitHub secrets"))
        resultSection.addView(text(
            "Settings → Secrets and variables → Actions → New repository secret. " +
                "Copy each value here or from the Bitwarden item."
        ))
        resultSection.button("Copy KEYSTORE") {
            keystore?.let { copy("KEYSTORE", Base64.encodeToString(it.bytes, Base64.NO_WRAP)) }
        }
        resultSection.button("Copy KEYSTORE_PASSWORD") { keystore?.let { copy("KEYSTORE_PASSWORD", it.password) } }
        resultSection.button("Copy KEYSTORE_ALIAS") { keystore?.let { copy("KEYSTORE_ALIAS", it.alias) } }
        resultSection.button("Copy KEYSTORE_ALIAS_PASSWORD") {
            keystore?.let { copy("KEYSTORE_ALIAS_PASSWORD", it.password) }
        }

        resultSection.addView(heading("Optional: keystore file"))
        resultSection.addView(text("For signing.properties builds on a computer."))
        resultSection.button("Save keystore file…") {
            keystore?.let { createDocument("${it.alias}.jks", "application/octet-stream", REQUEST_KEYSTORE) }
        }
    }

    private fun generate() {
        val alias = alias.text.toString().trim()
        val name = commonName.text.toString().trim().ifEmpty { alias }
        val years = validityYears.text.toString().toIntOrNull()
        if (alias.isEmpty() || years == null || years !in 1..1000) {
            toast("Enter an alias and a validity between 1 and 1000 years")
            return
        }
        val replace = keystore != null
        val run = {
            busy(generateButton, "Generating… (can take a few seconds)")
            thread {
                val result = runCatching {
                    KeystoreGenerator.generate(alias, KeystoreGenerator.randomPassword(), name, years)
                }
                runOnUiThread {
                    busyDone(generateButton, "Generate keystore")
                    result.onSuccess(::showResult).onFailure { error(it) }
                }
            }
        }
        if (replace) {
            AlertDialog.Builder(this)
                .setMessage("Replace the current key? Unless you saved it, it is lost for good.")
                .setPositiveButton("Replace") { _, _ -> run() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            run()
        }
    }

    private fun showResult(keystore: GeneratedKeystore) {
        this.keystore = keystore
        resultText.text = buildString {
            appendLine()
            appendLine("Alias: ${keystore.alias}")
            appendLine("Password (keystore and key): ${keystore.password}")
            appendLine("Valid until: ${keystore.certificate.notAfter}")
            appendLine("SHA-256: ${KeystoreGenerator.sha256Fingerprint(keystore.certificate)}")
            appendLine()
            append("The key exists only in this app until you save it, and is gone when the app closes.")
        }
        resultSection.visibility = View.VISIBLE
    }

    private fun saveBitwardenExport() {
        val password = filePassword.text.toString()
        when {
            password.length < 12 -> toast("Use a file password of at least 12 characters")
            password != filePasswordConfirm.text.toString() -> toast("The file passwords don't match")
            else -> createDocument(
                "${keystore!!.alias}-bitwarden.json", "application/json", REQUEST_BITWARDEN
            )
        }
    }

    @Deprecated("Activity result API of the platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        val keystore = keystore
        if (resultCode != RESULT_OK || uri == null || keystore == null) return
        when (requestCode) {
            REQUEST_KEYSTORE -> write(uri, keystore.bytes, "Keystore saved")
            REQUEST_BITWARDEN -> {
                val password = filePassword.text.toString()
                toast("Encrypting…")
                thread {
                    val json = runCatching {
                        BitwardenExport.encryptedJson(
                            BitwardenExport.plainJson(keystore, "NexAlloy signing key (${keystore.alias})"),
                            password,
                        )
                    }
                    runOnUiThread {
                        json.onSuccess { write(uri, it.toByteArray(), "Bitwarden export saved") }
                            .onFailure { error(it) }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun createDocument(fileName: String, mimeType: String, requestCode: Int) {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_TITLE, fileName),
            requestCode,
        )
    }

    private fun write(uri: android.net.Uri, bytes: ByteArray, message: String) {
        runCatching {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
        }.onSuccess { toast(message) }.onFailure { error(it) }
    }

    private fun copy(label: String, value: String) {
        val clip = ClipData.newPlainText(label, value).apply {
            // Hides the value from the clipboard preview and keyboard suggestions.
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        toast("$label copied")
    }

    private fun error(e: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("Failed")
            .setMessage(e.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun busy(button: Button, label: String) {
        button.isEnabled = false
        button.text = label
    }

    private fun busyDone(button: Button, label: String) {
        button.isEnabled = true
        button.text = label
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun heading(value: String) = TextView(this).apply {
        text = value
        textSize = 20f
        setPadding(0, dp(24), 0, dp(8))
    }

    private fun text(value: String) = TextView(this).apply {
        text = value
        setPadding(0, 0, 0, dp(8))
    }

    private fun LinearLayout.field(label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT) =
        EditText(this@MainActivity).also {
            addView(text(label).apply { setPadding(0, dp(8), 0, 0) })
            it.setText(value)
            it.inputType = type
            it.isSingleLine = true
            addView(it)
        }

    private fun LinearLayout.button(label: String, onClick: () -> Unit) =
        Button(this@MainActivity).also {
            it.text = label
            it.isAllCaps = false
            it.setOnClickListener { onClick() }
            addView(it)
        }

    companion object {
        private const val REQUEST_KEYSTORE = 1
        private const val REQUEST_BITWARDEN = 2
    }
}
