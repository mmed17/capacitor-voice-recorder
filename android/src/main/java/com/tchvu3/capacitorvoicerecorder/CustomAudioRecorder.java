package com.tchvu3.capacitorvoicerecorder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CustomAudioRecorder {
  private final String TAG = "CustomAudioRecorder";
  private final Context context;

  private AudioRecord audioRecord;
  private MediaCodec mediaCodec;
  private MediaMuxer mediaMuxer;
  private int audioTrackIndex = -1;
  private long totalSamples = 0;
  private File outputFile;
  private String outputFilePath;
  private CurrentRecordingStatus currentRecordingStatus = CurrentRecordingStatus.NONE;

  private static final int SAMPLE_RATE = 16000;
  private static final int BIT_RATE = 24000;

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

      outputFilePath = new File(context.getCacheDir(), outputPath).getAbsolutePath();
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

      setupMediaCodec();
  }

  private void setupMediaCodec() throws IOException {
    mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);

    MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, 1);
    format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
    format.setLong(MediaFormat.KEY_DURATION, 0);

    mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    mediaMuxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM);

    mediaCodec.start();
  }

  private void encodeAndSaveAudio(int bufferSize) {
    byte[] audioBuffer = new byte[bufferSize];
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    boolean muxerStarted = false;

    while (currentRecordingStatus == CurrentRecordingStatus.RECORDING) {
      int readBytes = audioRecord.read(audioBuffer, 0, bufferSize);
      if (readBytes > 0) {
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(10000);
        if (inputBufferIndex >= 0) {
          ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
          inputBuffer.clear();
          inputBuffer.put(audioBuffer, 0, readBytes);

          // For 16-bit mono audio, each sample is 2 bytes
          long samplesInBuffer = readBytes / 2;
          totalSamples += samplesInBuffer;

          // Calculate PTS in microseconds
          long ptsUs = (totalSamples * 1_000_000L) / SAMPLE_RATE;
          mediaCodec.queueInputBuffer(inputBufferIndex, 0, readBytes, ptsUs, 0);
        }
      }

      int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
      while (outputBufferIndex >= 0) {
        // Check if this is the format change buffer
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
          if (audioTrackIndex == -1) {
            MediaFormat newFormat = mediaCodec.getOutputFormat();
            audioTrackIndex = mediaMuxer.addTrack(newFormat);
            mediaMuxer.start();
            muxerStarted = true;
            Log.d(TAG, "MediaMuxer started with track index: " + audioTrackIndex);
          }
        } else if (bufferInfo.size > 0 && muxerStarted) {
          ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
          outputBuffer.position(bufferInfo.offset);
          outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

          try {
            mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);
          } catch (Exception e) {
            Log.e(TAG, "Error writing to muxer", e);
          }
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

    if (audioRecord != null) {
      audioRecord.stop();
      audioRecord.release();
      audioRecord = null;
    }

    if (mediaCodec != null) {
      mediaCodec.stop();
      mediaCodec.release();
      mediaCodec = null;
    }

    if (mediaMuxer != null) {
      try {
        mediaMuxer.stop();
        mediaMuxer.release();
        mediaMuxer = null;
        totalSamples = 0;

        if (outputFile.exists() && outputFile.length() > 0) {
          Log.d(TAG, "Recording saved successfully: " + outputFile.getAbsolutePath() +
            " (" + outputFile.length() + " bytes)");
        } else {
          Log.e(TAG, "Recording file is empty or doesn't exist!");
        }
      } catch (IllegalStateException e) {
        Log.e(TAG, "Error stopping MediaMuxer", e);
      }
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
