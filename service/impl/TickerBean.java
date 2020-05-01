package com.yin.service.impl;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;

public class TickerBean {
	private String instrumentId;
	private BigDecimal  lastPrice;
	private BigDecimal indexPrice;
	private String volume;
	private long time;
	private String table;
	public String getTable() {
		return table;
	}
	public void setTable(String table) {
		this.table = table;
	}
	public BigDecimal  getLastPrice() {
		return lastPrice;
	}
	public float getFloatLastPrice()
	{
		return lastPrice.floatValue();
	}
	public void setLastPrice(BigDecimal  price) {
		this.lastPrice = price;
	}
	public void setIndexPricePrice(BigDecimal  price) {
		this.indexPrice = price;
	}
	public float getFloatIndexPrice()
	{
		return indexPrice.floatValue();
	}

	public String getVolume() {
		return volume;
	}

	public void setVolume(String volume) {
		this.volume = volume;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public String getInstrumentId() {
		return instrumentId;
	}

	public void setInstrumentId(String instrumentId) {
		this.instrumentId = instrumentId;
	}

	public int getIntVolume() {
		try {
			return NumberFormat.getInstance().parse(volume).intValue();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	public double getDoubleVolume() {
		try {
			return NumberFormat.getInstance().parse(volume).doubleValue();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
}
