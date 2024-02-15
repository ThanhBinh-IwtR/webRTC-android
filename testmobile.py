# import asyncio
# import cv2
# import numpy as np

# import websockets
# from aiortc import RTCPeerConnection, RTCSessionDescription, VideoStreamTrack

# pc = RTCPeerConnection()

# @pc.on('track')
# async def on_track(track):
#     if isinstance(track, VideoStreamTrack):
#         print("Video track added")
        
#         # create a new OpenCV window
#         window_name = 'Video Window'
#         cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
        
#         async for frame in track.recv():
#             # convert PIL image to OpenCV format
#             img = cv2.cvtColor(np.array(frame.to_pil()), cv2.COLOR_RGB2BGR)
            
#             # display image
#             cv2.imshow(window_name, img)
#             if cv2.waitKey(1) & 0xFF == ord('q'):
#                 break

#         cv2.destroyAllWindows()


# async def set_offer(offer):
#     await pc.setRemoteDescription(offer)

# async def echo(websocket, path):
#     async for message in websocket:
#         offer_sdp = message
#         offer_type = "offer"
#         offer = RTCSessionDescription(offer_sdp, offer_type)
#         await set_offer(offer)

#         # Tạo answer
#         answer = await pc.createAnswer()

#         # Đặt answer làm local description
#         await pc.setLocalDescription(answer)

#         # Gửi answer trở lại ứng dụng Android
#         await websocket.send(pc.localDescription.sdp)

# start_server = websockets.serve(echo, "0.0.0.0", 8765)

# asyncio.get_event_loop().run_until_complete(start_server)
# asyncio.get_event_loop().run_forever()


import asyncio
import cv2
import numpy as np

import websockets
from aiortc import RTCPeerConnection, RTCSessionDescription, VideoStreamTrack, RTCIceCandidate

pc = RTCPeerConnection()

@pc.on('datachannel')
def on_datachannel(channel):
    @channel.on('message')
    def on_message(message):
        print(f"Received message from client: {message}")
        # Handle the received message, e.g., setRemoteDescription

@pc.on('iceconnectionstatechange')
def on_iceconnectionstatechange():
    print("ICE Connection State:", pc.iceConnectionState)

async def send_ice_candidate(candidate):
    await websockets.send(candidate)

@pc.on('icecandidate')
def on_icecandidate(candidate):
    print("Sending ICE candidate to client")
    asyncio.ensure_future(send_ice_candidate(candidate))

@pc.on('track')
async def on_track(track):
    if isinstance(track, VideoStreamTrack):
        print("Video track added")

        # create a new OpenCV window
        window_name = 'Video Window'
        cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)

        async for frame in track.recv():
            # convert PIL image to OpenCV format
            img = cv2.cvtColor(np.array(frame.to_pil()), cv2.COLOR_RGB2BGR)

            # display image
            cv2.imshow(window_name, img)
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break

        cv2.destroyAllWindows()

async def set_offer(offer):
    await pc.setRemoteDescription(offer)

async def on_connect(websocket, path):
    global pc
    
    # await websocket.send(pc.localDescription.sdp)
    
    async for message in websocket:
        print(message)
        if message.startswith("candidate"):
            # Handle ICE candidate from client
            
            cand = message[9:]
            print("abc"+cand)
            candidate = RTCIceCandidate(cand)
            await pc.addIceCandidate(candidate)
        elif message.startswith("offer"):
            # Handle SDP offer from client
            offer_sdp = message[5:]
            print(offer_sdp)
            offer_type = "offer"
            offer = RTCSessionDescription(offer_sdp, offer_type)
            await set_offer(offer)

            # Create answer
            answer = await pc.createAnswer()

            # Set answer as local description
            await pc.setLocalDescription(answer) 

            # Send answer back to Android app
            await websocket.send("answer" + pc.localDescription.sdp)

start_server = websockets.serve(on_connect, "0.0.0.0", 8765)

asyncio.get_event_loop().run_until_complete(start_server)
asyncio.get_event_loop().run_forever()


