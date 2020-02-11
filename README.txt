Android app project description

GoogleMobileVisionDemo

	This Demo contains a few functions: TCP/IP file transfer between client and server, video encoder and decoder, face detection, tracking and anonymization in images and videos. 
	Videos are processed in a frame level to detect faces and anonymize them. Frames are extracted from mpeg4 file using MediaExtracter and feed individual access units into a MediaCodec decoder. The frames are decoded to a Surface created from SurfaceTexture, rendered(off-screen) into a buffer, extracted with glReadPixels() and the extracted bitmap is supplied to a face detector from the Google mobile vision API. The detector calls synchronously with a frame to detect faces. The result returned includes a collection of face instances which could be iterated to anonymize by blurring the detected faces. Frames are then encoded back to a new mpeg4 file.



