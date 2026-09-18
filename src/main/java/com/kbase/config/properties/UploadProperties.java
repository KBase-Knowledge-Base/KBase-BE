package com.kbase.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "kbase.upload")
public class UploadProperties {

    private DataSize documentMaxSize = DataSize.ofMegabytes(50);
    private DataSize imageMaxSize = DataSize.ofMegabytes(20);
    private DataSize videoMaxSize = DataSize.ofMegabytes(500);
    private int maxBatchFiles = 10;

    public DataSize getDocumentMaxSize() {
        return documentMaxSize;
    }

    public void setDocumentMaxSize(DataSize documentMaxSize) {
        this.documentMaxSize = documentMaxSize;
    }

    public DataSize getImageMaxSize() {
        return imageMaxSize;
    }

    public void setImageMaxSize(DataSize imageMaxSize) {
        this.imageMaxSize = imageMaxSize;
    }

    public DataSize getVideoMaxSize() {
        return videoMaxSize;
    }

    public void setVideoMaxSize(DataSize videoMaxSize) {
        this.videoMaxSize = videoMaxSize;
    }

    public int getMaxBatchFiles() {
        return maxBatchFiles;
    }

    public void setMaxBatchFiles(int maxBatchFiles) {
        this.maxBatchFiles = maxBatchFiles;
    }
}
