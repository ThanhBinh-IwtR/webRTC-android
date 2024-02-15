package com.example.webrtc

import android.content.ContentValues.TAG
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraProvider
import androidx.camera.core.CameraSelector
import org.webrtc.*
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.permissionx.guolindev.PermissionX
import okhttp3.*

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: CameraProvider? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var factory: PeerConnectionFactory? = null
    private var rootEglBase: EglBase? = EglBase.create()
    private var peerConnection: PeerConnection? = null
    private lateinit var client: OkHttpClient
    private lateinit var webSocket: WebSocket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Khởi tạo WebSocket client
        client = OkHttpClient()
        Log.d("checkip","connect")
        val request = Request.Builder().url("ws://192.168.1.141:8765").build()
        val webSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Gửi tin nhắn khi kết nối WebSocket được mở
                Log.d("checkip","connected")
                createPeerConnectionAndOffer()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                processWebSocketMessage(text)
            }

            private fun processWebSocketMessage(message: String) {
                if (message.startsWith("candidate")) {
                    // Handle ICE candidate from server
                    val candidateSdp = message.substring(9)
                    handleRemoteIceCandidate(candidateSdp)
                    Log.d("WebSocket", "Received ICE candidate: $candidateSdp")

                } else if (message.startsWith("answer")) {
                    // Handle SDP answer from server
                    val answerSdp = message.substring(6)
                    handleRemoteAnswer(answerSdp)
                }
                // Add more cases if needed
            }

            private fun handleRemoteIceCandidate(candidateSdp: String) {
                Log.d("checkip", candidateSdp)
                // Convert the received SDP to IceCandidate and add it to the peer connection
                val iceCandidate = IceCandidate("0", 0, candidateSdp)
                peerConnection?.addIceCandidate(iceCandidate)
            }

            private fun handleRemoteAnswer(answerSdp: String) {
                Log.d("checkip", answerSdp)
                // Convert the received SDP to SessionDescription and set it as remote description
                val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    // Implement the necessary methods
                    override fun onCreateSuccess(p0: SessionDescription?) {
                        // Xử lý tạo mô tả thành công nếu cần
                    }

                    override fun onSetSuccess() {
                        // Xử lý đặt mô tả từ xa thành công nếu cần
                    }

                    override fun onCreateFailure(p0: String?) {
                        // Xử lý tạo mô tả thất bại nếu cần
                    }

                    override fun onSetFailure(p0: String?) {
                        // Xử lý đặt mô tả từ xa thất bại nếu cần
                    }
                }, answer)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Connection failed
                Log.d("checkip", t.toString())
            }

        }




        webSocket = client.newWebSocket(request, webSocketListener)
    }

    public fun openPermissioncamera() {

        PermissionX.init(this)
            .permissions(android.Manifest.permission.CAMERA)
            .request { allGranted, _, _ ->
                if (allGranted) {
                    // Quyền được cấp, mở camera
                    openCamera()
                } else {
                    Toast.makeText(this, "Quyền truy cập Camera bị từ chối", Toast.LENGTH_SHORT).show()
                    finish() // Đóng ứng dụng nếu quyền bị từ chối
                }
            }
    }


    private fun createPeerConnectionAndOffer() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(options)

        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase?.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase?.eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()

        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        // Tạo RTCPeerConnection
        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            // Các phương thức callback cho PeerConnection
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                // Handle signaling state change here
            }

            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                // Handle ICE connection state change here
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {
                // Handle ICE connection receiving change here
            }

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                // Handle ICE gathering state change here
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                Log.d("checkip", "candidate")
                webSocket.send("candidate"+ candidate.toString())
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                // Handle removed ICE candidates here
            }

            override fun onAddStream(p0: MediaStream?) {
                // Handle new media stream here
                Log.d("checkip", p0.toString())

            }

            override fun onRemoveStream(p0: MediaStream?) {
                // Handle removed media stream here
            }

            override fun onDataChannel(p0: DataChannel?) {
                // Handle new DataChannel here
                Log.d("checkip", "onData")
            }

            override fun onRenegotiationNeeded() {
                // Handle renegotiation needed here
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                Log.d("checkip", "Video track added to RTCPeerConnection")
            }
        })
        // Tạo SDP offer
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }



        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                // Đặt local description và gửi offer tới signaling server
                peerConnection?.setLocalDescription(this, desc)
                webSocket.send("offer"+desc.toString())
            }

            override fun onSetSuccess() {
                // Handle set success here
                Log.d("checkip", "opencamera")
                openPermissioncamera()

            }

            override fun onCreateFailure(p0: String?) {
                // Handle create failure here

                Log.d("checkip","fail_sendoffer")
            }

            override fun onSetFailure(p0: String?) {
                // Handle set failure here

                Log.d("checkip","fail_sendoffer1")
            }

            // Các phương thức callback khác
        }, constraints)
    }


    private fun openCamera() {
        val viewFinder: PreviewView = findViewById(R.id.viewFinder)

        //Khởi tạo ExecutorService
        cameraExecutor = Executors.newSingleThreadExecutor()

        //Khởi tạo CameraProvider
        cameraProviderFuture = ProcessCameraProvider.getInstance(this@MainActivity)
        cameraProviderFuture?.addListener({
            // Lấy CameraProvider từ Future
            cameraProvider = cameraProviderFuture?.get()

            //Cấu hình CameraSelector
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Bước 7: Cấu hình Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Bước 8: Kết hợp các use case vào CameraProvider
            try {
                // Giữ tham chiếu đến cameraProvider để có thể giải phóng nó khi cần thiết
                (cameraProvider as ProcessCameraProvider?)?.bindToLifecycle(
                    this, cameraSelector, preview
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

            // Bắt đầu luồng camera và thêm nó như một track vào RTCPeerConnection
            val videoCapturer = createVideoCapturer(this)
            if (videoCapturer != null) {
                Log.d("checkip", peerConnection.toString())
                val videoSource = factory?.createVideoSource(videoCapturer.isScreencast)
                val videoTrack = factory?.createVideoTrack("ARDAMSv0", videoSource)
                peerConnection?.addTrack(videoTrack)
            }


        }, ContextCompat.getMainExecutor(this))
    }

    private fun createVideoCapturer(context:Context): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }

            }
        }

        return null

    }

}













