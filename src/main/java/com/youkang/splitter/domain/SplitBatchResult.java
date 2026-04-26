package com.youkang.splitter.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次拆分批次的聚合结果
 *
 * @author youkang
 */
@Data
public class SplitBatchResult {

    /** 各样品分类结果：相对路径 -> 分类 */
    private Map<String, SampleFolderClassification> classifications = new LinkedHashMap<>();

    /** 处理失败的样品数 */
    private int sampleFailed;

    /** 各样品失败的异常信息 */
    private List<String> errorMessages = new ArrayList<>();

    public void addClassification(String relativePath, SampleFolderClassification classification) {
        classifications.put(relativePath, classification);
    }

    public void addSampleError(String relativePath, String message) {
        sampleFailed++;
        errorMessages.add(relativePath + ": " + message);
    }
}
