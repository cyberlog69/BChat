package com.praveen.bchat.data.transport

import com.praveen.bchat.domain.model.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.*
import platform.MultipeerConnectivity.*
import platform.darwin.NSObject
import platform.posix.memcpy

interface IosTransportListener {
    fun onPeerDiscovered(peer: PeerDevice)
    fun onPeerLost(peerId: String)
    fun onConnected(peer: PeerDevice)
    fun onDisconnected(peerId: String)
    fun onPacketReceived(peerId: String, packet: ProtocolPacket)
}

@OptIn(ExperimentalForeignApi::class)
class IosMultipeerTransport : NSObject(), MCSessionDelegateProtocol, MCNearbyServiceAdvertiserDelegateProtocol, MCNearbyServiceBrowserDelegateProtocol {

    private val serviceType = "bchat-p2p"
    private var myPeerID: MCPeerID? = null
    private var session: MCSession? = null
    private var advertiser: MCNearbyServiceAdvertiser? = null
    private var browser: MCNearbyServiceBrowser? = null

    private var listener: IosTransportListener? = null

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val connectedPeers: StateFlow<List<PeerDevice>> = _connectedPeers.asStateFlow()

    val transportType: TransportType = TransportType.NEARBY_SHARE

    private val peerMap = mutableMapOf<String, MCPeerID>()

    fun setListener(listener: IosTransportListener) {
        this.listener = listener
    }

    fun startAdvertising(deviceName: String) {
        val peerID = MCPeerID(displayName = deviceName)
        this.myPeerID = peerID

        val sess = MCSession(peer = peerID, securityIdentity = null, encryptionPreference = MCEncryptionNone)
        sess.delegate = this
        this.session = sess

        val adv = MCNearbyServiceAdvertiser(peer = peerID, discoveryInfo = null, serviceType = serviceType)
        adv.delegate = this
        adv.startAdvertisingPeer()
        this.advertiser = adv
    }

    fun stopAdvertising() {
        advertiser?.stopAdvertisingPeer()
        advertiser = null
    }

    fun startDiscovery() {
        val peerID = myPeerID ?: MCPeerID(displayName = "BChat-iOS")
        this.myPeerID = peerID

        if (session == null) {
            val sess = MCSession(peer = peerID, securityIdentity = null, encryptionPreference = MCEncryptionNone)
            sess.delegate = this
            this.session = sess
        }

        val brows = MCNearbyServiceBrowser(peer = peerID, serviceType = serviceType)
        brows.delegate = this
        brows.startBrowsingForPeers()
        this.browser = brows
    }

    fun stopDiscovery() {
        browser?.stopBrowsingForPeers()
        browser = null
    }

    fun connect(peer: PeerDevice) {
        val targetPeerID = peerMap[peer.id] ?: return
        val currentSession = session ?: return
        browser?.invitePeer(targetPeerID, toSession = currentSession, withContext = null, timeout = 30.0)
    }

    fun disconnect(peerId: String) {
        session?.disconnect()
    }

    fun disconnectAll() {
        session?.disconnect()
    }

    fun sendPacket(peerId: String, packet: ProtocolPacket) {
        val targetPeerID = peerMap[peerId] ?: return
        val bytes = packet.toByteArray()
        val data = bytes.toNSData()
        session?.sendData(data, toPeers = listOf(targetPeerID), withMode = MCSessionSendDataMode.MCSessionSendDataReliable, error = null)
    }

    fun broadcastPacket(packet: ProtocolPacket) {
        val currentSession = session ?: return
        val connected = currentSession.connectedPeers
        if (connected.isNotEmpty()) {
            val bytes = packet.toByteArray()
            val data = bytes.toNSData()
            currentSession.sendData(data, toPeers = connected, withMode = MCSessionSendDataMode.MCSessionSendDataReliable, error = null)
        }
    }

    fun release() {
        stopAdvertising()
        stopDiscovery()
        session?.disconnect()
    }

    // MCSessionDelegate
    override fun session(session: MCSession, peer: MCPeerID, didChangeState: MCSessionState) {
        val peerDevice = PeerDevice(
            id = peer.displayName,
            name = peer.displayName,
            transportType = TransportType.NEARBY_SHARE,
            connectionStatus = when (didChangeState) {
                MCSessionState.MCSessionStateConnected -> ConnectionStatus.CONNECTED
                MCSessionState.MCSessionStateConnecting -> ConnectionStatus.CONNECTING
                else -> ConnectionStatus.DISCONNECTED
            }
        )
        when (didChangeState) {
            MCSessionState.MCSessionStateConnected -> listener?.onConnected(peerDevice)
            MCSessionState.MCSessionStateNotConnected -> listener?.onDisconnected(peer.displayName)
            else -> {}
        }
    }

    override fun session(session: MCSession, didReceiveData: NSData, fromPeer: MCPeerID) {
        val bytes = didReceiveData.toByteArray()
        val packet = ProtocolPacket.fromByteArray(bytes)
        if (packet != null) {
            listener?.onPacketReceived(fromPeer.displayName, packet)
        }
    }

    override fun session(session: MCSession, didReceiveStream: NSInputStream, withName: String, fromPeer: MCPeerID) {}
    override fun session(session: MCSession, didStartReceivingResourceWithName: String, fromPeer: MCPeerID, withProgress: NSProgress) {}
    override fun session(session: MCSession, didFinishReceivingResourceWithName: String, fromPeer: MCPeerID, atURL: NSURL?, withError: NSError?) {}

    // MCNearbyServiceAdvertiserDelegate
    override fun advertiser(advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer: MCPeerID, withContext: NSData?, invitationHandler: (Boolean, MCSession?) -> Unit) {
        invitationHandler(true, session)
    }

    // MCNearbyServiceBrowserDelegate
    override fun browser(browser: MCNearbyServiceBrowser, foundPeer: MCPeerID, withDiscoveryInfo: Map<Any?, *>?) {
        peerMap[foundPeer.displayName] = foundPeer
        val peer = PeerDevice(
            id = foundPeer.displayName,
            name = foundPeer.displayName,
            transportType = TransportType.NEARBY_SHARE
        )
        listener?.onPeerDiscovered(peer)
    }

    override fun browser(browser: MCNearbyServiceBrowser, lostPeer: MCPeerID) {
        peerMap.remove(lostPeer.displayName)
        listener?.onPeerLost(lostPeer.displayName)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}
