package week11.st991695851.finalproject

import android.R.id.message
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import week11.st991695851.finalproject.data.KnowledgeRepository
import week11.st991695851.finalproject.ui.theme.InsightPrimaryButton
import week11.st991695851.finalproject.ui.theme.InsightSecondaryButton
import week11.st991695851.finalproject.ui.theme.InsightTextField
import week11.st991695851.finalproject.util.AuthenticatedScreen
import week11.st991695851.finalproject.util.UiState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import coil.compose.AsyncImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InsightApp()
                }
            }
        }
    }
}


class MainViewModel : ViewModel() {
    private val repository = KnowledgeRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _notes = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val notes: StateFlow<List<Map<String, Any>>> = _notes


    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Navigation State (Requirement 4b)
    private val _currentScreen = MutableStateFlow<AuthenticatedScreen>(AuthenticatedScreen.Scanner)
    val currentScreen: StateFlow<AuthenticatedScreen> = _currentScreen

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri

    fun onImageSelected(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun scanImage(context: Context, uri: Uri, onResult: (String, String) -> Unit) {
        _isLoading.value = true
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try{
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    _isLoading.value = false

                    val lines = visionText.text.lines().filter{it.isNotBlank()}
                    val title = lines.firstOrNull() ?: "Scanned Document"
                    val content = visionText.text
                    onResult(title, content)
                }
                .addOnFailureListener {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to recognize text"
                }

        } catch (e: Exception){
            _isLoading.value = false
            _errorMessage.value = "Failed to process image"
        }

    }
    fun clearError(){
        _errorMessage.value = null
    }

    init {
        checkAuthStatus()
        observeNotes()
    }

    private fun observeNotes() {
        viewModelScope.launch {

            _notes.value = emptyList()
            repository.getNotesFlow().collect { list ->
                _notes.value = list
            }
        }
    }

    fun saveScan(title: String, content: String, category: String) {
        if (title.isBlank() || content.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveNote(title, content, category)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun checkAuthStatus() {
        if (auth.currentUser != null) {
            _uiState.value = UiState.Authenticated
            observeNotes()
        } else {
            _uiState.value = UiState.AuthRequired
            _notes.value = emptyList()
        }
    }


    fun login(email: String, pass: String) {
        _errorMessage.value = null
        if (email.isBlank() || pass.isBlank()) {
            _errorMessage.value = "Email and Password cannot be empty"
            return
        }

        _isLoading.value = true
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                _isLoading.value = false
                _uiState.value = UiState.Authenticated
                _errorMessage.value = null
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                val customMessage = when {
                    e.message?.contains("auth credential") == true -> "Invalid Email/Password"
                    else -> "Login Failed"
                }
                _errorMessage.value = customMessage
            }
    }

    fun navigateTo(screen: AuthenticatedScreen) {
        _currentScreen.value = screen
    }

    fun logout() {
        repository.logout()
        _uiState.value = UiState.AuthRequired
        _notes.value = emptyList()
    }

    fun register(email: String, pass: String, confirmPass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _errorMessage.value = "All fields are required"
            return
        }
        if (pass != confirmPass) {
            _errorMessage.value = "Passwords do not match"
            return
        }

        _isLoading.value = true
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                _isLoading.value = false
                auth.signOut()
                _errorMessage.value = "Registration Successful. Please log in."
                _uiState.value = UiState.AuthRequired
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = e.localizedMessage ?: "Registration Failed"
            }
    }
}

@Composable

fun InsightApp(vm: MainViewModel = viewModel()) {
    // Collect states from ViewModel
    val state by vm.uiState.collectAsState()
    val currentScreen by vm.currentScreen.collectAsState()


    when (state) {
        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.AuthRequired -> {
            // Local state to toggle between Login and Register screens
            var showRegister by remember { mutableStateOf(false) }

            if (showRegister) {
                RegisterScreen(vm, onBackToLogin = {
                    vm.clearError()
                    showRegister = false })
            } else {
                // Pass the navigation trigger to your Login screen
                LoginScreen(vm, onNavigateToRegister = {
                    vm.clearError()
                    showRegister = true })
            }
        }
        is UiState.Authenticated -> {

            Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = currentScreen is AuthenticatedScreen.Scanner,
                            onClick = { vm.navigateTo(AuthenticatedScreen.Scanner) },
                            icon = { Text("📷") },
                            label = { Text("Scanner") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is AuthenticatedScreen.Library,
                            onClick = { vm.navigateTo(AuthenticatedScreen.Library) },
                            icon = { Text("📚") },
                            label = { Text("Library") }
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)) {
                    when (currentScreen) {
                        is AuthenticatedScreen.Scanner -> MainAppContent(vm)
                        is AuthenticatedScreen.Library -> LibraryScreen(vm)
                    }
                }
            }
        }
    }
}


@Composable
fun LoginScreen(vm: MainViewModel, onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }


    val isLoading by vm.isLoading.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Insight",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = "Document Scanner & Manager",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(40.dp))


        InsightTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email"
        )

        Spacer(modifier = Modifier.height(16.dp))

        InsightTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            isPassword = true
        )

        errorMessage?. let { message ->
            val isSuccess = message.contains("Registration Successful")
            Text(
                text = message,
                color = if (isSuccess) Color(0xFF4CAF50) else Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Action Buttons ---
        // Requirement 4a: Connect to Firebase Authentication via vm.login
        InsightPrimaryButton(
            text = "Login",
            isLoading = isLoading,
            onClick = { vm.login(email, password) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        InsightSecondaryButton(
            text = "Create Account",
            onClick = onNavigateToRegister
        )

    }
}


@Composable
fun MainAppContent(vm: MainViewModel) {
    val notes by vm.notes.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val context = LocalContext.current
    val selectedImageUri by vm.selectedImageUri.collectAsState()

    var docTitle by remember { mutableStateOf("") }
    var docText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ){
        uri: Uri? ->
        vm.onImageSelected(uri)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Document Scanner", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Upload and scan documents", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color(0xFFF5F5F5), shape = RoundedCornerShape(12.dp))
                .border(1.dp, Color.LightGray, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null)   {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = "Selected Image",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📷", fontSize = 40.sp)
                Text("No Image Selected", fontWeight = FontWeight.SemiBold)
                Text("Select an image to scan", fontSize = 12.sp, color = Color.Gray)
            }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                InsightSecondaryButton(text = "Upload Image", onClick = { launcher.launch("image/*") })
            }
            Box(modifier = Modifier.weight(1f)) {

                Button(
                    onClick = {
                        selectedImageUri?.let { uri ->
                            vm.scanImage(context, uri) { title, text ->
                                docTitle = title
                                docText = text
                            }
                        }
                    },
                    enabled = !isLoading && selectedImageUri != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9FA8DA))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    }else {
                        Text("Scan")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

    /*    Text("Document Title", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        InsightTextField(
            value = docTitle,
            onValueChange = { docTitle = it },
            label = "Auto-fills after scanning"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Category", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        InsightTextField(
            value = category,
            onValueChange = { category = it },
            label = "e.g., History, Science, Literature"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Scanned Text", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        InsightTextField(
            value = docText,
            onValueChange = { docText = it },
            label = "Auto-fills after scanning"
        )*/

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                vm.saveScan(docTitle, docText, category)
                docTitle = ""
                docText = ""
                category = ""
                vm.onImageSelected(null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA5D6A7))
        ) {
            Text("Save to Vault", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(80.dp))

    /*    InsightSecondaryButton(
            text = "Logout",
            onClick = { vm.logout() }
        )*/
    }


}

@Composable
fun RegisterScreen(vm: MainViewModel, onBackToLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val isLoading by vm.isLoading.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()

    androidx.compose.runtime.LaunchedEffect(errorMessage){
        if (errorMessage?.startsWith("Registration Successful") == true){
            kotlinx.coroutines.delay(2000)
            onBackToLogin()
        }

    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Join the Insight Knowledge Vault", fontSize = 14.sp, color = Color.Gray)

        errorMessage?. let { message ->
            val isSuccess = message.contains("Registration Successful")
            Text(
                text = message,
                color = if (isSuccess) Color(0xFF4CAF50) else Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

        }

        Spacer(modifier = Modifier.height(32.dp))

        InsightTextField(value = email, onValueChange = { email = it }, label = "Email")

        Spacer(modifier = Modifier.height(16.dp))

        InsightTextField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true)

        Spacer(modifier = Modifier.height(16.dp))

        InsightTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = "Confirm Password", isPassword = true)


        Spacer(modifier = Modifier.height(32.dp))

        InsightPrimaryButton(
            text = "Register",
            isLoading = isLoading,
            onClick = { vm.register(email, password, confirmPassword) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        InsightSecondaryButton(
            text = "Back to Login",
            onClick = onBackToLogin
        )
    }
}

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val notes by vm.notes.collectAsState()

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Library", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Your saved knowledge", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(notes) { note ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(note["title"]?.toString() ?: "Untitled", fontWeight = FontWeight.Bold)
                        Text(
                            note["category"]?.toString() ?: "General",
                            color = Color.Blue,
                            fontSize = 12.sp
                        )
                        Text(note["content"]?.toString() ?: "", maxLines = 2, fontSize = 14.sp)

                    }
                }
            }
        }
    }
}



