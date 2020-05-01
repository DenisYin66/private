package com.yin.service.impl;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.annotation.PostConstruct;

import com.okcoin.commons.okex.open.api.bean.futures.result.Ticker;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.okcoin.commons.okex.open.api.bean.futures.result.Instruments;
import com.okcoin.commons.okex.open.api.service.futures.FuturesMarketAPIService;
import com.okcoin.commons.okex.open.api.service.futures.impl.FuturesMarketAPIServiceImpl;
import com.okex.websocket.FutureInstrument;
import com.yin.service.FutureInstrumentService;

/**
 * 虚拟币合约ID列表
 * @author yin
 * @createDate 2018年12月27日 上午10:01:22
 */
@Service("futureInstrumentService")
@EnableScheduling
public class FutureInstrumentServiceImpl
		implements FutureInstrumentService{
	private FuturesMarketAPIService futuresMarketV3;
	private Map<String, FutureInstrument> cacheInstruments = new HashMap<>();
	private Map<String, FutureInstrument> cachePeriodInstruments = new HashMap<>();
	private Map<String, Ticker> cacheFiveMinPeriodTickers = new HashMap<>();

	@Autowired
	private WebSoketClient client;
	@Autowired
	private SystemConfig systemConfig;
	private List<Instruments> getInstruments()
	{
		List<Instruments> instruments = futuresMarketV3.getInstruments();

		List<Instruments> result = new ArrayList<>();
		for(Instruments instrument : instruments){
			if(instrument.getUnderlying_index().indexOf("USDT") != -1){
				continue;
			}
			result.add(instrument);
		}
		return result;
	}

	private  String findNextQuarterString(List<Instruments> instruments){
		//过滤掉次季合约
		long nextQuarterInstruments = 0;
		String nextQuarterString = "";
		for(int i=0;i<4;i++){
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			try {
				long time = format.parse(instruments.get(i).getDelivery()).getTime();
				if(time > nextQuarterInstruments){
					nextQuarterInstruments = time;
					nextQuarterString = instruments.get(i).getDelivery();
				}
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		return nextQuarterString;
	}

	private Ticker getTicker(String instrumentId){
		Ticker ticker = futuresMarketV3.getInstrumentTicker(instrumentId);
		return ticker;
	}

	@Scheduled(cron = "0/3 0-30 16 ? * FRI") // 每个星期五下午16点0分到30分，每隔3秒刷新一次
	@Cacheable(value = "instrumentCache", sync=true,key = "#root.methodName")
	public void refresh() {
		SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		Calendar cal = Calendar.getInstance();
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		// 将列表根据币种进行排序，币种的第一个合约当作当周，第二个合约当作次周，第三个合约当作季度，其他合约忽略
		List<Instruments> instruments = getInstruments();
		String currentDate = format.format(cal.getTime());
		cal.add(Calendar.DATE, 7);
		String next7Date = format.format(cal.getTime());
		cal.add(Calendar.DATE, 7);
		String next14Date = format.format(cal.getTime());
		System.out.println(currentDate + " " + next7Date + "  " + next14Date);

		//找出基准价格
		long fivemin = 5 * 60 *1000;
		Calendar cal2 = Calendar.getInstance();
		long currentTime = cal2.getTime().getTime();
		Date indexDate = new Date(currentTime - currentTime%fivemin);
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.sss'Z'");

		if (instruments != null) {
			for (Instruments instrument : instruments) {
				FutureInstrument futureInstrument = new FutureInstrument();
				BeanUtils.copyProperties(instrument, futureInstrument);
				try {
					futureInstrument
							.setDeliveryTime(dateformat.parse(futureInstrument.getDelivery() + " 16:00:00").getTime());
				} catch (ParseException e) {
				}
				String nextQuarterString = findNextQuarterString(instruments);
				if(!"".equals(nextQuarterString)  && futureInstrument.getDelivery().indexOf(nextQuarterString) != -1){
					continue;  //过滤次季合约
				}
				if (currentDate.compareTo(futureInstrument.getDelivery()) > 0)// 已过期
				{
					continue;
				}
				if (currentDate.compareTo(futureInstrument.getDelivery()) == 0 && hour>15)// 已过期
				{
					continue;
				}
				if(futureInstrument.getUnderlying_index().indexOf("USDT") != -1){
					continue;
				}

				if (next7Date.compareTo(futureInstrument.getDelivery()) > 0
						|| (hour > 15 && next7Date.compareTo(futureInstrument.getDelivery()) == 0))
					futureInstrument.setContractType("this_week");
				else if (next14Date.compareTo(futureInstrument.getDelivery()) > 0
						|| (hour > 15 && next14Date.compareTo(futureInstrument.getDelivery()) == 0))
					futureInstrument.setContractType("next_week");
				else
					futureInstrument.setContractType("quarter");
				System.out.println(futureInstrument.getInstrument_id() + " " + futureInstrument.getDelivery() + " "
						+ futureInstrument.getUnderlying_index() + "  " + futureInstrument.getContractType());
				cacheInstruments.put(futureInstrument.getInstrument_id(), futureInstrument);
				String key = periodKey(futureInstrument.getUnderlying_index(), futureInstrument.getContractType());
				cachePeriodInstruments.put(key, futureInstrument);

				Ticker ticker = getTicker(futureInstrument.getInstrument_id());
				String timestamp = ticker.getTimestamp();
				try {
					long time = sdf.parse(timestamp).getTime();
					System.out.println(time + "- " + (indexDate.getTime() - 8 * 60 * 60 * 1000));
					if (time > indexDate.getTime() - 8 * 60 * 60 * 1000) {
						if (cacheFiveMinPeriodTickers.get(key) == null) {
							cacheFiveMinPeriodTickers.put(key, ticker);
						} else {
							long cacheTickerTime = sdf.parse(cacheFiveMinPeriodTickers.get(key).getTimestamp()).getTime();
							//1.判断cache是否过期
							if(cacheTickerTime < indexDate.getTime() - 8 * 60 * 60 * 1000){
								cacheFiveMinPeriodTickers.put(key, ticker);
							}else {
								//2.判断是否为最接近的Index
								if(time < cacheTickerTime){
									cacheFiveMinPeriodTickers.put(key,ticker);
								}
							}
						}
					}
				} catch (ParseException e) {
					System.out.println("日期解析错误");
				}

			}
		}

		List<String> subscribes = getSubscribes();
		System.out.println(subscribes);
		client.tryAddChannel("subscribe", subscribes);
	}

	private String periodKey(String coin,String contractType) {
		return coin.toUpperCase() + "-" + contractType;
	}

	/**
	 * 
	 * @param coin   (BTC,ETC..)
	 * @param bi {USDT,USD}
	 * @param period (this_week,next_week,quarter)
	 * @return
	 */
	@Override
	public FutureInstrument getFutureInstrument(String coin, String contractType) {
	
		String key = periodKey(coin,contractType);
//		if (!cachePeriodInstruments.containsKey(key))
//		{
//			refresh();
//		}
			
		return cachePeriodInstruments.get(key);
	}

	/**
	 *
	 * @param coin   (BTC,ETC..)
	 * @param period (this_week,next_week,quarter)
	 * @return
	 */
	@Override
	public Ticker getInstrumentTicker(String coin, String contractType) {

		String key = periodKey(coin, contractType);
//		if (!cachePeriodInstruments.containsKey(key))
//		{
//			refresh();
//		}

		return cacheFiveMinPeriodTickers.get(key);
	}

	@Override
	public List<String> getSubscribes() {
		String[] coins = null;
		if (!Strings.isNullOrEmpty(systemConfig.getCoins())) {
			coins = systemConfig.getCoins().split(",");
		}
		return getSubscribes(coins);
	}

	private List<String> getSubscribes(String[] coins) {
		List<String> subscribes = new LinkedList<String>();
		for (String coin : coins) {
			FutureInstrument instrument = getFutureInstrument(coin, "this_week");
			if (instrument != null) {
				subscribes.add("futures/depth5:" + instrument.getInstrument_id());
				subscribes.add("futures/order:" + instrument.getInstrument_id());
				//subscribes.add("futures/trade:" + instrument.getInstrument_id());
				subscribes.add("futures/ticker:" + instrument.getInstrument_id());
			}
			instrument = getFutureInstrument(coin, "next_week");
			if (instrument != null) {
				subscribes.add("futures/depth5:" + instrument.getInstrument_id());
				subscribes.add("futures/order:" + instrument.getInstrument_id());
				//subscribes.add("futures/trade:" + instrument.getInstrument_id());
				subscribes.add("futures/ticker:" + instrument.getInstrument_id());
			}
			instrument = getFutureInstrument(coin, "quarter");
			if (instrument != null) {
				subscribes.add("futures/depth5:" + instrument.getInstrument_id());
				subscribes.add("futures/order:" + instrument.getInstrument_id());
				//subscribes.add("futures/trade:" + instrument.getInstrument_id());
				subscribes.add("futures/ticker:" + instrument.getInstrument_id());
			}
		}
		return subscribes;
	}

	@PostConstruct
	private void init() {
		futuresMarketV3 = new FuturesMarketAPIServiceImpl(systemConfig);

	}

	@Override
	public FutureInstrument getFutureInstrument(String instrumentId) {
//		// TODO Auto-generated method stub
//		if (!cacheInstruments.containsKey(instrumentId))
//			refresh();
		return cacheInstruments.get(instrumentId);
	}


}
