package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.RaktaSewaRepository
import com.example.ui.RaktaSewaApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RaktaSewaViewModel
import com.example.viewmodel.RaktaSewaViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Room persistence layer
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = RaktaSewaRepository(database.appDao())
        
        // Instantiate the ViewModel
        val viewModel: RaktaSewaViewModel by viewModels {
            RaktaSewaViewModelFactory(application, repository)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RaktaSewaApp(viewModel)
                }
            }
        }
    }
}
