package com.veltrix.messenger

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.signInWith
import io.github.jan.supabase.auth.signUpWith
import io.github.jan.supabase.auth.currentUserOrNull
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val Ink = Color(0xFF0B1018)
private val Panel = Color(0xFF151F2D)
private val Accent = Color(0xFFE04455)
private val Soft = Color(0xFF9AAEC5)

private val supabase = createSupabaseClient(
    supabaseUrl = "https://gpelalzygwnfngikrcvs.supabase.co",
    supabaseKey = "sb_publishable_O17L43rRgYkbA9JY5kSEsQ_7qyQUhTZ"
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
}

@Serializable data class ProfileRow(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null
)
@Serializable data class ProfileUpdate(val username: String, @SerialName("display_name") val displayName: String)
@Serializable data class MemberRow(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member"
)
@Serializable data class ConversationRow(
    val id: String,
    val kind: String,
    val title: String? = null,
    @SerialName("created_by") val createdBy: String
)
@Serializable data class ConversationInsert(
    val kind: String,
    val title: String?,
    @SerialName("created_by") val createdBy: String
)
@Serializable data class MemberInsert(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    val role: String
)
@Serializable data class MessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String = "text",
    val body: String? = null,
    @SerialName("created_at") val createdAt: String
)
@Serializable data class MessageInsert(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String = "text",
    val body: String
)
@Serializable data class CallSignalRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val signal: JsonObject
)
@Serializable data class CallSignalInsert(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val signal: JsonObject
)

class MainActivity : ComponentActivity() {
    private var userId by mutableStateOf<String?>(null)
    private var profile by mutableStateOf<ProfileRow?>(null)
    private var conversations by mutableStateOf<List<ConversationRow>>(emptyList())
    private var selectedConversation by mutableStateOf<ConversationRow?>(null)
    private var messages by mutableStateOf<List<MessageRow>>(emptyList())
    private var busy by mutableStateOf(false)
    private var notice by mutableStateOf("")
    private var incomingCall by mutableStateOf<CallSignalRow?>(null)
    private var callStatus by mutableStateOf("")
    private var isInCall by mutableStateOf(false)
    private var speakerOn by mutableStateOf(false)
    private var callPeer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var peerFactory: PeerConnectionFactory? = null
    private var callRemoteId: String? = null
    private var callConversationId: String? = null
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private val handledSignals = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        )
        val adm = JavaAudioDeviceModule.builder(this).createAudioDeviceModule()
        peerFactory = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
        lifecycleScope.launch {
            val current = supabase.auth.currentUserOrNull()
            if (current != null) onAuthenticated(current.id)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Ink, surface = Panel, primary = Accent,
                onBackground = Color.White, onSurface = Color.White
            )) {
                when {
                    userId == null -> LoginScreen(
                        busy = busy, notice = notice,
                        onLogin = { email, password -> authenticate(email, password, false) },
                        onSignup = { email, password -> authenticate(email, password, true) }
                    )
                    profile == null -> ProfileSetupScreen(
                        busy = busy, notice = notice,
                        onSave = { username, display -> createProfile(username, display) },
                        onLogout = { logout() }
                    )
                    selectedConversation == null -> ConversationScreen(
                        profile = profile!!, conversations = conversations, busy = busy, notice = notice,
                        onOpen = { openConversation(it) },
                        onNewChat = { username -> createDirectChat(username) },
                        onLogout = { logout() }
                    )
                    else -> ChatScreen(
                        conversation = selectedConversation!!,
                        messages = messages,
                        busy = busy,
                        notice = notice,
                        inCall = isInCall,
                        callStatus = callStatus,
                        onBack = { selectedConversation = null; messages = emptyList() },
                        onSend = { sendMessage(it) },
                        onCall = { requestAudioPermissionAndCall() },
                        onHangup = { endCall(true) },
                        onSpeaker = { toggleSpeaker() }
                    )
                }
                incomingCall?.let { signal ->
                    AlertDialog(
                        onDismissRequest = { incomingCall = null; declineCall(signal) },
                        title = { Text("تماس صوتی ورودی") },
                        text = { Text("یکی از مخاطبانت با تو تماس می‌گیرد. پاسخ می‌دهی؟") },
                        confirmButton = { TextButton(onClick = { incomingCall = null; acceptCall(signal) }) { Text("پاسخ") } },
                        dismissButton = { TextButton(onClick = { incomingCall = null; declineCall(signal) }) { Text("رد تماس") } }
                    )
                }
            }
        }
    }

    private fun authenticate(email: String, password: String, signup: Boolean) {
        lifecycleScope.launch {
            busy = true; notice = ""
            try {
                if (signup) {
                    supabase.auth.signUpWith(Email) { this.email = email.trim(); this.password = password }
                    notice = "ثبت‌نام ارسال شد. اگر تأیید ایمیل فعال باشد، ایمیلت را تأیید کن و سپس وارد شو."
                } else {
                    supabase.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
                    val id = supabase.auth.currentUserOrNull()?.id ?: error("ورود انجام شد اما شناسه کاربر دریافت نشد.")
                    onAuthenticated(id)
                }
            } catch (e: Exception) {
                notice = e.message ?: "اتصال به حساب ناموفق بود."
            } finally { busy = false }
        }
    }

    private suspend fun onAuthenticated(id: String) {
        userId = id
        profile = supabase.from("profiles").select {
            filter { eq("id", id) }
        }.decodeList<ProfileRow>().firstOrNull()
        if (profile != null) loadConversations()
        startPolling()
    }

    private fun createProfile(usernameInput: String, displayName: String) {
        lifecycleScope.launch {
            busy = true; notice = ""
            try {
                val id = userId ?: error("ابتدا وارد حساب شو.")
                val username = usernameInput.trim().removePrefix("@").lowercase()
                require(Regex("^[a-z0-9_]{3,24}$").matches(username)) {
                    "نام کاربری باید ۳ تا ۲۴ نویسه و فقط شامل حروف انگلیسی، عدد یا _ باشد."
                }
                supabase.from("profiles").update(
                    ProfileUpdate(username, displayName.trim().ifBlank { username })
                ) {
                    filter { eq("id", id) }
                }
                profile = ProfileRow(id, username, displayName.trim().ifBlank { username })
                loadConversations()
            } catch (e: Exception) { notice = e.message ?: "ذخیره پروفایل ناموفق بود." }
            finally { busy = false }
        }
    }

    private suspend fun loadConversations() {
        val id = userId ?: return
        val memberships = supabase.from("conversation_members").select {
            filter { eq("user_id", id) }
        }.decodeList<MemberRow>()
        val ids = memberships.map { it.conversationId }.distinct()
        conversations = if (ids.isEmpty()) emptyList() else supabase.from("conversations").select {
            filter { isIn("id", ids) }
            order("created_at", Order.DESCENDING)
        }.decodeList<ConversationRow>()
    }

    private fun openConversation(conversation: ConversationRow) {
        selectedConversation = conversation
        lifecycleScope.launch {
            try { loadMessages() } catch (e: Exception) { notice = e.message ?: "دریافت پیام‌ها ناموفق بود." }
            refreshJob?.cancel()
            refreshJob = launch {
                while (selectedConversation?.id == conversation.id) {
                    delay(2500)
                    try { loadMessages() } catch (_: Exception) { }
                }
            }
        }
    }

    private suspend fun loadMessages() {
        val conversation = selectedConversation ?: return
        messages = supabase.from("messages").select {
            filter { eq("conversation_id", conversation.id) }
            order("created_at", Order.ASCENDING)
            limit(120)
        }.decodeList<MessageRow>()
    }

    private fun sendMessage(text: String) {
        lifecycleScope.launch {
            val body = text.trim()
            val conversation = selectedConversation ?: return@launch
            val sender = userId ?: return@launch
            if (body.isBlank()) return@launch
            busy = true; notice = ""
            try {
                supabase.from("messages").insert(MessageInsert(conversation.id, sender, "text", body))
                loadMessages()
            } catch (e: Exception) { notice = e.message ?: "ارسال پیام ناموفق بود." }
            finally { busy = false }
        }
    }

    private fun createDirectChat(usernameInput: String) {
        lifecycleScope.launch {
            busy = true; notice = ""
            try {
                val id = userId ?: error("ابتدا وارد حساب شو.")
                val username = usernameInput.trim().removePrefix("@").lowercase()
                require(username.isNotBlank()) { "نام کاربری را وارد کن." }
                val person = supabase.from("profiles").select {
                    filter { eq("username", username) }
                }.decodeList<ProfileRow>().firstOrNull() ?: error("این نام کاربری پیدا نشد.")
                require(person.id != id) { "نمی‌توانی با خودت گفت‌وگو بسازی." }
                val conversation = supabase.from("conversations").insert(
                    ConversationInsert("direct", person.displayName ?: person.username, id)
                ) { select() }.decodeSingle<ConversationRow>()
                supabase.from("conversation_members").insert(listOf(
                    MemberInsert(conversation.id, id, "owner"),
                    MemberInsert(conversation.id, person.id, "member")
                ))
                loadConversations()
                openConversation(conversation)
            } catch (e: Exception) { notice = e.message ?: "ساخت گفت‌وگو ناموفق بود." }
            finally { busy = false }
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            try { endCall(false); supabase.auth.signOut() } catch (_: Exception) { }
            pollJob?.cancel(); refreshJob?.cancel()
            userId = null; profile = null; conversations = emptyList()
            selectedConversation = null; messages = emptyList(); notice = ""
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (userId != null) {
                try {
                    val id = userId ?: break
                    val rows = supabase.from("call_signals").select {
                        filter { eq("recipient_id", id) }
                        order("created_at", Order.DESCENDING)
                        limit(12)
                    }.decodeList<CallSignalRow>()
                    rows.asReversed().forEach { row ->
                        if (handledSignals.add(row.id)) processCallSignal(row)
                    }
                } catch (_: Exception) { }
                delay(1200)
            }
        }
    }

    private suspend fun sendSignal(remoteId: String, conversationId: String, type: String, sdp: String? = null) {
        val id = userId ?: error("نشست کاربر معتبر نیست.")
        val payload = buildJsonObject {
            put("type", type)
            if (sdp != null) put("sdp", sdp)
        }
        supabase.from("call_signals").insert(CallSignalInsert(conversationId, id, remoteId, payload))
    }

    private fun requestAudioPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 701)
            notice = "برای تماس صوتی باید اجازه میکروفون را فعال کنی."
            return
        }
        lifecycleScope.launch { startAudioCall() }
    }

    private suspend fun startAudioCall() {
        val conversation = selectedConversation ?: return
        if (conversation.kind != "direct") { notice = "تماس صوتی فعلاً فقط در گفت‌وگوی خصوصی فعال است."; return }
        if (callPeer != null) { notice = "تماس دیگری در حال اجراست."; return }
        try {
            val members = supabase.from("conversation_members").select {
                filter { eq("conversation_id", conversation.id) }
            }.decodeList<MemberRow>()
            val remote = members.firstOrNull { it.userId != userId }?.userId ?: error("طرف مقابل پیدا نشد.")
            callRemoteId = remote; callConversationId = conversation.id
            createAudioPeer()
            val offer = createOffer(callPeer!!)
            setLocal(callPeer!!, offer)
            waitForIce()
            sendSignal(remote, conversation.id, "offer", callPeer!!.localDescription?.description ?: offer.description)
            callStatus = "در حال برقراری تماس…"; isInCall = true
        } catch (e: Exception) { endCall(false); notice = "شروع تماس ناموفق بود: ${e.message}" }
    }

    private fun createAudioPeer() {
        val factory = peerFactory ?: error("موتور تماس آماده نیست.")
        val servers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val config = PeerConnection.RTCConfiguration(servers)
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                runOnUiThread {
                    callStatus = when (state) {
                        PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> "تماس صوتی برقرار است"
                        PeerConnection.IceConnectionState.FAILED -> "اتصال ناموفق؛ ممکن است شبکه به TURN نیاز داشته باشد."
                        PeerConnection.IceConnectionState.DISCONNECTED -> "اتصال قطع شد…"
                        else -> "در حال اتصال…"
                    }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
        }
        callPeer = factory.createPeerConnection(config, observer) ?: error("ساخت اتصال WebRTC ناموفق بود.")
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("veltrix-audio", audioSource)
        callPeer?.addTrack(audioTrack, listOf("veltrix-audio-stream"))
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        manager.isSpeakerphoneOn = speakerOn
    }

    private suspend fun createOffer(peer: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { cont ->
            peer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null) cont.resume(desc) else cont.resumeWithException(IllegalStateException("ساخت پیشنهاد تماس ناموفق بود."))
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) { cont.resumeWithException(IllegalStateException(error ?: "Offer failed")) }
                override fun onSetFailure(error: String?) {}
            }, MediaConstraints())
        }

    private suspend fun createAnswer(peer: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { cont ->
            peer.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null) cont.resume(desc) else cont.resumeWithException(IllegalStateException("ساخت پاسخ تماس ناموفق بود."))
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) { cont.resumeWithException(IllegalStateException(error ?: "Answer failed")) }
                override fun onSetFailure(error: String?) {}
            }, MediaConstraints())
        }

    private suspend fun setLocal(peer: PeerConnection, desc: SessionDescription) {
        suspendCancellableCoroutine<Unit> { cont ->
            peer.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(error: String?) { cont.resumeWithException(IllegalStateException(error ?: "Local SDP failed")) }
                override fun onCreateSuccess(desc: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
            }, desc)
        }
    }

    private suspend fun setRemote(peer: PeerConnection, desc: SessionDescription) {
        suspendCancellableCoroutine<Unit> { cont ->
            peer.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(error: String?) { cont.resumeWithException(IllegalStateException(error ?: "Remote SDP failed")) }
                override fun onCreateSuccess(desc: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
            }, desc)
        }
    }

    private suspend fun waitForIce() {
        repeat(80) {
            if (callPeer?.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) return
            delay(100)
        }
    }

    private suspend fun processCallSignal(row: CallSignalRow) {
        val type = row.signal["type"]?.jsonPrimitive?.content ?: return
        when (type) {
            "offer" -> {
                if (callPeer != null) sendSignal(row.senderId, row.conversationId, "busy")
                else { incomingCall = row }
            }
            "answer" -> {
                val sdp = row.signal["sdp"]?.jsonPrimitive?.content ?: return
                if (row.senderId == callRemoteId && callPeer != null) {
                    setRemote(callPeer!!, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    callStatus = "تماس صوتی برقرار شد"; isInCall = true
                }
            }
            "hangup", "decline", "busy" -> {
                if (row.senderId == callRemoteId) {
                    callStatus = when (type) { "busy" -> "طرف مقابل مشغول است."; "decline" -> "تماس رد شد."; else -> "تماس پایان یافت." }
                    endCall(false)
                }
            }
        }
    }

    private fun acceptCall(row: CallSignalRow) {
        lifecycleScope.launch {
            try {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 701)
                    notice = "اجازه میکروفون را فعال کن و دوباره تماس بگیر."
                    return@launch
                }
                val sdp = row.signal["sdp"]?.jsonPrimitive?.content ?: error("اطلاعات تماس ناقص است.")
                callRemoteId = row.senderId; callConversationId = row.conversationId
                createAudioPeer()
                setRemote(callPeer!!, SessionDescription(SessionDescription.Type.OFFER, sdp))
                val answer = createAnswer(callPeer!!)
                setLocal(callPeer!!, answer)
                waitForIce()
                sendSignal(row.senderId, row.conversationId, "answer", callPeer!!.localDescription?.description ?: answer.description)
                callStatus = "در حال اتصال به تماس…"; isInCall = true
                val conv = conversations.firstOrNull { it.id == row.conversationId }
                if (conv != null) selectedConversation = conv
            } catch (e: Exception) { endCall(false); notice = "پاسخ به تماس ناموفق بود: ${e.message}" }
        }
    }

    private fun declineCall(row: CallSignalRow) {
        lifecycleScope.launch { try { sendSignal(row.senderId, row.conversationId, "decline") } catch (_: Exception) {} }
    }

    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.isSpeakerphoneOn = speakerOn
    }

    private fun endCall(notify: Boolean) {
        if (notify && callRemoteId != null && callConversationId != null) {
            lifecycleScope.launch { try { sendSignal(callRemoteId!!, callConversationId!!, "hangup") } catch (_: Exception) {} }
        }
        callPeer?.close(); callPeer = null
        audioTrack?.dispose(); audioTrack = null
        audioSource?.dispose(); audioSource = null
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.mode = AudioManager.MODE_NORMAL
        callRemoteId = null; callConversationId = null
        isInCall = false; speakerOn = false
    }

    override fun onDestroy() {
        pollJob?.cancel(); refreshJob?.cancel(); endCall(false)
        peerFactory?.dispose(); peerFactory = null
        super.onDestroy()
    }
}

@Composable
private fun LoginScreen(
    busy: Boolean, notice: String,
    onLogin: (String, String) -> Unit,
    onSignup: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signup by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("VELTRIX", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("MESSENGER", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Spacer(Modifier.height(28.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text(if (signup) "ساخت حساب جدید" else "خوش برگشتی", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("ایمیل") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("رمز عبور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { if (signup) onSignup(email, password) else onLogin(email, password) },
                        enabled = !busy && email.isNotBlank() && password.length >= 8,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text(if (busy) "در حال اتصال…" else if (signup) "ثبت‌نام" else "ورود") }
                    TextButton(onClick = { signup = !signup }) { Text(if (signup) "حساب داری؟ ورود" else "حساب نداری؟ ثبت‌نام") }
                    if (notice.isNotBlank()) Text(notice, color = Soft, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileSetupScreen(
    busy: Boolean, notice: String,
    onSave: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("پروفایل تو", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("برای شروع چت، یک نام کاربری یکتا انتخاب کن.", color = Soft)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("نام کاربری") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(display, { display = it }, Modifier.fillMaxWidth(), label = { Text("نام نمایشی") }, singleLine = true)
            Spacer(Modifier.height(14.dp))
            Button(onClick = { onSave(username, display) }, enabled = !busy && username.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال ذخیره…" else "ذخیره و ادامه") }
            TextButton(onClick = onLogout) { Text("خروج از حساب") }
            if (notice.isNotBlank()) Text(notice, color = Accent)
        }
    }
}

@Composable
private fun ConversationScreen(
    profile: ProfileRow, conversations: List<ConversationRow>, busy: Boolean, notice: String,
    onOpen: (ConversationRow) -> Unit, onNewChat: (String) -> Unit, onLogout: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("VELTRIX MESSENGER", color = Color.White, fontWeight = FontWeight.Black)
                    Text("@${profile.username ?: ""}", color = Soft, fontSize = 12.sp)
                }
                TextButton(onClick = onLogout) { Text("خروج") }
            }
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp)) {
                    Text("شروع گفت‌وگوی خصوصی", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("نام کاربری مخاطب") }, singleLine = true)
                    Button(onClick = { onNewChat(username); username = "" }, enabled = !busy && username.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("ایجاد گفت‌وگو") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("گفت‌وگوهای من", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (conversations.isEmpty()) Text("هنوز گفت‌وگویی نداری.", color = Soft, modifier = Modifier.padding(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(conversation) },
                        colors = CardDefaults.cardColors(containerColor = Panel)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(conversation.title ?: "گفت‌وگوی خصوصی", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(if (conversation.kind == "direct") "خصوصی" else conversation.kind, color = Soft, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (notice.isNotBlank()) Text(notice, color = Accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChatScreen(
    conversation: ConversationRow, messages: List<MessageRow>, busy: Boolean, notice: String,
    inCall: Boolean, callStatus: String,
    onBack: () -> Unit, onSend: (String) -> Unit, onCall: () -> Unit, onHangup: () -> Unit, onSpeaker: () -> Unit
) {
    var draft by remember(conversation.id) { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("بازگشت") }
                Column(Modifier.weight(1f)) {
                    Text(conversation.title ?: "گفت‌وگوی خصوصی", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("VELTRIX", color = Soft, fontSize = 11.sp)
                }
                if (inCall) {
                    TextButton(onClick = onSpeaker) { Text("بلندگو") }
                    TextButton(onClick = onHangup) { Text("قطع") }
                } else TextButton(onClick = onCall) { Text("تماس صوتی") }
            }
            if (callStatus.isNotBlank()) Text(callStatus, color = Accent, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), reverseLayout = false) {
                items(messages, key = { it.id }) { message ->
                    val mine = message.senderId == supabase.auth.currentUserOrNull()?.id
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (mine) Color(0xFF542735) else Panel),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 310.dp).padding(vertical = 4.dp)
                        ) {
                            Text(message.body ?: "", color = Color.White, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
            if (notice.isNotBlank()) Text(notice, color = Accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
            Row(Modifier.fillMaxWidth().background(Panel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("پیامت را بنویس…") }, maxLines = 4)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSend(draft); draft = "" }, enabled = !busy && draft.isNotBlank()) { Text("ارسال") }
            }
        }
    }
}
