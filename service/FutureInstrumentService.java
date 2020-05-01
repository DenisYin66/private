package com.yin.service;

import java.util.List;

import com.okcoin.commons.okex.open.api.bean.futures.result.Ticker;
import com.okex.websocket.FutureInstrument;

/**
* @author yin
* @createDate 2018年12月27日 上午11:00:48
*/
public interface FutureInstrumentService {
	public FutureInstrument getFutureInstrument(String instrumentId);
	public FutureInstrument getFutureInstrument(String coin,String contractType);
	public Ticker getInstrumentTicker(String coin, String contractType);
	public List<String> getSubscribes();
	public void refresh();
}
