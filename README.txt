Android app project description

GoogleMobileVisionDemo

This Demo contains a few functions: TCP/IP file transfer between client and server, video encoder and decoder, face detection, tracking and anonymization in images and videos. 


Client:
	Initialize TCP file transfer and encrypt file while transferring

Server:
	Receive files sent from Client.

Image:
	Perform face detection and its associated facial landmarks(e.g. eyes, nose, etc) on the selected photo using Google Mobile Vision API then draw graphics over the detected face. The face detector is created in onCreate() and initializes options for detecting faces in a photo. Given a bitmap, create frame instance from the bitmap to supply to the detector. The detector can be called synchronously with a frame to detect faces. The result returned includes a collection of face instances, then iterate over the faces to do the drawing. At the end of the code, release the detector instance once its no longer needed. Same face detector instance can be used for detection with multiple photos.


Video:
	Perform face detection and graphic overlay on selected video on a frame level (i.e. extract each frame, perform drawing and then encode back to a new video file). Code added to simulate bus stops to perform face detection on bus footage to detect student occupancy on school buses, total count of riders, daily count of riders, student nearness on bus seats due to cover 19, etc.


Face Tracker:
	Perform face tracking on customized camera using the android cameraSource. Tracks multiple faces simultaneously and draw rectangle around each, indicating the approximate position, size, and face ID. Camera source is created to capture video images from the camera, and continuously stream those images into the detector and its associated processor pipeline. Once started, the camera will continuously send preview images into the pipeline, the cameraSource component manages receiving these images and passing the images into the detector at a maximum rate of 30 frames/second.




