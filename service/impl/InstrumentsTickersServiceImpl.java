package com.yin.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okex.websocket.OkexConstant;
import com.yin.service.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 获取合约的最新成交价、买一价、卖一价和24交易量等信息
 * @author yin
 * @createDate 2020年04月05日 下午2:17:28
 */
@Service("instrumentsTickersService")
public class InstrumentsTickersServiceImpl implements WebSocketService, InstrumentsTickersService {
	Map<String, TickerService> tickersServices = new HashMap<>();

	@Override
	public void onReceive(Object obj) {

		if (obj instanceof JSONObject) {
			JSONObject root = (JSONObject) obj;
			if (root.containsKey(OkexConstant.TABLE)) {
				String table = root.getString(OkexConstant.TABLE);
				if (OkexConstant.FUTURES_TICKER.equals(table) || OkexConstant.SPOT_TICKER.equals(table)) {
					if (root.containsKey(OkexConstant.DATA)) {
						if(root.getString(OkexConstant.DATA).indexOf("BTC-USD-2006") != -1 ){
							System.out.println(root);
						}
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
}
