package com.tchvu3.capacitorvoicerecorder;

import android.Manifest;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;

@CapacitorPlugin(
        name = "VoiceRecorder",
        permissions = {@Permission(alias = VoiceRecorder.RECORD_AUDIO_ALIAS, strings = {Manifest.permission.RECORD_AUDIO})}
)
public class VoiceRecorder extends Plugin {
    private CustomAudioRecorder mediaRecorder;

    static final String TAG = "VoiceRecorder";
    static final String RECORD_AUDIO_ALIAS = "voice recording";

    @PluginMethod()
    public void canDeviceVoiceRecord(PluginCall call) {
        if (CustomAudioRecorder.canPhoneCreateMediaRecorder(getContext())) {
            call.resolve(ResponseGenerator.successResponse());
        } else {
            call.resolve(ResponseGenerator.failResponse());
        }
    }

    @PluginMethod()
    public void requestAudioRecordingPermission(PluginCall call) {
        if (doesUserGaveAudioRecordingPermission()) {
            call.resolve(ResponseGenerator.successResponse());
        } else {
            requestPermissionForAlias(RECORD_AUDIO_ALIAS, call, "recordAudioPermissionCallback");
        }
    }

    @PermissionCallback
    private void recordAudioPermissionCallback(PluginCall call) {
        this.hasAudioRecordingPermission(call);
    }

    @PluginMethod()
    public void hasAudioRecordingPermission(PluginCall call) {
        call.resolve(ResponseGenerator.fromBoolean(doesUserGaveAudioRecordingPermission()));
    }

    @PluginMethod()
    public void startRecording(PluginCall call) {
        if (!CustomAudioRecorder.canPhoneCreateMediaRecorder(getContext())) {
            call.reject(Messages.CANNOT_RECORD_ON_THIS_PHONE);
            return;
        }

        if (!doesUserGaveAudioRecordingPermission()) {
            call.reject(Messages.MISSING_PERMISSION);
            return;
        }

        if (this.isMicrophoneOccupied()) {
            call.reject(Messages.MICROPHONE_BEING_USED);
            return;
        }

        if (mediaRecorder != null) {
            call.reject(Messages.ALREADY_RECORDING);
            return;
        }

        String outputPath = call.getString("path");

        try {
            mediaRecorder = new CustomAudioRecorder(getContext(), outputPath);
            mediaRecorder.startRecording();
            call.resolve(ResponseGenerator.successResponse());
        } catch (Exception exp) {
            call.reject(Messages.FAILED_TO_RECORD, exp);
        }
    }

    @PluginMethod()
    public void stopRecording(PluginCall call) {
        if (mediaRecorder == null) {
            call.reject(Messages.RECORDING_HAS_NOT_STARTED);
            return;
        }

        try {
            mediaRecorder.stopRecording();
            File recordedFile = mediaRecorder.getOutputFile();
            String outputPath = recordedFile.getAbsolutePath();

            long duration = getAudioDuration(recordedFile);
            RecordData recordData = new RecordData(outputPath, duration);

            call.resolve(ResponseGenerator.dataResponse(recordData.toJSObject()));

        } catch (Exception exp) {
            call.reject(Messages.FAILED_TO_FETCH_RECORDING, exp);
        } finally {
            mediaRecorder = null;
        }
    }

    @PluginMethod()
    public void getCurrentStatus(PluginCall call) {
        if (mediaRecorder == null) {
            call.resolve(ResponseGenerator.statusResponse(CurrentRecordingStatus.NONE));
        } else {
            call.resolve(ResponseGenerator.statusResponse(mediaRecorder.getCurrentStatus()));
        }
    }

    private boolean doesUserGaveAudioRecordingPermission() {
        return getPermissionState(VoiceRecorder.RECORD_AUDIO_ALIAS).equals(PermissionState.GRANTED);
    }

    private boolean isMicrophoneOccupied() {
        AudioManager audioManager = (AudioManager) this.getContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null)
            return true;
        return audioManager.getMode() != AudioManager.MODE_NORMAL;
    }

  private long getAudioDuration(File file) {
    if (file.exists() && file.canRead()) {
      MediaMetadataRetriever retriever = new MediaMetadataRetriever();
      try {
        retriever.setDataSource(file.getAbsolutePath());
        String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        retriever.release();

        if (durationStr != null) {
          return Long.parseLong(durationStr); // Duration in milliseconds
        } else {
          Log.e(TAG, "Duration metadata is not available.");
          return 0;
        }
      } catch (Exception e) {
        e.printStackTrace();
        Log.e(TAG, "Error retrieving duration: " + e.getMessage());
        return 0;
      }
    } else {
      Log.e(TAG, "File does not exist or is not readable.");
      return 0;
    }
  }
}
