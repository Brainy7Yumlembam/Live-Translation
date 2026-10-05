package com.livetranslate.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.livetranslate.app.data.ConversationRepository
import com.livetranslate.app.data.IosFileStorageDriver
import com.livetranslate.app.speech.IOSSpeechRecognizer
import com.livetranslate.app.translation.IosTranslationEngine
import com.livetranslate.app.ui.App
import com.livetranslate.app.viewmodel.LiveTranslateViewModel
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val translationEngine = remember { IosTranslationEngine() }
    val speechRecognizer = remember { IOSSpeechRecognizer() }
    val storageDriver = remember { IosFileStorageDriver() }
    val conversationRepository = remember { ConversationRepository(storageDriver) }
    val viewModel = remember { 
        LiveTranslateViewModel(
            translationEngine = translationEngine,
            speechRecognizer = speechRecognizer,
            conversationRepository = conversationRepository
        ) 
    }
    App(viewModel = viewModel)
}
