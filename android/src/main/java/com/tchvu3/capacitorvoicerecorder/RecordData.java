package com.tchvu3.capacitorvoicerecorder;

import com.getcapacitor.JSObject;

public class RecordData {

    private String outputPath;
    private long duration;

    public RecordData() {}

    public RecordData(String outputPath, long duration) {
        if(outputPath.startsWith("/")) {
          outputPath = outputPath.substring(1);
        }
        this.outputPath = outputPath;
        this.duration = duration;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }


    public JSObject toJSObject() {
        JSObject toReturn = new JSObject();
        toReturn.put("uri", "file://" + outputPath);
        toReturn.put("msDuration", this.duration);
        return toReturn;
    }
}
