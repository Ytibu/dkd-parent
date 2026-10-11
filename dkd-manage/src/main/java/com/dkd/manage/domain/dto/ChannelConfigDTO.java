package com.dkd.manage.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChannelConfigDTO {

    private String innerCode;
    private List<ChannelSkuDTO> channelList;
}
