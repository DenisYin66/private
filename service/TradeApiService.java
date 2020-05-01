package com.yin.service;

import java.util.List;
import java.util.concurrent.TimeoutException;

import com.yin.service.impl.HedgingTrade;

/**
* @author yin
* @createDate 2018年12月28日 上午10:43:39
*/
public interface TradeApiService {
	public void batchOrders(List<HedgingTrade> trades) throws TimeoutException;
	public void repairHedgingTrade(HedgingTrade trade) throws TimeoutException;
	public void order(HedgingTrade trade) throws TimeoutException;
	public void cancelHedgingTrade(HedgingTrade trade) throws TimeoutException;
	public void batchCancel(List<HedgingTrade> trades) throws TimeoutException;
}
