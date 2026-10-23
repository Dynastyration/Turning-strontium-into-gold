package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer sourceId;
    private String deviceNum;
    private String name;
    private Integer typeId;
    private Long projectId;
    private Integer runStatus;
    private Integer battery;
    private Integer sensorCount;
    private Integer collectPeriod;
    private Integer cmdInterval;
    private Integer debugMode;
    private Integer cfgStatus;
    private Integer powerType;
    private Integer sortNo;
    private BigDecimal lng;
    private BigDecimal lat;
    private LocalDate simExpire;
    private Integer mapLevel;
    private String searchLocation;
    private LocalDate installAt;
    private LocalDateTime lastPushAt;
    private String remark;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
