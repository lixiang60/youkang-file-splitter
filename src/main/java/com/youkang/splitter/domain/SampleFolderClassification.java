package com.youkang.splitter.domain;

/**
 * 样品文件夹分类结果
 *
 * @author youkang
 */
public enum SampleFolderClassification {

    /** 正常拆分，包含 Bam/Var/Sequence/QC 目标目录 */
    NORMAL,

    /** 空白文件夹（无有效产物，不建子目录） */
    EMPTY
}
