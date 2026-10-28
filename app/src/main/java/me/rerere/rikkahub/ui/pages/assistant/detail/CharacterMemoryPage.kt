package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import me.rerere.locallm.hasUsableTextEmbeddingFile
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.memory.*
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAction
import me.rerere.rikkahub.ui.components.chat.LastChatComposerActionButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerCapsule
import me.rerere.rikkahub.ui.components.chat.LastChatComposerRow
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterMemoryPage(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    repository: CharacterMemoryRepository = koinInject(),
    processor: MemoryProcessor = koinInject(),
    settingsStore: SettingsStore = koinInject(),
    localModels: me.rerere.locallm.LocalModelStore = koinInject(),
    decisionClient: JevDecisionClient = koinInject(),
) {
    val owner = assistant.id.toString()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val installedModels by localModels.models.collectAsStateWithLifecycle(emptyList())
    val embeddingReady = installedModels.any { it.hasUsableTextEmbeddingFile() }
    val stream = remember(owner) { repository.observe(owner) }
    val allMemory by stream.collectAsStateWithLifecycle(CharacterMemory())
    val view = allMemory.copy(notes = allMemory.notes.filter { it.context == assistant.memoryContext },
        facts = allMemory.facts.filter { it.context == assistant.memoryContext },
        nodes = allMemory.nodes.filter { it.context == assistant.memoryContext })
    var error by remember { mutableStateOf<String?>(null) }
    var request by rememberSaveable(owner) { mutableStateOf("") }
    var settingsOpen by rememberSaveable(owner) { mutableStateOf(false) }
    var browse by rememberSaveable(owner) { mutableStateOf(false) }
    var graph by rememberSaveable(owner) { mutableStateOf(false) }
    var selected by rememberSaveable(owner) { mutableStateOf<String?>(null) }
    var popup by rememberSaveable(owner) { mutableStateOf(false) }
    var draftId by rememberSaveable(owner) { mutableStateOf<String?>(null) }
    var activities by remember { mutableStateOf<List<MemoryActivity>>(emptyList()) }
    var work by remember { mutableStateOf<Job?>(null) }
    var working by remember { mutableStateOf(false) }
    var interruptedRequest by rememberSaveable(owner) { mutableStateOf<String?>(null) }
    var undoId by rememberSaveable(owner) { mutableStateOf<String?>(null) }
    var footerHeight by remember { mutableIntStateOf(130) }
    val density = LocalDensity.current
    val draft = view.drafts.firstOrNull { it.id == draftId }
    val visibleNotes = view.notes.filter { !it.sourceOnly && it.context == assistant.memoryContext }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(owner) {
        if (interruptedRequest != null && request.isBlank()) {
            request = interruptedRequest.orEmpty()
            error = "The previous request was interrupted. Send it again to retry; nothing was applied."
        }
        try { repository.initialise(owner) }
        catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(owner, assistant.enableMemory, assistant.memoryExperience, assistant.memoryContext, embeddingReady) {
        if (assistant.enableMemory && assistant.memoryMode() == MemoryExperience.ADVANCED && embeddingReady) {
            try {
                repository.initialise(owner)
                val metadata = repository.metadata(owner)
                if (!metadata.activeAdvanced && !metadata.preparing && !metadata.cancelled && !metadata.paused) {
                    repository.startPreparation(owner)
                    MemoryLearningWorker.enqueue(context, owner, 0)
                }
            } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message }
        }
    }
    fun perform(block: suspend () -> Unit) {
        scope.launch { try { error = null; block() } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message ?: "Unable to finish this memory operation."
        } }
    }
    fun updateMemoryAssistant(next: Assistant) {
        perform {
            if (assistant.memoryMode() == MemoryExperience.ADVANCED && next.memoryMode() == MemoryExperience.BASIC) repository.materializeBasic(owner)
            val changedContext = assistant.memoryContext != next.memoryContext
            if (changedContext || assistant.memoryMode() != next.memoryMode()) repository.cancelPreparation(owner)
            repository.configure(owner) { it.copy(activeAdvanced = if (changedContext) false else it.activeAdvanced,
                cancelled = false, paused = false, inFlight = null,
                learningEnabledAt = if (!assistant.memoryDreaming && next.memoryDreaming) System.currentTimeMillis() else it.learningEnabledAt) }
            settingsStore.update { current -> current.copy(assistants = current.assistants.map { latest ->
                if (latest.id == assistant.id) latest.copy(enableMemory = next.enableMemory, memoryExperience = next.memoryExperience,
                    memoryDreaming = next.memoryDreaming, memoryProcessingModelId = next.memoryProcessingModelId, memoryContext = next.memoryContext) else latest
            }) }
            if (next.enableMemory && next.memoryMode() == MemoryExperience.BASIC && next.memoryDreaming) MemoryLearningWorker.enqueue(context, owner)
        }
    }
    fun send() {
        if (request.isBlank() || working) return
        val sent = request
        interruptedRequest = sent
        working = true
        request = ""
        popup = true
        activities = emptyList()
        work = scope.launch {
            try {
                error = null
                val refinement = draft?.let { "Previous request: ${it.request}\nRefinement: $sent" } ?: sent
                val next = processor.request(owner, refinement, selected) { event ->
                    activities = activities.filterNot { it.label == event.label } + event
                }
                draftId?.let { repository.dao.delete(owner, "draft", it) }
                draftId = next.id
                interruptedRequest = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message ?: "No changes were made."
            } finally {
                working = false
                if (interruptedRequest != null && request.isBlank()) request = interruptedRequest.orEmpty()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start=20.dp,end=20.dp,top=8.dp,
            bottom=with(density) { footerHeight.toDp() }+24.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Text(if (!assistant.enableMemory) "Memory" else if (assistant.memoryMode()==MemoryExperience.BASIC) "You" else "Overview",
                        color=colors.primary, style=MaterialTheme.typography.titleMedium, modifier=Modifier.weight(1f))
                    IconButton(onClick={ settingsOpen=true }) { Icon(Icons.Rounded.Settings,"Memory settings") }
                }
            }
            if (!assistant.enableMemory) {
                item {
                    MemorySurface {
                        Text("Keep what matters", style=MaterialTheme.typography.headlineSmall)
                        Text("Give this character continuity across conversations.", color=colors.onSurfaceVariant)
                        Button(onClick={ settingsOpen=true }) { Text("Set up memory") }
                    }
                }
            } else if (assistant.memoryMode()==MemoryExperience.BASIC) {
                val you = visibleNotes.filter { it.category=="You" }
                if (you.isEmpty()) item { MemoryIdentityRow(settings.displaySetting.userNickname.ifBlank{"You"},settings.displaySetting.userAvatar,"Add details") {
                    selected="new-you"
                } }
                if (you.isNotEmpty()) item { MemoryIdentityRow(settings.displaySetting.userNickname.ifBlank{"You"},settings.displaySetting.userAvatar,relativeMemoryTime(you.first().updatedAt)){selected=you.first().id} }
                items(you.drop(1), key={it.id}) { note -> ConnectedMemoryRow(note.title, relativeMemoryTime(note.updatedAt),true,true) { selected=note.id } }
                for (category in listOf("Topics","People")) {
                    val notes=visibleNotes.filter { it.category==category }
                    if (notes.isNotEmpty()) {
                        item { MemoryHeading(category) }
                        items(notes.size,key={notes[it].id}) { index ->
                            ConnectedMemoryRow(notes[index].title,relativeMemoryTime(notes[index].updatedAt),index==0,index==notes.lastIndex) { selected=notes[index].id }
                        }
                    }
                }
                item { TextButton(onClick={
                    selected="new-topic"
                }) { Icon(Icons.Rounded.Add,null); Text("Add a note") } }
            } else {
                if (view.metadata.summary.isNotEmpty()) {
                    view.metadata.summary.forEachIndexed { index, section ->
                        if(index>0) item { MemoryHeading(section.title) }
                        item { MemorySurface { LinkedMemoryText(section.text,view.nodes) { selected=it } } }
                    }
                } else {
                    val durable=view.facts.filter { isCurrentMemory(it,System.currentTimeMillis()) && it.kind in setOf("fact","habit") && it.context==assistant.memoryContext }
                    if (durable.isNotEmpty()) item { MemorySurface { LinkedMemoryText(durable.take(6).joinToString(" "){it.text},view.nodes) { selected=it } } }
                    else item { MemorySurface {
                        Text("A memory that grows with you",style=MaterialTheme.typography.titleLarge)
                        Text(if(view.notes.isNotEmpty()) "Your saved notes are available while Advanced memory is prepared." else "Useful details will appear here as this character learns.",color=colors.onSurfaceVariant)
                        Button(enabled=embeddingReady,onClick={ perform {
                            repository.startPreparation(owner)
                            MemoryLearningWorker.enqueue(context,owner,0)
                        } }) { Text("Prepare memory") }
                    } }
                }
                val suggestions=view.nodes.filter { node -> view.facts.any { it.subject==node.id || it.objectId==node.id } }.take(3)
                if(suggestions.isNotEmpty()) {
                    item { MemoryHeading("Dive deeper") }
                    items(suggestions.size) { index -> ConnectedMemoryRow("More about ${suggestions[index].name}","",index==0,index==suggestions.lastIndex){selected=suggestions[index].id} }
                }
                item { MemoryHeading("Memory Graph") }
                item { MemoryGraph(view,avatars=mapOf("user" to (settings.displaySetting.userNickname.ifBlank{"You"} to settings.displaySetting.userAvatar),"assistant" to (assistant.name to assistant.avatar)),onOpen={ graph=true }) }
                item { FilledTonalButton(onClick={browse=true},modifier=Modifier.fillMaxWidth()) { Text("Browse all memories") } }
            }
            view.metadata.error?.let { failure -> item { MemorySurface { Text(failure,color=colors.error);TextButton(onClick={perform {
                repository.configure(owner){it.copy(paused=false,cancelled=false,error=null,inFlight=null)};MemoryLearningWorker.enqueue(context,owner,0)
            }}){Text("Retry")} } } }
            if(view.drafts.isNotEmpty()) item {
                ConnectedMemoryRow("Review pending changes", "${view.drafts.size} requests",true,true){draftId=view.drafts.last().id;popup=true}
            }
            if(undoId!=null) item { TextButton(onClick={perform { repository.undo(owner,requireNotNull(undoId));undoId=null }}){Text("Undo last change")} }
            error?.let { message -> item { Text(message,color=colors.error) } }
        }
        if (assistant.enableMemory) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                .background(Brush.verticalGradient(listOf(Color.Transparent,colors.background.copy(alpha=.94f),colors.background)))
                .onSizeChanged {footerHeight=it.height}.padding(start=20.dp,end=20.dp,top=32.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(view.metadata.paused) "Paused" else view.metadata.progress.ifBlank { relativeMemoryTime(view.metadata.updatedAt) },
                        modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
                    if(view.metadata.progress.isNotBlank() || view.metadata.paused) {
                        FilledTonalButton(onClick={perform {
                            val paused=!view.metadata.paused
                            repository.configure(owner){it.copy(paused=paused,inFlight=if(paused)it.inFlight else null)}
                            if(paused) MemoryLearningWorker.cancel(context,owner) else MemoryLearningWorker.enqueue(context,owner,0)
                        }}){Text(if(view.metadata.paused) "Resume" else "Pause")}
                    } else FilledTonalButton(onClick={perform {
                        repository.configure(owner){it.copy(paused=false,cancelled=false,error=null,inFlight=null)}
                        if(assistant.memoryMode()==MemoryExperience.ADVANCED&&!view.metadata.activeAdvanced)repository.startPreparation(owner)
                        MemoryLearningWorker.enqueue(context,owner,0,refresh=true)
                    }}){Text("Update")}
                }
                MemoryComposer(request,{request=it},false,::send,{})
            }
        }
    }

    if(settingsOpen) ModalBottomSheet(onDismissRequest={settingsOpen=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Memory settings",style=MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Memory",Modifier.weight(1f)); HapticSwitch(checked=assistant.enableMemory,onCheckedChange={enabled->
                    updateMemoryAssistant(assistant.copy(enableMemory=enabled,memoryExperience=assistant.memoryMode().name))
                })
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                for(mode in MemoryExperience.entries) FilterChip(selected=assistant.memoryMode()==mode,onClick={
                    updateMemoryAssistant(assistant.copy(memoryExperience=mode.name,enableMemory=true))
                },label={Text(if(mode==MemoryExperience.BASIC) "Basic" else "Advanced")})
            }
            if(assistant.memoryMode()==MemoryExperience.BASIC) Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text("Dreaming");Text("Learn from new chats automatically. Update also checks saved chats.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                HapticSwitch(checked=assistant.memoryDreaming,onCheckedChange={updateMemoryAssistant(assistant.copy(memoryDreaming=it))})
            } else EmbeddingModelCard()
            if(view.metadata.preparing)TextButton(onClick={perform{
                repository.cancelPreparation(owner)
                MemoryLearningWorker.cancel(context,owner)
            }}){Text("Cancel preparation")}
            Text("Memory-processing model",style=MaterialTheme.typography.titleSmall)
            ModelSelector(modelId=assistant.memoryProcessingModelId?:settings.summarizerModelId?:assistant.chatModelId?:settings.chatModelId,
                type=me.rerere.ai.provider.ModelType.CHAT,
                providers=settings.providers,onSelect={updateMemoryAssistant(assistant.copy(memoryProcessingModelId=it.id))})
            Text("Conversation context",style=MaterialTheme.typography.titleSmall)
            var contextName by remember(assistant.memoryContext){mutableStateOf(assistant.memoryContext)}
            OutlinedTextField(value=contextName,onValueChange={contextName=it},label={Text("real or a story name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            TextButton(onClick={updateMemoryAssistant(assistant.copy(memoryContext=contextName.trim().ifBlank{"real"}))}){Text("Use this context")}
            var rulesOpen by remember{mutableStateOf(false)}
            TextButton(onClick={rulesOpen=!rulesOpen}){Text("Remembering preferences")}
            if(rulesOpen){
                var rules by remember(view.metadata.rules){mutableStateOf(view.metadata.rules)}
                OutlinedTextField(value=rules,onValueChange={rules=it},label={Text("How and what to remember")},modifier=Modifier.fillMaxWidth(),minLines=3)
                TextButton(onClick={perform {repository.configure(owner){it.copy(rules=rules)};rulesOpen=false}}){Text("Save preferences")}
            }
            if(assistant.memoryMode()==MemoryExperience.ADVANCED){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("Decision assistance")
                        Text("Check requested edits with Jev before review",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    }
                    HapticSwitch(checked=view.metadata.decisionAssistance,onCheckedChange={enabled->perform{repository.configure(owner){it.copy(decisionAssistance=enabled)}}})
                }
                if(view.metadata.decisionAssistance){
                    Text("Your edit request and proposed changes are sent to TypeSafe. This optional check cannot change memory or grant permission.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    var decisionKey by remember{mutableStateOf("")}
                    OutlinedTextField(value=decisionKey,onValueChange={decisionKey=it},label={Text(if(decisionClient.hasKey())"Replace TypeSafe API key" else "TypeSafe API key")},
                        visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
                    Row{
                        TextButton(onClick={decisionClient.setKey(decisionKey);decisionKey=""},enabled=decisionKey.isNotBlank()){Text("Save key")}
                        if(decisionClient.hasKey())TextButton(onClick={decisionClient.setKey("")}){Text("Remove key")}
                    }
                    var decisionModel by remember(view.metadata.decisionModel){mutableStateOf(view.metadata.decisionModel)}
                    OutlinedTextField(value=decisionModel,onValueChange={decisionModel=it},label={Text("Decision model")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    TextButton(onClick={perform{require(decisionModel.isNotBlank());repository.configure(owner){it.copy(decisionModel=decisionModel.trim())}}}){Text("Save model")}
                }
            }
            var historyOpen by remember { mutableStateOf(false) }
            TextButton(onClick={historyOpen=!historyOpen}){Text("Recent changes")}
            if(historyOpen) {
                if(view.history.isEmpty())Text("No reversible changes",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                view.history.take(30).forEach { commit ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(commit.label,Modifier.weight(1f),maxLines=2,style=MaterialTheme.typography.bodyMedium)
                        TextButton(onClick={perform{repository.undo(owner,commit.id)}}){Text("Undo")}
                    }
                }
            }
            TextButton(onClick={settingsOpen=false}){Text("Done")}
        }
    }
    if(selected!=null && !popup) {
        MemoryDetailSheet(view,requireNotNull(selected),repository,owner,
            onDismiss={selected=null},onRequest={text->request=text;popup=true},onSelect={selected=it},onChanged={undoId=it},onError={error=it})
    }
    if(browse || graph) ModalBottomSheet(onDismissRequest={browse=false;graph=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        var search by rememberSaveable {mutableStateOf("")}
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=24.dp)) {
            Text(if(graph) "Memory Graph" else "All memories",style=MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick={selected="new-fact";browse=false;graph=false}){Text("Add memory")}
                TextButton(onClick={selected="new-node";browse=false;graph=false}){Text("Add entity")}
            }
            if(graph) MemoryGraph(view)
            OutlinedTextField(value=search,onValueChange={search=it},placeholder={Text("Search memories")},modifier=Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f,false).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                items(view.facts.filter{it.text.contains(search,true)},key={it.id}){fact->ConnectedMemoryRow(fact.text,fact.occurredAt?:relativeMemoryTime(fact.recordedAt),true,true){selected=fact.id;browse=false;graph=false}}
                items(visibleNotes.filter{it.body.contains(search,true)||it.title.contains(search,true)},key={it.id}){note->ConnectedMemoryRow(note.title,"",true,true){selected=note.id;browse=false;graph=false}}
            }
        }
    }
    if(popup) Dialog(onDismissRequest={popup=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal=16.dp,vertical=12.dp)) {
            val popupMaxHeight = (maxHeight - 100.dp).coerceAtLeast(120.dp)
            Column(Modifier.align(Alignment.BottomCenter).widthIn(max=600.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(shape=RoundedCornerShape(28.dp),color=colors.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().heightIn(max=popupMaxHeight)) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(working) "Working on memory" else if(draft!=null) "Review changes" else "Memory",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                            IconButton(onClick={popup=false}){Icon(Icons.Rounded.Close,"Close memory request")}
                        }
                        Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            for((index,activity) in activities.withIndex()) {
                                var expanded by remember(activity.label){mutableStateOf(false)}
                                Surface(onClick={expanded=!expanded},shape=groupShape(index==0,index==activities.lastIndex),color=colors.surfaceContainerLow) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                            if(activity.running && working) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                                            else Icon(Icons.Rounded.Check,null,Modifier.size(18.dp))
                                            Text(activity.label,style=MaterialTheme.typography.bodyMedium)
                                        }
                                        if(expanded && activity.detail.isNotBlank())Text(activity.detail,style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if(draft!=null && !working) {
                                draft.reviewNote?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                                for(change in draft.changes) {
                                    val old=view.notes.firstOrNull{it.id==change.id}?.body?:view.facts.firstOrNull{it.id==change.id}?.text
                                    if(old!=null) Surface(shape=groupShape(true,false),color=colors.surfaceContainerLow){Column(Modifier.fillMaxWidth().padding(14.dp)){Text("Before",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant);Text(old)}}
                                    Surface(shape=groupShape(old==null,true),color=colors.secondaryContainer){Column(Modifier.fillMaxWidth().padding(14.dp)){
                                        Text(if(change.delete) "Forget" else "After",style=MaterialTheme.typography.labelSmall)
                                        Text(if(change.delete) old.orEmpty() else change.note?.body?:change.fact?.text?:change.node?.name.orEmpty())
                                    }}
                                }
                                if(draft.summary.isNotEmpty()) {
                                    var summaryOpen by remember{mutableStateOf(false)}
                                    TextButton(onClick={summaryOpen=!summaryOpen}){Text("Summary preview")}
                                    if(summaryOpen)Text(draft.summary.joinToString("\n\n"){it.text})
                                }
                            }
                            error?.let{Text(it,color=colors.error)}
                        }
                        if(draft!=null && !working) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            TextButton(onClick={perform {repository.dao.delete(owner,"draft",draft.id);draftId=null;popup=false}}){Text("Discard")}
                            Button(onClick={perform {undoId=repository.apply(owner,draft).takeIf{draft.changes.none{it.delete}};draftId=null;popup=false}}){Text("Apply changes")}
                        }
                    }
                }
                MemoryComposer(request,{request=it},working,::send,{work?.cancel();activities=activities.map{it.copy(running=false)}})
            }
        }
    }
}

@Composable private fun MemoryHeading(text:String){Text(text,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp,start=4.dp))}
@Composable private fun MemoryIdentityRow(name:String,avatar:me.rerere.rikkahub.data.model.Avatar,subtitle:String,onClick:()->Unit){
    Surface(onClick=onClick,shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){
        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Column(Modifier.weight(1f)){Text(name,style=MaterialTheme.typography.titleLarge);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            me.rerere.rikkahub.ui.components.ui.UIAvatar(name,avatar,Modifier.size(64.dp))
        }
    }
}
@Composable internal fun MemorySurface(content:@Composable ColumnScope.()->Unit){Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}
internal fun groupShape(first:Boolean,last:Boolean)=RoundedCornerShape(topStart=if(first)28.dp else 5.dp,topEnd=if(first)28.dp else 5.dp,bottomStart=if(last)28.dp else 5.dp,bottomEnd=if(last)28.dp else 5.dp)
@Composable internal fun ConnectedMemoryRow(title:String,subtitle:String,first:Boolean,last:Boolean,onClick:()->Unit){
    Surface(onClick=onClick,shape=groupShape(first,last),color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(title,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium)
            if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun MemoryComposer(value:String,onChange:(String)->Unit,working:Boolean,onSend:()->Unit,onStop:()->Unit){
    LastChatComposerRow {
        LastChatComposerCapsule {
            Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.weight(1f).padding(vertical=8.dp)){
                    if(value.isEmpty())Text(if(working)"Working…" else "Manage memory",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(value=value,onValueChange=onChange,modifier=Modifier.fillMaxWidth(),textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),maxLines=4,
                        cursorBrush=Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.primary)))
                }
                LastChatComposerActionButton(action=if(working)LastChatComposerAction.Loading else LastChatComposerAction.Send,onClick={if(working)onStop()else onSend()}){
                    Icon(if(working)Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,if(working)"Stop" else "Send memory request")
                }
            }
        }
    }
}
@Composable internal fun LinkedMemoryText(text:String,nodes:List<MemoryNode>,onOpen:(String)->Unit){
    val color=MaterialTheme.colorScheme.primary
    val annotated=buildAnnotatedString {
        append(text)
        val occupied=BooleanArray(text.length)
        nodes.flatMap { node -> (listOf(node.name)+node.aliases).filter { it.isNotBlank() }.map { it to node.id } }
            .sortedByDescending { it.first.length }.forEach { (name,id) ->
                val pattern=if(name.all { it.code<128 }) "(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])" else Regex.escape(name)
                Regex(pattern,RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
                    val start=match.range.first
                    val end=match.range.last+1
                    if((start until end).none { occupied[it] }) {
                        (start until end).forEach { occupied[it]=true }
                        addLink(LinkAnnotation.Clickable(id,TextLinkStyles(SpanStyle(color=color,textDecoration=TextDecoration.Underline))) { onOpen(id) },start,end)
                    }
                }
            }
    }
    Text(annotated,style=MaterialTheme.typography.bodyLarge)
}
internal fun relativeMemoryTime(time:Long):String {
    if(time<=0)return "Not updated yet"
    val minutes=((System.currentTimeMillis()-time)/60_000).coerceAtLeast(0)
    return when{minutes<1->"Updated just now";minutes<60->"Updated $minutes minutes ago";minutes<1440->"Updated ${minutes/60} hours ago";else->"Updated ${minutes/1440} days ago"}
}
