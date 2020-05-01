package com.yin.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okex.websocket.OkexConstant;
import com.yin.service.DepthService;
import com.yin.service.TickerService;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;

/**
 * 使用数组实现委托数据排序
 * 
 * @author yin
 *
 */
public class ArrayTickerServiceImpl implements TickerService {

	/**
	 * 从小到大
	 */
	private TickerBean lastTicker = new TickerBean();// 从小到大

	@Override
	public void processData(String table, String action, JSONObject data) {
		try {
			processDataV3(table, true, data);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void processDataV3(String table, boolean init, JSONObject data) throws Exception {
		String instrumentId = data.getString(OkexConstant.INSTRUMENT_ID);
		if (data.containsKey(OkexConstant.LAST)) {
			    BigDecimal lastPrice = data.getBigDecimal(OkexConstant.LAST);
			    TickerBean tickerBean = new TickerBean();
			    tickerBean.setTable(table);
			    tickerBean.setLastPrice(lastPrice);
			    tickerBean.setInstrumentId(instrumentId);
			    lastTicker = tickerBean;
		}
	}


	/*
	 * crc32 检验前25个价位
	 */
	@Override
	public boolean validateChecksum(long checksum) {
		return true;
	}

	@Override
	public TickerBean getLastPrice(String instrumentId) {
		return lastTicker;
	}
}
