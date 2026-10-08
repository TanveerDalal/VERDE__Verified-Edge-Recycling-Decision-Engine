package com.example.verde.ui.ask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.example.verde.R
import com.example.verde.data.WasteKnowledge
import com.example.verde.databinding.ActivityAskBinding
import com.example.verde.databinding.ItemMessageBotBinding
import com.example.verde.databinding.ItemMessageUserBinding
import com.example.verde.ui.common.Tab
import com.example.verde.ui.common.WhatsApp
import com.example.verde.ui.common.setUpBottomNav
import com.example.verde.ui.common.setUpDarkScreen
import com.example.verde.ui.common.openTab
import java.util.Date

/** Ask tab: a WhatsApp-style chat. Answers come from WasteKnowledge (later: Llama on the Pi). */
class AskActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_QUESTION = "question"
        private const val REPLY_DELAY_MS = 900L

        /** Opens the chat and sends [question] straight away (used by "Ask Verde" on the Result screen). */
        fun newIntent(context: Context, question: String): Intent =
            Intent(context, AskActivity::class.java)
                .putExtra(EXTRA_QUESTION, question)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }

    private lateinit var binding: ActivityAskBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDarkScreen(binding.root)
        setUpBottomNav(binding.bottomNav, Tab.ASK)

        addBotMessage(getString(R.string.ask_welcome))

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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
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

    /** Shows the user's message, then "typing…", then Verde's answer. */
    private fun send(text: String) {
        addUserMessage(text)
        binding.statusText.setText(R.string.ask_typing)
        val typing = addBotMessage(getString(R.string.ask_typing_bubble), showTime = false)

        handler.postDelayed({
            binding.messages.removeView(typing)
            binding.statusText.setText(R.string.ask_online)
            addBotMessage(WasteKnowledge.answer(this, text))    // TODO: Llama on the Pi
        }, REPLY_DELAY_MS)
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
