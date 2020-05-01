package com.yin.service;

import java.util.List;

import com.okex.websocket.SpotInstrument;

/**
* @author yin
* @createDate 2018年12月27日 上午11:00:48
*/
public interface SpotInstrumentService {
	public SpotInstrument getSpotInstrument(String instrumentId);
	public SpotInstrument getSpotInstrument(String base, String quote);
	public List<String> getSubscribes();
}
