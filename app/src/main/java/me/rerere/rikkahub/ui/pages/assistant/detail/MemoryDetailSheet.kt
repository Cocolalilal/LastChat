package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.memory.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryDetailSheet(
    view:CharacterMemory,id:String,repository:CharacterMemoryRepository,owner:String,
    onDismiss:()->Unit,onRequest:(String)->Unit,onSelect:(String)->Unit,onChanged:(String)->Unit,onError:(String)->Unit,
    initialEditing: Boolean = false,
) {
    val scope=rememberCoroutineScope()
    val navController=me.rerere.rikkahub.ui.context.LocalNavController.current
    val colors=MaterialTheme.colorScheme
    val newNote=remember(id){if(id in setOf("new-you","new-topic"))MemoryNote(Uuid.random().toString(),"",if(id=="new-you")"You" else "Topics",context=repository.owner(owner).memoryContext)else null}
    val newNode=remember(id){if(id=="new-node")MemoryNode(Uuid.random().toString(),"",context=repository.owner(owner).memoryContext)else null}
    val newFact=remember(id){if(id=="new-fact")MemoryFact(Uuid.random().toString(),"",context=repository.owner(owner).memoryContext,authored=true)else null}
    val note=view.notes.firstOrNull{it.id==id}?:newNote
    val fact=view.facts.firstOrNull{it.id==id}?:newFact
    val node=view.nodes.firstOrNull{it.id==id}?:newNode
    var editing by remember(id,initialEditing){mutableStateOf(initialEditing||newNote!=null||newNode!=null||newFact!=null)}
    var deleting by remember(id){mutableStateOf(false)}
    var localError by remember(id){mutableStateOf<String?>(null)}
    var title by remember(id){mutableStateOf(note?.title?:node?.name.orEmpty())}
    var body by remember(id){mutableStateOf(note?.body?:fact?.text.orEmpty())}
    var description by remember(id){mutableStateOf(note?.description.orEmpty())}
    var aliases by remember(id){mutableStateOf((note?.aliases?:node?.aliases).orEmpty().joinToString(", "))}
    var category by remember(id){mutableStateOf(note?.category?:"Topics")}
    var subject by remember(id){mutableStateOf(fact?.subject?:"user")}
    var relation by remember(id){mutableStateOf(fact?.relation?:"related to")}
    var objectId by remember(id){mutableStateOf(fact?.objectId)}
    var nodeType by remember(id){mutableStateOf(node?.type?:"Thing")}
    val initialNote=remember(id,editing){note}
    val initialFact=remember(id,editing){fact}
    val initialNode=remember(id,editing){node}
    fun perform(action:suspend()->Unit){scope.launch{try{action()}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;localError=e.message?:"Memory could not be changed."}}}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            localError?.let { Text(it,color=colors.error) }
            if(editing){
                Text("Edit memory",style=MaterialTheme.typography.headlineSmall)
                if(note!=null || node!=null)OutlinedTextField(title,{title=it},label={Text("Name")},modifier=Modifier.fillMaxWidth())
                if(note!=null){
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(c in listOf("You","Topics","People"))FilterChip(category==c,{category=c},label={Text(c)})}
                    OutlinedTextField(description,{description=it},label={Text("Description")},modifier=Modifier.fillMaxWidth())
                }
                if(note!=null||node!=null)OutlinedTextField(aliases,{aliases=it},label={Text("Aliases, separated by commas")},modifier=Modifier.fillMaxWidth())
                if(node!=null)FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){for(type in listOf("Person","Place","Thing","Concept"))FilterChip(selected=nodeType==type,onClick={nodeType=type},label={Text(type)})}
                if(fact!=null){
                    MemoryNodePicker("From",subject,view.nodes){subject=it?:"user"}
                    OutlinedTextField(relation,{relation=it},label={Text("Connection")},modifier=Modifier.fillMaxWidth())
                    MemoryNodePicker("To (optional)",objectId,view.nodes){objectId=it}
                }
                if(node==null)OutlinedTextField(body,{body=it},label={Text("Memory")},modifier=Modifier.fillMaxWidth(),minLines=5)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    TextButton(onClick={if(newNote!=null||newNode!=null||newFact!=null)onDismiss()else editing=false}){Text("Cancel")}
                    Button(enabled=if(node!=null)title.isNotBlank()else body.isNotBlank()&&(note==null||title.isNotBlank()),onClick={perform{
                        val change=when{
                            note!=null->MemoryChange("note",note.id,if(newNote!=null)null else initialNote?.revision,
                                note=note.copy(title=title.trim(),category=category,description=description.trim(),aliases=aliases.split(',').map{it.trim()}.filter{it.isNotEmpty()},
                                    paragraphs=editNoteParagraphs(initialNote?:note,body){Uuid.random().toString()}))
                            fact!=null->MemoryChange("fact",fact.id,if(newFact!=null)null else initialFact?.revision,fact=fact.copy(text=body.trim(),subject=subject,relation=relation.trim(),objectId=objectId,authored=true,evidence=listOf(MemoryEvidence(origin="user edit"))))
                            node!=null->MemoryChange("node",node.id,if(newNode!=null)null else initialNode?.revision,node=node.copy(name=title.trim(),type=nodeType,aliases=aliases.split(',').map{it.trim()}.filter{it.isNotEmpty()}))
                            else->error("Memory no longer exists.")
                        }
                        onChanged(repository.apply(owner,MemoryDraft(Uuid.random().toString(),"Edit memory",listOf(change),repository.metadata(owner).policyRevision)))
                        onDismiss()
                    }}){Text("Save")}
                }
            }else{
                Text(note?.category?:node?.type?:fact?.kind.orEmpty(),style=MaterialTheme.typography.labelLarge,color=colors.primary)
                Text(note?.let(::memoryNoteTitle)?:node?.name?:fact?.text?:"Memory unavailable",style=MaterialTheme.typography.headlineSmall)
                if(note!=null) {
                    val paragraphs=if(note.paragraphs.firstOrNull()?.text?.trim()==memoryNoteTitle(note).trim())note.paragraphs.drop(1)else note.paragraphs
                    if(paragraphs.isNotEmpty())MemoryReadableBody(paragraphs)
                }
                val connectedFacts = if(node!=null)view.facts.filter{it.subject==id||it.objectId==id} else emptyList()
                if(fact!=null||connectedFacts.isNotEmpty())MemoryGraph(view,focus=id)
                if(fact!=null){
                    Text(relativeMemoryTime(fact.recordedAt),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    fact.occurredAt?.let{Text("Reported time: $it",style=MaterialTheme.typography.bodyMedium)}
                    if(fact.status!="active")Text("Historical",color=colors.onSurfaceVariant)
                    fact.validUntil?.let { until -> Text("Relevant until ${java.time.Instant.ofEpochMilli(until).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant) }
                    if(fact.kind=="plan")Text("Planned · completion not confirmed",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
                val evidence=when {
                    fact!=null -> fact.evidence
                    note!=null -> note.paragraphs.flatMap{it.evidence}
                    node!=null -> view.facts.filter{it.subject==id||it.objectId==id}.flatMap{it.evidence}.distinct().take(12)
                    else -> emptyList()
                }
                if(evidence.isNotEmpty()){
                    val sources = evidence.distinct()
                    var sourcesExpanded by remember(id) { mutableStateOf(sources.size <= 2 && sources.none{it.quote.length>280}) }
                    Surface(shape=MaterialTheme.shapes.extraLarge,color=colors.secondaryContainer){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Row(Modifier.fillMaxWidth(), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Sources",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                            IconButton(onClick={sourcesExpanded=!sourcesExpanded}) { Icon(if(sourcesExpanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,if(sourcesExpanded)"Collapse sources" else "Expand sources") }
                        }
                        if(sourcesExpanded)sources.forEach{e->
                            when(e.origin) {
                                "chat" -> {
                                    Text("“${e.quote}”")
                                    TextButton(onClick={perform {
                                        if(repository.evidenceValid(owner,e)) {
                                            onDismiss()
                                            me.rerere.rikkahub.utils.navigateToChatPage(navController,Uuid.parse(e.conversationId),searchQuery=e.quote)
                                        } else error("This source has changed.")
                                    }}) { Text("Open conversation") }
                                }
                                "saved note" -> {
                                    Text("“${e.quote}”")
                                    TextButton(onClick={onSelect(e.messageId)}){Text("Open saved note")}
                                }
                                "imported note" -> Text("Imported saved note")
                                "memory request" -> Text("Your memory request")
                                else -> Text("Edited by you")
                            }
                        }
                    }}
                }
                if(node!=null){
                    connectedFacts.forEachIndexed{index,f->ConnectedMemoryRow(f.text,f.occurredAt.orEmpty(),index==0,index==connectedFacts.lastIndex){onSelect(f.id)}}
                }
                if(note!=null||fact!=null||node!=null)Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    if(note!=null||fact!=null)FilledTonalButton(onClick={perform{
                        val change=if(note!=null)MemoryChange("note",id,note.revision,note=note.copy(pinned=!note.pinned))else MemoryChange("fact",id,fact?.revision,fact=requireNotNull(fact).copy(pinned=!fact.pinned))
                        onChanged(repository.apply(owner,MemoryDraft(Uuid.random().toString(),"Pin memory",listOf(change),repository.metadata(owner).policyRevision)))
                    }},modifier=Modifier.weight(1f).fillMaxHeight(),contentPadding=PaddingValues(horizontal=8.dp,vertical=12.dp),shape=memoryActionShape(true,false)){Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){Icon(Icons.Rounded.PushPin,null,Modifier.size(18.dp));Text(if(note?.pinned==true||fact?.pinned==true)"Unpin" else "Pin")}}
                    FilledTonalButton(onClick={
                        title=note?.title?:node?.name.orEmpty();body=note?.body?:fact?.text.orEmpty()
                        description=note?.description.orEmpty();aliases=(note?.aliases?:node?.aliases).orEmpty().joinToString(", ")
                        category=note?.category?:"Topics";subject=fact?.subject?:"user";relation=fact?.relation?:"related to";objectId=fact?.objectId;nodeType=node?.type?:"Thing"
                        editing=true
                    },modifier=Modifier.weight(1f).fillMaxHeight(),contentPadding=PaddingValues(horizontal=8.dp,vertical=12.dp),shape=memoryActionShape(note==null&&fact==null,false)){Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){Icon(Icons.Rounded.Edit,null,Modifier.size(18.dp));Text("Edit")}}
                    FilledTonalButton(onClick={deleting=true},modifier=Modifier.weight(1f).fillMaxHeight(),contentPadding=PaddingValues(horizontal=8.dp,vertical=12.dp),shape=memoryActionShape(false,true),colors=ButtonDefaults.filledTonalButtonColors(containerColor=colors.errorContainer,contentColor=colors.onErrorContainer)){Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){Icon(Icons.Rounded.Delete,null,Modifier.size(18.dp));Text("Forget")}}
                }
                if(note!=null||fact!=null||node!=null)TextButton(onClick={onRequest("Change the memory about ${note?.let(::memoryNoteTitle)?:node?.name?:fact?.text.orEmpty()}: ")}){Text("Request a change")}
            }
        }
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("Forget this memory?")},text={Text(
        if(node!=null)"This removes this entity and its ${view.facts.count{it.subject==id||it.objectId==id}} connected memories. Other independent memories stay. This cannot be undone."
        else "It will be removed from recall and future rebuilding from these sources. This cannot be undone.")},
        confirmButton={TextButton(onClick={perform{
            val kind=if(note!=null)"note" else if(fact!=null)"fact" else "node"
            repository.apply(owner,MemoryDraft(Uuid.random().toString(),"Forget memory",listOf(MemoryChange(kind,id,note?.revision?:fact?.revision?:node?.revision?:1,delete=true)),repository.metadata(owner).policyRevision))
            deleting=false;onDismiss()
        }}){Text("Forget",color=colors.error)}},dismissButton={TextButton(onClick={deleting=false}){Text("Cancel")}})
}

@Composable private fun MemoryNodePicker(label:String,value:String?,nodes:List<MemoryNode>,onSelect:(String?)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Box{
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("$label: ${nodes.firstOrNull{it.id==value}?.name?:value?:"None"}")}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
            DropdownMenuItem(text={Text("None")},onClick={onSelect(null);expanded=false})
            for(node in (listOf(MemoryNode("user","You","Person"),MemoryNode("assistant","Character","Person"))+nodes).distinctBy{it.id}){
                DropdownMenuItem(text={Text(node.name)},onClick={onSelect(node.id);expanded=false})
            }
        }
    }
}

private fun memoryActionShape(first:Boolean,last:Boolean)=androidx.compose.foundation.shape.RoundedCornerShape(
    topStart=if(first)28.dp else 5.dp,bottomStart=if(first)28.dp else 5.dp,
    topEnd=if(last)28.dp else 5.dp,bottomEnd=if(last)28.dp else 5.dp)
