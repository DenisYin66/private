package com.yin.service.impl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.okcoin.commons.okex.open.api.bean.futures.result.Ticker;
import com.sun.org.apache.xpath.internal.operations.Bool;
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
	HedgingConfigManager hedgingConfigManager = HedgingConfigManager.getInstance();
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
			if(thisInstrument==null || nextInstrument==null || quarterInstrument==null)
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
				processData(hc, "tq", thisInstrument.getInstrument_id(), quarterInstrument.getInstrument_id());
			}
			// 次周与季度
			//if (nextInstrument != null && quarterInstrument != null) {
			//	processData(hc, "nq", nextInstrument.getInstrument_id(), quarterInstrument.getInstrument_id(),nextTickerIndex,quarterTickerIndex);
			//}
		}
	}

	public void processData(HedgingContext hedgingContext, String type, String thisInstrumentId,
			String nextInstrumentId) {
		//Level2Bean level2Buy = instrumentsDepthService.getBuyFirst(thisInstrumentId);  //通过InstrumentId获取最新成交价，参考websocketApi 公共Ticker频道
		//Level2Bean level2Sell = instrumentsDepthService.getSellFirst(nextInstrumentId);

		float aoteman_index = 0f;
		float dangzhou = 0f;
		float dangji = 0f;
		float dangzhou_index = 0f;
		float dangji_index = 0f;
		String dangzhou_index_time = "";
		String dangji_index_time = "";

		TickerBean tickerBean1 = instrumentsTickersService.getLastPrice(thisInstrumentId);
		TickerBean tickerBean2 = instrumentsTickersService.getLastPrice(nextInstrumentId);
		TickerBean thisTickerIndex = instrumentsTickersService.getFiveMinIndexPrice(thisInstrumentId);
		TickerBean nextTickerIndex = instrumentsTickersService.getFiveMinIndexPrice(nextInstrumentId);

		if(tickerBean1 != null) {
			dangzhou = tickerBean1.getFloatLastPrice();
		}
		if(tickerBean2 != null) {
			dangji = tickerBean2.getFloatLastPrice();
		}
		if(thisTickerIndex != null){
			dangzhou_index = thisTickerIndex.getFloatIndexPrice();
			Date a = new Date(thisTickerIndex.getTime());
			dangzhou_index_time = a.toString();
		}
		if(nextTickerIndex != null){
			dangji_index = nextTickerIndex.getFloatIndexPrice();
			Date b = new Date(nextTickerIndex.getTime());
			dangji_index_time = b.toString();
		}
		if (tickerBean1 != null && tickerBean2 != null) {
			// 奥特曼触发指数
			aoteman_index = ((dangji - dangji_index) / dangji_index ) / ((dangzhou - dangzhou_index) / dangzhou_index);
		}
		List<HedgingConfig> configs = hedgingConfigManager.getConfigs("btc", "tq");
		boolean isOpen = false;
		String opentime = "";
		for(HedgingConfig config : configs) {
			isOpen = openHedging(config,tickerBean1, tickerBean2, thisTickerIndex, nextTickerIndex);
			if(isOpen){
				opentime = new Date(tickerBean1.getTime()).toString();
			}
		}
		Map<String, Object> map = new HashMap<String, Object>();// 装的是%之后的结果
		map.put("type", type);
		map.put("time", System.currentTimeMillis()+"");
		map.put("s_b_v", 0 * 100f);
		map.put("b_s_v", 0 * 100f);
		map.put("dangzhou", dangzhou);
		map.put("dangji", dangji);
		map.put("dangzhou_index", dangzhou_index);
		map.put("dangji_index",dangji_index);
		map.put("dangzhou", dangzhou);
		map.put("dangzhou_index_time",dangzhou_index_time);
		map.put("dangji_index_time",dangji_index_time);
		map.put("a_t_m",aoteman_index );
		map.put("open",isOpen);
		map.put("opentime",opentime);
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

	public boolean openHedging(HedgingConfig config, TickerBean xianjia1, TickerBean xianjia2,
								TickerBean jizhunjia1,TickerBean jizhunjia2) {
		//1.判断参数是否初始化
		if(xianjia1 == null || xianjia2 == null || jizhunjia1 == null || jizhunjia2 == null){
			return false;
		}
		float atm_index_config = config.getAtmInRate();
		float djz_diff_config = config.getDangjizhouDiffRate();
		float dangzhou = xianjia1.getFloatLastPrice();
		float dangji = xianjia2.getFloatLastPrice();
		float dangzhou_index = jizhunjia1.getFloatIndexPrice();
		float dangji_index = jizhunjia2.getFloatIndexPrice();
		float atm_index = ((dangji - dangji_index) / dangji_index ) / ((dangzhou - dangzhou_index) / dangzhou_index);

		//2.判断参数是否符合基础的数学条件
		if((dangji - dangji_index) == 0f || (dangzhou - dangji_index) == 0f || dangji_index == 0f || dangzhou_index == 0f){
			return false;
		}
		float dangji_f = (dangji - dangji_index) / dangji_index;
		float dangzhou_f = (dangzhou - dangzhou_index) / dangzhou_index;

		if(config.getAtmInSign() != 1) {
			if (config.isStart() && atm_index >= atm_index_config && Math.abs(dangji_f - dangzhou_f) > djz_diff_config) {
				return true;
			}
		}else{
			if (config.isStart() && atm_index <= atm_index_config && Math.abs(dangji_f - dangzhou_f) > djz_diff_config) {
				return true;
			}
		}
		return false;
	}
}
