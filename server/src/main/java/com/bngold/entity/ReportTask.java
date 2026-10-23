package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_task")
public class ReportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer bizType;
    private String params;
    private Integer fmt;
    private Integer status;
    private Integer progress;
    private Integer rowCount;
    private String fileKey;
    private Long fileSize;
    private String errorMsg;
    private Long createdBy;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
