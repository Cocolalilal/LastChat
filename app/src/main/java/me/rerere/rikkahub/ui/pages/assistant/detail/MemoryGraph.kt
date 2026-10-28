package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.memory.*
import kotlin.math.*
import kotlin.random.Random

internal data class GraphPoint(val id: String, val x: Float, val y: Float, val radius: Float)

/** Seeded force layout; topology controls shape, never an evenly spaced radial template. */
internal fun layoutMemoryGraph(ids: List<String>, edges: List<Pair<String, String>>): List<GraphPoint> {
    if (ids.isEmpty()) return emptyList()
    val x = FloatArray(ids.size) { 0.12f + Random(ids[it].hashCode()).nextFloat() * .76f }
    val y = FloatArray(ids.size) { 0.12f + Random(ids[it].hashCode() xor 17371).nextFloat() * .76f }
    val indexed = ids.withIndex().associate { it.value to it.index }
    val links = edges.mapNotNull { (a, b) -> indexed[a]?.let { ai -> indexed[b]?.let { bi -> ai to bi } } }
    val degree = IntArray(ids.size)
    links.forEach { (a,b) -> degree[a]++; degree[b]++ }
    repeat(180) {
        val dx = FloatArray(ids.size)
        val dy = FloatArray(ids.size)
        for (a in ids.indices) for (b in a+1 until ids.size) {
            val vx = x[a]-x[b]; val vy=y[a]-y[b]
            val d2 = (vx*vx+vy*vy).coerceAtLeast(.00015f)
            val force = (.00010f/d2).coerceAtMost(.03f)
            val d = sqrt(d2)
            dx[a] += vx/d*force; dy[a] += vy/d*force
            dx[b] -= vx/d*force; dy[b] -= vy/d*force
        }
        links.forEach { (a,b) ->
            val vx=x[b]-x[a]; val vy=y[b]-y[a]; val d=sqrt(vx*vx+vy*vy).coerceAtLeast(.001f)
            val rest = .11f + ((ids[a]+ids[b]).hashCode().toUInt()%91u).toFloat()*.0015f
            val force = (d-rest)*.045f
            dx[a]+=vx/d*force; dy[a]+=vy/d*force; dx[b]-=vx/d*force; dy[b]-=vy/d*force
        }
        for (a in ids.indices) {
            x[a]=(x[a]+dx[a]+(.5f-x[a])*.002f).coerceIn(.055f,.945f)
            y[a]=(y[a]+dy[a]+(.5f-y[a])*.002f).coerceIn(.075f,.925f)
        }
    }
    return ids.indices.map { GraphPoint(ids[it],x[it],y[it],(4f+sqrt(degree[it].toFloat())*2.2f).coerceAtMost(15f)) }
}

@Composable
internal fun MemoryGraph(view: CharacterMemory, focus: String? = null,
                         avatars:Map<String,Pair<String,me.rerere.rikkahub.data.model.Avatar>> = emptyMap(),onOpen: () -> Unit = {}) {
    val facts = remember(view.facts, focus) {
        if (focus == null) view.facts else view.facts.filter { it.id == focus || it.subject == focus || it.objectId == focus }
    }
    val edges = remember(facts) { facts.flatMap { f ->
        if(f.objectId==null){val topic="topic:${f.context}:${f.topic}";listOf(f.subject to topic,topic to f.id)}
        else listOf(f.subject to f.id,f.id to requireNotNull(f.objectId))
    }.distinct() }
    val ids = remember(edges) { edges.flatMap { listOf(it.first,it.second) }.distinct().take(180) }
    val points by produceState<List<GraphPoint>>(emptyList(), ids, edges) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { layoutMemoryGraph(ids, edges) }
    }
    val ink = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    Surface(onClick = onOpen, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(if (focus == null) 220.dp else 180.dp).padding(12.dp)) {
        val graphWidth=maxWidth
        val graphHeight=maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val indexed = points.associateBy { it.id }
            edges.forEach { (a,b) ->
                val p=indexed[a]; val q=indexed[b]
                if (p!=null && q!=null) drawLine(ink.copy(alpha=.22f), Offset(p.x*size.width,p.y*size.height),Offset(q.x*size.width,q.y*size.height), 1.5f)
            }
            points.forEach { p ->
                val fact = facts.firstOrNull { it.id==p.id }
                val age = fact?.let { (System.currentTimeMillis()-it.recordedAt)/86_400_000f } ?: 0f
                drawCircle(if (age>60) muted else ink.copy(alpha=(1f-age/140f).coerceIn(.35f,1f)),
                    p.radius* density, Offset(p.x*size.width,p.y*size.height))
            }
        }
        for(p in points){
            avatars[p.id]?.let{(name,avatar)->
                me.rerere.rikkahub.ui.components.ui.UIAvatar(name,avatar,
                    Modifier.offset(x=graphWidth*p.x-14.dp,y=graphHeight*p.y-14.dp).size(28.dp))
            }
        }
        }
    }
}
