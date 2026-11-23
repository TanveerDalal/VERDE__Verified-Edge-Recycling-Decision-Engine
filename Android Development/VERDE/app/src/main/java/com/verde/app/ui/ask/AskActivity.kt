package com.verde.app.ui.ask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.verde.app.R
import com.verde.app.core.LlamaClient
import com.verde.app.core.LlamaServer
import com.verde.app.core.PiSettings
import com.verde.app.core.WasteKnowledge
import com.verde.app.databinding.ActivityAskBinding
import com.verde.app.databinding.ItemMessageBotBinding
import com.verde.app.databinding.ItemMessageUserBinding
import com.verde.app.ui.common.Tab
import com.verde.app.ui.common.WhatsApp
import com.verde.app.ui.common.setUpBottomNav
import com.verde.app.ui.common.setUpDarkScreen
import com.verde.app.ui.common.openTab
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Ask tab: a WhatsApp-style chat. Answers come from Llama on this phone (ExecuTorch),
 * then Llama on the Pi, then the built-in WasteKnowledge answers.
 */
class AskActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_QUESTION = "question"

        /** Opens the chat and sends [question] straight away (used by "Ask Verde" on the Result screen). */
        fun newIntent(context: Context, question: String): Intent =
            Intent(context, AskActivity::class.java)
                .putExtra(EXTRA_QUESTION, question)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }

    private lateinit var binding: ActivityAskBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)
        setUpBottomNav(binding.bottomNav, Tab.ASK)

        // Lets the WhatsApp server use this phone's Llama (port 5001, see LlamaServer)
        com.verde.app.core.LlamaService.start(this)

        addBotMessage(getString(R.string.ask_welcome))

        // Switch: Llama on this phone or on the Pi (tap to change)
        showEngine()
        binding.chipEngine.setOnClickListener {
            PiSettings.setLlamaOnPhone(this, !PiSettings.llamaOnPhone(this))
            showEngine()
        }

        binding.sendButton.setOnClickListener { sendTyped() }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
        }
        binding.chipWhatsApp.setOnClickListener {
            WhatsApp.openChat(this, getString(R.string.whatsapp_hello))   // continue in real WhatsApp
        }
        listOf(binding.chipBatteries, binding.chipGlass, binding.chipCoffee).forEach { chip ->
            chip.setOnClickListener { send(chip.text.toString()) }
        }
        // Back arrow and camera both go to the Scan tab
        binding.backButton.setOnClickListener { openTab(Tab.SCAN) }
        binding.cameraButton.setOnClickListener { openTab(Tab.SCAN) }

        sendQuestionFrom(intent)
    }

    /** Called when the chat is already open and "Ask Verde" is tapped again. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sendQuestionFrom(intent)
    }

    private fun sendQuestionFrom(intent: Intent) {
        val question = intent.getStringExtra(EXTRA_QUESTION) ?: return
        intent.removeExtra(EXTRA_QUESTION)           // don't send it twice
        send(question)
    }

    private fun sendTyped() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isEmpty()) return
        binding.messageInput.text.clear()
        send(text)
    }

    /** Shows the user's message, then "typing…" while Llama thinks, then Verde's answer. */
    private fun send(text: String) {
        addUserMessage(text)
        binding.statusText.setText(R.string.ask_typing)
        val typing = addBotMessage(getString(R.string.ask_typing_bubble), showTime = false)

        lifecycleScope.launch {
            val llama = LlamaClient.ask(this@AskActivity, text)          // phone Llama → Pi Llama → null
            binding.messages.removeView(typing)
            binding.statusText.setText(R.string.ask_online)
            addBotMessage(llama ?: (getString(R.string.llama_no_answer,
                getString(if (PiSettings.llamaOnPhone(this@AskActivity)) R.string.engine_phone else R.string.engine_pi)) +
                    "\n\n" + WasteKnowledge.answer(this@AskActivity, text)))
        }
    }

    private fun showEngine() {
        binding.chipEngine.setText(
            if (PiSettings.llamaOnPhone(this)) R.string.chip_engine_phone else R.string.chip_engine_pi)
    }

    private fun addUserMessage(text: String) {
        ItemMessageUserBinding.inflate(layoutInflater, binding.messages, true).apply {
            messageText.text = text
            messageTime.text = now()
        }
        scrollToBottom()
    }

    private fun addBotMessage(text: String, showTime: Boolean = true): View {
        val bubble = ItemMessageBotBinding.inflate(layoutInflater, binding.messages, true).apply {
            messageText.text = text
            messageTime.text = if (showTime) now() else ""
        }
        scrollToBottom()
        return bubble.root
    }

    private fun now(): String = DateFormat.getTimeFormat(this).format(Date())

    private fun scrollToBottom() {
        binding.chatScroll.post { binding.chatScroll.smoothScrollTo(0, binding.messages.height) }
    }
}