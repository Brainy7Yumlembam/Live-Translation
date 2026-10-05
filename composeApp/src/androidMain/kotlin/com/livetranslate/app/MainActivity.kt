package com.livetranslate.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.livetranslate.app.data.AndroidFileStorageDriver
import com.livetranslate.app.data.ConversationRepository
import com.livetranslate.app.speech.AndroidSpeechRecognizer
import com.livetranslate.app.translation.AndroidTranslationEngine
import com.livetranslate.app.ui.App
import com.livetranslate.app.viewmodel.LiveTranslateViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = this
            val translationEngine = remember { AndroidTranslationEngine() }
            val speechRecognizer = remember { AndroidSpeechRecognizer(context) }
            val storageDriver = remember { AndroidFileStorageDriver(context) }
            val conversationRepository = remember { ConversationRepository(storageDriver) }
            val viewModel = remember { 
                LiveTranslateViewModel(
                    translationEngine = translationEngine,
                    speechRecognizer = speechRecognizer,
                    conversationRepository = conversationRepository
                ) 
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        viewModel.onPause()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    // Handled gracefully in view model / state
                }
            }

            LaunchedEffect(Unit) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            App(viewModel = viewModel)
        }
    }
}
