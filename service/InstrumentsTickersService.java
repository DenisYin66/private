package com.yin.service;

import com.yin.service.impl.TickerBean;

/**
* @author yin
* @createDate 2018年11月27日 上午9:51:01
*/
public interface InstrumentsTickersService {
	public TickerBean getLastPrice(String instrumentId);//获取最新成交价
}
