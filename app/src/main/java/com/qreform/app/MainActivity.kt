package com.qreform.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.qreform.app.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var logoBitmap: Bitmap? = null
    private var lastPdfFile: File? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.openInputStream(uri)?.use { stream ->
                logoBitmap = BitmapFactory.decodeStream(stream)
            }
            binding.imgLogoPreview.setImageBitmap(logoBitmap)
            binding.imgLogoPreview.visibility = android.view.View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickLogo.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnGenerate.setOnClickListener {
            generatePdf()
        }

        binding.btnView.setOnClickListener {
            lastPdfFile?.let { openPdf(it) }
        }

        binding.btnShare.setOnClickListener {
            lastPdfFile?.let { sharePdf(it) }
        }
    }

    private fun generatePdf() {
        val rawText = binding.editRawText.text.toString()
        if (rawText.isBlank()) {
            Toast.makeText(this, "Please paste the question paper text first.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.txtStatus.text = "Formatting..."

        try {
            val blocks = TextFormatter.format(rawText)
            val outDir = File(getExternalFilesDir(null), "pdfs")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, "formatted_question_paper_${System.currentTimeMillis()}.pdf")

            PdfBuilder.build(blocks, logoBitmap, outFile)

            lastPdfFile = outFile
            binding.txtStatus.text = "Done! Saved as ${outFile.name}"
            binding.rowActions.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            binding.txtStatus.text = "Something went wrong: ${e.message}"
        }
    }

    private fun getUriForFile(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun openPdf(file: File) {
        val uri = getUriForFile(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No PDF viewer app found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePdf(file: File) {
        val uri = getUriForFile(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share formatted question paper"))
    }
}