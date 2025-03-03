package com.tchvu3.capacitorvoicerecorder;

import com.getcapacitor.JSObject;

public class RecordData {

    private String outputPath;

    public RecordData() {}

    public RecordData(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }


    public JSObject toJSObject() {
        JSObject toReturn = new JSObject();
        toReturn.put("outputPath", outputPath);
        return toReturn;
    }
}
