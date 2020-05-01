package com.yin.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.okcoin.commons.okex.open.api.bean.futures.result.Ticker;
import com.yin.service.InstrumentsTickersService;
import org.apache.activemq.command.ActiveMQDestination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;
import com.okex.websocket.FutureInstrument;
import com.yin.service.FutureInstrumentService;
import com.yin.service.HedgingDataService;
import com.yin.service.InstrumentsDepthService;

/**
 * 数据采集服务，3秒采集一次价差数据，推送到MQ
 * @author yin
 * @createDate 2018年12月11日 下午2:49:31
 */
@Service("hedgingDataService")
@EnableScheduling
public class HedgingDataServiceImpl extends BaseDataServiceImpl implements HedgingDataService {
	@Resource(name = "instrumentsDepthService")
	InstrumentsDepthService instrumentsDepthService;
	@Resource(name = "instrumentsTickersService")
	InstrumentsTickersService instrumentsTickersService;
	@Autowired
	private FutureInstrumentService futureInstrumentService;
	@Autowired
	private SystemConfig systemConfig;
	@Resource
	private JmsTemplate jmsTemplate;
	@Resource(name = "destinations")
	private Destination destinations;
	private Map<String, ActiveMQDestination> destinationsMap = new ConcurrentHashMap<String, ActiveMQDestination>();

	@Scheduled(fixedDelay = 3000)
	private void process() {
		String[] coins = null;
		if (!Strings.isNullOrEmpty(systemConfig.getCoins())) {
			coins = systemConfig.getCoins().split(",");
		}
		coins = new String[]{"btc"};
		for (String coin : coins) {
			HedgingContext hc = getHedgingContext(coin);
			FutureInstrument thisInstrument = futureInstrumentService.getFutureInstrument(coin, "this_week");
			FutureInstrument nextInstrument = futureInstrumentService.getFutureInstrument(coin, "next_week");
			FutureInstrument quarterInstrument = futureInstrumentService.getFutureInstrument(coin, "quarter");
			Ticker thisTickerIndex = futureInstrumentService.getInstrumentTicker(coin, "this_week");
			Ticker nextTickerIndex = futureInstrumentService.getInstrumentTicker(coin, "next_week");
			Ticker quarterTickerIndex = futureInstrumentService.getInstrumentTicker(coin, "quarter");
			if(thisInstrument==null || nextInstrument==null || quarterInstrument==null || thisTickerIndex==null || nextTickerIndex==null || quarterTickerIndex==null)
			{
				futureInstrumentService.refresh();
			}
			// 生成处理图表图表数据
			// 当周与次周
			//if (thisInstrument != null && nextInstrument != null) {
			//	processData(hc, "tn", thisInstrument.getInstrument_id(), nextInstrument.getInstrument_id(),thisTickerIndex,nextTickerIndex);
			//}
			// 当周与季度
			if (thisInstrument != null && quarterInstrument != null) {
				processData(hc, "tq", thisInstrument.getInstrument_id(), quarterInstrument.getInstrument_id(),thisTickerIndex,quarterTickerIndex);
			}
			// 次周与季度
			//if (nextInstrument != null && quarterInstrument != null) {
			//	processData(hc, "nq", nextInstrument.getInstrument_id(), quarterInstrument.getInstrument_id(),nextTickerIndex,quarterTickerIndex);
			//}
		}
	}

	public void processData(HedgingContext hedgingContext, String type, String thisInstrumentId,
			String nextInstrumentId,Ticker thisTickerIndex,Ticker nextTickerIndex) {
		float sell_buy_value = 0f;
		Level2Bean level2Buy = instrumentsDepthService.getBuyFirst(thisInstrumentId);  //通过InstrumentId获取最新成交价，参考websocketApi 公共Ticker频道
		Level2Bean level2Sell = instrumentsDepthService.getSellFirst(nextInstrumentId);

		if (level2Buy != null && level2Sell != null) {
			// 开仓策略 近期开空，远期开多  (买1-卖1)/卖1  现货为：websocketApi现货部分中的 公共Ticker频道 last数据（参数为InstrumentId：币对，交易时间）
			sell_buy_value = (level2Buy.getFloatPrice() - level2Sell.getFloatPrice()) / level2Sell.getFloatPrice();
		}
		float buy_sell_value = 0f;
		level2Buy = instrumentsDepthService.getBuyFirst(nextInstrumentId);
		level2Sell = instrumentsDepthService.getSellFirst(thisInstrumentId);
		if (level2Buy != null && level2Sell != null) {
			// 开仓策略 近期开多，远期开空
			buy_sell_value = (level2Buy.getFloatPrice() - level2Sell.getFloatPrice()) / level2Sell.getFloatPrice();
		}

		float aoteman_index = 0f;
		float dangzhou = 0f;
		float dangji = 0f;
		float dangzhou_index = 0f;
		TickerBean tickerBean1 = instrumentsTickersService.getLastPrice(thisInstrumentId);
		TickerBean tickerBean2 = instrumentsTickersService.getLastPrice(nextInstrumentId);
		//System.out.println("=========================");
		if(tickerBean1 != null) {
			dangzhou = tickerBean1.getFloatLastPrice();
			//System.out.println("当周：" + tickerBean1.getFloatLastPrice());
		}
		if(tickerBean2 != null) {
			dangji = tickerBean1.getFloatLastPrice();
			//System.out.println("当季：" + tickerBean2.getFloatLastPrice());
		}
		if (tickerBean1 != null && tickerBean2 != null) {
			// 奥特曼触发指数
			aoteman_index = (tickerBean2.getFloatLastPrice() - tickerBean1.getFloatLastPrice()) ;
		}
		if(thisTickerIndex != null){
			//System.out.println("当周5分钟指标a：" + thisTickerIndex.getLast());
		}
		if(nextTickerIndex != null){
			//System.out.println("当季5分钟指标：" + nextTickerIndex.getLast());
		}
		//System.out.println("=========================");
		Map<String, Object> map = new HashMap<String, Object>();// 装的是%之后的结果
		map.put("type", type);
		map.put("time", System.currentTimeMillis()+"");
		map.put("s_b_v", sell_buy_value * 100f);
		map.put("b_s_v", buy_sell_value * 100f);
		//map.put("dangzhou_index", thisTickerIndex.getLast());
		map.put("dangzhou", dangzhou);
		//map.put("dangji_index", nextTickerIndex.getLast());
		map.put("dangji", dangji);
		//map.put("a_t_m", aoteman_index * 100f);
		hedgingContext.addHedgingData(map);
		send(type + "_" + hedgingContext.getCoin(), map);
	}

	/**
	 * @param hedgingtype 队列名： tn_btc（现周与次周价差）,tq_btc（现周与季度价差）,nq_btc.....（次周与季度价差）  
	 * @param map
	 */
	public void send(String hedgingtype, Map<String, Object> map) {
		ActiveMQDestination destination = destinationsMap.get(hedgingtype);
		if (destination == null) {
			ActiveMQDestination activeMQDestinations = (ActiveMQDestination) destinations;
			destination = activeMQDestinations.createDestination(hedgingtype);
			destinationsMap.put(hedgingtype, destination);
		}
		jmsTemplate.send(destination, new MessageCreator() {
			@Override
			public Message createMessage(Session session) throws JMSException {
				TextMessage textMessage = session.createTextMessage();
				String msg = JSONObject.toJSONString(map);
				textMessage.setText(msg);
				return textMessage;
			}
		});
	}
}
