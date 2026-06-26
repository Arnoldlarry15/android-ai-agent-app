package com.example

import android.app.Application
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.AppDatabase
import com.example.database.HubRepository
import com.example.database.SharedMemoryEntity
import com.example.database.TaskEntity
import com.example.ui.AgentViewModel
import com.example.ui.AgentViewModelFactory
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
  private var tts: TextToSpeech? = null
  private var isTtsInitialized = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize Room Database
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = HubRepository(
      chatDao = database.chatDao(),
      taskDao = database.taskDao(),
      sharedMemoryDao = database.sharedMemoryDao()
    )
    val viewModelFactory = AgentViewModelFactory(application, repository)

    // Setup TTS Engine
    tts = TextToSpeech(this, this)

    setContent {
      MyApplicationTheme {
        val agentViewModel: AgentViewModel = viewModel(factory = viewModelFactory)
        
        // Listen to Speech triggers from the ViewModel
        LaunchedEffect(Unit) {
          agentViewModel.speechTrigger.collectLatest { text ->
            speak(text)
          }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
          MainAppContainer(agentViewModel)
        }
      }
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      val result = tts?.setLanguage(Locale.US)
      if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        Log.e("TTS", "Language is not supported or missing data")
      } else {
        isTtsInitialized = true
      }
    } else {
      Log.e("TTS", "Failed to initialize TTS")
    }
  }

  private fun speak(text: String) {
    if (isTtsInitialized) {
      tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AgentSpeechID")
    }
  }

  override fun onDestroy() {
    tts?.stop()
    tts?.shutdown()
    super.onDestroy()
  }
}

@Composable
fun MainAppContainer(viewModel: AgentViewModel) {
  val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val activeChatAgent by viewModel.activeChatAgent.collectAsStateWithLifecycle()

  if (!isLoggedIn) {
    LoginScreen(viewModel = viewModel)
  } else {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = BackgroundDark,
      bottomBar = {
        // Bottom navigation respects navigation insets
        NavigationBar(
          containerColor = SurfaceContainer,
          modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) {
          val tabs = listOf(
            Triple("Home", Icons.Default.Home, Icons.Outlined.Home),
            Triple("Tasks", Icons.Default.Assignment, Icons.Outlined.Assignment),
            Triple("Memory", Icons.Default.Psychology, Icons.Outlined.Psychology),
            Triple("Settings", Icons.Default.Settings, Icons.Outlined.Settings)
          )

          tabs.forEach { (tabName, filledIcon, outlinedIcon) ->
            val isSelected = currentTab == tabName && activeChatAgent == null
            NavigationBarItem(
              selected = isSelected,
              onClick = { viewModel.selectTab(tabName) },
              icon = {
                Icon(
                  imageVector = if (isSelected) filledIcon else outlinedIcon,
                  contentDescription = tabName
                )
              },
              label = { Text(tabName, style = MaterialTheme.typography.labelSmall) },
              colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BackgroundDark,
                selectedTextColor = Primary,
                indicatorColor = Primary,
                unselectedIconColor = OnSurfaceVariant,
                unselectedTextColor = OnSurfaceVariant
              ),
              modifier = Modifier.testTag("nav_tab_${tabName.lowercase()}")
            )
          }
        }
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        // Direct view selection based on Tab & active chat state
        if (activeChatAgent != null) {
          AgentChatScreen(viewModel = viewModel, agent = activeChatAgent!!)
        } else {
          when (currentTab) {
            "Home" -> HomeScreen(viewModel)
            "Tasks" -> TasksScreen(viewModel)
            "Memory" -> MemoryScreen(viewModel)
            "Settings" -> SettingsScreen(viewModel)
          }
        }
      }
    }
  }
}

@Composable
fun HomeScreen(viewModel: AgentViewModel) {
  val battery by viewModel.batteryLevel.collectAsStateWithLifecycle()
  val cpu by viewModel.cpuUsage.collectAsStateWithLifecycle()
  val memory by viewModel.memoryUsage.collectAsStateWithLifecycle()
  val queryInput by viewModel.inputText.collectAsStateWithLifecycle()
  val username by viewModel.username.collectAsStateWithLifecycle()
  val focusManager = LocalFocusManager.current

  val initials = remember(username) {
    val name = username ?: "JD"
    val parts = name.trim().split("\\s+".toRegex())
    if (parts.size >= 2) {
      (parts[0].take(1) + parts[1].take(1)).uppercase()
    } else {
      name.take(2).uppercase()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "AI Agent Hub",
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = OnSurface
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(Color(0xFF4CAF50))
          )
          Text(
            text = "Hello, ${username ?: "User"} • Local Hub Active",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
          )
        }
      }

      // User Initials Avatar
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(Primary)
          .clickable { viewModel.selectTab("Settings") },
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = initials,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = BackgroundDark
        )
      }
    }

    // Grid of Six Agents
    val agents = listOf(
      AgentItem("Assistant", "Daily agenda • Weather • TTS", Icons.Default.Mic, PrimaryContainer, OnSurface),
      AgentItem("Automation", "Routines • Power • Triggers", Icons.Default.Bolt, SurfaceContainer, OnSurface),
      AgentItem("Coding", "Write • Debug • Review Code", Icons.Default.Code, SurfaceContainer, OnSurface),
      AgentItem("Business", "Invoices • Copy • Tracking", Icons.Default.TrendingUp, SurfaceContainer, OnSurface),
      AgentItem("Research", "Summarize • Deep Reports", Icons.Default.Search, SurfaceContainer, OnSurface),
      AgentItem("Security", "Scan • Shield • Audit Check", Icons.Default.Shield, SurfaceContainer, OnSurface)
    )

    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .weight(1f)
        .padding(vertical = 12.dp)
    ) {
      items(agents) { agent ->
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = agent.color),
          modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clickable { viewModel.openChat(agent.name) }
            .testTag("agent_card_${agent.name.lowercase()}"),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (agent.color == PrimaryContainer) Color(0xFFEADDFF) else SurfaceVariant),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = agent.icon,
                contentDescription = agent.name,
                tint = if (agent.color == PrimaryContainer) BackgroundDark else Primary,
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = agent.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (agent.color == PrimaryContainer) Color(0xFFEADDFF) else OnSurface
              )
              Text(
                text = agent.subtext,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = if (agent.color == PrimaryContainer) Primary else OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }

    // Telemetry and Input Section
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // System Stats telemetry bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(SurfaceVariant.copy(alpha = 0.3f))
          .clickable { viewModel.updateSystemStats() }
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.BatteryChargingFull, contentDescription = "Battery", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("BATTERY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, fontSize = 9.sp)
            Text("$battery%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
          }
        }
        Box(modifier = Modifier.size(1.dp, 24.dp).background(SurfaceVariant))
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Memory, contentDescription = "CPU", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CPU USAGE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, fontSize = 9.sp)
            Text("$cpu%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
          }
        }
        Box(modifier = Modifier.size(1.dp, 24.dp).background(SurfaceVariant))
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Dns, contentDescription = "Memory", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MEMORY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, fontSize = 9.sp)
            Text(memory, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
          }
        }
      }

      // Input Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(CircleShape)
          .background(SurfaceContainer)
          .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = {
            viewModel.updateInputText("Plan a daily reminder for my standing desk breaks")
            focusManager.clearFocus()
          },
          modifier = Modifier.testTag("microphone_sim_button")
        ) {
          Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Simulated voice prompt trigger",
            tint = Primary
          )
        }

        TextField(
          value = queryInput,
          onValueChange = { viewModel.updateInputText(it) },
          placeholder = { Text("Ask anything...", color = OnSurfaceVariant, fontSize = 14.sp) },
          colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface
          ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = {
            if (queryInput.isNotBlank()) {
              viewModel.sendMessage(queryInput)
              focusManager.clearFocus()
            }
          }),
          modifier = Modifier
            .weight(1f)
            .testTag("global_ask_input")
        )

        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Primary)
            .clickable {
              if (queryInput.isNotBlank()) {
                viewModel.sendMessage(queryInput)
                focusManager.clearFocus()
              }
            },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = "Send",
            tint = BackgroundDark,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun AgentChatScreen(viewModel: AgentViewModel, agent: String) {
  val messages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
  val queryInput by viewModel.inputText.collectAsStateWithLifecycle()
  val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current

  val speechLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val data = result.data
      val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
      val spokenText = results?.firstOrNull()
      if (!spokenText.isNullOrBlank()) {
        viewModel.updateInputText(spokenText)
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundDark)
  ) {
    // Agent Topbar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        IconButton(
          onClick = { viewModel.closeChat() },
          modifier = Modifier.testTag("back_to_hub_button")
        ) {
          Icon(Icons.Default.ArrowBack, contentDescription = "Back to Agent Hub", tint = OnSurface)
        }

        Column {
          Text(
            text = agent,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OnSurface
          )
          Text(
            text = "Active • Memory Synced",
            style = MaterialTheme.typography.bodySmall,
            color = Primary
          )
        }
      }

      IconButton(
        onClick = {
          viewModel.clearAgentHistory(agent)
          Toast.makeText(context, "Cleared $agent conversation", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.testTag("clear_history_button")
      ) {
        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Agent History", tint = OnSurfaceVariant)
      }
    }

    Divider(color = SurfaceVariant, thickness = 0.5.dp)

    // Message Log List
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(vertical = 16.dp)
    ) {
      items(messages) { msg ->
        val isUser = msg.sender == "user"
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
          Box(
            modifier = Modifier
              .widthIn(max = 290.dp)
              .clip(
                RoundedCornerShape(
                  topStart = 18.dp,
                  topEnd = 18.dp,
                  bottomStart = if (isUser) 18.dp else 4.dp,
                  bottomEnd = if (isUser) 4.dp else 18.dp
                )
              )
              .background(if (isUser) PrimaryContainer else SurfaceContainer)
              .padding(horizontal = 14.dp, vertical = 10.dp)
          ) {
            Column {
              Text(
                text = msg.message,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = if (isUser) "You" else agent,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = OnSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
              )
            }
          }
        }
      }

      if (isLoading) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                Text("Analyzing context...", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
              }
            }
          }
        }
      }
    }

    // Agent Bottom Input Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .clip(CircleShape)
        .background(SurfaceContainer)
        .padding(horizontal = 6.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = {
          val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to $agent Agent...")
          }
          try {
            speechLauncher.launch(intent)
          } catch (e: Exception) {
            val testCommand = when (agent) {
              "Assistant" -> "Schedule standard stand-up reminder for 4 PM daily"
              "Automation" -> "Enable bedtime power routine and wifi sleep schedule"
              "Coding" -> "Write a clean Kotlin helper class for a dynamic state machine"
              "Business" -> "Draft professional consulting agreement for Acme Corp"
              "Research" -> "Compile findings on dynamic on-device context caching"
              "Security" -> "Perform full local storage permission vulnerability scan"
              else -> "Create custom workflow trigger"
            }
            viewModel.updateInputText(testCommand)
            Toast.makeText(context, "Voice dictation fallback triggered", Toast.LENGTH_SHORT).show()
          }
          focusManager.clearFocus()
        },
        modifier = Modifier.testTag("microphone_chat_button")
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = "Trigger simulated vocal command",
          tint = Primary
        )
      }

      TextField(
        value = queryInput,
        onValueChange = { viewModel.updateInputText(it) },
        placeholder = { Text("Ask $agent...", color = OnSurfaceVariant, fontSize = 14.sp) },
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
          disabledContainerColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          focusedTextColor = OnSurface,
          unfocusedTextColor = OnSurface
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = {
          if (queryInput.isNotBlank()) {
            viewModel.sendMessage(queryInput)
            focusManager.clearFocus()
          }
        }),
        modifier = Modifier
          .weight(1f)
          .testTag("agent_ask_input")
      )

      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(Primary)
          .clickable {
            if (queryInput.isNotBlank()) {
              viewModel.sendMessage(queryInput)
              focusManager.clearFocus()
            }
          },
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.ArrowForward,
          contentDescription = "Send Message",
          tint = BackgroundDark,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
fun TasksScreen(viewModel: AgentViewModel) {
  val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
  var showAddDialog by remember { mutableStateOf(false) }
  var newTaskTitle by remember { mutableStateOf("") }
  var newTaskAgent by remember { mutableStateOf("Assistant") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text("Active Reminders", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        Text("Managed and triggers scheduled", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
      }

      IconButton(
        onClick = { showAddDialog = true },
        colors = IconButtonDefaults.iconButtonColors(containerColor = Primary),
        modifier = Modifier
          .size(40.dp)
          .testTag("add_task_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Reminder", tint = BackgroundDark)
      }
    }

    Spacer(Modifier.height(16.dp))

    if (tasks.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Task, contentDescription = null, tint = SurfaceVariant, modifier = Modifier.size(48.dp))
          Text("No active reminders configured.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
          Text("Ask an agent to \"schedule\" a task to see it here!", style = MaterialTheme.typography.bodySmall, color = SurfaceVariant, textAlign = TextAlign.Center)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(tasks) { task ->
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = task.status == "Completed",
                onCheckedChange = { viewModel.toggleTaskStatus(task) },
                colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = OnSurfaceVariant)
              )

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = task.title,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.status == "Completed") androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                  ),
                  color = if (task.status == "Completed") OnSurfaceVariant else OnSurface
                )
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(PrimaryContainer)
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = task.agent,
                      style = MaterialTheme.typography.labelSmall,
                      color = OnSurface,
                      fontSize = 8.sp
                    )
                  }
                  Text(
                    text = "Scheduled Trigger",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    fontSize = 11.sp
                  )
                }
              }

              IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = Color(0xFFEF5350))
              }
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Schedule New Event", color = OnSurface) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = newTaskTitle,
            onValueChange = { newTaskTitle = it },
            label = { Text("Event details / Reminder", color = OnSurfaceVariant) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVariant),
            modifier = Modifier.fillMaxWidth()
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val agents = listOf("Assistant", "Automation", "Business", "Security")
            agents.forEach { agent ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (newTaskAgent == agent) Primary else SurfaceContainer)
                  .clickable { newTaskAgent = agent }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(agent, style = MaterialTheme.typography.labelSmall, color = if (newTaskAgent == agent) BackgroundDark else OnSurface)
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (newTaskTitle.isNotBlank()) {
              viewModel.addNewTask(newTaskAgent, newTaskTitle, "Manually created reminder event")
              newTaskTitle = ""
              showAddDialog = false
            }
          }
        ) {
          Text("Save", color = Primary)
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("Cancel", color = OnSurfaceVariant)
        }
      },
      containerColor = SurfaceContainer
    )
  }
}

@Composable
fun MemoryScreen(viewModel: AgentViewModel) {
  val memories by viewModel.allMemory.collectAsStateWithLifecycle()
  var newMemoryText by remember { mutableStateOf("") }
  var selectedAgent by remember { mutableStateOf("Assistant") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text("Shared Agent Memory", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
    Text("Knowledge base used collectively by the six agents", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)

    Spacer(Modifier.height(16.dp))

    // Direct memory input field to seed knowledge base manually
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text("Seed Knowledge Base", style = MaterialTheme.typography.titleSmall, color = OnSurface)
        OutlinedTextField(
          value = newMemoryText,
          onValueChange = { newMemoryText = it },
          placeholder = { Text("e.g. Remember that standing desk breaks occur every 60 mins.", color = OnSurfaceVariant, fontSize = 13.sp) },
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVariant),
          modifier = Modifier.fillMaxWidth()
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val agents = listOf("Assistant", "Automation", "Security")
            agents.forEach { agent ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (selectedAgent == agent) Primary else SurfaceVariant)
                  .clickable { selectedAgent = agent }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(agent, style = MaterialTheme.typography.labelSmall, color = if (selectedAgent == agent) BackgroundDark else OnSurface)
              }
            }
          }

          Button(
            onClick = {
              if (newMemoryText.isNotBlank()) {
                viewModel.addMemoryItem(selectedAgent, newMemoryText)
                newMemoryText = ""
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = BackgroundDark),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text("Insert")
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    if (memories.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Psychology, contentDescription = null, tint = SurfaceVariant, modifier = Modifier.size(48.dp))
          Text("No memories saved in local database.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
          Text("Memories are automatically collected from chats when you ask agents to \"remember\" something.", style = MaterialTheme.typography.bodySmall, color = SurfaceVariant, textAlign = TextAlign.Center)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(memories) { mem ->
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(PrimaryContainer)
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(mem.agent, style = MaterialTheme.typography.labelSmall, color = OnSurface, fontSize = 8.sp)
                  }
                  Text("Shared Knowledge", style = MaterialTheme.typography.bodySmall, color = Primary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(mem.value, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
              }

              IconButton(onClick = { viewModel.deleteMemoryItem(mem.key) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = Color(0xFFEF5350))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingsScreen(viewModel: AgentViewModel) {
  val isTtsEnabled by viewModel.isTtsEnabled.collectAsStateWithLifecycle()
  val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
  val voiceWakeEnabled by viewModel.voiceWakeEnabled.collectAsStateWithLifecycle()
  val permissions by viewModel.permissions.collectAsStateWithLifecycle()
  val sandboxedFiles by viewModel.sandboxedFiles.collectAsStateWithLifecycle()
  val sandboxOutput by viewModel.sandboxOutput.collectAsStateWithLifecycle()
  val username by viewModel.username.collectAsStateWithLifecycle()
  val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()

  val geminiKey by viewModel.geminiKey.collectAsStateWithLifecycle()
  val openaiKey by viewModel.openaiKey.collectAsStateWithLifecycle()
  val claudeKey by viewModel.claudeKey.collectAsStateWithLifecycle()
  val ollamaEndpoint by viewModel.ollamaEndpoint.collectAsStateWithLifecycle()

  var tempGeminiKey by remember(geminiKey) { mutableStateOf(geminiKey) }
  var tempOpenaiKey by remember(openaiKey) { mutableStateOf(openaiKey) }
  var tempClaudeKey by remember(claudeKey) { mutableStateOf(claudeKey) }
  var tempOllamaEndpoint by remember(ollamaEndpoint) { mutableStateOf(ollamaEndpoint) }

  var showGeminiKey by remember { mutableStateOf(false) }
  var showOpenaiKey by remember { mutableStateOf(false) }
  var showClaudeKey by remember { mutableStateOf(false) }

  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text("Settings & Automation", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)

    // User Profile Information
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Active User Session",
            style = MaterialTheme.typography.labelMedium.copy(color = Primary, fontWeight = FontWeight.Bold)
          )
          Text(
            text = username ?: "Arnold Larry",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = OnSurface
          )
          Text(
            text = userEmail ?: "arnoldlarry15@gmail.com",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
          )
        }

        Button(
          onClick = { viewModel.logoutUser() },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350), contentColor = Color.White),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.ExitToApp, contentDescription = "Log Out", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Log Out", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
      }
    }

    // Secure API Keys Card
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Shield Icon",
            tint = Color(0xFF81C784),
            modifier = Modifier.size(24.dp)
          )
          Column {
            Text(
              "Secure API Key Manager",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = OnSurface
            )
            Text(
              "Stored securely using EncryptedSharedPreferences (AES-256).",
              style = MaterialTheme.typography.bodySmall,
              color = OnSurfaceVariant
            )
          }
        }

        // Gemini Key
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("GOOGLE GEMINI API KEY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Primary)
          OutlinedTextField(
            value = tempGeminiKey,
            onValueChange = { tempGeminiKey = it },
            placeholder = { Text("Enter Gemini API Key (defaults to project configuration)", color = OnSurfaceVariant) },
            singleLine = true,
            visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                Icon(
                  imageVector = if (showGeminiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                  contentDescription = "Toggle Gemini visibility",
                  tint = OnSurfaceVariant
                )
              }
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Primary,
              unfocusedBorderColor = SurfaceVariant,
              focusedLabelColor = Primary
            ),
            modifier = Modifier.fillMaxWidth().testTag("settings_gemini_key_input")
          )
        }

        // OpenAI Key
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("OPENAI API KEY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Primary)
          OutlinedTextField(
            value = tempOpenaiKey,
            onValueChange = { tempOpenaiKey = it },
            placeholder = { Text("Enter OpenAI API Key", color = OnSurfaceVariant) },
            singleLine = true,
            visualTransformation = if (showOpenaiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showOpenaiKey = !showOpenaiKey }) {
                Icon(
                  imageVector = if (showOpenaiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                  contentDescription = "Toggle OpenAI visibility",
                  tint = OnSurfaceVariant
                )
              }
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Primary,
              unfocusedBorderColor = SurfaceVariant,
              focusedLabelColor = Primary
            ),
            modifier = Modifier.fillMaxWidth().testTag("settings_openai_key_input")
          )
        }

        // Claude Key
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("ANTHROPIC CLAUDE API KEY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Primary)
          OutlinedTextField(
            value = tempClaudeKey,
            onValueChange = { tempClaudeKey = it },
            placeholder = { Text("Enter Anthropic Claude API Key", color = OnSurfaceVariant) },
            singleLine = true,
            visualTransformation = if (showClaudeKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showClaudeKey = !showClaudeKey }) {
                Icon(
                  imageVector = if (showClaudeKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                  contentDescription = "Toggle Claude visibility",
                  tint = OnSurfaceVariant
                )
              }
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Primary,
              unfocusedBorderColor = SurfaceVariant,
              focusedLabelColor = Primary
            ),
            modifier = Modifier.fillMaxWidth().testTag("settings_claude_key_input")
          )
        }

        // Ollama Endpoint
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("LOCAL OLLAMA INSTANCE ENDPOINT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Primary)
          OutlinedTextField(
            value = tempOllamaEndpoint,
            onValueChange = { tempOllamaEndpoint = it },
            placeholder = { Text("http://10.0.2.2:11434 (Standard loopback)", color = OnSurfaceVariant) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Primary,
              unfocusedBorderColor = SurfaceVariant,
              focusedLabelColor = Primary
            ),
            modifier = Modifier.fillMaxWidth().testTag("settings_ollama_endpoint_input")
          )
        }

        Spacer(Modifier.height(4.dp))

        Button(
          onClick = {
            viewModel.saveApiKeys(
              openai = tempOpenaiKey,
              claude = tempClaudeKey,
              gemini = tempGeminiKey,
              ollama = tempOllamaEndpoint
            )
            Toast.makeText(context, "API Credentials Encrypted & Saved Successfully!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = BackgroundDark),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_api_keys_button")
        ) {
          Icon(Icons.Default.Save, contentDescription = "Save keys")
          Spacer(Modifier.width(8.dp))
          Text("SAVE & SECURE CREDENTIALS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }

        // Security Warn Message
        Text(
          text = "⚠️ Security Warning: Keys are secured inside device storage using AES-256 standard encryption. Do not share raw screenshots or public APKs that expose active production credentials.",
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
          color = OnSurfaceVariant
        )
      }
    }

    // Phase 4: Model Chooser Card
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("Active AI LLM Model", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        Text("Select the engine model that powers the agent response streams:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)

        val models = listOf("Google Gemini 3.5 Flash", "OpenAI ChatGPT", "Anthropic Claude", "Local Ollama LLM")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          models.forEach { model ->
            val isSelected = activeModel == model
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) SurfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
                .clickable { viewModel.selectModel(model) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = isSelected,
                onClick = { viewModel.selectModel(model) },
                colors = RadioButtonDefaults.colors(selectedColor = Primary, unselectedColor = OnSurfaceVariant)
              )
              Spacer(Modifier.width(8.dp))
              Column {
                Text(
                  text = model,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = if (isSelected) Primary else OnSurface
                )
                val sub = when (model) {
                  "Google Gemini 3.5 Flash" -> "Low latency on-device multimodal reasoning (Default)"
                  "OpenAI ChatGPT" -> "Cloud-synced hybrid reasoning engine"
                  "Anthropic Claude" -> "High fidelity coding and logical reasoning model"
                  else -> "Fully offline local model (Llama-3-8B) on loopback"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
              }
            }
          }
        }
      }
    }

    // Voice & Wake Configuration
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text("Voice & Wake Commands", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text("Simulated Agent Speech (TTS)", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text("Speaks responses using Android system TTS engine", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
          }
          Switch(
            checked = isTtsEnabled,
            onCheckedChange = { viewModel.toggleTts() },
            colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer)
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text("Voice Wake Word (Within Capabilities)", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text("Triggers listening session when \"Hey Hub\" is detected", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
          }
          Switch(
            checked = voiceWakeEnabled,
            onCheckedChange = { viewModel.toggleVoiceWake() },
            colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer)
          )
        }
      }
    }

    // Phase 3: Android Permissions & Automations
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("Android Permissions & Automation", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        Text("Configure active permission channels for system integration:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          permissions.forEach { (perm, granted) ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.togglePermission(perm) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                  perm.contains("Calendar") -> Icons.Default.Event
                  perm.contains("Contacts") -> Icons.Default.Person
                  perm.contains("Notification") -> Icons.Default.Notifications
                  perm.contains("Tasker") -> Icons.Default.Settings
                  else -> Icons.Default.Build
                }
                Icon(icon, contentDescription = null, tint = if (granted) Primary else OnSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(perm, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                  Text(
                    text = if (perm.contains("Tasker") || perm.contains("Termux")) "Plugin Integration" else "System Permission",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                  )
                }
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (granted) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFC62828).copy(alpha = 0.2f))
                  .padding(horizontal = 10.dp, vertical = 4.dp)
              ) {
                Text(
                  text = if (granted) "ENABLED / GRANTED" else "DISABLED / DENIED",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = if (granted) Color(0xFF81C784) else Color(0xFFE57373)
                )
              }
            }
          }
        }
      }
    }

    // Phase 5: On-Device File & Code Sandbox
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("On-Device Sandbox Files & Code RAG", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        Text("Scan PDFs to feed long-term RAG memory or run sandbox Python/Bash automation:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          sandboxedFiles.forEach { file ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant.copy(alpha = 0.2f))
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val icon = if (file.endsWith(".pdf")) Icons.Default.Description else Icons.Default.Code
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(file, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    text = if (file.endsWith(".pdf")) "Document (PDF)" else "Automation Code",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                  )
                }
              }

              Button(
                onClick = {
                  if (file.endsWith(".pdf")) {
                    viewModel.extractPdfToMemory(file)
                  } else {
                    viewModel.runSandboxedCode(file)
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant, contentColor = OnSurface),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Text(
                  text = if (file.endsWith(".pdf")) "RAG Index" else "Run Sandbox",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
              }
            }
          }
        }

        // Terminal Output Box
        if (sandboxOutput != null) {
          Spacer(Modifier.height(8.dp))
          Text("Sandbox Terminal Output:", style = MaterialTheme.typography.labelMedium, color = Primary)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 100.dp, max = 200.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black)
              .padding(12.dp)
          ) {
            Text(
              text = sandboxOutput ?: "",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
              color = Color.Green
            )
          }
        }
      }
    }

    // Engine Information Card
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text("Engine Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        Text("Model: $activeModel", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
        Text("Interface Style: Sleek Interface Minimalist (M3)", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text("Architecture: Clean MVVM + Room Database SQLite", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text("Device Compatibility: Pixel 6 Optimal Setup (Android 16 READY)", style = MaterialTheme.typography.bodySmall, color = Primary)
      }
    }
  }
}

data class AgentItem(
  val name: String,
  val subtext: String,
  val icon: ImageVector,
  val color: Color,
  val textColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AgentViewModel) {
  var nameInput by remember { mutableStateOf("Arnold Larry") }
  var emailInput by remember { mutableStateOf("arnoldlarry15@gmail.com") }
  var passwordInput by remember { mutableStateOf("••••••••") }
  val authError by viewModel.authError.collectAsStateWithLifecycle()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(BackgroundDark)
      .windowInsetsPadding(WindowInsets.systemBars)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
      // Sleek Branding Icon
      Box(
        modifier = Modifier
          .size(80.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(Primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Lock,
          contentDescription = "Secure login",
          tint = Primary,
          modifier = Modifier.size(40.dp)
        )
      }

      // Title header
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "AI Agent Suite",
          style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          ),
          color = OnSurface
        )
        Text(
          text = "Sleek Interface Minimalist Authenticator",
          style = MaterialTheme.typography.bodySmall,
          color = OnSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }

      // Input Card
      Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            text = "Secure User Account Access",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = OnSurface
          )

          // Email Field
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "EMAIL ADDRESS",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = Primary
            )
            OutlinedTextField(
              value = emailInput,
              onValueChange = {
                emailInput = it
                viewModel.clearAuthError()
              },
              placeholder = { Text("Enter your email", color = OnSurfaceVariant) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Primary
              ),
              modifier = Modifier.fillMaxWidth().testTag("login_email_input")
            )
          }

          // Full Name / Username Field
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "FULL NAME",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = Primary
            )
            OutlinedTextField(
              value = nameInput,
              onValueChange = {
                nameInput = it
                viewModel.clearAuthError()
              },
              placeholder = { Text("Enter your name", color = OnSurfaceVariant) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Primary
              ),
              modifier = Modifier.fillMaxWidth().testTag("login_name_input")
            )
          }

          // Password Field (Simulated secure credential)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "PASSWORD (SECURE KEY)",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = Primary
            )
            OutlinedTextField(
              value = passwordInput,
              onValueChange = { passwordInput = it },
              placeholder = { Text("Enter account password", color = OnSurfaceVariant) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Primary
              ),
              modifier = Modifier.fillMaxWidth().testTag("login_password_input")
            )
          }

          if (authError != null) {
            Text(
              text = authError ?: "",
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
              color = Color(0xFFEF5350)
            )
          }

          Spacer(Modifier.height(4.dp))

          Button(
            onClick = {
              viewModel.loginUser(email = emailInput, name = nameInput)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = BackgroundDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("submit_login_button")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(
                text = "AUTHENTICATE & ENTER",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
              )
            }
          }
        }
      }

      // Security indicator footer
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
        Text(
          text = "Local Sandboxed SQLite Database Protection Active",
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = OnSurfaceVariant
        )
      }
    }
  }
}
