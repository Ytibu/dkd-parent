package com.dkd.manage.domain.dto;

import lombok.Data;

@Data
public class TaskDetailsDTO {

    private String channelCode;
    private Long expectCapacity;
    private Long skuId;
    private String skuName;
    private String skuImage;
}
