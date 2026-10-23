package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_command")
public class DeviceCommand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;
    private String cmdType;
    private String payload;
    private Integer status;
    private Long issuedBy;
    private LocalDateTime issuedAt;
    private LocalDateTime ackAt;
    private Integer retryCount;
    private LocalDateTime createdAt;
}
