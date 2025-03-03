package com.tchvu3.capacitorvoicerecorder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CustomAudioRecorder {
    private final String TAG = "CustomAudioRecorder";
    private final Context context;

    private AudioRecord audioRecord;
    private MediaCodec mediaCodec;
    private File outputFile;
    private String outputFilePath;
    private FileOutputStream outputStream;
    private CurrentRecordingStatus currentRecordingStatus = CurrentRecordingStatus.NONE;

    private static final int SAMPLE_RATE = 16000;
    private static final int BIT_RATE = 32000;

    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public CustomAudioRecorder(Context context, String path) throws IOException {
        this.context = context;
        generateAudioRecorder(path);
    }

    private void generateAudioRecorder(String outputPath) throws IOException {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);

        outputFilePath = new File(context.getFilesDir(), outputPath).getAbsolutePath();
        outputFile = new File(outputFilePath);

        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
          boolean dirCreated = parentDir.mkdirs();
        }

        boolean fileCreated = outputFile.createNewFile();

        if (!fileCreated) {
          throw new IOException("Failed to create output file at: " + outputFilePath);
        }

        if (!outputFile.canWrite()) {
          throw new IOException("Cannot write to output file at: " + outputFilePath);
        }

        outputStream = new FileOutputStream(outputFilePath);
        setupMediaCodec();
    }

    private void setupMediaCodec() throws IOException {
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);

        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
    }

    private void encodeAndSaveAudio(int bufferSize) {
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

        byte[] audioBuffer = new byte[bufferSize];
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (currentRecordingStatus == CurrentRecordingStatus.RECORDING) {
            int readBytes = audioRecord.read(audioBuffer, 0, bufferSize);
            if (readBytes > 0) {
                int inputBufferIndex = mediaCodec.dequeueInputBuffer(10000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(audioBuffer, 0, readBytes);
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, readBytes, System.nanoTime() / 1000, 0);
                }
            }

            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] encodedData = new byte[bufferInfo.size];
                outputBuffer.get(encodedData);

                try {
                    outputStream.write(encodedData);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
            }
        }
    }

    public void startRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        audioRecord.startRecording();
        new Thread(() -> encodeAndSaveAudio(bufferSize)).start();
        currentRecordingStatus = CurrentRecordingStatus.RECORDING;
    }

    public void stopRecording() {
        currentRecordingStatus = CurrentRecordingStatus.NONE;

        audioRecord.stop();
        audioRecord.release();

        mediaCodec.stop();
        mediaCodec.release();

        try {
            outputStream.flush();
            outputStream.close();

            if (outputFile.exists() && outputFile.length() > 0) {
              Log.d(TAG, "Recording saved successfully: " + outputFile.getAbsolutePath() + " (" + outputFile.length() + " bytes)");
            } else {
              Log.e(TAG, "Recording file is empty or doesn't exist!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File getOutputFile() {
        return outputFile;
    }

    public CurrentRecordingStatus getCurrentStatus() {
        return currentRecordingStatus;
    }

    public static boolean canPhoneCreateMediaRecorder(Context context) {
        return true;
    }
}
