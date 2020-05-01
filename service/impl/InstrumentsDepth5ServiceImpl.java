package com.yin.service.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okex.websocket.OkexConstant;
import com.yin.service.DepthService;
import com.yin.service.InstrumentsDepthService;
import com.yin.service.WebSocketService;

/**
 * 5档委托深度数据
 * @author yin
 * @createDate 2018年12月26日 下午2:17:28
 */
@Service("instrumentsDepthService")
public class InstrumentsDepth5ServiceImpl implements WebSocketService, InstrumentsDepthService {
	Map<String, DepthService> depthServices = new HashMap<>();

	@Override
	public void onReceive(Object obj) {

		if (obj instanceof JSONObject) {
			JSONObject root = (JSONObject) obj;
			if (root.containsKey(OkexConstant.TABLE)) {
				String table = root.getString(OkexConstant.TABLE);
				if (OkexConstant.FUTURES_DEPTH5.equals(table) || OkexConstant.SPOT_DEPTH5.equals(table)) {
					if (root.containsKey(OkexConstant.DATA)) {
						JSONArray data = root.getJSONArray(OkexConstant.DATA);
						Iterator it = data.iterator();
						while (it.hasNext()) {
							Object instrument = it.next();
							if (instrument instanceof JSONObject) {
								JSONObject instrumentJSON = (JSONObject) instrument;
								String instrumentId = instrumentJSON.getString(OkexConstant.INSTRUMENT_ID);
								DepthService depthService = depthServices.get(instrumentId);
								if (depthService == null) {
									depthService = new ArrayDepthServiceImpl();
									depthServices.put(instrumentId, depthService);
								}
								depthService.processData(table,OkexConstant.PARTIAL_ACTION, (JSONObject) instrument);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * 获取当前合约ID的第几档卖价深度委托数据
	 * @param instrumentId
	 * @param pos
	 * @return
	 */
	@Override
	public Level2Bean getSellLevel2Postion(String instrumentId, int pos) {
		DepthService depthService = depthServices.get(instrumentId);
		if (depthService != null)
			return depthService.getSellLevel2Postion(pos);
		return null;
	}

	/**
	 * 获取当前合约ID的第几档买价深度委托数据
	 * @param instrumentId
	 * @param pos
	 * @return
	 */
	@Override
	public Level2Bean getBuyLevel2Postion(String instrumentId, int pos) {
		DepthService depthService = depthServices.get(instrumentId);
		if (depthService != null)
			return depthService.getBuyLevel2Postion(pos);
		return null;
	}

	/**
	 * 卖一价
	 * @param instrumentId
	 * @return
	 */
	@Override
	public Level2Bean getSellFirst(String instrumentId) {
		DepthService depthService = depthServices.get(instrumentId);
		if (depthService != null)
			return depthService.getSellFirst();
		return null;
	}

	/**
	 * 买一价
	 * @param instrumentId
	 * @return
	 */
	@Override
	public Level2Bean getBuyFirst(String instrumentId) {
		DepthService depthService = depthServices.get(instrumentId);
		if (depthService != null)
			return depthService.getBuyFirst();
		return null;
	}

}
