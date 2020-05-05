package com.yin.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okcoin.commons.okex.open.api.bean.futures.result.Ticker;
import com.okex.websocket.OkexConstant;
import com.yin.service.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 获取合约的最新成交价、买一价、卖一价和24交易量等信息
 * @author yin
 * @createDate 2020年04月05日 下午2:17:28
 */
@Service("instrumentsTickersService")
public class InstrumentsTickersServiceImpl implements WebSocketService, InstrumentsTickersService {
	Map<String, TickerService> tickersServices = new HashMap<>();
	private Map<String, TickerBean> cacheFiveMinPeriodTickers = new HashMap<>();

	@Override
	public void onReceive(Object obj) {

		if (obj instanceof JSONObject) {
			JSONObject root = (JSONObject) obj;
			if (root.containsKey(OkexConstant.TABLE)) {
				String table = root.getString(OkexConstant.TABLE);
				if (OkexConstant.FUTURES_TICKER.equals(table) || OkexConstant.SPOT_TICKER.equals(table)) {
					if (root.containsKey(OkexConstant.DATA)) {
						//处理实时数据
						//if(root.getString(OkexConstant.DATA).indexOf("BTC") != -1 ){
						//	System.out.println(root);
						//}
						JSONArray data = root.getJSONArray(OkexConstant.DATA);
						Iterator it = data.iterator();
						while (it.hasNext()) {
							Object instrument = it.next();
							if (instrument instanceof JSONObject) {
								JSONObject instrumentJSON = (JSONObject) instrument;
								String instrumentId = instrumentJSON.getString(OkexConstant.INSTRUMENT_ID);
								TickerService tickerService = tickersServices.get(instrumentId);
								if (tickerService == null) {
									tickerService = new ArrayTickerServiceImpl();
									tickersServices.put(instrumentId, tickerService);
								}
								tickerService.processData(table,OkexConstant.PARTIAL_ACTION, (JSONObject) instrument);

								//处理5分钟基准价格
								long fivemin = 5 * 60 *1000;
								Calendar cal2 = Calendar.getInstance();
								long currentTime = cal2.getTime().getTime();
								Date indexDate = new Date(currentTime - currentTime%fivemin);
								DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.sss'Z'");
								BigDecimal lastPrice = instrumentJSON.getBigDecimal(OkexConstant.LAST);
								String timestamp = instrumentJSON.getString("timestamp");
								try {
									long time = sdf.parse(timestamp).getTime();
									//System.out.println(indexDate.getTime());
									if (time > indexDate.getTime() - 8 * 60 * 60 * 1000 && time < indexDate.getTime() - 8 * 60 * 60 *1000 + 60*1000) {
										if (cacheFiveMinPeriodTickers.get(instrumentId) == null) {
											putCacheFiveMinPeriodTickers(instrumentId,lastPrice,time);
										} else {
											long cacheTickerTime = cacheFiveMinPeriodTickers.get(instrumentId).getTime();
											//1.判断cache是否过期
											//System.out.println("标准时刻为："+indexDate);
											//System.out.println(cacheTickerTime < indexDate.getTime() - 8 * 60 * 60 * 1000);
											if(cacheTickerTime < indexDate.getTime() - 8 * 60 * 60 * 1000){
												putCacheFiveMinPeriodTickers(instrumentId,lastPrice,time);
											}else {
												//2.判断是否为最接近的Index
												if(time < cacheTickerTime){
													putCacheFiveMinPeriodTickers(instrumentId,lastPrice,time);
												}
											}
										}
									}
								} catch (ParseException e) {
									System.out.println("日期解析错误");
								}
							}
						}

					}
				}
			}
		}
	}

	/**
	 * 获取当前合约ID的最新成交价
	 * @param instrumentId
	 * @return
	 */
	@Override
	public TickerBean getLastPrice(String instrumentId) {
		TickerService tickerService  = tickersServices.get(instrumentId);
		if (tickerService != null)
			return tickerService.getLastPrice(instrumentId);
		return null;
	}

	/**
	 * 获取当前合约ID的5分钟基准成交价
	 * @param instrumentId
	 * @return
	 */
	@Override
	public TickerBean getFiveMinIndexPrice(String instrumentId) {
		TickerBean ticker  = cacheFiveMinPeriodTickers.get(instrumentId);
		if (ticker != null)
			return ticker;
		return null;
	}

	private void putCacheFiveMinPeriodTickers(String instrumentId,BigDecimal lastPrice,long time){
		TickerBean ticker = new TickerBean();
		ticker.setInstrumentId(instrumentId);
		ticker.setIndexPricePrice(lastPrice);
		ticker.setTime(time);
		cacheFiveMinPeriodTickers.put(instrumentId, ticker);
	}

}
