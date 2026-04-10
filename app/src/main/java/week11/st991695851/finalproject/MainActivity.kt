package week11.st991695851.finalproject

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign


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

    private val db = Firebase.firestore

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

                    val title = ""
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

    fun deleteNote(noteId: String){
        val userId = auth.currentUser?.uid ?: return
        db.collection("knowledge_vault").document(noteId)
            .delete()
            .addOnSuccessListener {
                _errorMessage.value = "Note deleted successfully"
            }
            .addOnFailureListener {
                _errorMessage.value = "Failed to delete note: ${it.message}"
            }
    }

    fun viewNoteDetail(noteId: String){
        _currentScreen.value = AuthenticatedScreen.ViewNote(noteId)
    }


    fun saveScan(title: String, content: String, category: String, onComplete: () -> Unit) {
        if (title.isBlank() || content.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveNote(title, content, category)
                onComplete()
                _errorMessage.value = "Saved successfully"
            } catch(e: Exception){
                _errorMessage.value = "Failed to save note"
            }
            finally {
                _isLoading.value = false
              //  navigateTo(AuthenticatedScreen.Library)
            }
        }
    }

    private fun checkAuthStatus() {
        val currentUser = auth.currentUser
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
                observeNotes()
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
                    when (val screen = currentScreen) {
                        is AuthenticatedScreen.Scanner -> MainAppContent(vm)
                        is AuthenticatedScreen.Library -> LibraryScreen(vm)
                        is AuthenticatedScreen.ViewNote -> NoteDetailScreen(screen.noteId, vm)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically

        ){
            Text("Document Scanner", fontSize = 24.sp, fontWeight = FontWeight.Bold)
          //  Text("Upload and scan documents", fontSize = 14.sp, color = Color.Gray)

            IconButton(onClick = { vm.logout() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Logout",
                    tint = Color.Gray

                )
            }

        }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
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
                            vm.scanImage(context, uri) { _, text ->

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

       Text("Document Title", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        InsightTextField(
            value = docTitle,
            onValueChange = { docTitle = it },
            label = "Enter document title"
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
        )

        Spacer(modifier = Modifier.height(32.dp))

        

        Button(
            onClick = {
                vm.saveScan(docTitle, docText, category) {
                    docTitle = ""
                    docText = ""
                    category = ""
                    vm.onImageSelected(null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA5D6A7))
        ) {
            Text("Save to Vault", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(120.dp))

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
fun NoteDetailScreen(noteId: String, vm: MainViewModel) {
    val notes by vm.notes.collectAsState()
    val note = notes.find {it["id"] == noteId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {

                TextButton(
                    onClick = { vm.navigateTo(AuthenticatedScreen.Library) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Back to Library",
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (note == null) {
                    Text("Error (ID: $noteId)", color = Color.Red)
                } else {

                    Text(
                        text = note["title"]?.toString() ?: "Untitled",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFE8EAF6),
                            shape = RoundedCornerShape(4.dp)
                        ) {


                            Text(
                                text = note["category"]?.toString() ?: "General",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3F51B5)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Thursday, March 19, 2026",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }


            Spacer(modifier = Modifier.height(32.dp))

        if (note != null){
        Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📄", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Extracted Text",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = note["content"]?.toString() ?: "No content found.",
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            color = Color(0xFF424242)

                        )
                    }
                }
            Spacer(modifier = Modifier.height(40.dp))
            }
        }}




@Composable
fun LibraryScreen(vm: MainViewModel) {
    val notes by vm.notes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Library", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("${notes.size} documents saved", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(notes) { note ->

                val noteId = note["id"]?.toString() ?: ""
                LibraryCard(
                    note = note,
                    onClick = { vm.viewNoteDetail(noteId) },
                    onDelete = { vm.deleteNote(noteId) }
                )
            }
        }
    }
}

@Composable
fun LibraryCard(note: Map<String, Any>, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE3F2FD)),
                contentAlignment = Alignment.Center
            ) {
                Text("📄", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        note["title"]?.toString() ?: "Untitled",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1

                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = note["category"]?.toString() ?: "General",
                            color = Color.DarkGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Medium
                        )

                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = note["content"]?.toString() ?: "",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    lineHeight = 18.sp
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFFCDD2))
            }
        }
    }
}











