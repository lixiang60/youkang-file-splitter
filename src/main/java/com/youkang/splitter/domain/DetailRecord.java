package com.youkang.splitter.domain;

import lombok.Data;

/**
 * Excel 明细表单行数据
 */
@Data
public class DetailRecord {

    /** 生产编号 */
    private String productionCode;

    /** 订单号 */
    private String orderId;

    /** 客户姓名 */
    private String customerName;

    /** 样品编号 */
    private String sampleCode;

    /** 序列 */
    private String sequence;

    /** 样品类型 */
    private String sampleType;

    /** 测序项目 */
    private String sequencingProject;

    /** 预估片段大小 */
    private String estimatedFragmentSize;

    /** 板号 */
    private String boardNo;

    /** 孔号 */
    private String wellNo;

    /** barcode */
    private String barcode;

    /** 浓度 */
    private String concentration;

    /** 备注 */
    private String remark;
}
