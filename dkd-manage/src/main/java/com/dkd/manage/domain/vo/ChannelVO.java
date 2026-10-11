package com.dkd.manage.domain.vo;

import com.dkd.manage.domain.Channel;
import com.dkd.manage.domain.Sku;
import lombok.Data;

@Data
public class ChannelVO extends Channel {

    private Sku sku;
}
